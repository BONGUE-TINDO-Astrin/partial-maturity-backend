package com.belife.partial_maturity_backend.dtos.responses;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Représentation légère d'un paiement valide
 * dans l'historique financier d'une police.
 *
 * <p>Les lignes justificatives sont chargées uniquement
 * lorsque l'utilisateur sélectionne le paiement.</p>
 */
public record PolicyPaymentHistoryResponse(
        Long id,
        LocalDate paymentDate,
        BigDecimal capitalAmount,
        BigDecimal interestAmount,
        BigDecimal paidAmount,
        long completedCycles
) {
}