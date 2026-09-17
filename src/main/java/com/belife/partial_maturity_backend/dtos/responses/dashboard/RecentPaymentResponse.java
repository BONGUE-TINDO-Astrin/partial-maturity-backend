package com.belife.partial_maturity_backend.dtos.responses.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Paiement PAID récent affiché dans le tableau de bord.
 */
public record RecentPaymentResponse(
        Long id,
        String policyNumber,
        LocalDate paymentDate,
        BigDecimal capitalAmount,
        BigDecimal interestAmount,
        BigDecimal paidAmount,
        String createdBy
) {
}