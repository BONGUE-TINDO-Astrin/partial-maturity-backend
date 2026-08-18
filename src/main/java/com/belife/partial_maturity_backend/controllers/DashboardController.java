package com.belife.partial_maturity_backend.controllers;

import com.belife.partial_maturity_backend.dtos.responses.dashboard.DashboardResponse;
import com.belife.partial_maturity_backend.services.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expose le tableau de bord commun aux utilisateurs
 * ADMIN et COMPTABILITE.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Retourne la même vue synthétique pour tous
     * les utilisateurs autorisés.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPTABILITE')")
    public ResponseEntity<DashboardResponse>
    getDashboard() {return ResponseEntity.ok(dashboardService.getDashboard());
    }
}