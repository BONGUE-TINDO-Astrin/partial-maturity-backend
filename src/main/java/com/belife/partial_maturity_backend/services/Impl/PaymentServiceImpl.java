package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.config.properties.InterestProperties;
import com.belife.partial_maturity_backend.dtos.requests.CancelPaymentRequest;
import com.belife.partial_maturity_backend.dtos.responses.InterestCalculationLineResponse;
import com.belife.partial_maturity_backend.dtos.responses.InterestSimulationResponse;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.dtos.responses.PaymentDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.PaymentResponse;
import com.belife.partial_maturity_backend.dtos.responses.PaymentSummaryResponse;
import com.belife.partial_maturity_backend.entities.PaymentDetailEntity;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;
import com.belife.partial_maturity_backend.enums.CalculationEventType;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.exceptions.ConcurrentPaymentOperationException;
import com.belife.partial_maturity_backend.exceptions.InvalidPolicyNumberException;
import com.belife.partial_maturity_backend.exceptions.PaymentNotAllowedException;
import com.belife.partial_maturity_backend.exceptions.PaymentNotFoundException;
import com.belife.partial_maturity_backend.exceptions.PolicyNotFoundException;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.repositories.specifications.PaymentSpecifications;
import com.belife.partial_maturity_backend.services.AuditService;
import com.belife.partial_maturity_backend.services.BusinessDateProvider;
import com.belife.partial_maturity_backend.services.InterestCalculationEngine;
import com.belife.partial_maturity_backend.services.PaymentService;
import com.belife.partial_maturity_backend.services.models.AuditRecordCommand;
import com.belife.partial_maturity_backend.services.models.CalculationEvent;
import com.belife.partial_maturity_backend.services.models.PaymentCancellationEligibility;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.belife.partial_maturity_backend.utils.FinancialAmountUtils.normalize;
import static com.belife.partial_maturity_backend.utils.FinancialAmountUtils.zero;

/**
 * Implémente le cycle de vie des paiements.
 *
 * <p>Le montant d'un paiement n'est jamais fourni par Angular.
 * Le backend verrouille les maturités de la police, recharge
 * les paiements valides puis recalcule entièrement la situation.</p>
 *
 * <p>Seul le paiement PAID le plus récent d'une police peut
 * être annulé. Cette règle garantit que les paiements sont
 * annulés dans l'ordre chronologique inverse.</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final PolicyMaturityRepository policyMaturityRepository;

    private final PaymentRepository paymentRepository;

    private final AuditService auditService;

    private final InterestCalculationEngine calculationEngine;

    private final InterestProperties interestProperties;

    /**
     * Horloge technique réelle utilisée notamment
     * pour l'instant d'annulation.
     */
    private final Clock clock;

    /**
     * Fournit la date métier, éventuellement simulée
     * dans le profil de développement.
     */
    private final BusinessDateProvider businessDateProvider;

    /**
     * Retourne une page de paiements selon des filtres
     * optionnels.
     *
     * <p>La liste utilise un DTO léger et ne charge pas les
     * lignes justificatives. Les détails sont chargés uniquement
     * lors de l'ouverture d'un paiement.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentSummaryResponse>
    searchPayments(
            String search,
            PaymentStatus status,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);

        int safeSize =
                Math.min(
                        Math.max(size, 1),
                        MAXIMUM_PAGE_SIZE
                );

        Sort sort =
                Sort.by(
                        Sort.Order.desc(
                                "paymentDate"
                        ),
                        Sort.Order.desc("id")
                );

        Pageable pageable =
                PageRequest.of(
                        safePage,
                        safeSize,
                        sort
                );

        Specification<PaymentEntity> specification =
                Specification
                        .where(
                                PaymentSpecifications
                                        .policyNumberContains(
                                                search
                                        )
                        )
                        .and(
                                PaymentSpecifications
                                        .hasStatus(status)
                        );

        Page<PaymentEntity> paymentPage =
                paymentRepository.findAll(
                        specification,
                        pageable
                );

        List<PaymentSummaryResponse> content =
                paymentPage.getContent()
                        .stream()
                        .map(this::toSummaryResponse)
                        .toList();

        return PageResponse.from(
                paymentPage,
                content
        );
    }

    /**
     * Enregistre atomiquement le paiement total
     * de la situation courante d'une police.
     */
    @Override
    @Transactional
    public PaymentResponse recordPayment(
            String policyNumber,
            String currentUsername
    ) {
        String normalizedPolicyNumber =
                normalizePolicyNumber(
                        policyNumber
                );

        List<PolicyMaturityEntity> maturities =
                lockPolicyMaturities(
                        normalizedPolicyNumber
                );

        if (maturities.isEmpty()) {
            throw new PolicyNotFoundException(
                    normalizedPolicyNumber
            );
        }

        List<PaymentEntity> paidPayments =
                paymentRepository
                        .findAllByPolicyNumberIgnoreCaseAndStatusOrderByPaymentDateAscIdAsc(
                                normalizedPolicyNumber,
                                PaymentStatus.PAID
                        );

        LocalDate paymentDate =
                businessDateProvider.currentDate();

        String storedPolicyNumber =
                maturities.getFirst()
                        .getPolicyNumber();

        List<CalculationEvent> events =
                buildEvents(
                        maturities,
                        paidPayments
                );

        LocalDate interestEndDate =
                resolveInterestEndDate(
                        maturities
                );

        InterestSimulationResponse simulation =
                calculationEngine.calculate(
                        storedPolicyNumber,
                        paymentDate,
                        interestEndDate,
                        interestProperties.annualRate(),
                        events
                );

        if (simulation.balance().signum() <= 0) {
            throw new PaymentNotAllowedException(
                    "La situation de la police est déjà soldée "
                            + "ou ne possède aucun montant payable."
            );
        }

        PaymentEntity payment =
                createPayment(simulation);

        PaymentEntity savedPayment =
                paymentRepository.saveAndFlush(
                        payment
                );

        auditService.record(
                new AuditRecordCommand(
                        AuditEventType.PAYMENT_RECORDED,
                        AuditResourceType.PAYMENT,
                        savedPayment.getId().toString(),
                        savedPayment.getPolicyNumber(),
                        currentUsername,
                        "Enregistrement du paiement total n° "
                                + savedPayment.getId()
                                + " pour la police "
                                + savedPayment.getPolicyNumber()
                                + ".",
                        Map.of(
                                "paymentId",
                                savedPayment.getId(),
                                "policyNumber",
                                savedPayment.getPolicyNumber(),
                                "paymentDate",
                                savedPayment
                                        .getPaymentDate()
                                        .toString(),
                                "calculationDate",
                                savedPayment
                                        .getCalculationDate()
                                        .toString(),
                                "annualRate",
                                savedPayment
                                        .getAnnualRate()
                                        .toPlainString(),
                                "capitalAmount",
                                savedPayment
                                        .getCapitalAmount()
                                        .toPlainString(),
                                "interestAmount",
                                savedPayment
                                        .getInterestAmount()
                                        .toPlainString(),
                                "paidAmount",
                                savedPayment
                                        .getPaidAmount()
                                        .toPlainString(),
                                "completedCycles",
                                savedPayment.getCompletedCycles(),
                                "status",
                                savedPayment
                                        .getStatus()
                                        .name()
                        )
                )
        );

        /*
         * Sous le verrou de la police, le paiement qui vient
         * d'être créé est nécessairement le paiement PAID
         * le plus récent.
         */
        return toResponse(
                savedPayment,
                PaymentCancellationEligibility.allowed()
        );
    }

    /**
     * Annule le paiement PAID le plus récent d'une police.
     *
     * <p>Les paiements doivent être annulés dans l'ordre
     * chronologique inverse. Un ancien paiement ne peut donc
     * pas être annulé tant qu'un paiement PAID plus récent
     * existe pour la même police.</p>
     */
    @Override
    @Transactional
    public PaymentResponse cancelPayment(
            Long paymentId,
            CancelPaymentRequest request,
            String currentUsername
    ) {
        /*
         * Première lecture permettant d'identifier
         * la police avant l'acquisition des verrous.
         */
        PaymentEntity preliminaryPayment =
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(
                                () ->
                                        new PaymentNotFoundException(
                                                paymentId
                                        )
                        );

        /*
         * Le verrou des maturités constitue le verrou logique
         * de la police. Il empêche un nouveau paiement concurrent
         * de modifier la chronologie pendant l'annulation.
         */
        lockPolicyMaturities(
                preliminaryPayment
                        .getPolicyNumber()
        );

        PaymentEntity payment =
                lockPayment(paymentId);

        /*
         * La décision est refaite après acquisition
         * des verrous.
         */
        validatePaymentCanBeCancelled(
                payment
        );

        payment.setStatus(
                PaymentStatus.CANCELLED
        );

        /*
         * L'instant d'annulation est une donnée technique.
         * Il utilise donc l'horloge réelle et non la date
         * métier configurable.
         */
        payment.setCancelledAt(
                Instant.now(clock)
        );

        payment.setCancelledBy(
                currentUsername
        );

        payment.setCancellationReason(
                request.reason().trim()
        );

        try {
            PaymentEntity cancelledPayment =
                    paymentRepository
                            .saveAndFlush(payment);

            auditService.record(
                    new AuditRecordCommand(
                            AuditEventType
                                    .PAYMENT_CANCELLED,
                            AuditResourceType.PAYMENT,
                            cancelledPayment
                                    .getId()
                                    .toString(),
                            cancelledPayment
                                    .getPolicyNumber(),
                            currentUsername,
                            "Annulation du paiement n° "
                                    + cancelledPayment.getId()
                                    + " de la police "
                                    + cancelledPayment
                                    .getPolicyNumber()
                                    + ".",
                            Map.of(
                                    "paymentId",
                                    cancelledPayment.getId(),
                                    "policyNumber",
                                    cancelledPayment
                                            .getPolicyNumber(),
                                    "paidAmount",
                                    cancelledPayment
                                            .getPaidAmount()
                                            .toPlainString(),
                                    "previousStatus",
                                    PaymentStatus.PAID.name(),
                                    "newStatus",
                                    cancelledPayment
                                            .getStatus()
                                            .name(),
                                    "cancelledAt",
                                    cancelledPayment
                                            .getCancelledAt()
                                            .toString(),
                                    "cancelledBy",
                                    cancelledPayment
                                            .getCancelledBy(),
                                    "cancellationReason",
                                    cancelledPayment
                                            .getCancellationReason()
                            )
                    )
            );

            return toResponse(
                    cancelledPayment,
                    PaymentCancellationEligibility
                            .blocked(
                                    "Le paiement est déjà annulé."
                            )
            );
        } catch (
                ObjectOptimisticLockingFailureException exception
        ) {
            throw new ConcurrentPaymentOperationException();
        }
    }

    /**
     * Retourne un paiement avec ses lignes justificatives
     * et son éligibilité actuelle à l'annulation.
     */
    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(
            Long paymentId
    ) {
        PaymentEntity payment =
                paymentRepository
                        .findByIdWithDetails(paymentId)
                        .orElseThrow(
                                () ->
                                        new PaymentNotFoundException(
                                                paymentId
                                        )
                        );

        PaymentCancellationEligibility eligibility =
                evaluateCancellationEligibility(
                        payment
                );

        return toResponse(
                payment,
                eligibility
        );
    }

    /**
     * Retourne l'historique complet des paiements
     * d'une police.
     *
     * <p>L'éligibilité est déterminée en mémoire afin
     * d'éviter une requête supplémentaire par paiement.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPolicyPayments(
            String policyNumber
    ) {
        String normalizedPolicyNumber =
                normalizePolicyNumber(
                        policyNumber
                );

        List<PaymentEntity> payments =
                paymentRepository
                        .findAllByPolicyNumberIgnoreCaseOrderByPaymentDateDescIdDesc(
                                normalizedPolicyNumber
                        );

        Long latestPaidPaymentId =
                payments.stream()
                        .filter(payment ->
                                payment.getStatus()
                                        == PaymentStatus.PAID
                        )
                        .max(
                                Comparator
                                        .comparing(
                                                PaymentEntity
                                                        ::getPaymentDate
                                        )
                                        .thenComparing(
                                                PaymentEntity::getId
                                        )
                        )
                        .map(PaymentEntity::getId)
                        .orElse(null);

        return payments.stream()
                .map(payment ->
                        toResponse(
                                payment,
                                cancellationEligibilityForHistory(
                                        payment,
                                        latestPaidPaymentId
                                )
                        )
                )
                .toList();
    }

    /**
     * Verrouille toutes les maturités d'une police.
     */
    private List<PolicyMaturityEntity>
    lockPolicyMaturities(
            String policyNumber
    ) {
        try {
            return policyMaturityRepository
                    .findAllByPolicyNumberForPaymentUpdate(
                            policyNumber
                    );
        } catch (
                PessimisticLockingFailureException exception
        ) {
            throw new ConcurrentPaymentOperationException();
        }
    }

    /**
     * Verrouille le paiement ciblé.
     */
    private PaymentEntity lockPayment(
            Long paymentId
    ) {
        try {
            return paymentRepository
                    .findByIdForUpdate(paymentId)
                    .orElseThrow(
                            () ->
                                    new PaymentNotFoundException(
                                            paymentId
                                    )
                    );
        } catch (
                PessimisticLockingFailureException exception
        ) {
            throw new ConcurrentPaymentOperationException();
        }
    }

    /**
     * Vérifie sous verrou que le paiement ciblé est encore
     * le paiement PAID le plus récent de la police.
     */
    private void validatePaymentCanBeCancelled(
            PaymentEntity payment
    ) {
        if (
                payment.getStatus()
                        != PaymentStatus.PAID
        ) {
            throw new PaymentNotAllowedException(
                    "Seul un paiement au statut PAID "
                            + "peut être annulé."
            );
        }

        List<PaymentEntity> paidPayments =
                paymentRepository
                        .findAllByPolicyNumberIgnoreCaseAndStatusOrderByPaymentDateAscIdAsc(
                                payment.getPolicyNumber(),
                                PaymentStatus.PAID
                        );

        if (paidPayments.isEmpty()) {
            throw new PaymentNotAllowedException(
                    "Le paiement n'est plus présent parmi "
                            + "les paiements valides de la police."
            );
        }

        PaymentEntity latestPaidPayment =
                paidPayments.getLast();

        if (
                !latestPaidPayment.getId()
                        .equals(payment.getId())
        ) {
            throw new PaymentNotAllowedException(
                    "Seul le paiement valide le plus récent "
                            + "de la police peut être annulé. "
                            + "Annulez d'abord les paiements "
                            + "plus récents."
            );
        }
    }

    /**
     * Calcule l'éligibilité affichée dans le détail.
     *
     * <p>Cette analyse améliore l'expérience utilisateur.
     * La méthode d'annulation refait systématiquement
     * le contrôle sous verrou.</p>
     */
    private PaymentCancellationEligibility
    evaluateCancellationEligibility(
            PaymentEntity payment
    ) {
        if (
                payment.getStatus()
                        == PaymentStatus.CANCELLED
        ) {
            return PaymentCancellationEligibility
                    .blocked(
                            "Le paiement est déjà annulé."
                    );
        }

        List<PaymentEntity> paidPayments =
                paymentRepository
                        .findAllByPolicyNumberIgnoreCaseAndStatusOrderByPaymentDateAscIdAsc(
                                payment.getPolicyNumber(),
                                PaymentStatus.PAID
                        );

        if (paidPayments.isEmpty()) {
            return PaymentCancellationEligibility
                    .blocked(
                            "Aucun paiement valide correspondant "
                                    + "n'est disponible."
                    );
        }

        PaymentEntity latestPaidPayment =
                paidPayments.getLast();

        if (
                latestPaidPayment.getId()
                        .equals(payment.getId())
        ) {
            return PaymentCancellationEligibility.allowed();
        }

        return PaymentCancellationEligibility.blocked(
                "Seul le paiement valide le plus récent "
                        + "de la police peut être annulé. "
                        + "Annulez d'abord les paiements "
                        + "plus récents."
        );
    }

    /**
     * Détermine l'éligibilité à partir de l'historique
     * déjà chargé.
     */
    private PaymentCancellationEligibility
    cancellationEligibilityForHistory(
            PaymentEntity payment,
            Long latestPaidPaymentId
    ) {
        if (
                payment.getStatus()
                        == PaymentStatus.CANCELLED
        ) {
            return PaymentCancellationEligibility
                    .blocked(
                            "Le paiement est déjà annulé."
                    );
        }

        if (
                latestPaidPaymentId != null
                        && latestPaidPaymentId.equals(
                        payment.getId()
                )
        ) {
            return PaymentCancellationEligibility.allowed();
        }

        return PaymentCancellationEligibility.blocked(
                "Seul le paiement valide le plus récent "
                        + "de la police peut être annulé. "
                        + "Annulez d'abord les paiements "
                        + "plus récents."
        );
    }

    /**
     * Fusionne maturités et paiements valides.
     *
     * <p>Le moteur applique lui-même le tri chronologique,
     * notamment maturité avant paiement lorsque les dates
     * sont identiques.</p>
     */
    private List<CalculationEvent> buildEvents(
            List<PolicyMaturityEntity> maturities,
            List<PaymentEntity> paidPayments
    ) {
        List<CalculationEvent> events =
                new ArrayList<>();

        maturities.stream()
                .map(this::toMaturityEvent)
                .forEach(events::add);

        paidPayments.stream()
                .map(this::toPaymentEvent)
                .forEach(events::add);

        return events;
    }

    private CalculationEvent toMaturityEvent(
            PolicyMaturityEntity maturity
    ) {
        return new CalculationEvent(
                maturity.getId(),
                maturity.getMaturityDate(),
                CalculationEvent.EventKind.MATURITY,
                maturity.getMaturityRank(),
                maturity.getMaturityType(),
                maturity.getMaturityAmount()
        );
    }

    private CalculationEvent toPaymentEvent(
            PaymentEntity payment
    ) {
        return new CalculationEvent(
                payment.getId(),
                payment.getPaymentDate(),
                CalculationEvent.EventKind.PAYMENT,
                0,
                "Paiement n° " + payment.getId(),
                payment.getPaidAmount()
        );
    }

    /**
     * Crée le paiement et la copie de toutes
     * les lignes ayant justifié son montant.
     */
    private PaymentEntity createPayment(
            InterestSimulationResponse simulation
    ) {
        PaymentEntity payment =
                new PaymentEntity();

        payment.setPolicyNumber(
                simulation.policyNumber()
        );

        payment.setPaymentDate(
                simulation.calculationDate()
        );

        payment.setCalculationDate(
                simulation.calculationDate()
        );

        payment.setAnnualRate(
                simulation.annualRate()
        );

        payment.setCapitalAmount(
                normalize(
                        simulation.openCapital()
                )
        );

        payment.setInterestAmount(
                normalize(
                        simulation.openInterest()
                )
        );

        payment.setPaidAmount(
                normalize(
                        simulation.balance()
                )
        );

        payment.setCompletedCycles(
                simulation.completedCycles()
        );

        payment.setStatus(
                PaymentStatus.PAID
        );

        simulation.lines()
                .stream()
                .sorted(
                        Comparator.comparingInt(
                                InterestCalculationLineResponse
                                        ::sequence
                        )
                )
                .map(this::toPaymentDetail)
                .forEach(payment::addDetail);

        /*
         * Ajoute une dernière ligne expliquant la remise
         * du solde à zéro par le paiement total.
         */
        PaymentDetailEntity paymentLine =
                new PaymentDetailEntity();

        paymentLine.setSequenceNumber(
                simulation.lines().size() + 1
        );

        paymentLine.setEventDate(
                simulation.calculationDate()
        );

        paymentLine.setEventType(
                CalculationEventType.PAYMENT_APPLIED
        );

        paymentLine.setDescription(
                "Paiement total de la situation financière."
        );

        paymentLine.setCycleNumber(null);

        paymentLine.setBalanceBefore(
                normalize(
                        simulation.balance()
                )
        );

        paymentLine.setCapitalAdded(
                zero()
        );

        paymentLine.setAnnualRate(null);

        paymentLine.setInterestAmount(
                zero()
        );

        paymentLine.setPaidAmount(
                normalize(
                        simulation.balance()
                )
        );

        paymentLine.setBalanceAfter(
                zero()
        );

        payment.addDetail(paymentLine);

        return payment;
    }

    private PaymentDetailEntity toPaymentDetail(
            InterestCalculationLineResponse line
    ) {
        PaymentDetailEntity detail =
                new PaymentDetailEntity();

        detail.setSequenceNumber(
                line.sequence()
        );

        detail.setEventDate(
                line.eventDate()
        );

        detail.setEventType(
                line.eventType()
        );

        detail.setDescription(
                line.description()
        );

        detail.setCycleNumber(
                line.cycleNumber()
        );

        detail.setBalanceBefore(
                normalize(
                        line.balanceBefore()
                )
        );

        detail.setCapitalAdded(
                normalize(
                        line.capitalAdded()
                )
        );

        detail.setAnnualRate(
                line.annualRate()
        );

        detail.setInterestAmount(
                normalize(
                        line.interestAmount()
                )
        );

        detail.setPaidAmount(
                normalize(
                        line.paidAmount()
                )
        );

        detail.setBalanceAfter(
                normalize(
                        line.balanceAfter()
                )
        );

        return detail;
    }

    /**
     * Construit la représentation légère utilisée
     * dans la liste paginée.
     *
     * <p>Cette méthode ne consulte volontairement pas
     * les détails du paiement.</p>
     */
    private PaymentSummaryResponse toSummaryResponse(
            PaymentEntity payment
    ) {
        return new PaymentSummaryResponse(
                payment.getId(),
                payment.getPolicyNumber(),
                payment.getPaymentDate(),
                payment.getCalculationDate(),
                payment.getAnnualRate(),
                payment.getCapitalAmount(),
                payment.getInterestAmount(),
                payment.getPaidAmount(),
                payment.getCompletedCycles(),
                payment.getStatus(),
                payment.getCancelledAt(),
                payment.getCreatedAt(),
                payment.getCreatedBy()
        );
    }

    /**
     * Construit la réponse complète d'un paiement.
     */
    private PaymentResponse toResponse(
            PaymentEntity payment,
            PaymentCancellationEligibility eligibility
    ) {
        List<PaymentDetailResponse> details =
                payment.getDetails()
                        .stream()
                        .sorted(
                                Comparator.comparingInt(
                                        PaymentDetailEntity
                                                ::getSequenceNumber
                                )
                        )
                        .map(this::toDetailResponse)
                        .toList();

        return new PaymentResponse(
                payment.getId(),
                payment.getPolicyNumber(),
                payment.getPaymentDate(),
                payment.getCalculationDate(),
                payment.getAnnualRate(),
                payment.getCapitalAmount(),
                payment.getInterestAmount(),
                payment.getPaidAmount(),
                payment.getCompletedCycles(),
                payment.getStatus(),
                payment.getCancelledAt(),
                payment.getCancelledBy(),
                payment.getCancellationReason(),
                payment.getCreatedAt(),
                payment.getCreatedBy(),
                payment.getUpdatedAt(),
                payment.getUpdatedBy(),
                payment.getVersion(),
                eligibility.cancellable(),
                eligibility.blockedReason(),
                details
        );
    }

    private PaymentDetailResponse toDetailResponse(
            PaymentDetailEntity detail
    ) {
        return new PaymentDetailResponse(
                detail.getId(),
                detail.getSequenceNumber(),
                detail.getEventDate(),
                detail.getEventType(),
                detail.getDescription(),
                detail.getCycleNumber(),
                detail.getBalanceBefore(),
                detail.getCapitalAdded(),
                detail.getAnnualRate(),
                detail.getInterestAmount(),
                detail.getPaidAmount(),
                detail.getBalanceAfter()
        );
    }

    /**
     * Retourne la date de fin commune
     * aux maturités de la police.
     */
    private LocalDate resolveInterestEndDate(
            List<PolicyMaturityEntity> maturities
    ) {
        LocalDate interestEndDate =
                maturities.getFirst()
                        .getInterestEndDate();

        boolean inconsistentDate =
                maturities.stream()
                        .anyMatch(maturity ->
                                !interestEndDate.equals(
                                        maturity
                                                .getInterestEndDate()
                                )
                        );

        if (inconsistentDate) {
            throw new IllegalStateException(
                    "La police "
                            + maturities.getFirst()
                            .getPolicyNumber()
                            + " possède plusieurs dates "
                            + "de fin des intérêts."
            );
        }

        return interestEndDate;
    }

    private String normalizePolicyNumber(
            String policyNumber
    ) {
        if (
                policyNumber == null
                        || policyNumber.isBlank()
        ) {
            throw new InvalidPolicyNumberException(
                    "Le numéro de police est obligatoire."
            );
        }

        String normalized =
                policyNumber.trim();

        if (normalized.length() > 100) {
            throw new InvalidPolicyNumberException(
                    "Le numéro de police ne doit pas dépasser "
                            + "100 caractères."
            );
        }

        return normalized;
    }
}