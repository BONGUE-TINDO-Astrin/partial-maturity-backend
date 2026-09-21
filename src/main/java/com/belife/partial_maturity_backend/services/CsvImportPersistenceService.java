package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;
import com.belife.partial_maturity_backend.services.models.MaturityImportRow;

import java.util.List;

/**
 * Réalise les écritures liées aux chargements CSV.
 */
public interface CsvImportPersistenceService {

    /**
     * Enregistre atomiquement un lot valide
     * et ses nouvelles maturités.
     */
    CsvImportResponse saveImportedBatch(
            String fileName,
            String fileSha256,
            long fileSize,
            int totalRows,
            List<MaturityImportRow> newRows,
            String currentUsername
    );

    /**
     * Conserve le rapport d'un fichier rejeté.
     */
    CsvImportResponse saveRejectedBatch(
            String fileName,
            String fileSha256,
            long fileSize,
            int totalRows,
            List<CsvValidationError> errors,
            String currentUsername
    );
}