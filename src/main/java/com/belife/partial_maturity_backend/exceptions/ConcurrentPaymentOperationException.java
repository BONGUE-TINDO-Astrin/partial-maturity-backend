package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique que la situation financière a été modifiée
 * simultanément par une autre opération.
 */
public class ConcurrentPaymentOperationException extends RuntimeException {

    public ConcurrentPaymentOperationException() {
        super(
                "La situation financière a été modifiée par une autre opération. Veuillez actualiser puis recommencer."
        );
    }
}