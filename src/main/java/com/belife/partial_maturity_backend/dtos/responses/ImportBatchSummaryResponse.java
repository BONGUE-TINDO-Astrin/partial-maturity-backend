package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.ImportBatchStatus;

import java.time.Instant;

/**
 * Résumé d'un chargement utilisé
 * dans la liste paginée.
 *
 * <p>Le détail des erreurs et le motif complet
 * de réversion ne sont pas inclus afin de garder
 * la réponse légère.</p>
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
        Instant reversedAt,
        String reversedBy,
        Instant createdAt,
        String createdBy
) {
}