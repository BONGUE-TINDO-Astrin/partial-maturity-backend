package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique que le paiement demandé n'existe pas.
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long paymentId) {
        super(
            "Aucun paiement ne correspond à l'identifiant "
                + paymentId
                + "."
        );
    }
}