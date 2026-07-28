package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique que le fichier CSV ne peut pas être lu.
 *
 * Cette exception est non contrôlée afin qu'une opération
 * transactionnelle éventuelle soit automatiquement annulée.
 */
public class CsvFileProcessingException extends RuntimeException {

    public CsvFileProcessingException(String message) {
        super(message);
    }

    public CsvFileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
