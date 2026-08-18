package com.belife.partial_maturity_backend.dtos.responses;

import java.time.Instant;

/**
 * Réponse principale du tableau de bord.
 *
 * <p>Les sections disponibles dépendent du rôle connecté :</p>
 *
 * <ul>
 *     <li>ADMIN reçoit administration, imports et audit ;</li>
 *     <li>COMPTABILITE reçoit portefeuille et paiements.</li>
 * </ul>
 *
 * @param generatedAt instant de génération du tableau de bord
 * @param role rôle utilisé pour construire la réponse
 * @param administration statistiques administratives éventuelles
 * @param imports statistiques des chargements éventuelles
 * @param audit statistiques du journal éventuelles
 * @param portfolio statistiques du portefeuille éventuelles
 * @param payments statistiques des paiements éventuelles
 */
public record DashboardResponse(
        Instant generatedAt,
        String role,
        AdministrationDashboardResponse administration,
        ImportDashboardResponse imports,
        AuditDashboardResponse audit,
        PortfolioDashboardResponse portfolio,
        PaymentDashboardResponse payments
) {
}
