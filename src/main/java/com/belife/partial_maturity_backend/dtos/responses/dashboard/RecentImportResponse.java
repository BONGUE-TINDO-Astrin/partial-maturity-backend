package com.belife.partial_maturity_backend.dtos.responses.dashboard;

import com.belife.partial_maturity_backend.enums.ImportBatchStatus;

import java.time.Instant;

/**
 * Chargement récent affiché dans le tableau de bord.
 */
public record RecentImportResponse(
        Long id,
        String fileName,
        ImportBatchStatus status,
        int insertedRows,
        Instant occurredAt,
        String actor
) {
}