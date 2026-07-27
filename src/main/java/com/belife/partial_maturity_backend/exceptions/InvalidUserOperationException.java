package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique qu'une opération mettrait les comptes
 * administratifs dans un état interdit.
 *
 * Exemples :
 * - désactivation de son propre compte ;
 * - suppression du rôle du dernier ADMIN actif.
 */
public class InvalidUserOperationException
        extends RuntimeException {

    public InvalidUserOperationException(String message) {
        super(message);
    }
}