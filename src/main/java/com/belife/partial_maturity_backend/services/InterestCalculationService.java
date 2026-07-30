package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.responses.InterestSimulationResponse;

/**
 * Orchestre la simulation d'une police.
 */
public interface InterestCalculationService {

    /**
     * Calcule la situation de la police à la date métier.
     *
     * @param policyNumber numéro de police
     * @return simulation détaillée
     */
    InterestSimulationResponse simulate(String policyNumber);
}