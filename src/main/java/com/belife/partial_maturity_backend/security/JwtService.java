package com.belife.partial_maturity_backend.security;

import com.belife.partial_maturity_backend.entities.AppUserEntity;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Fournit les opérations nécessaires à la génération
 * et à la validation des tokens JWT.
 */
public interface JwtService {

    /**
     * Génère un token signé pour l'utilisateur fourni.
     *
     * @param user utilisateur authentifié
     * @return JWT signé
     */
    String generateToken(AppUserEntity user);

    /**
     * Extrait le nom d'utilisateur contenu dans le token.
     *
     * @param token JWT reçu
     * @return nom d'utilisateur
     */
    String extractUsername(String token);

    /**
     * Vérifie la signature, l'expiration et le propriétaire du token.
     *
     * @param token JWT reçu
     * @param userDetails utilisateur chargé depuis la base
     * @return true si le token est valide
     */
    boolean isTokenValid(String token, UserDetails userDetails);

    /**
     * Retourne la durée de validité configurée du token.
     *
     * @return durée en millisecondes
     */
    long getExpirationMillis();
}
