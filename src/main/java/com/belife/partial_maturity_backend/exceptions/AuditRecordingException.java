package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique qu'une entrée du journal d'audit
 * ne peut pas être construite ou enregistrée.
 *
 * <p>Cette exception est non contrôlée afin qu'une opération
 * métier et sa trace d'audit soient annulées ensemble
 * lorsqu'elles partagent la même transaction.</p>
 */
public class AuditRecordingException extends RuntimeException {

    public AuditRecordingException(String message) {
        super(message);
    }

    public AuditRecordingException(String message, Throwable cause) {
        super(message, cause);
    }
}