package com.belife.partial_maturity_backend.dtos.responses.dashboard;

import java.math.BigDecimal;

/**
 * Répartition courante des intérêts générés.
 *
 * <p>Les intérêts générés correspondent à la somme :</p>
 *
 * <pre>
 * intérêts actuellement ouverts
 * + intérêts contenus dans les paiements PAID
 * </pre>
 *
 * @param openInterest intérêts actuellement ouverts
 * @param paidInterest intérêts déjà payés
 * @param generatedInterest total des intérêts générés
 * @param paidPercentage pourcentage des intérêts générés
 *                       déjà payés, compris entre 0 et 100
 */
public record InterestDistributionResponse(
        BigDecimal openInterest,
        BigDecimal paidInterest,
        BigDecimal generatedInterest,
        BigDecimal paidPercentage
) {
}