package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique que l'identifiant de connexion est déjà utilisé.
 */
public class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException(String username) {
        super(
            "Le nom d'utilisateur '"
                + username
                + "' est déjà utilisé."
        );
    }
}