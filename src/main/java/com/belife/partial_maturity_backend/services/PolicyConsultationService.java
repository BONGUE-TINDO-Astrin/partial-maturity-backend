package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.responses.PolicyDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyFinancialDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyFinancialSummaryResponse;

import java.util.List;

/**
 * Fournit les opérations de consultation des polices.
 *
 * <p>Ce service ne réalise aucune modification
 * de données.</p>
 */
public interface PolicyConsultationService {

    /**
     * Retourne la synthèse financière de toutes
     * les polices enregistrées.
     *
     * @return polices classées par numéro
     */
    List<PolicyFinancialSummaryResponse> getPolicyFinancialSummaries();

    /**
     * Retourne les maturités d'une police recherchée
     * par son numéro exact.
     *
     * @param policyNumber numéro de police recherché
     * @return détail descriptif de la police
     */
    PolicyDetailResponse getPolicyDetails(String policyNumber);

    /**
     * Retourne la situation financière complète d'une police.
     *
     * <p>La réponse contient les maturités, la chronologie
     * du calcul et les intérêts déjà payés.</p>
     *
     * @param policyNumber numéro de police recherché
     * @return détail financier de la police
     */
    PolicyFinancialDetailResponse getPolicyFinancialDetails(String policyNumber);
}