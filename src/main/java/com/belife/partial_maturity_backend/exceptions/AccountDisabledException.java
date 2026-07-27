package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique qu'un compte existe mais qu'il a été désactivé.
 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("Ce compte utilisateur est désactivé.");
    }
}
