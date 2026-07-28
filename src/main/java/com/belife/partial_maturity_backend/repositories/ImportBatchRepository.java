package com.belife.partial_maturity_backend.repositories;

import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}