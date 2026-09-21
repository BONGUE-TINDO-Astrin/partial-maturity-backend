package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.config.properties.InterestProperties;
import com.belife.partial_maturity_backend.dtos.responses.InterestSimulationResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyFinancialDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyFinancialSummaryResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyMaturityResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyPaymentHistoryResponse;
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
 * Implémente la consultation des polices
 * et de leur situation financière.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyConsultationServiceImpl implements PolicyConsultationService {

    private static final String UNKNOWN_CLIENT_NAME = "CLIENT NON RENSEIGNE";

    private final PolicyMaturityRepository policyMaturityRepository;

    private final PaymentRepository paymentRepository;

    private final PolicyMaturityMapper policyMaturityMapper;

    private final InterestCalculationEngine calculationEngine;

    private final InterestProperties interestProperties;

    private final BusinessDateProvider businessDateProvider;

    @Override
    public List<PolicyFinancialSummaryResponse>
    getPolicyFinancialSummaries() {
        List<PolicyMaturityEntity> allMaturities =
                policyMaturityRepository
                        .findAllByOrderByPolicyNumberAscMaturityRankAsc();

        if (allMaturities.isEmpty()) {
            return List.of();
        }

        Map<String, List<PolicyMaturityEntity>>
                maturitiesByPolicy =
                groupMaturitiesByPolicy(
                        allMaturities
                );

        Set<String> storedPolicyNumbers =
                maturitiesByPolicy.values()
                        .stream()
                        .map(List::getFirst)
                        .map(
                                PolicyMaturityEntity
                                        ::getPolicyNumber
                        )
                        .collect(Collectors.toSet());

        List<PaymentEntity> paidPayments =
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                storedPolicyNumbers,
                                PaymentStatus.PAID
                        );

        Map<String, List<PaymentEntity>>
                paymentsByPolicy =
                groupPaymentsByPolicy(
                        paidPayments
                );

        LocalDate calculationDate = businessDateProvider.currentDate();

        return maturitiesByPolicy.values()
                .stream()
                .map(maturities -> {
                    String policyKey =
                            normalizePolicyKey(
                                    maturities.getFirst()
                                            .getPolicyNumber()
                            );

                    return toFinancialSummary(
                            maturities,
                            paymentsByPolicy.getOrDefault(
                                    policyKey,
                                    List.of()
                            ),
                            calculationDate
                    );
                })
                .sorted(
                        Comparator.comparing(
                                PolicyFinancialSummaryResponse
                                        ::policyNumber,
                                String.CASE_INSENSITIVE_ORDER
                        )
                )
                .toList();
    }

    @Override
    public PolicyDetailResponse getPolicyDetails(String policyNumber) {
        List<PolicyMaturityEntity> maturities = findPolicyMaturities(policyNumber);

        List<PolicyMaturityResponse>
                maturityResponses =
                maturities.stream()
                        .map(
                                policyMaturityMapper
                                        ::toResponse
                        )
                        .toList();

        String storedPolicyNumber =
                maturities.getFirst()
                        .getPolicyNumber();

        String clientName =
                resolveClientName(
                        maturities
                );

        return new PolicyDetailResponse(
                storedPolicyNumber,
                clientName,
                maturities.size(),
                sumMaturityAmounts(maturities),
                findFirstMaturityDate(maturities),
                findLastMaturityDate(maturities),
                maturityResponses
        );
    }

    @Override
    public PolicyFinancialDetailResponse
    getPolicyFinancialDetails(
            String policyNumber
    ) {
        List<PolicyMaturityEntity> maturities =
                findPolicyMaturities(
                        policyNumber
                );

        String storedPolicyNumber =
                maturities.getFirst()
                        .getPolicyNumber();

        String clientName =
                resolveClientName(
                        maturities
                );

        List<PaymentEntity> paidPayments =
                paymentRepository
                        .findAllByPolicyNumberIgnoreCaseAndStatusOrderByPaymentDateAscIdAsc(
                                storedPolicyNumber,
                                PaymentStatus.PAID
                        );

        LocalDate calculationDate =
                businessDateProvider.currentDate();

        LocalDate interestEndDate =
                resolveInterestEndDate(
                        maturities
                );

        InterestSimulationResponse simulation =
                calculateSimulation(
                        storedPolicyNumber,
                        maturities,
                        paidPayments,
                        calculationDate,
                        interestEndDate
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

        List<PolicyMaturityResponse>
                maturityResponses =
                maturities.stream()
                        .map(
                                policyMaturityMapper
                                        ::toResponse
                        )
                        .toList();

        List<PolicyPaymentHistoryResponse>
                paymentResponses =
                paidPayments.stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                PaymentEntity
                                                        ::getPaymentDate
                                        )
                                        .thenComparing(
                                                PaymentEntity
                                                        ::getId
                                        )
                                        .reversed()
                        )
                        .map(
                                this::toPaymentHistoryResponse
                        )
                        .toList();

        return new PolicyFinancialDetailResponse(
                storedPolicyNumber,
                clientName,
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

    private PolicyFinancialSummaryResponse
    toFinancialSummary(
            List<PolicyMaturityEntity> maturities,
            List<PaymentEntity> paidPayments,
            LocalDate calculationDate
    ) {
        PolicyMaturityEntity firstMaturity =
                maturities.getFirst();

        String policyNumber =
                firstMaturity.getPolicyNumber();

        String clientName =
                resolveClientName(
                        maturities
                );

        LocalDate interestEndDate =
                resolveInterestEndDate(
                        maturities
                );

        InterestSimulationResponse simulation =
                calculateSimulation(
                        policyNumber,
                        maturities,
                        paidPayments,
                        calculationDate,
                        interestEndDate
                );

        BigDecimal paidInterestAmount =
                calculatePaidInterestAmount(
                        paidPayments
                );

        BigDecimal totalGeneratedInterestAmount =
                normalize(
                        paidInterestAmount.add(
                                simulation.openInterest()
                        )
                );

        return new PolicyFinancialSummaryResponse(
                policyNumber,
                clientName,
                maturities.size(),
                sumMaturityAmounts(maturities),
                findFirstMaturityDate(maturities),
                findLastMaturityDate(maturities),
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

    private InterestSimulationResponse
    calculateSimulation(
            String policyNumber,
            List<PolicyMaturityEntity> maturities,
            List<PaymentEntity> paidPayments,
            LocalDate calculationDate,
            LocalDate interestEndDate
    ) {
        return calculationEngine.calculate(
                policyNumber,
                calculationDate,
                interestEndDate,
                interestProperties.annualRate(),
                buildEvents(
                        maturities,
                        paidPayments
                )
        );
    }

    private List<PolicyMaturityEntity>
    findPolicyMaturities(
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

        return maturities;
    }

    /**
     * Retourne le nom cohérent d'une police.
     *
     * La valeur historique CLIENT NON RENSEIGNE est ignorée
     * lorsqu'un véritable nom est disponible.
     */
    private String resolveClientName(
            List<PolicyMaturityEntity> maturities
    ) {
        String resolvedClientName = null;

        for (PolicyMaturityEntity maturity : maturities) {
            String candidate =
                    normalizeClientName(
                            maturity.getClientName()
                    );

            if (
                    candidate.isBlank()
                            || isUnknownClientName(candidate)
            ) {
                continue;
            }

            if (resolvedClientName == null) {
                resolvedClientName = candidate;
                continue;
            }

            if (
                    !normalizeClientKey(
                            resolvedClientName
                    ).equals(
                            normalizeClientKey(candidate)
                    )
            ) {
                throw new IllegalStateException(
                        "La police "
                                + maturity.getPolicyNumber()
                                + " possède plusieurs noms "
                                + "de client."
                );
            }
        }

        return resolvedClientName == null
                ? UNKNOWN_CLIENT_NAME
                : resolvedClientName;
    }

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
                "Paiement n° "
                        + payment.getId(),
                payment.getPaidAmount()
        );
    }

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

    private BigDecimal calculateTotalPaidAmount(
            List<PaymentEntity> paidPayments
    ) {
        return normalize(
                paidPayments.stream()
                        .map(
                                PaymentEntity
                                        ::getPaidAmount
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

    private boolean isUnknownClientName(
            String clientName
    ) {
        return UNKNOWN_CLIENT_NAME.equalsIgnoreCase(
                normalizeClientName(clientName)
        );
    }

    private String normalizeClientName(
            String clientName
    ) {
        if (clientName == null) {
            return "";
        }

        return clientName
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizeClientKey(
            String clientName
    ) {
        return normalizeClientName(clientName)
                .toUpperCase(Locale.ROOT);
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
                    "Le numéro de police ne doit pas "
                            + "dépasser 100 caractères."
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