package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.responses.dashboard.DashboardResponse;

/**
 * Construit le tableau de bord commun
 * à l'ensemble des utilisateurs autorisés.
 */
public interface DashboardService {

    /**
     * Retourne les indicateurs financiers,
     * les statistiques et les opérations récentes.
     *
     * @return tableau de bord commun
     */
    DashboardResponse getDashboard();
}