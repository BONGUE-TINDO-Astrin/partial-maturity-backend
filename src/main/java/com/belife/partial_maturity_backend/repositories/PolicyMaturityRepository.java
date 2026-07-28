package com.belife.partial_maturity_backend.repositories;

import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux maturités enregistrées.
 *
 * <p>Les méthodes de ce repository sont utilisées pour :</p>
 *
 * <ul>
 *     <li>consulter l'historique d'une police ;</li>
 *     <li>détecter les maturités déjà connues ;</li>
 *     <li>contrôler la continuité des rangs ;</li>
 *     <li>détecter les contradictions entre plusieurs fichiers.</li>
 * </ul>
 */
public interface PolicyMaturityRepository extends JpaRepository<PolicyMaturityEntity, Long> {

    Optional<PolicyMaturityEntity>
    findByPolicyNumberIgnoreCaseAndMaturityRank(String policyNumber, int maturityRank);

    List<PolicyMaturityEntity>
    findAllByPolicyNumberIgnoreCaseOrderByMaturityRankAsc(String policyNumber);

    /**
     * Charge en une seule requête les maturités des polices
     * présentes dans le fichier à importer.
     */
    List<PolicyMaturityEntity> findAllByPolicyNumberIn(Collection<String> policyNumbers);

    /**
     * Retourne les maturités enregistrées par un lot donné.
     *
     * @param importBatchId identifiant du chargement
     * @return maturités classées par police puis par rang
     */
    List<PolicyMaturityEntity>
    findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(Long importBatchId);
}