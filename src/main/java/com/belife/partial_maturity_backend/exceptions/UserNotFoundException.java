package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique que le compte demandé n'existe pas.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long userId) {
        super(
            "Aucun utilisateur ne correspond à l'identifiant "
                + userId
                + "."
        );
    }
}
