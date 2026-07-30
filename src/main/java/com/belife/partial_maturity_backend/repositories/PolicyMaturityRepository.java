package com.belife.partial_maturity_backend.repositories;

import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Verrouille les maturités d'une police pendant
     * l'enregistrement d'un paiement.
     *
     * <p>Tous les traitements de paiement d'une même police
     * doivent acquérir ce verrou avant de recalculer.</p>
     *
     * <p>Le second traitement concurrent attendra la fin du premier,
     * puis recalculera la situation avec le paiement fraîchement
     * enregistré. Si la situation est déjà soldée, le second paiement
     * sera refusé.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select maturity
        from PolicyMaturityEntity maturity
        where upper(maturity.policyNumber)
              = upper(:policyNumber)
        order by maturity.maturityRank asc
        """)
    List<PolicyMaturityEntity> findAllByPolicyNumberForPaymentUpdate(@Param("policyNumber") String policyNumber);
}