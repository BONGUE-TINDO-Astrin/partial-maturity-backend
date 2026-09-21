package com.belife.partial_maturity_backend.dtos.responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Présente une police et ses maturités.
 */
public record PolicyDetailResponse(
        String policyNumber,
        String clientName,
        int maturityCount,
        BigDecimal totalMaturityAmount,
        LocalDate firstMaturityDate,
        LocalDate lastMaturityDate,
        List<PolicyMaturityResponse> maturities
) {

    public PolicyDetailResponse {
        maturities = List.copyOf(maturities);
    }
}