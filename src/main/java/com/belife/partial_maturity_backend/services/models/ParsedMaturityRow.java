package com.belife.partial_maturity_backend.services.models;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ligne CSV convertie en types métier.
 *
 * @param rowNumber numéro physique de la ligne dans le fichier
 * @param policyNumber numéro de police normalisé
 * @param maturityType type de maturité normalisé
 * @param maturityRank rang numérique extrait du type
 * @param maturityDate date de maturité
 * @param maturityAmount montant de maturité
 * @param interestEndDate date de fin de production des intérêts
 */
public record ParsedMaturityRow(
        int rowNumber,
        String policyNumber,
        String maturityType,
        int maturityRank,
        LocalDate maturityDate,
        BigDecimal maturityAmount,
        LocalDate interestEndDate
) {
}