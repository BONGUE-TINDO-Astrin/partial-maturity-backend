package com.belife.partial_maturity_backend.controllers;

import com.belife.partial_maturity_backend.dtos.responses.AuditLogDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.AuditLogSummaryResponse;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;
import com.belife.partial_maturity_backend.services.AuditConsultationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Expose la consultation du journal d'audit.
 *
 * <p>Toutes les opérations sont réservées à ADMIN.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditConsultationService auditConsultationService;

    /**
     * Retourne le journal paginé avec des filtres
     * optionnels et combinables.
     *
     * <p>Les dates utilisent le format ISO-8601 avec fuseau,
     * par exemple {@code 2026-07-01T00:00:00Z}.</p>
     */
    @GetMapping
    public ResponseEntity<PageResponse<AuditLogSummaryResponse>> search(
            @RequestParam(required = false)
            AuditEventType eventType,

            @RequestParam(required = false)
            AuditResourceType resourceType,

            @RequestParam(required = false)
            String actor,

            @RequestParam(required = false)
            String policyNumber,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {
        return ResponseEntity.ok(
            auditConsultationService.search(
                eventType,
                resourceType,
                actor,
                policyNumber,
                from,
                to,
                page,
                size
            )
        );
    }

    /**
     * Retourne le détail d'une entrée précise.
     */
    @GetMapping("/{auditId}")
    public ResponseEntity<AuditLogDetailResponse>
    getById(@PathVariable Long auditId) {
        return ResponseEntity.ok(auditConsultationService.getById(auditId));
    }
}