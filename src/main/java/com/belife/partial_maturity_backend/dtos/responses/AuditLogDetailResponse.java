package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;

import java.time.Instant;
import java.util.Map;

/**
 * Détail complet d'une entrée du journal d'audit.
 *
 * @param details informations structurées reconstituées
 *                depuis detail_json
 */
public record AuditLogDetailResponse(
        Long id,
        AuditEventType eventType,
        AuditResourceType resourceType,
        String resourceId,
        String policyNumber,
        String actorUsername,
        Instant occurredAt,
        String summary,
        Map<String, Object> details
) {

    public AuditLogDetailResponse {
        details = details == null
                ? Map.of()
                : Map.copyOf(details);
    }
}
