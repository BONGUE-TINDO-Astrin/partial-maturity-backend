package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.entities.AuditLogEntity;
import com.belife.partial_maturity_backend.services.models.AuditRecordCommand;

/**
 * Enregistre les opérations métier sensibles
 * dans le journal d'audit.
 *
 * <p>Par défaut, l'enregistrement participe à la transaction
 * du service appelant.</p>
 *
 * <p>Une opération métier critique et sa trace doivent
 * être confirmées ou annulées ensemble.</p>
 */
public interface AuditService {

    /**
     * Enregistre une nouvelle entrée d'audit.
     *
     * @param command informations de l'événement
     * @return entrée persistée
     */
    AuditLogEntity record(AuditRecordCommand command);
}
