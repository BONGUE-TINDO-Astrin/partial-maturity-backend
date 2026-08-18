package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.services.models.ImportBatchReversalEligibility;

/**
 * Évalue si un chargement peut actuellement
 * être annulé.
 *
 * <p>Cette analyse améliore l'expérience utilisateur.
 * La réversion transactionnelle refait toujours les
 * contrôles au moment de l'action.</p>
 */
public interface ImportBatchReversalEligibilityService {

    /**
     * Évalue l'éligibilité d'un chargement.
     *
     * @param batch lot à analyser
     * @return résultat de l'analyse
     */
    ImportBatchReversalEligibility evaluate(
            ImportBatchEntity batch
    );
}