package com.belife.partial_maturity_backend.services.models;

import java.util.List;

/**
 * Résultat des contrôles métier précédant l'importation.
 *
 * @param newRows nouvelles maturités à insérer
 * @param existingRowsCount nombre de lignes déjà connues ou
 *                          répétées à l'identique dans le fichier
 * @param errors erreurs métier bloquantes
 */
public record MaturityImportAnalysis(
        List<ParsedMaturityRow> newRows,
        int existingRowsCount,
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