package com.belife.partial_maturity_backend.services;


import com.belife.partial_maturity_backend.dtos.requests.LoginRequest;
import com.belife.partial_maturity_backend.dtos.responses.CurrentUserResponse;
import com.belife.partial_maturity_backend.dtos.responses.LoginResponse;

/**
 * Définit les opérations liées à l'authentification.
 */
public interface AuthenticationService {

    /**
     * Authentifie un utilisateur et génère un JWT.
     *
     * @param request identifiants fournis
     * @return token et informations utilisateur
     */
    LoginResponse login(LoginRequest request);

    /**
     * Retourne l'utilisateur actuellement authentifié.
     *
     * @param username identifiant extrait du contexte de sécurité
     * @return informations publiques du compte
     */
    CurrentUserResponse getCurrentUser(String username);
}