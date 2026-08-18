package com.belife.partial_maturity_backend.repositories;

import com.belife.partial_maturity_backend.entities.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;

/**
 * Fournit l'accès au journal d'audit.
 *
 * <p>JpaSpecificationExecutor permet de combiner dynamiquement
 * plusieurs filtres sans créer une méthode repository pour
 * chaque combinaison possible.</p>
 *
 * <p>Aucune méthode métier de suppression ou de modification
 * ne doit être utilisée sur le journal.</p>
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long>, JpaSpecificationExecutor<AuditLogEntity> {
    /**
     * Compte les événements produits dans une période.
     */
    long countByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            Instant fromInclusive,
            Instant toExclusive
    );

    /**
     * Retourne le dernier événement du journal.
     */
    Optional<AuditLogEntity> findFirstByOrderByOccurredAtDescIdDesc();
}