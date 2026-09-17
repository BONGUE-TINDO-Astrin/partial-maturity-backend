package com.belife.partial_maturity_backend.repositories;

import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux historiques des chargements CSV.
 */
public interface ImportBatchRepository extends JpaRepository<ImportBatchEntity, Long> {

    /**
     * Retourne les chargements du plus récent au plus ancien.
     *
     * @param pageable informations de pagination
     * @return page de chargements
     */
    Page<ImportBatchEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

//    /**
//     * Compte les chargements possédant un statut donné.
//     */
//    long countByStatus(ImportBatchStatus status);

//    /**
//     * Calcule le nombre total de lignes réellement insérées.
//     */
//    @Query("""
//        select coalesce(sum(batch.insertedRows), 0)
//        from ImportBatchEntity batch
//        where batch.status = :status
//        """)
//    long sumInsertedRowsByStatus(@Param("status") ImportBatchStatus status);
//
//    /**
//     * Retourne le dernier chargement créé.
//     */
//    Optional<ImportBatchEntity> findFirstByOrderByCreatedAtDescIdDesc();

    /**
     * Verrouille un chargement pendant sa réversion.
     *
     * <p>Le verrou empêche deux demandes concurrentes
     * d'annuler le même lot.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select batch
        from ImportBatchEntity batch
        where batch.id = :batchId
        """)
    Optional<ImportBatchEntity> findByIdForUpdate(@Param("batchId") Long batchId);

    /**
     * Retourne les chargements les plus récents.
     *
     * <p>Le Pageable permet au tableau de bord
     * de limiter la réponse aux cinq derniers lots.</p>
     */
    @Query("""
        select batch
        from ImportBatchEntity batch
        order by
          batch.createdAt desc,
          batch.id desc
        """)
    List<ImportBatchEntity> findRecentImports(Pageable pageable);
}