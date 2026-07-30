package com.belife.partial_maturity_backend.dtos.responses;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Résultat complet de la simulation d'une police.
 *
 * @param policyNumber numéro de police
 * @param calculationDate date métier du calcul
 * @param annualRate taux annuel utilisé
 * @param completedCycles nombre total de cycles appliqués
 * @param openCapital capital ouvert depuis le dernier paiement
 * @param openInterest intérêts ouverts depuis le dernier paiement
 * @param balance solde total à la date de calcul
 * @param lines détail chronologique du calcul
 */
public record InterestSimulationResponse(
        String policyNumber,
        LocalDate calculationDate,
        BigDecimal annualRate,
        long completedCycles,
        BigDecimal openCapital,
        BigDecimal openInterest,
        BigDecimal balance,
        List<InterestCalculationLineResponse> lines
) {

    public InterestSimulationResponse {
        lines = List.copyOf(lines);
    }
}