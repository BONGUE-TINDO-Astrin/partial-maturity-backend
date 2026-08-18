package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.requests.ReverseImportBatchRequest;
import com.belife.partial_maturity_backend.dtos.responses.ImportBatchDetailResponse;

/**
 * Gère la réversion contrôlée des chargements CSV.
 */
public interface ImportBatchReversalService {

    /**
     * Retire les maturités introduites par un chargement
     * lorsque cette opération ne compromet aucune situation
     * financière.
     *
     * @param batchId identifiant du chargement
     * @param request motif obligatoire de réversion
     * @param currentUsername administrateur exécutant l'action
     * @return détail actualisé du chargement
     */
    ImportBatchDetailResponse reverseImportBatch(
            Long batchId,
            ReverseImportBatchRequest request,
            String currentUsername
    );
}