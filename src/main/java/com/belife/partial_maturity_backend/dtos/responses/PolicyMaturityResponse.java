package com.belife.partial_maturity_backend.dtos.responses;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Représente une maturité importée et présentée à l'administrateur.
 */
public record PolicyMaturityResponse(
        Long id,
        String policyNumber,
        String maturityType,
        int maturityRank,
        LocalDate maturityDate,
        BigDecimal maturityAmount,
        int sourceRowNumber,
        Instant createdAt,
        String createdBy
) {
}