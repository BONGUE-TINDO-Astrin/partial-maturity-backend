package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique qu'aucune maturité n'est enregistrée
 * pour le numéro de police demandé.
 */
public class PolicyNotFoundException extends RuntimeException {

    public PolicyNotFoundException(String policyNumber) {
        super("Aucune maturité n'est disponible pour la police '" + policyNumber + "'.");
    }
}