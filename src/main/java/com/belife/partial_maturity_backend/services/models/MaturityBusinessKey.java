package com.belife.partial_maturity_backend.services.models;

import java.util.Locale;

/**
 * Clé métier d'une maturité.
 *
 * <p>Une maturité est identifiée par :</p>
 *
 * <pre>
 * numéro de police + rang de maturité
 * </pre>
 *
 * @param normalizedPolicyNumber numéro de police normalisé
 * @param maturityRank rang de la maturité
 */
public record MaturityBusinessKey(
        String normalizedPolicyNumber,
        int maturityRank
) {

    /**
     * Construit une clé insensible à la casse du numéro de police.
     */
    public static MaturityBusinessKey of(String policyNumber, int maturityRank) {
        return new MaturityBusinessKey(
            policyNumber
                .trim()
                .toUpperCase(Locale.ROOT),
            maturityRank
        );
    }
}