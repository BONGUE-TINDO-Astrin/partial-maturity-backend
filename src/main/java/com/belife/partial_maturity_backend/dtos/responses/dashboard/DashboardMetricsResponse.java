package com.belife.partial_maturity_backend.dtos.responses.dashboard;

import java.math.BigDecimal;

/**
 * Indicateurs essentiels du tableau de bord commun.
 *
 * @param totalPolicies nombre de polices ayant
 *                      au moins une maturité active
 * @param totalMaturities nombre total de maturités actives
 * @param totalMaturityAmount montant nominal cumulé
 *                            des maturités actives
 * @param totalGeneratedInterest somme des intérêts ouverts
 *                               et déjà payés
 * @param totalPaidInterest intérêts contenus dans
 *                          les paiements PAID
 * @param totalPaidAmount montant total des paiements PAID
 */
public record DashboardMetricsResponse(
        long totalPolicies,
        long totalMaturities,
        BigDecimal totalMaturityAmount,
        BigDecimal totalGeneratedInterest,
        BigDecimal totalPaidInterest,
        BigDecimal totalPaidAmount
) {
}