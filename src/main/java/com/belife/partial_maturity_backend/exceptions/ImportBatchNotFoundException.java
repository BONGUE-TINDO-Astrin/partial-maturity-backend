package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique que le chargement CSV demandé n'existe pas.
 */
public class ImportBatchNotFoundException extends RuntimeException {

    public ImportBatchNotFoundException(Long batchId) {
        super("Aucun chargement CSV ne correspond à l'identifiant " + batchId + ".");
    }
}