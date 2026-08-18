package com.belife.partial_maturity_backend.exceptions;

/**
 * Indique que l'entrée d'audit demandée n'existe pas.
 */
public class AuditLogNotFoundException extends RuntimeException {

    public AuditLogNotFoundException(Long auditId) {
        super(
            "Aucune entrée du journal d'audit ne correspond à l'identifiant "
                + auditId
                + "."
        );
    }
}
