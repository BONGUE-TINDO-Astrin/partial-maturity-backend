package com.belife.partial_maturity_backend.controllers;

import com.belife.partial_maturity_backend.dtos.responses.PolicyDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyFinancialDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyFinancialSummaryResponse;
import com.belife.partial_maturity_backend.services.PolicyConsultationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Expose la consultation des polices,
 * de leurs maturités et de leur situation financière.
 *
 * <p>Ces opérations sont accessibles à ADMIN
 * et COMPTABILITE.</p>
 */
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'COMPTABILITE')")
public class PolicyConsultationController {

    private final PolicyConsultationService policyConsultationService;

    /**
     * Retourne toutes les polices avec leur synthèse
     * financière à la date métier courante.
     */
    @GetMapping
    public ResponseEntity<List<PolicyFinancialSummaryResponse>> getPolicyFinancialSummaries() {
        return ResponseEntity.ok(policyConsultationService.getPolicyFinancialSummaries());
    }

    /**
     * Retourne les informations descriptives
     * et les maturités d'une police.
     */
    @GetMapping("/{policyNumber}")
    public ResponseEntity<PolicyDetailResponse> getPolicyDetails(@PathVariable String policyNumber) {
        return ResponseEntity.ok(policyConsultationService.getPolicyDetails(policyNumber));
    }

    /**
     * Retourne la situation financière complète
     * destinée à la popup de consultation.
     */
    @GetMapping("/{policyNumber}/financial-details")
    public ResponseEntity<PolicyFinancialDetailResponse> getPolicyFinancialDetails(@PathVariable String policyNumber) {
        return ResponseEntity.ok(policyConsultationService.getPolicyFinancialDetails(policyNumber));
    }
}