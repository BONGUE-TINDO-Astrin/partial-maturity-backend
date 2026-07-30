package com.belife.partial_maturity_backend.controllers;

import com.belife.partial_maturity_backend.dtos.requests.CancelPaymentRequest;
import com.belife.partial_maturity_backend.dtos.responses.PaymentResponse;
import com.belife.partial_maturity_backend.services.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Expose l'enregistrement, la consultation
 * et l'annulation des paiements.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Recalcule et enregistre le paiement total
     * de la situation courante.
     *
     * Aucun montant n'est accepté depuis le client.
     */
    @PostMapping("/policies/{policyNumber}/payments")
    @PreAuthorize("hasRole('COMPTABILITE')")
    public ResponseEntity<PaymentResponse>
    recordPayment(@PathVariable String policyNumber, Authentication authentication) {
        PaymentResponse payment = paymentService.recordPayment(policyNumber, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    /**
     * Annule un paiement valide.
     */
    @PostMapping("/payments/{paymentId}/cancel")
    @PreAuthorize("hasRole('COMPTABILITE')")
    public ResponseEntity<PaymentResponse>
    cancelPayment(
            @PathVariable Long paymentId,
            @Valid
            @RequestBody
            CancelPaymentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentService.cancelPayment(paymentId, request, authentication.getName()));
    }

    /**
     * Retourne le détail d'un paiement.
     *
     * ADMIN peut consulter, mais ne peut ni payer
     * ni annuler.
     */
    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPTABILITE')")
    public ResponseEntity<PaymentResponse>
    getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId));
    }

    /**
     * Retourne l'historique d'une police.
     */
    @GetMapping("/policies/{policyNumber}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPTABILITE')")
    public ResponseEntity<List<PaymentResponse>>
    getPolicyPayments(@PathVariable String policyNumber) {
        return ResponseEntity.ok(paymentService.getPolicyPayments(policyNumber));
    }
}
