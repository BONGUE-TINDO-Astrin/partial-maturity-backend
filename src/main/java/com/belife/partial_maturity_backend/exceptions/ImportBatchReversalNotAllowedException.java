package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique qu'un chargement ne peut pas être annulé
 * dans l'état actuel des données.
 */
public class ImportBatchReversalNotAllowedException
        extends RuntimeException {

    public ImportBatchReversalNotAllowedException(
            String message
    ) {
        super(message);
    }
}