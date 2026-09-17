package com.belife.partial_maturity_backend.dtos.responses.dashboard;

import java.math.BigDecimal;

/**
 * Agrégation des paiements valides d'un mois.
 */
public record MonthlyPaymentStatisticResponse(
        String month,
        long paymentCount,
        BigDecimal paidAmount,
        BigDecimal interestAmount
) {
}