package com.belife.partial_maturity_backend.dtos.responses.dashboard;

import java.math.BigDecimal;

/**
 * Agrégation des paiements valides d'un mois.
 *
 * @param month mois au format yyyy-MM
 * @param paymentCount nombre de paiements PAID
 * @param paidAmount montant total payé
 * @param interestAmount intérêts inclus dans les paiements
 */
public record MonthlyPaymentStatisticResponse(
        String month,
        long paymentCount,
        BigDecimal paidAmount,
        BigDecimal interestAmount
) {
}