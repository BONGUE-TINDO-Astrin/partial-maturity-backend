package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique que la situation courante ne peut pas
 * donner lieu à un nouveau paiement.
 *
 * Exemples :
 * - solde nul ;
 * - solde négatif ;
 * - situation déjà payée ;
 * - paiement déjà annulé.
 */
public class PaymentNotAllowedException extends RuntimeException {

    public PaymentNotAllowedException(String message) {
        super(message);
    }
}