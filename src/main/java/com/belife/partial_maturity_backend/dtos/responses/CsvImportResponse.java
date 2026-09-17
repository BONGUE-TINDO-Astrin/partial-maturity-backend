package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;

import java.time.Instant;
import java.util.List;

/**
 * Résultat retourné après le traitement d'un CSV.
 */
public record CsvImportResponse(
        Long batchId,
        String fileName,
        ImportBatchStatus status,
        int totalRows,
        int insertedRows,
        int existingRows,
        int errorRows,
        Instant processedAt,
        List<CsvValidationError> errors
) {
//    public CsvImportResponse {
//        errors = List.copyOf(errors);
//    }
}