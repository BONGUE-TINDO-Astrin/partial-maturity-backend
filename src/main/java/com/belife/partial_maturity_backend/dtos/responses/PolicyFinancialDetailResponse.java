package com.belife.partial_maturity_backend.dtos.responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Regroupe les informations nécessaires à la consultation
 * financière détaillée d'une police.
 */
public record PolicyFinancialDetailResponse(
        String policyNumber,
        String clientName,
        int maturityCount,
        BigDecimal totalMaturityAmount,
        LocalDate firstMaturityDate,
        LocalDate lastMaturityDate,
        LocalDate interestEndDate,
        boolean interestAccrualClosed,
        BigDecimal paidInterestAmount,
        BigDecimal totalPaidAmount,
        BigDecimal totalGeneratedInterestAmount,
        List<PolicyMaturityResponse> maturities,
        List<PolicyPaymentHistoryResponse> payments,
        InterestSimulationResponse simulation
) {

    public PolicyFinancialDetailResponse {
        maturities = List.copyOf(maturities);
        payments = List.copyOf(payments);
    }
}