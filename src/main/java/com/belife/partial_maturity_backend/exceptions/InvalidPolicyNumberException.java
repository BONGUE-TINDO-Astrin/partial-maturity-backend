package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique que le numéro de police fourni ne respecte pas
 * les contraintes minimales de consultation.
 */
public class InvalidPolicyNumberException extends RuntimeException {

    public InvalidPolicyNumberException(String message) {
        super(message);
    }
}