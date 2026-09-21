package com.belife.partial_maturity_backend.dtos.responses;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Présente la synthèse financière courante d'une police.
 */
public record PolicyFinancialSummaryResponse(
        String policyNumber,
        String clientName,
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