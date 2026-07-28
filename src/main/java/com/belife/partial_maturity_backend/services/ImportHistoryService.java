package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.responses.ImportBatchDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.ImportBatchSummaryResponse;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyMaturityResponse;

import java.util.List;

/**
 * Fournit les opérations de consultation de l'historique CSV.
 */
public interface ImportHistoryService {

    /**
     * Retourne la liste paginée des chargements.
     */
    PageResponse<ImportBatchSummaryResponse> getImportHistory(int page, int size);

    /**
     * Retourne le détail d'un chargement.
     */
    ImportBatchDetailResponse getImportDetail(Long batchId);

    /**
     * Retourne les maturités enregistrées par un chargement.
     */
    List<PolicyMaturityResponse> getImportedMaturities(Long batchId);
}