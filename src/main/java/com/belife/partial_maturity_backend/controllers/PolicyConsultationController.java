package com.belife.partial_maturity_backend.controllers;

import com.belife.partial_maturity_backend.dtos.responses.PolicyDetailResponse;
import com.belife.partial_maturity_backend.services.PolicyConsultationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Expose la consultation des polices et de leurs maturités.
 *
 * <p>Ces opérations sont réservées au rôle COMPTABILITE.</p>
 */
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'COMPTABILITE')")
public class PolicyConsultationController {

    private final PolicyConsultationService policyConsultationService;

    /**
     * Retourne le détail d'une police recherchée
     * par son numéro exact.
     *
     * @param policyNumber numéro de police
     * @return police et maturités classées par rang
     */
    @GetMapping("/{policyNumber}")
    public ResponseEntity<PolicyDetailResponse>
    getPolicyDetails(@PathVariable String policyNumber) {
        return ResponseEntity.ok(policyConsultationService.getPolicyDetails(policyNumber));
    }
}
