package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.responses.AuditLogDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.AuditLogSummaryResponse;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;

import java.time.Instant;

/**
 * Fournit les opérations de consultation du journal d'audit.
 */
public interface AuditConsultationService {

    /**
     * Recherche les entrées du journal avec des filtres
     * optionnels et combinables.
     */
    PageResponse<AuditLogSummaryResponse> search(
            AuditEventType eventType,
            AuditResourceType resourceType,
            String actor,
            String policyNumber,
            Instant from,
            Instant to,
            int page,
            int size
    );

    /**
     * Retourne le détail d'une entrée.
     */
    AuditLogDetailResponse getById(Long auditId);
}
