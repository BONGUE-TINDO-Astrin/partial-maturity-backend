package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;

import java.time.Instant;
import java.util.List;

/**
 * Détail complet d'un chargement CSV.
 *
 * <p>Pour un lot rejeté, {@code errors} contient
 * les erreurs structurées reconstituées depuis
 * {@code errorSummary}.</p>
 *
 * <p>Pour un lot annulé, les informations de réversion
 * expliquent quand, par qui et pour quelle raison
 * les maturités ont été retirées.</p>
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
        Instant reversedAt,
        String reversedBy,
        String reversalReason,
        boolean reversible,
        String reversalBlockedReason,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        List<CsvValidationError> errors
) {

    public ImportBatchDetailResponse {
        errors = List.copyOf(errors);
    }
}