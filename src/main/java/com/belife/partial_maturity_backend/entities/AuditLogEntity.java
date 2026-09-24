package com.belife.partial_maturity_backend.entities;

import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * Représente un événement métier sensible enregistré
 * dans le journal d'audit.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Immutable
@Table(
    name = "audit_log",
    schema = "partial_maturity",
    indexes = {
        @Index(name = "ix_audit_log_occurred_at", columnList = "occurred_at DESC, id DESC"),
        @Index(name = "ix_audit_log_event_type", columnList = "event_type, occurred_at DESC, id DESC"),
        @Index(name = "ix_audit_log_actor", columnList = "actor_username, occurred_at DESC, id DESC"),
        @Index(name = "ix_audit_log_resource", columnList = "resource_type, resource_id, occurred_at DESC, id DESC")
    }
)
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nature de l'action métier.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuditEventType eventType;

    /**
     * Type de ressource concernée.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 40)
    private AuditResourceType resourceType;

    /**
     * Identifiant technique de la ressource sous forme
     * textuelle.
     *
     * <p>Ce choix permet de supporter un identifiant numérique
     * ou une future référence alphanumérique.</p>
     */
    @Column(name = "resource_id", nullable = false, length = 100)
    private String resourceId;

    /**
     * Numéro de police concerné par l'événement,
     * lorsqu'il existe.
     */
    @Nationalized
    @Column(name = "policy_number", length = 100)
    private String policyNumber;

    /**
     * Utilisateur authentifié ayant exécuté l'action.
     */
    @Nationalized
    @Column(name = "actor_username", nullable = false, length = 100)
    private String actorUsername;

    /**
     * Instant précis auquel l'action métier
     * s'est produite.
     */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * Description courte destinée à la consultation
     * administrative.
     */
    @Nationalized
    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    /**
     * Informations complémentaires structurées
     * au format JSON.
     */
    @Nationalized
    @Column(name = "detail_json", columnDefinition = "NVARCHAR(MAX)")
    private String detailJson;
}
