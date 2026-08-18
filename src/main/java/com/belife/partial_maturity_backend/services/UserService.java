package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.requests.ChangeUserStatusRequest;
import com.belife.partial_maturity_backend.dtos.requests.CreateUserRequest;
import com.belife.partial_maturity_backend.dtos.requests.UpdateUserRequest;
import com.belife.partial_maturity_backend.dtos.responses.UserResponse;

import java.util.List;

/**
 * Définit les opérations d'administration des comptes utilisateurs.
 */
public interface UserService {

    /**
     * Retourne tous les comptes classés par nom complet.
     */
    List<UserResponse> getAllUsers();

    /**
     * Crée un compte et enregistre l'action dans le journal d'audit.
     *
     * @param request données du nouveau compte
     * @param currentUsername administrateur réalisant l'opération
     * @return compte créé
     */
    UserResponse createUser(CreateUserRequest request, String currentUsername);

    /**
     * Modifie le nom complet et le rôle d'un compte.
     */
    UserResponse updateUser(
        Long userId,
        UpdateUserRequest request,
        String currentUsername
    );

    /**
     * Active ou désactive un compte.
     */
    UserResponse changeUserStatus(
        Long userId,
        ChangeUserStatusRequest request,
        String currentUsername
    );
}