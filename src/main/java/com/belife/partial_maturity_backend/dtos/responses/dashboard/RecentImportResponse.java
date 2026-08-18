package com.belife.partial_maturity_backend.dtos.responses.dashboard;

import com.belife.partial_maturity_backend.enums.ImportBatchStatus;

import java.time.Instant;

/**
 * Chargement récent affiché dans le tableau de bord.
 *
 * @param id identifiant du lot
 * @param fileName nom du fichier source
 * @param status statut actuel
 * @param insertedRows nombre de maturités initialement insérées
 * @param occurredAt instant représentatif de la dernière opération
 * @param actor utilisateur ayant réalisé cette opération
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