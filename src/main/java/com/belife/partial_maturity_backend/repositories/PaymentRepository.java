package com.belife.partial_maturity_backend.repositories;

import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Fournit les opérations d'accès aux paiements.
 */
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    /**
     * Charge uniquement les paiements actifs participant
     * aux simulations financières.
     */
    List<PaymentEntity>
    findAllByPolicyNumberIgnoreCaseAndStatusOrderByPaymentDateAscIdAsc(
            String policyNumber,
            PaymentStatus status
    );

    /**
     * Retourne l'historique complet d'une police,
     * paiements annulés compris.
     */
    List<PaymentEntity>
    findAllByPolicyNumberIgnoreCaseOrderByPaymentDateDescIdDesc(String policyNumber);

    /**
     * Charge un paiement et ses détails pour consultation.
     */
    @EntityGraph(attributePaths = "details")
    @Query("""
            select payment
            from PaymentEntity payment
            where payment.id = :paymentId
            """)
    Optional<PaymentEntity> findByIdWithDetails( @Param("paymentId") Long paymentId);

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
}