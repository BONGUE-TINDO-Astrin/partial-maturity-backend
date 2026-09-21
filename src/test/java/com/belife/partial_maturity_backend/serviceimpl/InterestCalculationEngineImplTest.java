package com.belife.partial_maturity_backend.serviceimpl;

import com.belife.partial_maturity_backend.dtos.responses.InterestCalculationLineResponse;
import com.belife.partial_maturity_backend.dtos.responses.InterestSimulationResponse;
import com.belife.partial_maturity_backend.enums.CalculationEventType;
import com.belife.partial_maturity_backend.services.Impl.InterestCalculationEngineImpl;
import com.belife.partial_maturity_backend.services.models.CalculationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du moteur financier.
 *
 * <p>Ces tests ne démarrent pas Spring et n'accèdent pas
 * à la base de données. Les événements sont construits
 * directement afin d'isoler les règles de calcul.</p>
 */
class InterestCalculationEngineImplTest {

    private static final String POLICY_NUMBER =
            "POL001";

    private static final BigDecimal ANNUAL_RATE =
            new BigDecimal("0.035");

    private InterestCalculationEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new InterestCalculationEngineImpl();
    }

    @Test
    @DisplayName(
            "Une période inférieure à douze mois ne doit produire aucun intérêt"
    )
    void shouldNotApplyInterestForIncompleteCycle() {
        LocalDate maturityDate =
                LocalDate.of(2025, 1, 1);

        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2025, 12, 31),
                        LocalDate.of(2030, 1, 1),
                        maturity(
                                1L,
                                maturityDate,
                                1,
                                "1000.00"
                        )
                );

        assertThat(simulation.completedCycles())
                .isZero();

        assertThat(simulation.openCapital())
                .isEqualByComparingTo(
                        "1000.000000"
                );

        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "0.000000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "1000.000000"
                );

        assertThat(simulation.lines())
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.eventType())
                            .isEqualTo(
                                    CalculationEventType
                                            .MATURITY_ADDED
                            );

                    assertThat(line.capitalAdded())
                            .isEqualByComparingTo(
                                    "1000.000000"
                            );
                });
    }

    @Test
    @DisplayName(
            "Un cycle complet doit appliquer le taux annuel de 3,5 pour cent"
    )
    void shouldApplyOneCompleteAnnualCycle() {
        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2030, 1, 1),
                        maturity(
                                1L,
                                LocalDate.of(2025, 1, 1),
                                1,
                                "1000.00"
                        )
                );

        assertThat(simulation.annualRate())
                .isEqualByComparingTo(
                        "0.035"
                );

        assertThat(simulation.completedCycles())
                .isEqualTo(1);

        assertThat(simulation.openCapital())
                .isEqualByComparingTo(
                        "1000.000000"
                );

        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "35.000000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "1035.000000"
                );

        assertThat(simulation.lines())
                .hasSize(2);

        InterestCalculationLineResponse interestLine =
                simulation.lines().get(1);

        assertThat(interestLine.eventType())
                .isEqualTo(
                        CalculationEventType
                                .INTEREST_APPLIED
                );

        assertThat(interestLine.cycleNumber())
                .isEqualTo(1L);

        assertThat(interestLine.balanceBefore())
                .isEqualByComparingTo(
                        "1000.000000"
                );

        assertThat(interestLine.interestAmount())
                .isEqualByComparingTo(
                        "35.000000"
                );

        assertThat(interestLine.balanceAfter())
                .isEqualByComparingTo(
                        "1035.000000"
                );
    }

    @Test
    @DisplayName(
            "Plusieurs cycles doivent capitaliser les intérêts"
    )
    void shouldCapitalizeInterestAcrossMultipleCycles() {
        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2027, 1, 1),
                        LocalDate.of(2030, 1, 1),
                        maturity(
                                1L,
                                LocalDate.of(2025, 1, 1),
                                1,
                                "1000.00"
                        )
                );

        /*
         * Cycle 1 :
         * 1000,000000 × 3,5 % = 35,000000
         *
         * Cycle 2 :
         * 1035,000000 × 3,5 % = 36,225000
         */
        assertThat(simulation.completedCycles())
                .isEqualTo(2);

        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "71.23"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "1071.23"
                );

        assertThat(simulation.lines())
                .extracting(
                        InterestCalculationLineResponse
                                ::eventType
                )
                .containsExactly(
                        CalculationEventType
                                .MATURITY_ADDED,
                        CalculationEventType
                                .INTEREST_APPLIED,
                        CalculationEventType
                                .INTEREST_APPLIED
                );
    }

    @Test
    @DisplayName(
            "Une nouvelle maturité doit devenir la nouvelle date de référence"
    )
    void shouldResetReferenceDateWhenMaturityIsAdded() {
        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2025, 7, 1),
                        LocalDate.of(2030, 1, 1),
                        maturity(
                                1L,
                                LocalDate.of(2024, 1, 1),
                                1,
                                "1000.00"
                        ),
                        maturity(
                                2L,
                                LocalDate.of(2024, 7, 1),
                                2,
                                "500.00"
                        )
                );

        /*
         * Les six mois compris entre janvier et juillet 2024
         * ne sont pas reportés après la seconde maturité.
         *
         * Le seul cycle complet commence donc le 1er juillet 2024.
         */
        assertThat(simulation.completedCycles())
                .isEqualTo(1);

        assertThat(simulation.openCapital())
                .isEqualByComparingTo(
                        "1500.000000"
                );

        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "52.500000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "1552.500000"
                );

        assertThat(simulation.lines())
                .extracting(
                        InterestCalculationLineResponse
                                ::eventType
                )
                .containsExactly(
                        CalculationEventType
                                .MATURITY_ADDED,
                        CalculationEventType
                                .MATURITY_ADDED,
                        CalculationEventType
                                .INTEREST_APPLIED
                );
    }

    @Test
    @DisplayName(
            "Les intérêts doivent s'arrêter à la date de fin configurée"
    )
    void shouldStopInterestAtInterestEndDate() {
        LocalDate interestEndDate =
                LocalDate.of(2026, 1, 1);

        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2028, 1, 1),
                        interestEndDate,
                        maturity(
                                1L,
                                LocalDate.of(2025, 1, 1),
                                1,
                                "1000.00"
                        )
                );

        /*
         * La date de calcul est située trois ans après la maturité,
         * mais la production est clôturée après le premier cycle.
         */
        assertThat(simulation.completedCycles())
                .isEqualTo(1);

        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "35.000000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "1035.000000"
                );

        assertThat(simulation.interestEndDate())
                .isEqualTo(interestEndDate);

        assertThat(simulation.interestAccrualClosed())
                .isTrue();

        assertThat(simulation.lines())
                .filteredOn(
                        line ->
                                line.eventType()
                                        == CalculationEventType
                                        .INTEREST_APPLIED
                )
                .singleElement()
                .extracting(
                        InterestCalculationLineResponse
                                ::eventDate
                )
                .isEqualTo(interestEndDate);
    }

    @Test
    @DisplayName(
            "La production d'intérêts doit rester ouverte avant la date de fin"
    )
    void shouldKeepInterestAccrualOpenBeforeEndDate() {
        LocalDate interestEndDate =
                LocalDate.of(2030, 1, 1);

        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2026, 1, 1),
                        interestEndDate,
                        maturity(
                                1L,
                                LocalDate.of(2025, 1, 1),
                                1,
                                "1000.00"
                        )
                );

        assertThat(simulation.interestEndDate())
                .isEqualTo(interestEndDate);

        assertThat(simulation.interestAccrualClosed())
                .isFalse();

        assertThat(simulation.completedCycles())
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "La production d'intérêts doit être clôturée le jour de la date de fin"
    )
    void shouldCloseInterestAccrualOnEndDate() {
        LocalDate interestEndDate =
                LocalDate.of(2026, 1, 1);

        InterestSimulationResponse simulation =
                calculate(
                        interestEndDate,
                        interestEndDate,
                        maturity(
                                1L,
                                LocalDate.of(2025, 1, 1),
                                1,
                                "1000.00"
                        )
                );

        assertThat(simulation.interestAccrualClosed())
                .isTrue();

        assertThat(simulation.completedCycles())
                .isEqualTo(1);

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "1035.000000"
                );
    }

    @Test
    @DisplayName(
            "Une maturité le jour de la fin des intérêts doit être ajoutée sans cycle ultérieur"
    )
    void shouldAddMaturityOnInterestEndDateWithoutLaterInterest() {
        LocalDate interestEndDate =
                LocalDate.of(2026, 1, 1);

        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2028, 1, 1),
                        interestEndDate,
                        maturity(
                                1L,
                                LocalDate.of(2025, 1, 1),
                                1,
                                "1000.00"
                        ),
                        maturity(
                                2L,
                                interestEndDate,
                                2,
                                "500.00"
                        )
                );

        /*
         * Le capital initial produit un cycle jusqu'au 01/01/2026.
         * La seconde maturité est ensuite ajoutée le jour de la
         * clôture, sans produire de nouvel intérêt.
         */
        assertThat(simulation.completedCycles())
                .isEqualTo(1);

        assertThat(simulation.openCapital())
                .isEqualByComparingTo(
                        "1500.000000"
                );

        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "35.000000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "1535.000000"
                );

        assertThat(simulation.lines())
                .extracting(
                        InterestCalculationLineResponse
                                ::eventType
                )
                .containsExactly(
                        CalculationEventType
                                .MATURITY_ADDED,
                        CalculationEventType
                                .INTEREST_APPLIED,
                        CalculationEventType
                                .MATURITY_ADDED
                );
    }

    @Test
    @DisplayName(
            "Un paiement après la fin des intérêts doit rester autorisé"
    )
    void shouldApplyPaymentAfterInterestEndDate() {
        LocalDate interestEndDate =
                LocalDate.of(2026, 1, 1);

        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2027, 1, 1),
                        interestEndDate,
                        maturity(
                                1L,
                                LocalDate.of(2025, 1, 1),
                                1,
                                "1000.00"
                        ),
                        payment(
                                10L,
                                LocalDate.of(2026, 6, 1),
                                "1035.00"
                        )
                );

        assertThat(simulation.completedCycles())
                .isEqualTo(1);

        assertThat(simulation.openCapital())
                .isEqualByComparingTo(
                        "0.000000"
                );

        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "0.000000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "0.000000"
                );

        InterestCalculationLineResponse paymentLine =
                findPaymentLine(simulation);

        assertThat(paymentLine.eventDate())
                .isEqualTo(
                        LocalDate.of(2026, 6, 1)
                );

        assertThat(paymentLine.paidAmount())
                .isEqualByComparingTo(
                        "1035.000000"
                );

        assertThat(paymentLine.balanceAfter())
                .isEqualByComparingTo(
                        "0.000000"
                );
    }

    @Test
    @DisplayName(
            "Une ligne de paiement doit exposer le montant réellement enregistré"
    )
    void shouldDisplayRecordedPaymentAmount() {
        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2026, 1, 2),
                        LocalDate.of(2030, 1, 1),
                        maturity(
                                1L,
                                LocalDate.of(2025, 1, 1),
                                1,
                                "1000.00"
                        ),
                        payment(
                                10L,
                                LocalDate.of(2026, 1, 1),
                                "1035.00"
                        )
                );

        InterestCalculationLineResponse paymentLine =
                findPaymentLine(simulation);

        assertThat(paymentLine.balanceBefore())
                .isEqualByComparingTo(
                        "1035.000000"
                );

        assertThat(paymentLine.paidAmount())
                .isEqualByComparingTo(
                        "1035.000000"
                );

        assertThat(paymentLine.capitalAdded())
                .isEqualByComparingTo(
                        "0.000000"
                );

        assertThat(paymentLine.interestAmount())
                .isEqualByComparingTo(
                        "0.000000"
                );

        assertThat(paymentLine.balanceAfter())
                .isEqualByComparingTo(
                        "0.000000"
                );
    }

    @Test
    @DisplayName(
            "Le montant historique du paiement doit être conservé dans la ligne explicative"
    )
    void shouldPreserveHistoricalPaymentAmount() {
        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2026, 1, 2),
                        LocalDate.of(2030, 1, 1),
                        maturity(
                                1L,
                                LocalDate.of(2025, 1, 1),
                                1,
                                "1000.00"
                        ),
                        payment(
                                10L,
                                LocalDate.of(2026, 1, 1),
                                "1034.50"
                        )
                );

        /*
         * Cette différence simule une donnée historique.
         * La chronologie doit montrer le montant enregistré
         * dans le paiement, sans le remplacer par un recalcul.
         */
        InterestCalculationLineResponse paymentLine =
                findPaymentLine(simulation);

        assertThat(paymentLine.balanceBefore())
                .isEqualByComparingTo(
                        "1035.000000"
                );

        assertThat(paymentLine.paidAmount())
                .isEqualByComparingTo(
                        "1034.500000"
                );

        /*
         * Le paiement persisté est défini comme total.
         * Il clôture donc la situation même si une ancienne
         * donnée historique présente une différence.
         */
        assertThat(paymentLine.balanceAfter())
                .isEqualByComparingTo(
                        "0.000000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "0.000000"
                );
    }

    @Test
    @DisplayName(
            "Une nouvelle maturité après un paiement doit ouvrir une nouvelle situation"
    )
    void shouldOpenNewSituationAfterPayment() {
        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2027, 1, 1),
                        LocalDate.of(2030, 1, 1),
                        maturity(
                                1L,
                                LocalDate.of(2024, 1, 1),
                                1,
                                "1000.00"
                        ),
                        payment(
                                10L,
                                LocalDate.of(2025, 1, 1),
                                "1035.00"
                        ),
                        maturity(
                                2L,
                                LocalDate.of(2026, 1, 1),
                                2,
                                "500.00"
                        )
                );

        assertThat(simulation.completedCycles())
                .isEqualTo(2);

        assertThat(simulation.openCapital())
                .isEqualByComparingTo(
                        "500.000000"
                );

        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "17.500000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "517.500000"
                );

        assertThat(simulation.lines())
                .extracting(
                        InterestCalculationLineResponse
                                ::eventType
                )
                .containsExactly(
                        CalculationEventType
                                .MATURITY_ADDED,
                        CalculationEventType
                                .INTEREST_APPLIED,
                        CalculationEventType
                                .PAYMENT_APPLIED,
                        CalculationEventType
                                .MATURITY_ADDED,
                        CalculationEventType
                                .INTEREST_APPLIED
                );
    }

    @Test
    @DisplayName(
            "Une maturité doit précéder un paiement lorsque les dates sont identiques"
    )
    void shouldApplyMaturityBeforePaymentOnSameDate() {
        LocalDate eventDate =
                LocalDate.of(2025, 6, 1);

        InterestSimulationResponse simulation =
                calculate(
                        eventDate,
                        LocalDate.of(2030, 1, 1),
                        payment(
                                10L,
                                eventDate,
                                "1500.00"
                        ),
                        maturity(
                                2L,
                                eventDate,
                                2,
                                "500.00"
                        ),
                        maturity(
                                1L,
                                LocalDate.of(2024, 6, 1),
                                1,
                                "1000.00"
                        )
                );

        assertThat(simulation.lines())
                .extracting(
                        InterestCalculationLineResponse
                                ::eventType
                )
                .containsExactly(
                        CalculationEventType
                                .MATURITY_ADDED,
                        CalculationEventType
                                .INTEREST_APPLIED,
                        CalculationEventType
                                .MATURITY_ADDED,
                        CalculationEventType
                                .PAYMENT_APPLIED
                );

        InterestCalculationLineResponse paymentLine =
                findPaymentLine(simulation);

        assertThat(paymentLine.paidAmount())
                .isEqualByComparingTo(
                        "1500.000000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "0.000000"
                );
    }

    @Test
    @DisplayName(
            "Un événement futur ne doit pas participer à la situation courante"
    )
    void shouldIgnoreFutureEvent() {
        LocalDate calculationDate =
                LocalDate.of(2026, 1, 1);

        InterestSimulationResponse simulation =
                calculate(
                        calculationDate,
                        LocalDate.of(2030, 1, 1),
                        maturity(
                                1L,
                                LocalDate.of(2025, 1, 1),
                                1,
                                "1000.00"
                        ),
                        maturity(
                                2L,
                                LocalDate.of(2027, 1, 1),
                                2,
                                "500.00"
                        )
                );

        assertThat(simulation.openCapital())
                .isEqualByComparingTo(
                        "1000.000000"
                );

        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "35.000000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "1035.000000"
                );

        assertThat(simulation.lines())
                .hasSize(2);

        assertThat(simulation.lines())
                .noneSatisfy(line ->
                        assertThat(line.eventDate())
                                .isAfter(calculationDate)
                );
    }

    @Test
    @DisplayName(
            "Les intérêts doivent être arrondis à six décimales avec HALF_UP"
    )
    void shouldNormalizeAmountsToSixDecimals() {
        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2030, 1, 1),
                        maturity(
                                1L,
                                LocalDate.of(2025, 1, 1),
                                1,
                                "100.123456"
                        )
                );

        /*
         * 100,123456 × 0,035 = 3,50432096
         * Normalisation HALF_UP : 3,504321
         */
        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "3.50"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "103.62"
                );

        InterestCalculationLineResponse interestLine =
                simulation.lines()
                        .stream()
                        .filter(line ->
                                line.eventType()
                                        == CalculationEventType
                                        .INTEREST_APPLIED
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(interestLine.interestAmount())
                .isEqualByComparingTo(
                        "3.50"
                );
    }

    @Test
    @DisplayName(
            "Plusieurs maturités du même chargement doivent être appliquées par rang sans intérêt intermédiaire"
    )
    void shouldApplySameDayMaturitiesByRankWithoutIntermediateInterest() {
        LocalDate importDate =
                LocalDate.of(2026, 9, 18);

        InterestSimulationResponse simulation =
                calculate(
                        importDate,
                        LocalDate.of(2030, 12, 31),
                        maturity(
                                30L,
                                importDate,
                                5,
                                "250.00"
                        ),
                        maturity(
                                10L,
                                importDate,
                                3,
                                "1000.00"
                        ),
                        maturity(
                                20L,
                                importDate,
                                4,
                                "500.00"
                        )
                );

        assertThat(simulation.completedCycles())
                .isZero();

        assertThat(simulation.openCapital())
                .isEqualByComparingTo(
                        "1750.000000"
                );

        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "0.000000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "1750.000000"
                );

        assertThat(simulation.lines())
                .extracting(
                        InterestCalculationLineResponse
                                ::description
                )
                .containsExactly(
                        "Ajout de MATURITE_3 au capital ouvert.",
                        "Ajout de MATURITE_4 au capital ouvert.",
                        "Ajout de MATURITE_5 au capital ouvert."
                );

        assertThat(simulation.lines())
                .extracting(
                        InterestCalculationLineResponse
                                ::eventType
                )
                .containsOnly(
                        CalculationEventType
                                .MATURITY_ADDED
                );
    }

    @Test
    @DisplayName(
            "Une maturité historique doit précéder un paiement enregistré le même jour"
    )
    void shouldApplySameDayMaturityBeforePayment() {
        LocalDate eventDate =
                LocalDate.of(2026, 9, 18);

        InterestSimulationResponse simulation =
                calculate(
                        eventDate,
                        LocalDate.of(2030, 12, 31),
                        payment(
                                99L,
                                eventDate,
                                "1500.00"
                        ),
                        maturity(
                                2L,
                                eventDate,
                                2,
                                "500.00"
                        ),
                        maturity(
                                1L,
                                eventDate,
                                1,
                                "1000.00"
                        )
                );

        assertThat(simulation.lines())
                .extracting(
                        InterestCalculationLineResponse
                                ::eventType
                )
                .containsExactly(
                        CalculationEventType
                                .MATURITY_ADDED,
                        CalculationEventType
                                .MATURITY_ADDED,
                        CalculationEventType
                                .PAYMENT_APPLIED
                );

        InterestCalculationLineResponse paymentLine =
                findPaymentLine(simulation);

        assertThat(paymentLine.balanceBefore())
                .isEqualByComparingTo(
                        "1500.000000"
                );

        assertThat(simulation.openCapital())
                .isEqualByComparingTo(
                        "0.000000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "0.000000"
                );
    }

    @Test
    @DisplayName(
            "Plusieurs maturités après un paiement doivent ouvrir une nouvelle situation commune"
    )
    void shouldOpenNewSituationWithSameDayMaturitiesAfterPayment() {
        LocalDate newImportDate =
                LocalDate.of(2026, 9, 18);

        InterestSimulationResponse simulation =
                calculate(
                        LocalDate.of(2027, 9, 18),
                        LocalDate.of(2030, 12, 31),
                        maturity(
                                1L,
                                LocalDate.of(2024, 1, 1),
                                1,
                                "1000.00"
                        ),
                        payment(
                                10L,
                                LocalDate.of(2025, 1, 1),
                                "1035.00"
                        ),
                        maturity(
                                3L,
                                newImportDate,
                                3,
                                "250.00"
                        ),
                        maturity(
                                2L,
                                newImportDate,
                                2,
                                "500.00"
                        )
                );

        assertThat(simulation.openCapital())
                .isEqualByComparingTo(
                        "750.000000"
                );

        assertThat(simulation.openInterest())
                .isEqualByComparingTo(
                        "26.250000"
                );

        assertThat(simulation.balance())
                .isEqualByComparingTo(
                        "776.250000"
                );

        assertThat(simulation.completedCycles())
                .isEqualTo(2);

        assertThat(simulation.lines())
                .extracting(
                        InterestCalculationLineResponse
                                ::eventType
                )
                .containsExactly(
                        CalculationEventType.MATURITY_ADDED,
                        CalculationEventType.INTEREST_APPLIED,
                        CalculationEventType.PAYMENT_APPLIED,
                        CalculationEventType.MATURITY_ADDED,
                        CalculationEventType.MATURITY_ADDED,
                        CalculationEventType.INTEREST_APPLIED
                );
    }

    private InterestSimulationResponse calculate(
            LocalDate calculationDate,
            LocalDate interestEndDate,
            CalculationEvent... events
    ) {
        return engine.calculate(
                POLICY_NUMBER,
                calculationDate,
                interestEndDate,
                ANNUAL_RATE,
                List.of(events)
        );
    }

    private CalculationEvent maturity(
            Long eventId,
            LocalDate eventDate,
            int rank,
            String amount
    ) {
        return new CalculationEvent(
                eventId,
                eventDate,
                CalculationEvent.EventKind.MATURITY,
                rank,
                "MATURITE_" + rank,
                new BigDecimal(amount)
        );
    }

    private CalculationEvent payment(
            Long eventId,
            LocalDate eventDate,
            String amount
    ) {
        return new CalculationEvent(
                eventId,
                eventDate,
                CalculationEvent.EventKind.PAYMENT,
                0,
                "Paiement n°" + eventId,
                new BigDecimal(amount)
        );
    }

    private InterestCalculationLineResponse findPaymentLine(
            InterestSimulationResponse simulation
    ) {
        return simulation.lines()
                .stream()
                .filter(line ->
                        line.eventType()
                                == CalculationEventType
                                .PAYMENT_APPLIED
                )
                .findFirst()
                .orElseThrow();
    }
}