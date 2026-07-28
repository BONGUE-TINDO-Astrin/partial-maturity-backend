package com.belife.partial_maturity_backend.exceptions;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;

/**
 * Indique qu'un fichier a été rejeté après validation.
 *
 * Le rapport complet est conservé afin d'être retourné
 * à l'interface d'administration.
 */
public class CsvImportRejectedException extends RuntimeException {

    private final CsvImportResponse response;

    public CsvImportRejectedException(CsvImportResponse response) {
        super("Le fichier CSV contient des erreurs bloquantes.");

        this.response = response;
    }

    public CsvImportResponse getResponse() {
        return response;
    }
}