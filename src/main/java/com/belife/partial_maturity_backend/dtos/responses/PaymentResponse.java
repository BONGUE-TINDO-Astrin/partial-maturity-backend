package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Représente un paiement total et sa justification.
 *
 * @param cancellable indique si le paiement peut actuellement
 *                    être annulé
 * @param cancellationBlockedReason raison métier lorsque
 *                                  l'annulation est impossible
 */
public record PaymentResponse(
        Long id,
        String policyNumber,
        LocalDate paymentDate,
        LocalDate calculationDate,
        BigDecimal annualRate,
        BigDecimal capitalAmount,
        BigDecimal interestAmount,
        BigDecimal paidAmount,
        long completedCycles,
        PaymentStatus status,
        Instant cancelledAt,
        String cancelledBy,
        String cancellationReason,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        Long version,
        boolean cancellable,
        String cancellationBlockedReason,
        List<PaymentDetailResponse> details
) {

    public PaymentResponse {
        details = List.copyOf(details);
    }
}