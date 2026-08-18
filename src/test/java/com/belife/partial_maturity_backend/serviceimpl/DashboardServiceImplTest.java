package com.belife.partial_maturity_backend.serviceimpl;

import com.belife.partial_maturity_backend.config.properties.InterestProperties;
import com.belife.partial_maturity_backend.dtos.responses.InterestSimulationResponse;
import com.belife.partial_maturity_backend.dtos.responses.dashboard.DashboardResponse;
import com.belife.partial_maturity_backend.dtos.responses.dashboard.MonthlyPaymentStatisticResponse;
import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.repositories.ImportBatchRepository;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.BusinessDateProvider;
import com.belife.partial_maturity_backend.services.InterestCalculationEngine;
import com.belife.partial_maturity_backend.services.Impl.DashboardServiceImpl;
import com.belife.partial_maturity_backend.services.models.CalculationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du tableau de bord commun.
 *
 * <p>Les repositories et le moteur financier sont simulés
 * afin de vérifier uniquement l'orchestration du dashboard.</p>
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    private static final LocalDate CALCULATION_DATE =
            LocalDate.of(
                    2026,
                    8,
                    17
            );

    private static final BigDecimal ANNUAL_RATE =
            new BigDecimal("0.035");

    @Mock
    private PolicyMaturityRepository
            policyMaturityRepository;

    @Mock
    private PaymentRepository
            paymentRepository;

    @Mock
    private ImportBatchRepository
            importBatchRepository;

    @Mock
    private InterestCalculationEngine
            interestCalculationEngine;

    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        InterestProperties interestProperties =
                new InterestProperties(
                        ANNUAL_RATE
                );

        BusinessDateProvider businessDateProvider =
                () -> CALCULATION_DATE;

        dashboardService =
                new DashboardServiceImpl(
                        policyMaturityRepository,
                        paymentRepository,
                        importBatchRepository,
                        interestCalculationEngine,
                        interestProperties,
                        businessDateProvider
                );

        /*
         * Valeurs par défaut permettant aux tests
         * ciblés de ne configurer que leurs données utiles.
         */
        lenient()
                .when(
                        importBatchRepository
                                .findRecentImports(
                                        any(Pageable.class)
                                )
                )
                .thenReturn(List.of());
    }

    @Test
    @DisplayName(
            "Le dashboard doit retourner les six indicateurs financiers"
    )
    void shouldBuildDashboardMetrics() {
        PolicyMaturityEntity policyOneRankOne =
                maturity(
                        1L,
                        "POL001",
                        1,
                        "2024-01-01",
                        "1000000.00",
                        "2030-01-01"
                );

        PolicyMaturityEntity policyOneRankTwo =
                maturity(
                        2L,
                        "POL001",
                        2,
                        "2025-01-01",
                        "500000.00",
                        "2030-01-01"
                );

        PolicyMaturityEntity policyTwoRankOne =
                maturity(
                        3L,
                        "POL002",
                        1,
                        "2023-06-15",
                        "2000000.00",
                        "2031-06-15"
                );

        List<PolicyMaturityEntity> maturities =
                List.of(
                        policyOneRankOne,
                        policyOneRankTwo,
                        policyTwoRankOne
                );

        when(
                policyMaturityRepository
                        .findAllByOrderByPolicyNumberAscMaturityRankAsc()
        ).thenReturn(maturities);

        PaymentEntity policyOnePayment =
                paidPayment(
                        10L,
                        "POL001",
                        "2025-06-01",
                        "1500000.00",
                        "120000.00",
                        "1620000.00"
                );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(
                List.of(policyOnePayment)
        );

        /*
         * Intérêts actuellement ouverts :
         *
         * POL001 = 35 000
         * POL002 = 70 000
         */
        when(
                interestCalculationEngine.calculate(
                        eq("POL001"),
                        eq(CALCULATION_DATE),
                        eq(
                                LocalDate.of(
                                        2030,
                                        1,
                                        1
                                )
                        ),
                        eq(ANNUAL_RATE),
                        any()
                )
        ).thenReturn(
                simulation(
                        "POL001",
                        "1500000.00",
                        "35000.00"
                )
        );

        when(
                interestCalculationEngine.calculate(
                        eq("POL002"),
                        eq(CALCULATION_DATE),
                        eq(
                                LocalDate.of(
                                        2031,
                                        6,
                                        15
                                )
                        ),
                        eq(ANNUAL_RATE),
                        any()
                )
        ).thenReturn(
                simulation(
                        "POL002",
                        "2000000.00",
                        "70000.00"
                )
        );

        DashboardResponse response =
                dashboardService.getDashboard();

        assertThat(response.calculationDate())
                .isEqualTo(CALCULATION_DATE);

        assertThat(response.metrics().totalPolicies())
                .isEqualTo(2);

        assertThat(response.metrics().totalMaturities())
                .isEqualTo(3);

        assertThat(
                response.metrics()
                        .totalMaturityAmount()
        ).isEqualByComparingTo(
                "3500000.000000"
        );

        assertThat(
                response.metrics()
                        .totalPaidInterest()
        ).isEqualByComparingTo(
                "120000.000000"
        );

        assertThat(
                response.interestDistribution()
                        .openInterest()
        ).isEqualByComparingTo(
                "105000.000000"
        );

        /*
         * Intérêts générés :
         * 105 000 ouverts + 120 000 payés.
         */
        assertThat(
                response.metrics()
                        .totalGeneratedInterest()
        ).isEqualByComparingTo(
                "225000.000000"
        );

        assertThat(
                response.metrics()
                        .totalPaidAmount()
        ).isEqualByComparingTo(
                "1620000.000000"
        );
    }

    @Test
    @DisplayName(
            "Les paiements futurs doivent être exclus de la photographie financière"
    )
    void shouldExcludePaymentsAfterBusinessDate() {
        PolicyMaturityEntity maturity =
                maturity(
                        1L,
                        "POL001",
                        1,
                        "2024-01-01",
                        "1000000.00",
                        "2030-01-01"
                );

        when(
                policyMaturityRepository
                        .findAllByOrderByPolicyNumberAscMaturityRankAsc()
        ).thenReturn(
                List.of(maturity)
        );

        PaymentEntity currentPayment =
                paidPayment(
                        10L,
                        "POL001",
                        "2026-08-01",
                        "1000000.00",
                        "100000.00",
                        "1100000.00"
                );

        PaymentEntity futurePayment =
                paidPayment(
                        11L,
                        "POL001",
                        "2026-09-01",
                        "500000.00",
                        "50000.00",
                        "550000.00"
                );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(
                List.of(
                        currentPayment,
                        futurePayment
                )
        );

        when(
                interestCalculationEngine.calculate(
                        eq("POL001"),
                        eq(CALCULATION_DATE),
                        any(LocalDate.class),
                        eq(ANNUAL_RATE),
                        any()
                )
        ).thenReturn(
                simulation(
                        "POL001",
                        "0.00",
                        "0.00"
                )
        );

        DashboardResponse response =
                dashboardService.getDashboard();

        assertThat(
                response.metrics()
                        .totalPaidInterest()
        ).isEqualByComparingTo(
                "100000.000000"
        );

        assertThat(
                response.metrics()
                        .totalPaidAmount()
        ).isEqualByComparingTo(
                "1100000.000000"
        );

        assertThat(response.recentPayments())
                .extracting(
                        payment ->
                                payment.id()
                )
                .containsExactly(10L);

        /*
         * Vérifie également que seul le paiement
         * courant est transmis au moteur.
         */
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CalculationEvent>>
                eventCaptor =
                ArgumentCaptor.forClass(
                        List.class
                );

        verify(interestCalculationEngine)
                .calculate(
                        eq("POL001"),
                        eq(CALCULATION_DATE),
                        any(LocalDate.class),
                        eq(ANNUAL_RATE),
                        eventCaptor.capture()
                );

        assertThat(eventCaptor.getValue())
                .filteredOn(event ->
                        event.eventKind()
                                == CalculationEvent
                                .EventKind
                                .PAYMENT
                )
                .extracting(
                        CalculationEvent::eventId
                )
                .containsExactly(10L);
    }

    @Test
    @DisplayName(
            "Le dashboard doit produire exactement douze statistiques mensuelles"
    )
    void shouldAlwaysReturnTwelvePaymentMonths() {
        PolicyMaturityEntity maturity =
                maturity(
                        1L,
                        "POL001",
                        1,
                        "2024-01-01",
                        "1000000.00",
                        "2030-01-01"
                );

        when(
                policyMaturityRepository
                        .findAllByOrderByPolicyNumberAscMaturityRankAsc()
        ).thenReturn(
                List.of(maturity)
        );

        PaymentEntity septemberPayment =
                paidPayment(
                        10L,
                        "POL001",
                        "2025-09-10",
                        "900000.00",
                        "100000.00",
                        "1000000.00"
                );

        PaymentEntity augustPayment =
                paidPayment(
                        11L,
                        "POL001",
                        "2026-08-01",
                        "1800000.00",
                        "200000.00",
                        "2000000.00"
                );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(
                List.of(
                        septemberPayment,
                        augustPayment
                )
        );

        when(
                interestCalculationEngine.calculate(
                        eq("POL001"),
                        eq(CALCULATION_DATE),
                        any(LocalDate.class),
                        eq(ANNUAL_RATE),
                        any()
                )
        ).thenReturn(
                simulation(
                        "POL001",
                        "0.00",
                        "0.00"
                )
        );

        DashboardResponse response =
                dashboardService.getDashboard();

        assertThat(response.monthlyPayments())
                .hasSize(12);

        assertThat(
                response.monthlyPayments()
                        .getFirst()
                        .month()
        ).isEqualTo("2025-09");

        assertThat(
                response.monthlyPayments()
                        .getLast()
                        .month()
        ).isEqualTo("2026-08");

        MonthlyPaymentStatisticResponse
                september =
                findMonth(
                        response,
                        "2025-09"
                );

        assertThat(september.paymentCount())
                .isEqualTo(1);

        assertThat(september.paidAmount())
                .isEqualByComparingTo(
                        "1000000.000000"
                );

        assertThat(september.interestAmount())
                .isEqualByComparingTo(
                        "100000.000000"
                );

        MonthlyPaymentStatisticResponse
                october =
                findMonth(
                        response,
                        "2025-10"
                );

        assertThat(october.paymentCount())
                .isZero();

        assertThat(october.paidAmount())
                .isEqualByComparingTo(
                        "0.000000"
                );

        MonthlyPaymentStatisticResponse
                august =
                findMonth(
                        response,
                        "2026-08"
                );

        assertThat(august.paidAmount())
                .isEqualByComparingTo(
                        "2000000.000000"
                );
    }

    @Test
    @DisplayName(
            "Le pourcentage payé doit être calculé sur les intérêts générés"
    )
    void shouldCalculatePaidInterestPercentage() {
        PolicyMaturityEntity maturity =
                maturity(
                        1L,
                        "POL001",
                        1,
                        "2024-01-01",
                        "1000000.00",
                        "2030-01-01"
                );

        when(
                policyMaturityRepository
                        .findAllByOrderByPolicyNumberAscMaturityRankAsc()
        ).thenReturn(
                List.of(maturity)
        );

        PaymentEntity payment =
                paidPayment(
                        10L,
                        "POL001",
                        "2025-01-01",
                        "800000.00",
                        "250000.00",
                        "1050000.00"
                );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(
                List.of(payment)
        );

        when(
                interestCalculationEngine.calculate(
                        eq("POL001"),
                        eq(CALCULATION_DATE),
                        any(LocalDate.class),
                        eq(ANNUAL_RATE),
                        any()
                )
        ).thenReturn(
                simulation(
                        "POL001",
                        "1000000.00",
                        "750000.00"
                )
        );

        DashboardResponse response =
                dashboardService.getDashboard();

        /*
         * 250 000 payés sur 1 000 000 générés
         * représente 25 %.
         */
        assertThat(
                response.interestDistribution()
                        .generatedInterest()
        ).isEqualByComparingTo(
                "1000000.000000"
        );

        assertThat(
                response.interestDistribution()
                        .paidPercentage()
        ).isEqualByComparingTo(
                "25.000000"
        );
    }

    @Test
    @DisplayName(
            "Une absence d'intérêts doit produire un pourcentage nul"
    )
    void shouldReturnZeroPercentageWithoutInterest() {
        when(
                policyMaturityRepository
                        .findAllByOrderByPolicyNumberAscMaturityRankAsc()
        ).thenReturn(List.of());

        DashboardResponse response =
                dashboardService.getDashboard();

        assertThat(
                response.metrics()
                        .totalPolicies()
        ).isZero();

        assertThat(
                response.metrics()
                        .totalMaturities()
        ).isZero();

        assertThat(
                response.interestDistribution()
                        .paidPercentage()
        ).isEqualByComparingTo(
                "0.000000"
        );

        assertThat(response.monthlyPayments())
                .hasSize(12);

        assertThat(response.recentPayments())
                .isEmpty();
    }

    @Test
    @DisplayName(
            "Les imports annulés doivent rester visibles dans l'historique récent"
    )
    void shouldIncludeReversedImportInRecentHistory() {
        when(
                policyMaturityRepository
                        .findAllByOrderByPolicyNumberAscMaturityRankAsc()
        ).thenReturn(List.of());

        ImportBatchEntity reversedImport =
                importBatch(
                        12L,
                        "maturites-corrigees.csv",
                        ImportBatchStatus.REVERSED,
                        8
                );

        reversedImport.setCreatedAt(
                Instant.parse(
                        "2026-08-10T08:00:00Z"
                )
        );

        reversedImport.setCreatedBy(
                "first-admin"
        );

        reversedImport.setReversedAt(
                Instant.parse(
                        "2026-08-16T14:30:00Z"
                )
        );

        reversedImport.setReversedBy(
                "admin"
        );

        when(
                importBatchRepository
                        .findRecentImports(
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of(reversedImport)
        );

        DashboardResponse response =
                dashboardService.getDashboard();

        assertThat(response.recentImports())
                .singleElement()
                .satisfies(recentImport -> {
                    assertThat(
                            recentImport.status()
                    ).isEqualTo(
                            ImportBatchStatus.REVERSED
                    );

                    assertThat(
                            recentImport.insertedRows()
                    ).isEqualTo(8);

                    assertThat(
                            recentImport.occurredAt()
                    ).isEqualTo(
                            Instant.parse(
                                    "2026-08-16T14:30:00Z"
                            )
                    );

                    assertThat(
                            recentImport.actor()
                    ).isEqualTo("admin");
                });
    }

    @Test
    @DisplayName(
            "Les paiements récents doivent être triés par date puis identifiant décroissants"
    )
    void shouldReturnFiveMostRecentPaidPayments() {
        PolicyMaturityEntity maturity =
                maturity(
                        1L,
                        "POL001",
                        1,
                        "2024-01-01",
                        "1000000.00",
                        "2030-01-01"
                );

        when(
                policyMaturityRepository
                        .findAllByOrderByPolicyNumberAscMaturityRankAsc()
        ).thenReturn(
                List.of(maturity)
        );

        List<PaymentEntity> payments =
                List.of(
                        paidPayment(
                                1L,
                                "POL001",
                                "2026-01-01",
                                "100.00",
                                "10.00",
                                "110.00"
                        ),
                        paidPayment(
                                2L,
                                "POL001",
                                "2026-02-01",
                                "200.00",
                                "20.00",
                                "220.00"
                        ),
                        paidPayment(
                                3L,
                                "POL001",
                                "2026-03-01",
                                "300.00",
                                "30.00",
                                "330.00"
                        ),
                        paidPayment(
                                4L,
                                "POL001",
                                "2026-04-01",
                                "400.00",
                                "40.00",
                                "440.00"
                        ),
                        paidPayment(
                                5L,
                                "POL001",
                                "2026-05-01",
                                "500.00",
                                "50.00",
                                "550.00"
                        ),
                        paidPayment(
                                6L,
                                "POL001",
                                "2026-06-01",
                                "600.00",
                                "60.00",
                                "660.00"
                        )
                );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(payments);

        when(
                interestCalculationEngine.calculate(
                        eq("POL001"),
                        eq(CALCULATION_DATE),
                        any(LocalDate.class),
                        eq(ANNUAL_RATE),
                        any()
                )
        ).thenReturn(
                simulation(
                        "POL001",
                        "0.00",
                        "0.00"
                )
        );

        DashboardResponse response =
                dashboardService.getDashboard();

        assertThat(response.recentPayments())
                .hasSize(5);

        assertThat(response.recentPayments())
                .extracting(
                        recentPayment ->
                                recentPayment.id()
                )
                .containsExactly(
                        6L,
                        5L,
                        4L,
                        3L,
                        2L
                );
    }

    private MonthlyPaymentStatisticResponse findMonth(
            DashboardResponse response,
            String month
    ) {
        return response.monthlyPayments()
                .stream()
                .filter(statistic ->
                        statistic.month()
                                .equals(month)
                )
                .findFirst()
                .orElseThrow();
    }

    private InterestSimulationResponse simulation(
            String policyNumber,
            String openCapital,
            String openInterest
    ) {
        BigDecimal normalizedOpenCapital =
                new BigDecimal(
                        openCapital
                );

        BigDecimal normalizedOpenInterest =
                new BigDecimal(
                        openInterest
                );

        return new InterestSimulationResponse(
                policyNumber,
                CALCULATION_DATE,
                LocalDate.of(
                        2030,
                        1,
                        1
                ),
                false,
                ANNUAL_RATE,
                1,
                normalizedOpenCapital,
                normalizedOpenInterest,
                normalizedOpenCapital.add(
                        normalizedOpenInterest
                ),
                List.of()
        );
    }

    private PolicyMaturityEntity maturity(
            Long id,
            String policyNumber,
            int rank,
            String maturityDate,
            String amount,
            String interestEndDate
    ) {
        PolicyMaturityEntity maturity =
                new PolicyMaturityEntity();

        maturity.setId(id);
        maturity.setPolicyNumber(
                policyNumber
        );
        maturity.setMaturityType(
                "MATURITE_" + rank
        );
        maturity.setMaturityRank(rank);
        maturity.setMaturityDate(
                LocalDate.parse(
                        maturityDate
                )
        );
        maturity.setMaturityAmount(
                new BigDecimal(amount)
        );
        maturity.setInterestEndDate(
                LocalDate.parse(
                        interestEndDate
                )
        );
        maturity.setSourceRowNumber(
                rank + 1
        );

        return maturity;
    }

    private PaymentEntity paidPayment(
            Long id,
            String policyNumber,
            String paymentDate,
            String capitalAmount,
            String interestAmount,
            String paidAmount
    ) {
        PaymentEntity payment =
                new PaymentEntity();

        payment.setId(id);
        payment.setPolicyNumber(
                policyNumber
        );
        payment.setPaymentDate(
                LocalDate.parse(
                        paymentDate
                )
        );
        payment.setCalculationDate(
                LocalDate.parse(
                        paymentDate
                )
        );
        payment.setAnnualRate(
                ANNUAL_RATE
        );
        payment.setCapitalAmount(
                new BigDecimal(
                        capitalAmount
                )
        );
        payment.setInterestAmount(
                new BigDecimal(
                        interestAmount
                )
        );
        payment.setPaidAmount(
                new BigDecimal(
                        paidAmount
                )
        );
        payment.setCompletedCycles(1);
        payment.setStatus(
                PaymentStatus.PAID
        );
        payment.setCreatedBy(
                "accounting"
        );

        return payment;
    }

    private ImportBatchEntity importBatch(
            Long id,
            String fileName,
            ImportBatchStatus status,
            int insertedRows
    ) {
        ImportBatchEntity batch =
                new ImportBatchEntity();

        batch.setId(id);
        batch.setOriginalFileName(
                fileName
        );
        batch.setFileSha256(
                "a".repeat(64)
        );
        batch.setFileSizeBytes(1024L);
        batch.setTotalRows(insertedRows);
        batch.setInsertedRows(
                insertedRows
        );
        batch.setExistingRows(0);
        batch.setErrorRows(0);
        batch.setStatus(status);

        return batch;
    }
}