package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.dtos.requests.LoginRequest;
import com.belife.partial_maturity_backend.dtos.responses.CurrentUserResponse;
import com.belife.partial_maturity_backend.dtos.responses.LoginResponse;
import com.belife.partial_maturity_backend.entities.AppUserEntity;
import com.belife.partial_maturity_backend.exceptions.AccountDisabledException;
import com.belife.partial_maturity_backend.exceptions.InvalidCredentialsException;
import com.belife.partial_maturity_backend.repositories.AppUserRepository;
import com.belife.partial_maturity_backend.security.JwtService;
import com.belife.partial_maturity_backend.services.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/**
 * Implémente la connexion et la consultation de l'utilisateur connecté.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;

    /**
     * Vérifie les identifiants, met à jour la dernière connexion
     * et génère le JWT.
     */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    username,
                    request.password()
                )
            );
        } catch (DisabledException exception) {
            throw new AccountDisabledException();
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException();
        }

        AppUserEntity user = appUserRepository
            .findByUsernameIgnoreCase(username)
            .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive()) {
            throw new AccountDisabledException();
        }

        user.setLastLoginAt(Instant.now());
        appUserRepository.save(user);

        String accessToken = jwtService.generateToken(user);

        return new LoginResponse(
            accessToken,
            "Bearer",
            jwtService.getExpirationMillis(),
            toCurrentUserResponse(user)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String username) {
        AppUserEntity user = appUserRepository
            .findByUsernameIgnoreCase(normalizeUsername(username))
            .orElseThrow(InvalidCredentialsException::new);

        return toCurrentUserResponse(user);
    }

    private CurrentUserResponse toCurrentUserResponse(AppUserEntity user) {
        return new CurrentUserResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getRole()
        );
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
