package com.belife.partial_maturity_backend.repositories;

import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux maturités enregistrées.
 */
public interface PolicyMaturityRepository extends JpaRepository<PolicyMaturityEntity, Long> {

    Optional<PolicyMaturityEntity> findByPolicyNumberIgnoreCaseAndMaturityRank(
            String policyNumber,
            int maturityRank
    );

    List<PolicyMaturityEntity> findAllByPolicyNumberIgnoreCaseOrderByMaturityRankAsc(String policyNumber);

    /**
     * Charge en une seule requête les maturités
     * des polices présentes dans un import.
     */
    List<PolicyMaturityEntity> findAllByPolicyNumberIn(Collection<String> policyNumbers);

    /**
     * Retourne toutes les maturités nécessaires
     * à la synthèse financière des polices.
     *
     * <p>Le classement rend le regroupement en mémoire
     * prévisible et conserve l'ordre métier des rangs.</p>
     */
    List<PolicyMaturityEntity> findAllByOrderByPolicyNumberAscMaturityRankAsc();

    List<PolicyMaturityEntity> findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(Long importBatchId);

    /**
     * Verrouille les maturités d'une police pendant
     * l'enregistrement d'un paiement.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select maturity
            from PolicyMaturityEntity maturity
            where upper(maturity.policyNumber)
                = upper(:policyNumber)
            order by maturity.maturityRank asc
            """)
    List<PolicyMaturityEntity>
    findAllByPolicyNumberForPaymentUpdate(
            @Param("policyNumber")
            String policyNumber
    );

    @Query("""
            select count(
                distinct maturity.policyNumber
            )
            from PolicyMaturityEntity maturity
            """)
    long countDistinctPolicyNumbers();

    @Query("""
            select coalesce(
                sum(maturity.maturityAmount),
                0
            )
            from PolicyMaturityEntity maturity
            """)
    BigDecimal sumAllMaturityAmounts();

    @Query("""
            select min(maturity.maturityDate)
            from PolicyMaturityEntity maturity
            """)
    LocalDate findMinimumMaturityDate();

    @Query("""
            select max(maturity.maturityDate)
            from PolicyMaturityEntity maturity
            """)
    LocalDate findMaximumMaturityDate();
}