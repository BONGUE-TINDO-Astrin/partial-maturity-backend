package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.requests.CancelPaymentRequest;
import com.belife.partial_maturity_backend.dtos.responses.PaymentResponse;

import java.util.List;

/**
 * Gère l'enregistrement, la consultation
 * et l'annulation des paiements.
 */
public interface PaymentService {

    /**
     * Recalcule et enregistre le paiement total
     * de la situation courante.
     */
    PaymentResponse recordPayment(String policyNumber, String currentUsername);

    /**
     * Annule un paiement valide.
     */
    PaymentResponse cancelPayment(Long paymentId, CancelPaymentRequest request, String currentUsername);

    /**
     * Retourne le détail d'un paiement.
     */
    PaymentResponse getPayment(Long paymentId);

    /**
     * Retourne l'historique des paiements d'une police.
     */
    List<PaymentResponse> getPolicyPayments(String policyNumber);
}