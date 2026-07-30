package com.belife.partial_maturity_backend.repositories;

import com.belife.partial_maturity_backend.entities.PaymentDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Fournit l'accès aux lignes justificatives des paiements.
 */
public interface PaymentDetailRepository extends JpaRepository<PaymentDetailEntity, Long> {

    List<PaymentDetailEntity> findAllByPaymentIdOrderBySequenceNumberAsc(Long paymentId);
}