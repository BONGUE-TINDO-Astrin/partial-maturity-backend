package com.belife.partial_maturity_backend.services.models;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Maturité enrichie par le service d'import
 * et prête à être persistée.
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
            int maturityRank,
            LocalDate maturityDate
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

        if (maturityDate == null) {
            throw new IllegalArgumentException(
                    "La date de maturité est obligatoire."
            );
        }

        return new MaturityImportRow(
                source.rowNumber(),
                source.policyNumber(),
                source.clientName(),
                "MATURITE_" + maturityRank,
                maturityRank,
                maturityDate,
                source.maturityAmount(),
                source.interestEndDate()
        );
    }
}