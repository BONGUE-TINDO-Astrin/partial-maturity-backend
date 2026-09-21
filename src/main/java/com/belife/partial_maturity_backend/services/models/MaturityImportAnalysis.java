package com.belife.partial_maturity_backend.services.models;

import java.util.List;

/**
 * Résultat des contrôles métier précédant l'importation.
 */
public record MaturityImportAnalysis(
        List<MaturityImportRow> newRows,
        List<CsvValidationError> errors
) {

    public MaturityImportAnalysis {
        newRows = List.copyOf(newRows);
        errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}