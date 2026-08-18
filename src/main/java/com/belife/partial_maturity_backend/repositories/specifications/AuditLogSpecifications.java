package com.belife.partial_maturity_backend.repositories.specifications;

import com.belife.partial_maturity_backend.entities.AuditLogEntity;
import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;

/**
 * Construit les filtres combinables du journal d'audit.
 *
 * <p>Chaque méthode retourne une spécification neutre lorsque
 * son paramètre est absent. Le service peut donc composer les
 * filtres sans multiplier les conditions.</p>
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
        /*
         * Classe utilitaire non instanciable.
         */
    }

    public static Specification<AuditLogEntity>
    hasEventType(AuditEventType eventType) {
        return (root, query, builder) -> {
            if (eventType == null) {
                return builder.conjunction();
            }

            return builder.equal(root.get("eventType"), eventType);
        };
    }

    public static Specification<AuditLogEntity>
    hasResourceType(
            AuditResourceType resourceType
    ) {
        return (root, query, builder) -> {
            if (resourceType == null) {
                return builder.conjunction();
            }

            return builder.equal(root.get("resourceType"), resourceType);
        };
    }

    public static Specification<AuditLogEntity>
    hasActor(String actorUsername) {
        return (root, query, builder) -> {
            if (actorUsername == null || actorUsername.isBlank()
            ) {
                return builder.conjunction();
            }

            String normalizedActor = actorUsername.trim().toLowerCase(Locale.ROOT);

            return builder.equal(
                builder.lower(root.get("actorUsername")),
                normalizedActor
            );
        };
    }

    public static Specification<AuditLogEntity>
    hasPolicyNumber(String policyNumber) {
        return (root, query, builder) -> {
            if (policyNumber == null || policyNumber.isBlank()) {
                return builder.conjunction();
            }

            String normalizedPolicyNumber = policyNumber.trim().toLowerCase(Locale.ROOT);

            return builder.equal(builder.lower(root.get("policyNumber")), normalizedPolicyNumber);
        };
    }

    public static Specification<AuditLogEntity>
    occurredAtOrAfter(Instant from) {
        return (root, query, builder) -> {
            if (from == null) {
                return builder.conjunction();
            }

            return builder.greaterThanOrEqualTo(root.get("occurredAt"), from);
        };
    }

    public static Specification<AuditLogEntity>
    occurredAtOrBefore(Instant to) {
        return (root, query, builder) -> {
            if (to == null) {
                return builder.conjunction();
            }

            return builder.lessThanOrEqualTo(root.get("occurredAt"), to);
        };
    }
}
