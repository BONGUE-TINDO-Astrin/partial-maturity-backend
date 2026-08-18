package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.config.properties.InterestProperties;
import com.belife.partial_maturity_backend.dtos.responses.*;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.exceptions.InvalidPolicyNumberException;
import com.belife.partial_maturity_backend.exceptions.PolicyNotFoundException;
import com.belife.partial_maturity_backend.mappers.PolicyMaturityMapper;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.BusinessDateProvider;
import com.belife.partial_maturity_backend.services.InterestCalculationEngine;
import com.belife.partial_maturity_backend.services.PolicyConsultationService;
import com.belife.partial_maturity_backend.services.models.CalculationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.belife.partial_maturity_backend.utils.FinancialAmountUtils.normalize;
import static com.belife.partial_maturity_backend.utils.FinancialAmountUtils.zero;

/**
 * Implémente la consultation des polices,
 * de leurs maturités et de leur situation financière.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyConsultationServiceImpl implements PolicyConsultationService {

    private final PolicyMaturityRepository policyMaturityRepository;

    private final PaymentRepository paymentRepository;

    private final PolicyMaturityMapper policyMaturityMapper;

    private final InterestCalculationEngine calculationEngine;

    private final InterestProperties interestProperties;

    private final BusinessDateProvider businessDateProvider;

    /**
     * Construit les synthèses avec un chargement groupé
     * des maturités et des paiements.
     *
     * <p>Le moteur financier est ensuite exécuté en mémoire
     * pour chaque police. Aucune requête SQL supplémentaire
     * n'est déclenchée dans la boucle.</p>
     */
    @Override
    public List<PolicyFinancialSummaryResponse>
    getPolicyFinancialSummaries() {
        List<PolicyMaturityEntity> allMaturities =
                policyMaturityRepository
                        .findAllByOrderByPolicyNumberAscMaturityRankAsc();

        if (allMaturities.isEmpty()) {
            return List.of();
        }

        Map<String, List<PolicyMaturityEntity>> maturitiesByPolicy = groupMaturitiesByPolicy(allMaturities);

        Set<String> storedPolicyNumbers =
                maturitiesByPolicy.values()
                        .stream()
                        .map(List::getFirst)
                        .map(PolicyMaturityEntity::getPolicyNumber)
                        .collect(Collectors.toSet());

        List<PaymentEntity> paidPayments =
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                storedPolicyNumbers,
                                PaymentStatus.PAID
                        );

        Map<String, List<PaymentEntity>> paymentsByPolicy = groupPaymentsByPolicy(paidPayments);

        LocalDate calculationDate = businessDateProvider.currentDate();

        return maturitiesByPolicy
                .values()
                .stream()
                .map(maturities ->
                        toFinancialSummary(
                                maturities,
                                paymentsByPolicy.getOrDefault(
                                        normalizePolicyKey(
                                                maturities
                                                        .getFirst()
                                                        .getPolicyNumber()
                                        ),
                                        List.of()
                                ),
                                calculationDate
                        )
                )
                .sorted(
                        Comparator.comparing(
                                PolicyFinancialSummaryResponse
                                        ::policyNumber,
                                String.CASE_INSENSITIVE_ORDER
                        )
                )
                .toList();
    }

    /**
     * Charge les maturités et construit le résumé
     * descriptif d'une police.
     */
    @Override
    public PolicyDetailResponse getPolicyDetails(String policyNumber) {
        String normalizedPolicyNumber =
                normalizePolicyNumber(
                        policyNumber
                );

        List<PolicyMaturityEntity> maturities =
                policyMaturityRepository
                        .findAllByPolicyNumberIgnoreCaseOrderByMaturityRankAsc(
                                normalizedPolicyNumber
                        );

        if (maturities.isEmpty()) {
            throw new PolicyNotFoundException(
                    normalizedPolicyNumber
            );
        }

        List<PolicyMaturityResponse>
                maturityResponses =
                maturities.stream()
                        .map(
                                policyMaturityMapper
                                        ::toResponse
                        )
                        .toList();

        BigDecimal totalMaturityAmount = sumMaturityAmounts(maturities);

        LocalDate firstMaturityDate = findFirstMaturityDate(maturities);

        LocalDate lastMaturityDate = findLastMaturityDate(maturities);

        String storedPolicyNumber = maturities.getFirst().getPolicyNumber();

        return new PolicyDetailResponse(
                storedPolicyNumber,
                maturities.size(),
                totalMaturityAmount,
                firstMaturityDate,
                lastMaturityDate,
                maturityResponses
        );
    }

    /**
     * Construit le détail financier d'une police.
     *
     * <p>Seuls les paiements PAID participent à la simulation,
     * aux agrégats financiers et à l'historique présenté
     * dans cette consultation.</p>
     */
    @Override
    public PolicyFinancialDetailResponse
    getPolicyFinancialDetails(
            String policyNumber
    ) {
        String normalizedPolicyNumber =
                normalizePolicyNumber(
                        policyNumber
                );

        List<PolicyMaturityEntity> maturities =
                policyMaturityRepository
                        .findAllByPolicyNumberIgnoreCaseOrderByMaturityRankAsc(
                                normalizedPolicyNumber
                        );

        if (maturities.isEmpty()) {
            throw new PolicyNotFoundException(
                    normalizedPolicyNumber
            );
        }

        /*
         * Seuls les paiements valides participent
         * à la situation financière de la police.
         */
        List<PaymentEntity> paidPayments =
                paymentRepository
                        .findAllByPolicyNumberIgnoreCaseAndStatusOrderByPaymentDateAscIdAsc(
                                normalizedPolicyNumber,
                                PaymentStatus.PAID
                        );

        String storedPolicyNumber =
                maturities.getFirst()
                        .getPolicyNumber();

        LocalDate calculationDate =
                businessDateProvider.currentDate();

        LocalDate interestEndDate =
                resolveInterestEndDate(
                        maturities
                );

        InterestSimulationResponse simulation =
                calculationEngine.calculate(
                        storedPolicyNumber,
                        calculationDate,
                        interestEndDate,
                        interestProperties.annualRate(),
                        buildEvents(
                                maturities,
                                paidPayments
                        )
                );

        BigDecimal paidInterestAmount =
                calculatePaidInterestAmount(
                        paidPayments
                );

        BigDecimal totalPaidAmount =
                calculateTotalPaidAmount(
                        paidPayments
                );

        BigDecimal totalGeneratedInterestAmount =
                normalize(
                        paidInterestAmount.add(
                                simulation.openInterest()
                        )
                );

        List<PolicyMaturityResponse> maturityResponses =
                maturities.stream()
                        .map(
                                policyMaturityMapper::toResponse
                        )
                        .toList();

        /*
         * L'historique est présenté du paiement valide
         * le plus récent au plus ancien.
         */
        List<PolicyPaymentHistoryResponse> paymentResponses =
                paidPayments.stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                PaymentEntity
                                                        ::getPaymentDate
                                        )
                                        .thenComparing(
                                                PaymentEntity::getId
                                        )
                                        .reversed()
                        )
                        .map(
                                this::toPaymentHistoryResponse
                        )
                        .toList();

        return new PolicyFinancialDetailResponse(
                storedPolicyNumber,
                maturities.size(),
                sumMaturityAmounts(maturities),
                findFirstMaturityDate(maturities),
                findLastMaturityDate(maturities),
                interestEndDate,
                simulation.interestAccrualClosed(),
                paidInterestAmount,
                totalPaidAmount,
                totalGeneratedInterestAmount,
                maturityResponses,
                paymentResponses,
                simulation
        );
    }

    /**
     * Calcule le montant cumulé des paiements
     * encore valides.
     */
    private BigDecimal calculateTotalPaidAmount(
            List<PaymentEntity> paidPayments
    ) {
        return normalize(
                paidPayments.stream()
                        .map(
                                PaymentEntity::getPaidAmount
                        )
                        .reduce(
                                zero(),
                                BigDecimal::add
                        )
        );
    }

    /**
     * Construit le résumé léger d'un paiement PAID
     * affiché dans l'historique de la police.
     */
    private PolicyPaymentHistoryResponse
    toPaymentHistoryResponse(
            PaymentEntity payment
    ) {
        return new PolicyPaymentHistoryResponse(
                payment.getId(),
                payment.getPaymentDate(),
                normalize(
                        payment.getCapitalAmount()
                ),
                normalize(
                        payment.getInterestAmount()
                ),
                normalize(
                        payment.getPaidAmount()
                ),
                payment.getCompletedCycles()
        );
    }

    private PolicyFinancialSummaryResponse toFinancialSummary(
            List<PolicyMaturityEntity> maturities,
            List<PaymentEntity> paidPayments,
            LocalDate calculationDate
    ) {
        PolicyMaturityEntity firstMaturity =
                maturities.getFirst();

        String policyNumber =
                firstMaturity.getPolicyNumber();

        LocalDate interestEndDate =
                resolveInterestEndDate(
                        maturities
                );

        List<CalculationEvent> events =
                buildEvents(
                        maturities,
                        paidPayments
                );

        InterestSimulationResponse simulation =
                calculationEngine.calculate(
                        policyNumber,
                        calculationDate,
                        interestEndDate,
                        interestProperties.annualRate(),
                        events
                );

        BigDecimal paidInterestAmount = calculatePaidInterestAmount(paidPayments);

//        paidInterestAmount = normalize(paidInterestAmount);

        BigDecimal totalGeneratedInterestAmount = normalize(paidInterestAmount.add(simulation.openInterest()));

        LocalDate firstMaturityDate = findFirstMaturityDate(maturities);

        LocalDate lastMaturityDate = findLastMaturityDate(maturities);

        return new PolicyFinancialSummaryResponse(
                policyNumber,
                maturities.size(),
                sumMaturityAmounts(maturities),
                firstMaturityDate,
                lastMaturityDate,
                interestEndDate,
                simulation.interestAccrualClosed(),
                simulation.annualRate(),
                simulation.completedCycles(),
                simulation.openCapital(),
                simulation.openInterest(),
                paidInterestAmount,
                totalGeneratedInterestAmount,
                simulation.balance(),
                simulation.calculationDate()
        );
    }

    private Map<String, List<PolicyMaturityEntity>>
    groupMaturitiesByPolicy(
            List<PolicyMaturityEntity> maturities
    ) {
        return maturities.stream()
                .collect(
                        Collectors.groupingBy(
                                maturity ->
                                        normalizePolicyKey(
                                                maturity
                                                        .getPolicyNumber()
                                        ),
                                LinkedHashMap::new,
                                Collectors.toList()
                        )
                );
    }

    private Map<String, List<PaymentEntity>>
    groupPaymentsByPolicy(
            List<PaymentEntity> payments
    ) {
        return payments.stream()
                .collect(
                        Collectors.groupingBy(
                                payment ->
                                        normalizePolicyKey(
                                                payment
                                                        .getPolicyNumber()
                                        ),
                                LinkedHashMap::new,
                                Collectors.toList()
                        )
                );
    }

    /**
     * Fusionne les maturités et les paiements actifs.
     * Le moteur applique ensuite l'ordre chronologique.
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
                CalculationEvent.EventKind
                        .MATURITY,
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
                CalculationEvent.EventKind
                        .PAYMENT,
                0,
                "Paiement n°"
                        + payment.getId(),
                payment.getPaidAmount()
        );
    }

    private BigDecimal sumMaturityAmounts(
            List<PolicyMaturityEntity> maturities
    ) {
        return normalize(
                maturities.stream()
                        .map(
                                PolicyMaturityEntity
                                        ::getMaturityAmount
                        )
                        .reduce(
                                zero(),
                                BigDecimal::add
                        )
        );
    }

    /**
     * Calcule le montant des intérêts contenus dans
     * les paiements PAID encore valides.
     */
    private BigDecimal calculatePaidInterestAmount(
            List<PaymentEntity> paidPayments
    ) {
        return normalize(
                paidPayments.stream()
                        .map(
                                PaymentEntity
                                        ::getInterestAmount
                        )
                        .reduce(
                                zero(),
                                BigDecimal::add
                        )
        );
    }

    private LocalDate findFirstMaturityDate(
            List<PolicyMaturityEntity> maturities
    ) {
        return maturities.stream()
                .map(
                        PolicyMaturityEntity
                                ::getMaturityDate
                )
                .min(LocalDate::compareTo)
                .orElseThrow();
    }

    private LocalDate findLastMaturityDate(
            List<PolicyMaturityEntity> maturities
    ) {
        return maturities.stream()
                .map(
                        PolicyMaturityEntity
                                ::getMaturityDate
                )
                .max(LocalDate::compareTo)
                .orElseThrow();
    }

    /**
     * Retourne la date commune à toutes les maturités
     * d'une police.
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

    private String normalizePolicyKey(
            String policyNumber
    ) {
        return policyNumber
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}