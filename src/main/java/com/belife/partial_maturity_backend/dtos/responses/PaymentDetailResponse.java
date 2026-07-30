package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.CalculationEventType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ligne explicative conservée au moment d'un paiement.
 */
public record PaymentDetailResponse(
        Long id,
        int sequenceNumber,
        LocalDate eventDate,
        CalculationEventType eventType,
        String description,
        Long cycleNumber,
        BigDecimal balanceBefore,
        BigDecimal capitalAdded,
        BigDecimal annualRate,
        BigDecimal interestAmount,
        BigDecimal paidAmount,
        BigDecimal balanceAfter
) {
}
