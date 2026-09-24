package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique qu'un chargement ou les polices concernées
 * sont simultanément modifiés par une autre opération.
 */
public class ConcurrentImportBatchOperationException extends RuntimeException {

    public ConcurrentImportBatchOperationException() {
        super(
                "Le chargement ou les polices concernées "
                        + "ont été modifiés par une autre opération. "
                        + "Veuillez actualiser puis recommencer."
        );
    }
}