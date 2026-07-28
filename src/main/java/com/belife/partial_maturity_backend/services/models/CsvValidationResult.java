package com.belife.partial_maturity_backend.services.models;

import java.util.List;

/**
 * Résultat complet du parsing d'un fichier CSV.
 *
 * @param totalRows nombre total de lignes de données analysées,
 *                  hors en-tête
 * @param rows lignes valides et converties
 * @param errors erreurs détectées
 */
public record CsvValidationResult(
        int totalRows,
        List<ParsedMaturityRow> rows,
        List<CsvValidationError> errors
) {

    public CsvValidationResult {
        rows = List.copyOf(rows);
        errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}