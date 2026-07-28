package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;

import java.time.Instant;
import java.util.List;

/**
 * Détail complet d'un chargement CSV.
 *
 * <p>Pour un lot rejeté, errors contient les erreurs
 * structurées reconstituées depuis errorSummary.</p>
 */
public record ImportBatchDetailResponse(
        Long id,
        String originalFileName,
        String fileSha256,
        long fileSizeBytes,
        int totalRows,
        int insertedRows,
        int existingRows,
        int errorRows,
        ImportBatchStatus status,
        Instant importedAt,
        String importedBy,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        List<CsvValidationError> errors
) {
}