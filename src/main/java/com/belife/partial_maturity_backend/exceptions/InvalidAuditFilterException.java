package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique que les filtres de consultation du journal
 * sont incohérents.
 */
public class InvalidAuditFilterException extends RuntimeException {

    public InvalidAuditFilterException(String message) {
        super(message);
    }
}