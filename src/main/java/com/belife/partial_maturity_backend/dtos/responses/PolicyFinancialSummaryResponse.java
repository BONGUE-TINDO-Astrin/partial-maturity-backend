package com.belife.partial_maturity_backend.dtos.responses;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Présente la synthèse financière courante d'une police.
 *
 * <p>Les montants ouverts proviennent du moteur financier.
 * Les intérêts payés correspondent aux paiements PAID
 * encore valides.</p>
 *
 * @param policyNumber numéro de police tel qu'enregistré
 * @param maturityCount nombre total de maturités
 * @param totalMaturityAmount montant cumulé des maturités
 * @param firstMaturityDate date de la première maturité
 * @param lastMaturityDate date de la dernière maturité
 * @param interestEndDate date de fin de production des intérêts
 * @param interestAccrualClosed indique si la production
 *                              des intérêts est clôturée
 * @param annualRate taux annuel utilisé
 * @param completedCycles nombre total de cycles annuels appliqués
 * @param openCapital capital actuellement ouvert
 * @param openInterestAmount intérêts actuellement ouverts
 * @param paidInterestAmount intérêts contenus dans les paiements
 *                           PAID encore valides
 * @param totalGeneratedInterestAmount somme des intérêts ouverts
 *                                     et des intérêts déjà payés
 * @param balance solde actuellement payable
 * @param calculationDate date métier de la synthèse
 */
public record PolicyFinancialSummaryResponse(
        String policyNumber,
        int maturityCount,
        BigDecimal totalMaturityAmount,
        LocalDate firstMaturityDate,
        LocalDate lastMaturityDate,
        LocalDate interestEndDate,
        boolean interestAccrualClosed,
        BigDecimal annualRate,
        long completedCycles,
        BigDecimal openCapital,
        BigDecimal openInterestAmount,
        BigDecimal paidInterestAmount,
        BigDecimal totalGeneratedInterestAmount,
        BigDecimal balance,
        LocalDate calculationDate
) {
}