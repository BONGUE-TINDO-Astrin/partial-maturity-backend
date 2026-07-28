package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;
import com.belife.partial_maturity_backend.services.models.ParsedMaturityRow;

import java.util.List;

/**
 * Réalise les écritures SQL liées aux chargements CSV.
 *
 * <p>Ce service est séparé de l'orchestrateur afin que Spring puisse
 * appliquer correctement les frontières transactionnelles.</p>
 */
public interface CsvImportPersistenceService {

    /**
     * Enregistre atomiquement un lot valide et ses maturités.
     */
    CsvImportResponse saveImportedBatch(
        String fileName,
        String fileSha256,
        long fileSize,
        int totalRows,
        int existingRows,
        List<ParsedMaturityRow> newRows,
        String currentUsername
    );

    /**
     * Conserve le rapport d'un fichier rejeté.
     *
     * <p>Cette opération utilise sa propre transaction afin que
     * le rejet reste historisé indépendamment des autres traitements.</p>
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