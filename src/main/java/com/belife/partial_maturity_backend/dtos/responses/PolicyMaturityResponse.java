package com.belife.partial_maturity_backend.dtos.responses;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Représente une maturité importée et présentée
 * dans les écrans de consultation.
 *
 * @param id identifiant technique
 * @param policyNumber numéro de police
 * @param clientName nom du client
 * @param maturityType type généré de la maturité
 * @param maturityRank rang attribué automatiquement
 * @param maturityDate date métier du chargement
 * @param maturityAmount montant de la maturité
 * @param interestEndDate date commune de fin des intérêts
 * @param sourceRowNumber ligne d'origine dans le fichier CSV
 * @param createdAt date technique de création
 * @param createdBy utilisateur ayant créé la maturité
 */
public record PolicyMaturityResponse(
        Long id,
        String policyNumber,
        String clientName,
        String maturityType,
        int maturityRank,
        LocalDate maturityDate,
        BigDecimal maturityAmount,
        LocalDate interestEndDate,
        int sourceRowNumber,
        Instant createdAt,
        String createdBy
) {
}