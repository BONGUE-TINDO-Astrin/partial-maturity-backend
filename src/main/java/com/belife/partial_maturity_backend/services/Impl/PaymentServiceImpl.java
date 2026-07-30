package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.config.properties.InterestProperties;
import com.belife.partial_maturity_backend.dtos.requests.CancelPaymentRequest;
import com.belife.partial_maturity_backend.dtos.responses.InterestCalculationLineResponse;
import com.belife.partial_maturity_backend.dtos.responses.InterestSimulationResponse;
import com.belife.partial_maturity_backend.dtos.responses.PaymentDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.PaymentResponse;
import com.belife.partial_maturity_backend.entities.PaymentDetailEntity;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.CalculationEventType;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.exceptions.ConcurrentPaymentOperationException;
import com.belife.partial_maturity_backend.exceptions.InvalidPolicyNumberException;
import com.belife.partial_maturity_backend.exceptions.PaymentNotAllowedException;
import com.belife.partial_maturity_backend.exceptions.PaymentNotFoundException;
import com.belife.partial_maturity_backend.exceptions.PolicyNotFoundException;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.InterestCalculationEngine;
import com.belife.partial_maturity_backend.services.PaymentService;
import com.belife.partial_maturity_backend.services.models.CalculationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.belife.partial_maturity_backend.utils.FinancialAmountUtils.normalize;
import static com.belife.partial_maturity_backend.utils.FinancialAmountUtils.zero;

/**
 * Implémente le cycle de vie des paiements.
 *
 * <p>Le montant envoyé au paiement n'est jamais fourni
 * par Angular. Le backend verrouille les maturités puis
 * recalcule intégralement la situation.</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PolicyMaturityRepository policyMaturityRepository;
    private final PaymentRepository paymentRepository;
    private final InterestCalculationEngine calculationEngine;
    private final InterestProperties interestProperties;
    private final Clock clock;

    /**
     * Enregistre atomiquement un paiement total.
     *
     * <p>Le verrou pessimiste sur les maturités sérialise
     * les paiements d'une même police.</p>
     */
    @Override
    @Transactional
    public PaymentResponse recordPayment(String policyNumber, String currentUsername) {
        String normalizedPolicyNumber = normalizePolicyNumber(policyNumber);

        /*
         * Ce verrou doit être obtenu avant de charger
         * les paiements et de recalculer.
         */
        List<PolicyMaturityEntity> maturities;

        try {
            maturities =
                policyMaturityRepository
                    .findAllByPolicyNumberForPaymentUpdate(normalizedPolicyNumber);
        } catch (PessimisticLockingFailureException exception) {
            throw new ConcurrentPaymentOperationException();
        }

        if (maturities.isEmpty()) {
            throw new PolicyNotFoundException(normalizedPolicyNumber);
        }

        List<PaymentEntity> paidPayments =
            paymentRepository
                .findAllByPolicyNumberIgnoreCaseAndStatusOrderByPaymentDateAscIdAsc(
                    normalizedPolicyNumber,
                    PaymentStatus.PAID
                );

        LocalDate paymentDate = LocalDate.now(clock);

        String storedPolicyNumber = maturities.getFirst().getPolicyNumber();

        List<CalculationEvent> events = buildEvents(maturities, paidPayments);

        InterestSimulationResponse simulation =
            calculationEngine.calculate(
                storedPolicyNumber,
                paymentDate,
                interestProperties.annualRate(),
                events
            );

        if (simulation.balance().signum() <= 0) {
            throw new PaymentNotAllowedException(
                "La situation de la police est déjà soldée ou ne possède aucun montant payable."
            );
        }

        PaymentEntity payment = createPayment(simulation, currentUsername);

        PaymentEntity savedPayment = paymentRepository.saveAndFlush(payment);

        return toResponse(savedPayment);
    }

    /**
     * Annule un paiement existant.
     *
     * <p>Le verrouillage suit toujours l'ordre :</p>
     *
     * <pre>
     * maturités de la police
     * puis paiement
     * </pre>
     *
     * afin de réduire le risque de blocage concurrent.
     */
    @Override
    @Transactional
    public PaymentResponse cancelPayment(Long paymentId, CancelPaymentRequest request, String currentUsername) {
        /*
         * Première lecture permettant d'obtenir la police.
         * La décision métier finale est prise après
         * l'acquisition des verrous.
         */
        PaymentEntity preliminaryPayment =
            paymentRepository.findById(paymentId).orElseThrow( () ->new PaymentNotFoundException(paymentId) );

        policyMaturityRepository.findAllByPolicyNumberForPaymentUpdate( preliminaryPayment.getPolicyNumber() );

        PaymentEntity payment =
            paymentRepository
                .findByIdForUpdate(paymentId)
                .orElseThrow( () -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new PaymentNotAllowedException("Seul un paiement au statut PAID peut être annulé.");
        }

        payment.setStatus(PaymentStatus.CANCELLED);

        payment.setCancelledAt(Instant.now(clock));

        payment.setCancelledBy(currentUsername);

        payment.setCancellationReason(request.reason().trim());

        try {
            PaymentEntity cancelledPayment = paymentRepository.saveAndFlush(payment);

            return toResponse(cancelledPayment);

        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConcurrentPaymentOperationException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId) {
        PaymentEntity payment =
            paymentRepository
                .findByIdWithDetails(paymentId)
                .orElseThrow( () -> new PaymentNotFoundException(paymentId) );

        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPolicyPayments(String policyNumber) {
        String normalizedPolicyNumber = normalizePolicyNumber(policyNumber);

        return paymentRepository
            .findAllByPolicyNumberIgnoreCaseOrderByPaymentDateDescIdDesc(normalizedPolicyNumber)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Fusionne maturités et paiements valides.
     *
     * Le moteur applique lui-même le tri chronologique,
     * notamment maturité avant paiement à date égale.
     */
    private List<CalculationEvent> buildEvents(List<PolicyMaturityEntity> maturities, List<PaymentEntity> paidPayments) {
        List<CalculationEvent> events = new ArrayList<>();

        maturities.stream()
            .map(this::toMaturityEvent)
            .forEach(events::add);

        paidPayments.stream()
            .map(this::toPaymentEvent)
            .forEach(events::add);

        return events;
    }

    private CalculationEvent toMaturityEvent(PolicyMaturityEntity maturity) {
        return new CalculationEvent(
            maturity.getId(),
            maturity.getMaturityDate(),
            CalculationEvent.EventKind.MATURITY,
            maturity.getMaturityRank(),
            maturity.getMaturityType(),
            maturity.getMaturityAmount()
        );
    }

    private CalculationEvent toPaymentEvent(PaymentEntity payment) {
        return new CalculationEvent(
            payment.getId(),
            payment.getPaymentDate(),
            CalculationEvent.EventKind.PAYMENT,
            0,
            "Paiement n°" + payment.getId(),
            payment.getPaidAmount()
        );
    }

    /**
     * Crée le paiement et la copie de toutes
     * les lignes ayant justifié son montant.
     */
    private PaymentEntity createPayment(InterestSimulationResponse simulation, String currentUsername) {
        PaymentEntity payment = new PaymentEntity();

        payment.setPolicyNumber(simulation.policyNumber());

        payment.setPaymentDate(simulation.calculationDate());

        payment.setCalculationDate(simulation.calculationDate());

        payment.setAnnualRate(simulation.annualRate());

        payment.setCapitalAmount(normalize(simulation.openCapital()));

        payment.setInterestAmount(normalize(simulation.openInterest()));

        payment.setPaidAmount(normalize(simulation.balance()));

        payment.setCompletedCycles(simulation.completedCycles());

        payment.setStatus(PaymentStatus.PAID);

        simulation.lines()
            .stream()
            .sorted(Comparator.comparingInt(InterestCalculationLineResponse::sequence))
            .map(this::toPaymentDetail)
            .forEach(payment::addDetail);

        /*
         * Ajoute une dernière ligne expliquant la remise
         * du solde à zéro par le paiement total.
         */
        PaymentDetailEntity paymentLine = new PaymentDetailEntity();

        paymentLine.setSequenceNumber( simulation.lines().size() + 1 );

        paymentLine.setEventDate( simulation.calculationDate());

        paymentLine.setEventType(CalculationEventType.PAYMENT_APPLIED);

        paymentLine.setDescription("Paiement total de la situation financière.");

        paymentLine.setCycleNumber(null);
        paymentLine.setBalanceBefore(normalize(simulation.balance()));
        paymentLine.setCapitalAdded(zero());
        paymentLine.setAnnualRate(null);
        paymentLine.setInterestAmount(zero());
        paymentLine.setPaidAmount(normalize(simulation.balance()));
        paymentLine.setBalanceAfter(zero());

        payment.addDetail(paymentLine);

        return payment;
    }

    private PaymentDetailEntity toPaymentDetail(InterestCalculationLineResponse line) {
        PaymentDetailEntity detail = new PaymentDetailEntity();

        detail.setSequenceNumber(line.sequence());

        detail.setEventDate(line.eventDate());

        detail.setEventType(line.eventType());

        detail.setDescription(line.description());

        detail.setCycleNumber(line.cycleNumber());

        detail.setBalanceBefore(normalize(line.balanceBefore()));

        detail.setCapitalAdded(normalize(line.capitalAdded()));

        detail.setAnnualRate(line.annualRate());

        detail.setInterestAmount(normalize(line.interestAmount()));

        detail.setPaidAmount(normalize(line.paidAmount()));

        detail.setBalanceAfter(normalize(line.balanceAfter()));

        return detail;
    }

    private PaymentResponse toResponse(PaymentEntity payment) {
        List<PaymentDetailResponse> details =
            payment.getDetails()
                .stream()
                .sorted(Comparator.comparingInt(PaymentDetailEntity::getSequenceNumber))
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
            details
        );
    }

    private PaymentDetailResponse toDetailResponse(PaymentDetailEntity detail) {
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

    private String normalizePolicyNumber(String policyNumber) {
        if (policyNumber == null || policyNumber.isBlank()) {
            throw new InvalidPolicyNumberException("Le numéro de police est obligatoire.");
        }

        String normalized = policyNumber.trim();

        if (normalized.length() > 100) {
            throw new InvalidPolicyNumberException("Le numéro de police ne doit pas dépasser 100 caractères.");
        }

        return normalized;
    }
}
