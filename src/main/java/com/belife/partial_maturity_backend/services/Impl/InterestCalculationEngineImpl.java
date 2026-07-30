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
 *     <li>aucun arrondi dans le moteur.</li>
 * </ul>
 */
@Service
public class InterestCalculationEngineImpl implements InterestCalculationEngine {

    private static final BigDecimal ZERO = FinancialAmountUtils.zero();

    @Override
    public InterestSimulationResponse calculate(
            String policyNumber,
            LocalDate calculationDate,
            BigDecimal annualRate,
            List<CalculationEvent> events
    ) {
        List<CalculationEvent> orderedEvents =
            events.stream()
                /*
                 * Les événements futurs ne participent
                 * pas à la situation actuelle.
                 */
                .filter(event -> !event.eventDate().isAfter(calculationDate))
                .sorted(eventComparator())
                .toList();

        CalculationState state = new CalculationState();

        for (CalculationEvent event : orderedEvents) {

            applyCompletedCycles(state, event.eventDate(), annualRate);

            if (event.eventKind() == CalculationEvent.EventKind.MATURITY) {
                applyMaturity(state, event);
            } else {
                applyPayment(state, event);
            }
        }

        /*
         * Après le dernier événement, les cycles complets
         * restants sont appliqués jusqu'à la date métier.
         */
        applyCompletedCycles(state, calculationDate, annualRate);

        return new InterestSimulationResponse(
            policyNumber,
            calculationDate,
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
     * <ol>
     *     <li>date croissante ;</li>
     *     <li>maturité avant paiement ;</li>
     *     <li>rang croissant des maturités ;</li>
     *     <li>identifiant technique pour stabiliser le tri.</li>
     * </ol>
     */
    private Comparator<CalculationEvent>
    eventComparator() {
        return Comparator
            .comparing(CalculationEvent::eventDate)
            .thenComparingInt(event ->
                event.eventKind() == CalculationEvent.EventKind.MATURITY ? 0 : 1
            )
            .thenComparingInt(CalculationEvent::rank)
            .thenComparing(
                CalculationEvent::eventId,
                Comparator.nullsLast(Comparator.naturalOrder())
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

        long elapsedMonths = ChronoUnit.MONTHS.between(state.referenceDate, targetDate);

        long completedCycles = Math.max(0, elapsedMonths / 12);

        for (long cycle = 1; cycle <= completedCycles; cycle++) {
            BigDecimal balanceBefore = state.balance;

            /*
             * L'intérêt brut est calculé avec BigDecimal puis
             * normalisé à six décimales avant capitalisation.
             *
             * Le cycle suivant travaille donc sur le solde
             * réellement retenu par la règle financière.
             */
            BigDecimal interest = normalize(balanceBefore.multiply(annualRate));

            state.balance = normalize(balanceBefore.add(interest));

            state.openInterest = normalize(state.openInterest.add(interest));

            state.completedCycles++;

            LocalDate cycleDate = state.referenceDate.plusYears(cycle);

            state.lines.add(
                new InterestCalculationLineResponse(
                    state.nextSequence(),
                    cycleDate,
                    CalculationEventType.INTEREST_APPLIED,
                    "Application du cycle annuel complet numéro "
                            + cycle
                            + ".",
                    cycle,
                    balanceBefore,
                    ZERO,
                    annualRate,
                    interest,
                    ZERO,
                    state.balance
                )
            );
        }

        /*
         * La date n'est déplacée que du nombre exact
         * de cycles réellement consommés.
         *
         * Les mois restants sont conservés tant qu'aucun
         * nouvel événement métier ne remplace la référence.
         */
        if (completedCycles > 0) {
            state.referenceDate = state.referenceDate.plusYears(completedCycles);
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
        BigDecimal balanceBefore = state.balance;

        BigDecimal normalizedMaturityAmount = normalize(event.amount());

        state.balance = normalize(state.balance.add(normalizedMaturityAmount));

        state.openCapital = normalize(state.openCapital.add(normalizedMaturityAmount));

        /*
         * Toute nouvelle maturité devient la nouvelle
         * date de référence. Une période annuelle
         * incomplète antérieure n'est donc pas reportée.
         */
        state.referenceDate = event.eventDate();

        state.lines.add(
            new InterestCalculationLineResponse(
                state.nextSequence(),
                event.eventDate(),
                CalculationEventType.MATURITY_ADDED,
                "Ajout de "
                        + event.label()
                        + " au capital ouvert.",
                null,
                balanceBefore,
                normalizedMaturityAmount,
                null,
                ZERO,
                ZERO,
                state.balance
            )
        );
    }

    /**
     * Applique un paiement total valide.
     */
    private void applyPayment(CalculationState state, CalculationEvent event) {
        BigDecimal balanceBefore = state.balance;

        BigDecimal paidAmount = balanceBefore;

        state.lines.add(
            new InterestCalculationLineResponse(
                state.nextSequence(),
                event.eventDate(),
                CalculationEventType.PAYMENT_APPLIED,
                "Paiement total de la situation.",
                null,
                balanceBefore,
                ZERO,
                null,
                ZERO,
                paidAmount,
                ZERO
            )
        );

        state.balance = ZERO;
        state.openCapital = ZERO;
        state.openInterest = ZERO;
        state.referenceDate = null;
    }

    /**
     * État mutable limité à l'exécution d'un calcul.
     *
     * Cette classe n'est jamais partagée entre plusieurs requêtes.
     */
    private static final class CalculationState {

        private BigDecimal balance = FinancialAmountUtils.zero();

        private BigDecimal openCapital = FinancialAmountUtils.zero();

        private BigDecimal openInterest = FinancialAmountUtils.zero();

        private LocalDate referenceDate;

        private long completedCycles;
        private int sequence;

        private final List<InterestCalculationLineResponse> lines = new ArrayList<>();

        private int nextSequence() {
            sequence++;
            return sequence;
        }
    }
}