package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.ImportBatchStatus;

import java.time.Instant;

/**
 * Résumé d'un chargement utilisé dans la liste paginée.
 *
 * <p>Le détail des erreurs n'est volontairement pas inclus
 * afin de garder la réponse de la liste légère.</p>
 */
public record ImportBatchSummaryResponse(
        Long id,
        String originalFileName,
        long fileSizeBytes,
        int totalRows,
        int insertedRows,
        int existingRows,
        int errorRows,
        ImportBatchStatus status,
        Instant importedAt,
        String importedBy,
        Instant createdAt,
        String createdBy
) {
}
