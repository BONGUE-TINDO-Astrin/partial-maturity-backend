package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.responses.PolicyDetailResponse;

/**
 * Fournit les opérations de consultation des polices.
 *
 * <p>Ce service ne réalise aucune modification de données.</p>
 */
public interface PolicyConsultationService {

    /**
     * Recherche une police par son numéro exact,
     * sans tenir compte de la casse.
     *
     * @param policyNumber numéro de police recherché
     * @return détail de la police et de ses maturités
     */
    PolicyDetailResponse getPolicyDetails(String policyNumber);
}