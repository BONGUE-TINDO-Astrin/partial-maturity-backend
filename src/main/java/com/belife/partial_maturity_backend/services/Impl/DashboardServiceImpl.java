package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.config.properties.InterestProperties;
import com.belife.partial_maturity_backend.dtos.responses.InterestSimulationResponse;
import com.belife.partial_maturity_backend.dtos.responses.dashboard.DashboardMetricsResponse;
import com.belife.partial_maturity_backend.dtos.responses.dashboard.DashboardResponse;
import com.belife.partial_maturity_backend.dtos.responses.dashboard.InterestDistributionResponse;
import com.belife.partial_maturity_backend.dtos.responses.dashboard.MonthlyPaymentStatisticResponse;
import com.belife.partial_maturity_backend.dtos.responses.dashboard.RecentImportResponse;
import com.belife.partial_maturity_backend.dtos.responses.dashboard.RecentPaymentResponse;
import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.repositories.ImportBatchRepository;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.BusinessDateProvider;
import com.belife.partial_maturity_backend.services.DashboardService;
import com.belife.partial_maturity_backend.services.InterestCalculationEngine;
import com.belife.partial_maturity_backend.services.models.CalculationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * Construit le tableau de bord commun aux utilisateurs
 * ADMIN et COMPTABILITE.
 *
 * <p>Le service charge les maturités et les paiements valides
 * en groupe, puis utilise le moteur financier existant pour
 * calculer les intérêts actuellement ouverts.</p>
 *
 * <p>Aucune requête SQL n'est exécutée dans la boucle
 * de calcul des polices.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final int RECENT_ITEM_LIMIT = 5;

    private static final int MONTH_COUNT = 12;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final PolicyMaturityRepository policyMaturityRepository;

    private final PaymentRepository paymentRepository;

    private final ImportBatchRepository importBatchRepository;

    private final InterestCalculationEngine interestCalculationEngine;

    private final InterestProperties interestProperties;

    private final BusinessDateProvider businessDateProvider;

    /**
     * Retourne la synthèse commune du portefeuille,
     * des intérêts, des paiements et des chargements récents.
     */
    @Override
    public DashboardResponse getDashboard() {
        LocalDate calculationDate = businessDateProvider.currentDate();

        /*
         * Les maturités actives sont chargées une seule fois.
         * Les maturités retirées lors d'une réversion ne sont
         * plus présentes dans cette table.
         */
        List<PolicyMaturityEntity> allMaturities =
                policyMaturityRepository
                        .findAllByOrderByPolicyNumberAscMaturityRankAsc();

        Map<String, List<PolicyMaturityEntity>> maturitiesByPolicy = groupMaturitiesByPolicy(allMaturities);

        Set<String> storedPolicyNumbers =
                maturitiesByPolicy.values()
                        .stream()
                        .filter(maturities -> !maturities.isEmpty())
                        .map(List::getFirst)
                        .map(PolicyMaturityEntity::getPolicyNumber)
                        .collect(Collectors.toSet());

        /*
         * Seuls les paiements PAID participent :
         *
         * - au calcul financier ;
         * - aux intérêts déjà payés ;
         * - aux statistiques mensuelles ;
         * - à l'historique récent.
         */
        List<PaymentEntity> allPaidPayments = storedPolicyNumbers.isEmpty()
                        ? List.of()
                        : paymentRepository.findAllByPolicyNumbersAndStatus(storedPolicyNumbers, PaymentStatus.PAID);

        /*
         * Lorsqu'une date métier antérieure est simulée
         * en développement, les paiements dont la date est
         * ultérieure ne doivent pas participer à la photographie
         * financière affichée.
         */
        List<PaymentEntity> paidPaymentsAtCalculationDate =
                allPaidPayments.stream()
                        .filter(payment -> !payment.getPaymentDate().isAfter(calculationDate))
                        .toList();

        Map<String, List<PaymentEntity>> paidPaymentsByPolicy = groupPaymentsByPolicy(paidPaymentsAtCalculationDate);

        BigDecimal totalMaturityAmount = sumMaturityAmounts(allMaturities);

        BigDecimal totalPaidInterest = sumPaidInterest(paidPaymentsAtCalculationDate);

        BigDecimal totalPaidAmount = sumPaidAmounts(paidPaymentsAtCalculationDate);

        BigDecimal totalOpenInterest = calculateTotalOpenInterest(
                        maturitiesByPolicy,
                        paidPaymentsByPolicy,
                        calculationDate
                );

        BigDecimal totalGeneratedInterest = normalize(totalOpenInterest.add(totalPaidInterest));

        DashboardMetricsResponse metrics = new DashboardMetricsResponse(
                        maturitiesByPolicy.size(),
                        allMaturities.size(),
                        totalMaturityAmount,
                        totalGeneratedInterest,
                        totalPaidInterest,
                        totalPaidAmount
                );

        InterestDistributionResponse
                interestDistribution = buildInterestDistribution(totalOpenInterest, totalPaidInterest, totalGeneratedInterest);

        List<MonthlyPaymentStatisticResponse>
                monthlyPayments = buildMonthlyPaymentStatistics(paidPaymentsAtCalculationDate, calculationDate);

        List<RecentImportResponse> recentImports = buildRecentImports();

        List<RecentPaymentResponse> recentPayments = buildRecentPayments(paidPaymentsAtCalculationDate);

        return new DashboardResponse(
                calculationDate,
                metrics,
                interestDistribution,
                monthlyPayments,
                recentImports,
                recentPayments
        );
    }

    /**
     * Calcule les intérêts ouverts de toutes les polices.
     *
     * <p>Le moteur financier reste l'unique source
     * des règles de capitalisation, de paiement et
     * de clôture des intérêts.</p>
     */
    private BigDecimal calculateTotalOpenInterest(
            Map<String, List<PolicyMaturityEntity>> maturitiesByPolicy,
            Map<String, List<PaymentEntity>> paidPaymentsByPolicy,
            LocalDate calculationDate
    ) {
        BigDecimal totalOpenInterest = zero();

        for (List<PolicyMaturityEntity> maturities : maturitiesByPolicy.values()) {
            if (maturities.isEmpty()) {
                continue;
            }

            PolicyMaturityEntity firstMaturity = maturities.getFirst();

            String policyNumber = firstMaturity.getPolicyNumber();

            String normalizedPolicyNumber = normalizePolicyNumber(policyNumber);

            List<PaymentEntity> policyPayments = paidPaymentsByPolicy.getOrDefault(normalizedPolicyNumber, List.of());

            LocalDate interestEndDate = resolveInterestEndDate(maturities);

            List<CalculationEvent> events = buildCalculationEvents(maturities, policyPayments);

            InterestSimulationResponse simulation =
                    interestCalculationEngine.calculate(
                            policyNumber,
                            calculationDate,
                            interestEndDate,
                            interestProperties.annualRate(),
                            events
                    );

            totalOpenInterest =totalOpenInterest.add(simulation.openInterest());
        }

        return normalize(totalOpenInterest);
    }

    /**
     * Construit la répartition utilisée par
     * le graphique des intérêts.
     */
    private InterestDistributionResponse buildInterestDistribution(
            BigDecimal openInterest,
            BigDecimal paidInterest,
            BigDecimal generatedInterest
    ) {
        BigDecimal paidPercentage = calculatePaidPercentage(paidInterest, generatedInterest);

        return new InterestDistributionResponse(
                normalize(openInterest),
                normalize(paidInterest),
                normalize(generatedInterest),
                paidPercentage
        );
    }

    /**
     * Retourne un pourcentage compris entre 0 et 100.
     */
    private BigDecimal calculatePaidPercentage(BigDecimal paidInterest, BigDecimal generatedInterest) {
        if (generatedInterest.signum() <= 0) {
            return zero();
        }

        BigDecimal percentage = paidInterest
                        .multiply(ONE_HUNDRED)
                        .divide(generatedInterest,6, RoundingMode.HALF_UP);

        /*
         * Protection défensive en cas de données
         * historiques anormales.
         */
        if (percentage.signum() < 0) {
            return zero();
        }

        if (percentage.compareTo(ONE_HUNDRED) > 0 ) {
            return ONE_HUNDRED.setScale(6, RoundingMode.HALF_UP);
        }

        return percentage.setScale(6,RoundingMode.HALF_UP);
    }

    /**
     * Construit toujours douze mois consécutifs,
     * y compris lorsqu'un mois ne possède aucun paiement.
     */
    private List<MonthlyPaymentStatisticResponse>
    buildMonthlyPaymentStatistics(List<PaymentEntity> paidPayments, LocalDate calculationDate) {
        YearMonth currentMonth = YearMonth.from(calculationDate);

        YearMonth firstMonth = currentMonth.minusMonths(MONTH_COUNT - 1L);

        Map<YearMonth, MutableMonthlyPaymentStatistic> statisticsByMonth = new LinkedHashMap<>();

        for (int index = 0; index < MONTH_COUNT; index++) {
            YearMonth month = firstMonth.plusMonths(index);

            statisticsByMonth.put( month, new MutableMonthlyPaymentStatistic());
        }

        for (PaymentEntity payment : paidPayments) {
            YearMonth paymentMonth = YearMonth.from(payment.getPaymentDate());

            MutableMonthlyPaymentStatistic statistic = statisticsByMonth.get(paymentMonth);

            if (statistic == null) {
                continue;
            }

            statistic.addPayment(payment);
        }

        return statisticsByMonth
                .entrySet()
                .stream()
                .map(entry ->
                        new MonthlyPaymentStatisticResponse(
                                entry.getKey().toString(),
                                entry.getValue().paymentCount,
                                normalize(entry.getValue().paidAmount),
                                normalize(entry.getValue().interestAmount)
                        )
                )
                .toList();
    }

    /**
     * Retourne les cinq derniers chargements,
     * tous statuts confondus.
     */
    private List<RecentImportResponse> buildRecentImports() {
        return importBatchRepository
                .findRecentImports(PageRequest.of(0, RECENT_ITEM_LIMIT))
                .stream()
                .map(this::toRecentImport)
                .toList();
    }

    /**
     * Pour un chargement annulé, la date et l'acteur
     * représentatifs sont ceux de la réversion.
     */
    private RecentImportResponse toRecentImport(ImportBatchEntity batch) {
        boolean reversed = batch.getStatus() == ImportBatchStatus.REVERSED;

        return new RecentImportResponse(
                batch.getId(),
                batch.getOriginalFileName(),
                batch.getStatus(),
                batch.getInsertedRows(),
                reversed
                        && batch.getReversedAt() != null
                        ? batch.getReversedAt()
                        : batch.getCreatedAt(),
                reversed
                        && batch.getReversedBy() != null
                        ? batch.getReversedBy()
                        : resolveImportActor(batch)
        );
    }

    private String resolveImportActor(ImportBatchEntity batch) {
        if (
                batch.getImportedBy() != null
                        && !batch.getImportedBy()
                        .isBlank()
        ) {
            return batch.getImportedBy();
        }

        return batch.getCreatedBy();
    }

    /**
     * Retourne les cinq paiements PAID les plus récents
     * à la date métier du dashboard.
     */
    private List<RecentPaymentResponse>
    buildRecentPayments(List<PaymentEntity> paidPayments) {
        return paidPayments.stream()
                .sorted(
                        Comparator
                                .comparing(PaymentEntity::getPaymentDate)
                                .thenComparing(PaymentEntity::getId)
                                .reversed()
                )
                .limit(RECENT_ITEM_LIMIT)
                .map(this::toRecentPayment)
                .toList();
    }

    private RecentPaymentResponse toRecentPayment(PaymentEntity payment) {
        return new RecentPaymentResponse(
                payment.getId(),
                payment.getPolicyNumber(),
                payment.getPaymentDate(),
                normalize(payment.getCapitalAmount()),
                normalize(payment.getInterestAmount()),
                normalize(payment.getPaidAmount()),
                payment.getCreatedBy()
        );
    }

    private Map<String, List<PolicyMaturityEntity>>
    groupMaturitiesByPolicy(List<PolicyMaturityEntity> maturities) {
        return maturities.stream()
                .collect(
                        Collectors.groupingBy(
                                maturity ->normalizePolicyNumber(maturity.getPolicyNumber()),
                                LinkedHashMap::new,
                                Collectors.toList()
                        )
                );
    }

    private Map<String, List<PaymentEntity>>
    groupPaymentsByPolicy(List<PaymentEntity> payments) {
        return payments.stream()
                .collect(
                        Collectors.groupingBy(
                                payment -> normalizePolicyNumber(payment.getPolicyNumber()),
                                LinkedHashMap::new,
                                Collectors.toList()
                        )
                );
    }

    private List<CalculationEvent>
    buildCalculationEvents(List<PolicyMaturityEntity> maturities, List<PaymentEntity> paidPayments) {
        List<CalculationEvent> events = new ArrayList<>();

        maturities.stream().map(this::toMaturityEvent).forEach(events::add);

        paidPayments.stream().map(this::toPaymentEvent).forEach(events::add);

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
                "Paiement n° " + payment.getId(),
                payment.getPaidAmount()
        );
    }

    private BigDecimal sumMaturityAmounts(List<PolicyMaturityEntity> maturities) {
        return normalize(
                maturities.stream()
                        .map(PolicyMaturityEntity::getMaturityAmount)
                        .reduce(zero(), BigDecimal::add)
        );
    }

    private BigDecimal sumPaidInterest(List<PaymentEntity> payments) {
        return normalize(
                payments.stream()
                        .map(PaymentEntity::getInterestAmount)
                        .reduce(zero(),BigDecimal::add)
        );
    }

    private BigDecimal sumPaidAmounts(List<PaymentEntity> payments) {
        return normalize(
                payments.stream()
                        .map( PaymentEntity::getPaidAmount)
                        .reduce(zero(), BigDecimal::add)
        );
    }

    /**
     * Vérification défensive de la date commune
     * de fin des intérêts.
     */
    private LocalDate resolveInterestEndDate(List<PolicyMaturityEntity> maturities) {
        LocalDate expectedInterestEndDate = maturities.getFirst().getInterestEndDate();

        boolean inconsistentDate = maturities.stream()
                        .anyMatch(maturity -> !expectedInterestEndDate.equals(maturity.getInterestEndDate()));

        if (inconsistentDate) {
            throw new IllegalStateException(
                    "La police "
                            + maturities.getFirst()
                            .getPolicyNumber()
                            + " possède plusieurs dates "
                            + "de fin des intérêts."
            );
        }

        return expectedInterestEndDate;
    }

    private String normalizePolicyNumber(String policyNumber) {
        return policyNumber.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Accumulateur interne utilisé uniquement pendant
     * la construction des statistiques mensuelles.
     */
    private static final class
    MutableMonthlyPaymentStatistic {

        private long paymentCount;

        private BigDecimal paidAmount = zero();

        private BigDecimal interestAmount = zero();

        private void addPayment(PaymentEntity payment) {
            paymentCount++;

            paidAmount = paidAmount.add(payment.getPaidAmount());

            interestAmount = interestAmount.add(payment.getInterestAmount());
        }
    }
}