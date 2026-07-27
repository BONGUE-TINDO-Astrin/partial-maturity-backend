package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique que l'identifiant ou le mot de passe fourni est incorrect.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Identifiant ou mot de passe incorrect.");
    }
}
