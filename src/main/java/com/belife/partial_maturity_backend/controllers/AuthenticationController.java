package com.belife.partial_maturity_backend.controllers;

import com.belife.partial_maturity_backend.dtos.requests.LoginRequest;
import com.belife.partial_maturity_backend.dtos.responses.CurrentUserResponse;
import com.belife.partial_maturity_backend.dtos.responses.LoginResponse;
import com.belife.partial_maturity_backend.services.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Expose les opérations publiques et authentifiées
 * liées à la connexion.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    /**
     * Authentifie l'utilisateur et retourne un JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login( @Valid @RequestBody LoginRequest request ) {
        return ResponseEntity.
            ok( authenticationService.login(request)
        );
    }

    /**
     * Retourne les informations de l'utilisateur porté par le JWT.
     */
    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(
            authenticationService.getCurrentUser(
                authentication.getName()
            )
        );
    }
}
