package com.belife.partial_maturity_backend.controllers;

import com.belife.partial_maturity_backend.dtos.responses.InterestSimulationResponse;
import com.belife.partial_maturity_backend.services.InterestCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Expose les simulations financières des polices.
 *
 * <p>La simulation est accessible à ADMIN et COMPTABILITE.
 * Elle ne crée aucune écriture de paiement.</p>
 */
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class InterestCalculationController {

    private final InterestCalculationService calculationService;

    /**
     * Retourne la situation de la police à la date métier.
     */
    @GetMapping("/{policyNumber}/simulation")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPTABILITE')")
    public ResponseEntity<InterestSimulationResponse>
    simulate(@PathVariable String policyNumber) {
        return ResponseEntity.ok(calculationService.simulate(policyNumber));
    }
}
