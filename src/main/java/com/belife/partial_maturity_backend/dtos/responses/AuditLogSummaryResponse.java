package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;

import java.time.Instant;

/**
 * Résumé d'une entrée du journal d'audit.
 *
 * <p>Le document JSON détaillé n'est pas inclus dans la liste
 * afin de conserver une réponse paginée légère.</p>
 */
public record AuditLogSummaryResponse(
        Long id,
        AuditEventType eventType,
        AuditResourceType resourceType,
        String resourceId,
        String policyNumber,
        String actorUsername,
        Instant occurredAt,
        String summary
) {
}
