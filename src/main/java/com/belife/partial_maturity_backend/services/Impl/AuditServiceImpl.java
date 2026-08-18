package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.entities.AuditLogEntity;
import com.belife.partial_maturity_backend.exceptions.AuditRecordingException;
import com.belife.partial_maturity_backend.repositories.AuditLogRepository;
import com.belife.partial_maturity_backend.services.AuditService;
import com.belife.partial_maturity_backend.services.models.AuditRecordCommand;
import com.belife.partial_maturity_backend.utils.AuditDetailJsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Implémente l'enregistrement du journal d'audit.
 *
 * <p>Le service utilise la propagation transactionnelle
 * {@code REQUIRED}, qui est la propagation par défaut.</p>
 *
 * <p>Lorsqu'il est appelé depuis une opération transactionnelle,
 * l'audit rejoint donc cette transaction.</p>
 */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditDetailJsonCodec auditDetailJsonCodec;
    private final Clock clock;

    /**
     * Construit et enregistre une entrée d'audit.
     *
     * <p>L'appel à {@code saveAndFlush()} force l'exécution
     * des contraintes SQL avant le retour au service métier.</p>
     */
    @Override
    @Transactional
    public AuditLogEntity record(AuditRecordCommand command) {
        validate(command);

        AuditLogEntity auditLog = new AuditLogEntity();

        auditLog.setEventType(command.eventType());

        auditLog.setResourceType(command.resourceType());

        auditLog.setResourceId(command.resourceId().trim());

        auditLog.setPolicyNumber(normalizeNullable(command.policyNumber()));

        auditLog.setActorUsername(command.actorUsername().trim());

        auditLog.setOccurredAt(Instant.now(clock));

        auditLog.setSummary(command.summary().trim());

        auditLog.setDetailJson(auditDetailJsonCodec.write(command.details()));

        try {
            return auditLogRepository.saveAndFlush(auditLog);

        } catch (RuntimeException exception) {
            throw new AuditRecordingException(
                "Impossible d'enregistrer l'événement dans le journal d'audit.",
                exception
            );
        }
    }

    /**
     * Vérifie qu'une commande contient toutes
     * les informations obligatoires.
     */
    private void validate(AuditRecordCommand command) {
        if (command == null) {
            throw new AuditRecordingException("La commande d'audit est obligatoire.");
        }

        if (command.eventType() == null) {
            throw new AuditRecordingException("Le type d'événement d'audit est obligatoire.");
        }

        if (command.resourceType() == null) {
            throw new AuditRecordingException("Le type de ressource d'audit est obligatoire.");
        }

        if (command.resourceId() == null || command.resourceId().isBlank()) {
            throw new AuditRecordingException("L'identifiant de la ressource auditée est obligatoire.");
        }

        if (command.actorUsername() == null || command.actorUsername().isBlank()) {
            throw new AuditRecordingException("L'utilisateur à l'origine de l'événement est obligatoire.");
        }

        if (command.summary() == null || command.summary().isBlank()) {
            throw new AuditRecordingException(
                    "Le résumé de l'événement d'audit est obligatoire."
            );
        }

        String resourceId = command.resourceId().trim();

        String actorUsername = command.actorUsername().trim();

        String summary = command.summary().trim();

        if (resourceId.length() > 100) {
            throw new AuditRecordingException(
                    "L'identifiant de la ressource auditée ne doit pas dépasser 100 caractères."
            );
        }

        if (actorUsername.length() > 100) {
            throw new AuditRecordingException(
                "Le nom de l'acteur ne doit pas dépasser 100 caractères."
            );
        }

        if (summary.length() > 500) {
            throw new AuditRecordingException(
                "Le résumé de l'événement ne doit pas dépasser 500 caractères."
            );
        }

        if (
            command.policyNumber() != null
                && command.policyNumber()
                .trim()
                .length() > 100
        ) {
            throw new AuditRecordingException(
                    "Le numéro de police audité ne doit pas dépasser 100 caractères."
            );
        }
    }

    /**
     * Nettoie une valeur facultative.
     *
     * @return null lorsque la valeur est absente ou vide
     */
    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
