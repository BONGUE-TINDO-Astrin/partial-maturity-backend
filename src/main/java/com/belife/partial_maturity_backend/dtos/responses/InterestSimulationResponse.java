package com.belife.partial_maturity_backend.dtos.responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Résultat complet de la simulation d'une police.
 *
 * @param policyNumber numéro de police
 * @param calculationDate date métier du calcul
 * @param interestEndDate date de fin de production des intérêts
 * @param interestAccrualClosed indique si la production
 *                              d'intérêts est clôturée à la date
 *                              du calcul
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
        LocalDate interestEndDate,
        boolean interestAccrualClosed,
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