package com.belife.partial_maturity_backend.controllers;

import com.belife.partial_maturity_backend.dtos.requests.CancelPaymentRequest;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.dtos.responses.PaymentResponse;
import com.belife.partial_maturity_backend.dtos.responses.PaymentSummaryResponse;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.services.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * Retourne la liste paginée des paiements.
     *
     * <p>La recherche s'applique partiellement au numéro
     * de police. Le statut est facultatif.</p>
     */
    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPTABILITE')")
    public ResponseEntity<PageResponse<PaymentSummaryResponse>> searchPayments(
            @RequestParam(required = false) String search,

            @RequestParam(required = false) PaymentStatus status,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(paymentService.searchPayments(search, status, page, size));
    }

    /**
     * Recalcule et enregistre le paiement total
     * de la situation courante.
     *
     * <p>Aucun montant n'est accepté depuis le client.</p>
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse>
    cancelPayment(
            @PathVariable Long paymentId,

            @Valid
            @RequestBody CancelPaymentRequest request,

            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentService.cancelPayment(paymentId, request, authentication.getName()));
    }

    /**
     * Retourne le détail d'un paiement.
     *
     * <p>ADMIN peut consulter, mais ne peut ni payer
     * ni annuler.</p>
     */
    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPTABILITE')")
    public ResponseEntity<PaymentResponse>
    getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId));
    }

    /**
     * Retourne l'historique complet
     * des paiements d'une police.
     *
     * <p>Cet endpoint est conservé pour les consommateurs
     * qui consultent directement une police.</p>
     */
    @GetMapping("/policies/{policyNumber}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPTABILITE')")
    public ResponseEntity<List<PaymentResponse>>
    getPolicyPayments(@PathVariable String policyNumber) {
        return ResponseEntity.ok(paymentService.getPolicyPayments(policyNumber));
    }
}