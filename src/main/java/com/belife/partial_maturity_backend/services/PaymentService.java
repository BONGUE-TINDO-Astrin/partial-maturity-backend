package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.requests.CancelPaymentRequest;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.dtos.responses.PaymentResponse;
import com.belife.partial_maturity_backend.dtos.responses.PaymentSummaryResponse;
import com.belife.partial_maturity_backend.enums.PaymentStatus;

import java.util.List;

/**
 * Gère l'enregistrement, la consultation
 * et l'annulation des paiements.
 */
public interface PaymentService {

    /**
     * Retourne les paiements selon des filtres optionnels.
     *
     * @param search recherche partielle sur le numéro de police
     * @param status statut recherché ou null pour tous les statuts
     * @param page numéro de page commençant à zéro
     * @param size nombre d'éléments demandé
     * @return page de paiements sans leurs lignes justificatives
     */
    PageResponse<PaymentSummaryResponse>
    searchPayments(
            String search,
            PaymentStatus status,
            int page,
            int size
    );

    /**
     * Recalcule et enregistre le paiement total
     * de la situation courante.
     */
    PaymentResponse recordPayment(String policyNumber, String currentUsername);

    /**
     * Annule un paiement valide.
     */
    PaymentResponse cancelPayment(
            Long paymentId,
            CancelPaymentRequest request,
            String currentUsername
    );

    /**
     * Retourne le détail d'un paiement.
     */
    PaymentResponse getPayment(Long paymentId);

    /**
     * Retourne l'historique des paiements
     * d'une police.
     */
    List<PaymentResponse> getPolicyPayments(String policyNumber);
}