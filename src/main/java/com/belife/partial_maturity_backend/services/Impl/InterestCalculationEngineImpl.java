package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.dtos.responses.InterestCalculationLineResponse;
import com.belife.partial_maturity_backend.dtos.responses.InterestSimulationResponse;
import com.belife.partial_maturity_backend.enums.CalculationEventType;
import com.belife.partial_maturity_backend.services.InterestCalculationEngine;
import com.belife.partial_maturity_backend.services.models.CalculationEvent;
import com.belife.partial_maturity_backend.utils.FinancialAmountUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.belife.partial_maturity_backend.utils.FinancialAmountUtils.normalize;

/**
 * Implémente le calcul des intérêts capitalisés par cycles
 * complets de douze mois.
 *
 * <p>Règles principales :</p>
 *
 * <ul>
 *     <li>aucun prorata pour une période incomplète ;</li>
 *     <li>capitalisation après chaque cycle de douze mois ;</li>
 *     <li>intérêts calculés avant l'ajout d'une maturité ;</li>
 *     <li>maturité avant paiement à date identique ;</li>
 *     <li>paiement valide : remise de la situation à zéro ;</li>
 *     <li>aucun intérêt produit après la date de fin ;</li>
 *     <li>normalisation financière à six décimales.</li>
 * </ul>
 */
@Service
public class InterestCalculationEngineImpl
        implements InterestCalculationEngine {

    private static final BigDecimal ZERO =
            FinancialAmountUtils.zero();

    @Override
    public InterestSimulationResponse calculate(
            String policyNumber,
            LocalDate calculationDate,
            LocalDate interestEndDate,
            BigDecimal annualRate,
            List<CalculationEvent> events
    ) {
        Objects.requireNonNull(
                policyNumber,
                "Le numéro de police est obligatoire."
        );

        Objects.requireNonNull(
                calculationDate,
                "La date de calcul est obligatoire."
        );

        Objects.requireNonNull(
                interestEndDate,
                "La date de fin des intérêts est obligatoire."
        );

        Objects.requireNonNull(
                annualRate,
                "Le taux annuel est obligatoire."
        );

        Objects.requireNonNull(
                events,
                "La liste des événements est obligatoire."
        );

        List<CalculationEvent> orderedEvents =
                events.stream()
                        /*
                         * Les événements futurs ne participent
                         * pas à la situation courante.
                         */
                        .filter(event ->
                                !event.eventDate()
                                        .isAfter(
                                                calculationDate
                                        )
                        )
                        .sorted(eventComparator())
                        .toList();

        CalculationState state =
                new CalculationState();

        for (CalculationEvent event : orderedEvents) {
            /*
             * Les cycles sont arrêtés à la date de fin des
             * intérêts. L'événement lui-même reste cependant
             * appliqué à sa date réelle, notamment lorsqu'il
             * s'agit d'un paiement effectué après la clôture.
             */
            LocalDate interestCalculationTarget =
                    minimumDate(
                            event.eventDate(),
                            interestEndDate
                    );

            applyCompletedCycles(
                    state,
                    interestCalculationTarget,
                    annualRate
            );

            if (
                    event.eventKind()
                            == CalculationEvent
                            .EventKind
                            .MATURITY
            ) {
                applyMaturity(
                        state,
                        event
                );
            } else {
                applyPayment(
                        state,
                        event
                );
            }
        }

        /*
         * Après le dernier événement, les cycles complets
         * restants sont appliqués jusqu'à la première des
         * deux dates suivantes :
         *
         * - date métier du calcul ;
         * - date de fin de production des intérêts.
         */
        LocalDate finalInterestCalculationDate =
                minimumDate(
                        calculationDate,
                        interestEndDate
                );

        applyCompletedCycles(
                state,
                finalInterestCalculationDate,
                annualRate
        );

        boolean interestAccrualClosed =
                !calculationDate.isBefore(
                        interestEndDate
                );

        return new InterestSimulationResponse(
                policyNumber,
                calculationDate,
                interestEndDate,
                interestAccrualClosed,
                annualRate,
                state.completedCycles,
                normalize(state.openCapital),
                normalize(state.openInterest),
                normalize(state.balance),
                state.lines
        );
    }

    /**
     * Trie les événements par :
     *
     * <ul>
     *     <li>date croissante ;</li>
     *     <li>maturité avant paiement ;</li>
     *     <li>rang croissant des maturités ;</li>
     *     <li>identifiant technique pour stabiliser le tri.</li>
     * </ul>
     */
    private Comparator<CalculationEvent>
    eventComparator() {
        return Comparator
                .comparing(
                        CalculationEvent::eventDate
                )
                .thenComparingInt(event ->
                        event.eventKind()
                                == CalculationEvent
                                .EventKind
                                .MATURITY
                                ? 0
                                : 1
                )
                .thenComparingInt(
                        CalculationEvent::rank
                )
                .thenComparing(
                        CalculationEvent::eventId,
                        Comparator.nullsLast(
                                Comparator.naturalOrder()
                        )
                );
    }

    /**
     * Applique tous les cycles complets de douze mois
     * entre la date de référence et la date cible.
     */
    private void applyCompletedCycles(
            CalculationState state,
            LocalDate targetDate,
            BigDecimal annualRate
    ) {
        if (
                state.referenceDate == null
                        || state.balance.signum() <= 0
                        || targetDate.isBefore(
                        state.referenceDate
                )
        ) {
            return;
        }

        long elapsedMonths =
                ChronoUnit.MONTHS.between(
                        state.referenceDate,
                        targetDate
                );

        long completedCycles =
                Math.max(
                        0,
                        elapsedMonths / 12
                );

        for (
                long cycle = 1;
                cycle <= completedCycles;
                cycle++
        ) {
            BigDecimal balanceBefore =
                    state.balance;

            /*
             * L'intérêt est normalisé avant sa capitalisation.
             * Le cycle suivant travaille donc sur le montant
             * financier réellement retenu.
             */
            BigDecimal interest =
                    normalize(
                            balanceBefore.multiply(
                                    annualRate
                            )
                    );

            state.balance =
                    normalize(
                            balanceBefore.add(
                                    interest
                            )
                    );

            state.openInterest =
                    normalize(
                            state.openInterest.add(
                                    interest
                            )
                    );

            state.completedCycles++;

            long globalCycleNumber =
                    state.completedCycles;

            LocalDate cycleDate =
                    state.referenceDate
                            .plusYears(cycle);

            state.lines.add(
                    new InterestCalculationLineResponse(
                            state.nextSequence(),
                            cycleDate,
                            CalculationEventType.INTEREST_APPLIED,
                            "Application du cycle annuel complet numéro "
                                    + globalCycleNumber
                                    + ".",
                            globalCycleNumber,
                            balanceBefore,
                            ZERO,
                            annualRate,
                            interest,
                            ZERO,
                            state.balance
                    )
            );

//            state.completedCycles++;
//
//            LocalDate cycleDate =
//                    state.referenceDate
//                            .plusYears(cycle);
//
//            state.lines.add(
//                    new InterestCalculationLineResponse(
//                            state.nextSequence(),
//                            cycleDate,
//                            CalculationEventType
//                                    .INTEREST_APPLIED,
//                            "Application du cycle annuel complet numéro "
//                                    + cycle
//                                    + ".",
//                            cycle,
//                            balanceBefore,
//                            ZERO,
//                            annualRate,
//                            interest,
//                            ZERO,
//                            state.balance
//                    )
//            );
        }

        /*
         * La date de référence n'est déplacée que du nombre
         * exact de cycles réellement consommés.
         *
         * Les mois restants sont conservés jusqu'à ce qu'un
         * nouvel événement métier remplace cette référence.
         */
        if (completedCycles > 0) {
            state.referenceDate =
                    state.referenceDate.plusYears(
                            completedCycles
                    );
        }
    }

    /**
     * Ajoute une maturité après application préalable
     * des cycles échus.
     */
    private void applyMaturity(
            CalculationState state,
            CalculationEvent event
    ) {
        BigDecimal balanceBefore =
                state.balance;

        BigDecimal maturityAmount =
                normalize(event.amount());

        state.balance =
                normalize(
                        state.balance.add(
                                maturityAmount
                        )
                );

        state.openCapital =
                normalize(
                        state.openCapital.add(
                                maturityAmount
                        )
                );

        /*
         * Toute nouvelle maturité devient la nouvelle date
         * de référence. Une période annuelle incomplète
         * antérieure n'est pas reportée.
         */
        state.referenceDate =
                event.eventDate();

        state.lines.add(
                new InterestCalculationLineResponse(
                        state.nextSequence(),
                        event.eventDate(),
                        CalculationEventType
                                .MATURITY_ADDED,
                        "Ajout de "
                                + event.label()
                                + " au capital ouvert.",
                        null,
                        balanceBefore,
                        maturityAmount,
                        null,
                        ZERO,
                        ZERO,
                        state.balance
                )
        );
    }

    /**
     * Applique un paiement total valide.
     *
     * <p>Le montant provenant de l'événement correspond
     * au montant réellement enregistré dans le paiement.
     * Il est conservé dans la ligne explicative au lieu
     * d'être remplacé par le solde recalculé.</p>
     */
    private void applyPayment(
            CalculationState state,
            CalculationEvent event
    ) {
        BigDecimal balanceBefore =
                state.balance;

        BigDecimal paidAmount =
                normalize(event.amount());

        state.lines.add(
                new InterestCalculationLineResponse(
                        state.nextSequence(),
                        event.eventDate(),
                        CalculationEventType
                                .PAYMENT_APPLIED,
                        event.label()
                                + " : paiement total "
                                + "de la situation.",
                        null,
                        balanceBefore,
                        ZERO,
                        null,
                        ZERO,
                        paidAmount,
                        ZERO
                )
        );

        /*
         * Un paiement PAID représente toujours un paiement
         * total. La situation est donc remise à zéro même si
         * une ancienne donnée historique présente un écart
         * avec le solde recalculé.
         */
        state.balance = ZERO;
        state.openCapital = ZERO;
        state.openInterest = ZERO;
        state.referenceDate = null;
    }

    private LocalDate minimumDate(
            LocalDate first,
            LocalDate second
    ) {
        return first.isBefore(second)
                ? first
                : second;
    }

    /**
     * État mutable limité à l'exécution d'un calcul.
     *
     * <p>Cette classe n'est jamais partagée entre plusieurs
     * requêtes.</p>
     */
    private static final class CalculationState {

        private BigDecimal balance =
                FinancialAmountUtils.zero();

        private BigDecimal openCapital =
                FinancialAmountUtils.zero();

        private BigDecimal openInterest =
                FinancialAmountUtils.zero();

        private LocalDate referenceDate;

        private long completedCycles;

        private int sequence;

        private final List<
                InterestCalculationLineResponse
                > lines =
                new ArrayList<>();

        private int nextSequence() {
            sequence++;
            return sequence;
        }
    }
}