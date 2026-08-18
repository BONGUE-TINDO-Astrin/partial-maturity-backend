package com.belife.partial_maturity_backend.repositories;

import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Fournit les opérations d'accès aux paiements.
 */
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long>, JpaSpecificationExecutor<PaymentEntity> {

    /**
     * Charge uniquement les paiements actifs participant
     * à la simulation d'une police.
     */
    List<PaymentEntity> findAllByPolicyNumberIgnoreCaseAndStatusOrderByPaymentDateAscIdAsc(
            String policyNumber,
            PaymentStatus status
    );

    /**
     * Charge en une seule requête les paiements actifs
     * des polices demandées.
     */
    @Query("""
            select payment
            from PaymentEntity payment
            where payment.policyNumber in :policyNumbers
              and payment.status = :status
            order by
              payment.policyNumber asc,
              payment.paymentDate asc,
              payment.id asc
            """)
    List<PaymentEntity> findAllByPolicyNumbersAndStatus(
            @Param("policyNumbers")
            Collection<String> policyNumbers,

            @Param("status")
            PaymentStatus status
    );

    /**
     * Retourne l'historique complet d'une police,
     * paiements annulés compris.
     */
    List<PaymentEntity> findAllByPolicyNumberIgnoreCaseOrderByPaymentDateDescIdDesc(
            String policyNumber
    );

    /**
     * Charge un paiement avec ses lignes justificatives.
     *
     * <p>Cette méthode est destinée au détail et ne doit
     * pas être utilisée pour la liste paginée.</p>
     */
    @EntityGraph(attributePaths = "details")
    @Query("""
            select payment
            from PaymentEntity payment
            where payment.id = :paymentId
            """)
    Optional<PaymentEntity> findByIdWithDetails(@Param("paymentId") Long paymentId);

    /**
     * Verrouille un paiement avant son annulation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select payment
            from PaymentEntity payment
            where payment.id = :paymentId
            """)
    Optional<PaymentEntity> findByIdForUpdate(@Param("paymentId") Long paymentId);

    /**
     * Compte les paiements selon leur statut.
     */
    long countByStatus(PaymentStatus status);

    /**
     * Calcule le total réellement payé.
     * Les paiements annulés sont exclus.
     */
    @Query("""
            select coalesce(sum(payment.paidAmount), 0)
            from PaymentEntity payment
            where payment.status = :status
            """)
    BigDecimal sumPaidAmountByStatus( @Param("status") PaymentStatus status);

    /**
     * Retourne le dernier paiement créé.
     */
    Optional<PaymentEntity> findFirstByOrderByCreatedAtDescIdDesc();

    /**
     * Calcule le total des intérêts contenus
     * dans les paiements ayant le statut demandé.
     */
    @Query("""
        select coalesce(
            sum(payment.interestAmount),
            0
        )
        from PaymentEntity payment
        where payment.status = :status
        """)
    BigDecimal sumInterestAmountByStatus(@Param("status") PaymentStatus status);

    /**
     * Charge les paiements d'un statut donné sur une période
     * métier fermée à gauche et ouverte à droite.
     *
     * <p>Cette méthode alimente les statistiques mensuelles
     * du tableau de bord.</p>
     */
    @Query("""
        select payment
        from PaymentEntity payment
        where payment.status = :status
          and payment.paymentDate >= :fromDate
          and payment.paymentDate < :toDate
        order by
          payment.paymentDate asc,
          payment.id asc
        """)
    List<PaymentEntity> findAllByStatusAndPaymentDatePeriod(
            @Param("status")
            PaymentStatus status,

            @Param("fromDate")
            LocalDate fromDate,

            @Param("toDate")
            LocalDate toDate
    );


    /**
     * Retourne les paiements récents d'un statut donné.
     *
     * <p>Le Pageable limite le nombre de résultats
     * sans charger l'historique complet.</p>
     */
    @Query("""
        select payment
        from PaymentEntity payment
        where payment.status = :status
        order by
          payment.paymentDate desc,
          payment.id desc
        """)
    List<PaymentEntity> findRecentByStatus(
            @Param("status")
            PaymentStatus status,

            Pageable pageable
    );
}