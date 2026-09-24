package com.belife.partial_maturity_backend.services.models;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Maturité enrichie avec son rang et son type.
 */
public record MaturityImportRow(
        int rowNumber,
        String policyNumber,
        String clientName,
        String maturityType,
        int maturityRank,
        LocalDate maturityDate,
        BigDecimal maturityAmount,
        LocalDate interestEndDate
) {

    public static MaturityImportRow from(
            ParsedMaturityRow source,
            int maturityRank
    ) {
        if (source == null) {
            throw new IllegalArgumentException(
                    "La ligne CSV est obligatoire."
            );
        }

        if (maturityRank <= 0) {
            throw new IllegalArgumentException(
                    "Le rang de maturité doit être positif."
            );
        }

        return new MaturityImportRow(
                source.rowNumber(),
                source.policyNumber(),
                source.clientName(),
                "MATURITE_" + maturityRank,
                maturityRank,
                source.maturityDate(),
                source.maturityAmount(),
                source.interestEndDate()
        );
    }
}