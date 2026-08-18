package com.belife.partial_maturity_backend.dtos.responses.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * Réponse commune du tableau de bord.
 *
 * <p>ADMIN et COMPTABILITE reçoivent exactement
 * la même vue synthétique.</p>
 *
 * @param calculationDate date métier utilisée
 *                        pour les calculs financiers
 * @param metrics indicateurs essentiels
 * @param interestDistribution répartition des intérêts
 * @param monthlyPayments paiements PAID des douze derniers mois
 * @param recentImports cinq derniers chargements
 * @param recentPayments cinq derniers paiements PAID
 */
public record DashboardResponse(
        LocalDate calculationDate,
        DashboardMetricsResponse metrics,
        InterestDistributionResponse interestDistribution,
        List<MonthlyPaymentStatisticResponse> monthlyPayments,
        List<RecentImportResponse> recentImports,
        List<RecentPaymentResponse> recentPayments
) {

    public DashboardResponse {
        monthlyPayments =
                List.copyOf(monthlyPayments);

        recentImports =
                List.copyOf(recentImports);

        recentPayments =
                List.copyOf(recentPayments);
    }
}