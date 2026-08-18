package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.dtos.requests.ChangeUserStatusRequest;
import com.belife.partial_maturity_backend.dtos.requests.CreateUserRequest;
import com.belife.partial_maturity_backend.dtos.requests.UpdateUserRequest;
import com.belife.partial_maturity_backend.dtos.responses.UserResponse;
import com.belife.partial_maturity_backend.entities.AppUserEntity;
import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;
import com.belife.partial_maturity_backend.enums.UserRole;
import com.belife.partial_maturity_backend.exceptions.InvalidUserOperationException;
import com.belife.partial_maturity_backend.exceptions.UserNotFoundException;
import com.belife.partial_maturity_backend.exceptions.UsernameAlreadyExistsException;
import com.belife.partial_maturity_backend.mappers.AppUserMapper;
import com.belife.partial_maturity_backend.repositories.AppUserRepository;
import com.belife.partial_maturity_backend.services.AuditService;
import com.belife.partial_maturity_backend.services.UserService;
import com.belife.partial_maturity_backend.services.models.AuditRecordCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Implémente l'administration des comptes.
 *
 * <p>Cette classe protège notamment le dernier administrateur actif
 * et interdit à l'utilisateur courant de désactiver son propre compte.</p>
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final AppUserRepository appUserRepository;
    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return appUserRepository
            .findAllByOrderByFullNameAsc()
            .stream()
            .map(appUserMapper::toResponse)
            .toList();
    }

    /**
     * Crée un compte avec un identifiant normalisé
     * et enregistre l'action dans le journal d'audit.
     */
    @Override
    @Transactional
    public UserResponse createUser( CreateUserRequest request, String currentUsername ) {
        String normalizedUsername = normalizeUsername(request.username());

        if ( appUserRepository.existsByUsernameIgnoreCase( normalizedUsername ) ) {
            throw new UsernameAlreadyExistsException(normalizedUsername);
        }

        AppUserEntity user = new AppUserEntity();

        user.setUsername(normalizedUsername);
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);

        AppUserEntity savedUser = appUserRepository.saveAndFlush(user);

        auditService.record(
            new AuditRecordCommand(
                AuditEventType.USER_CREATED,
                AuditResourceType.USER,
                savedUser.getId().toString(),
                null,
                currentUsername,
                "Création du compte utilisateur "
                        + savedUser.getUsername()
                        + ".",
                Map.of(
                    "userId",
                    savedUser.getId(),
                    "username",
                    savedUser.getUsername(),
                    "fullName",
                    savedUser.getFullName(),
                    "role",
                    savedUser.getRole().name(),
                    "active",
                    savedUser.isActive()
                )
            )
        );

        return appUserMapper.toResponse(savedUser);
    }

    /**
     * Modifie le nom complet et le rôle d'un utilisateur.
     *
     * <p>Le nom d'utilisateur n'est pas modifié, car il constitue
     * l'identifiant stable utilisé pour la connexion et l'audit.</p>
     *
     * <p>La méthode protège également l'administrateur connecté
     * et garantit qu'au moins un ADMIN actif reste disponible.</p>
     *
     * @param userId identifiant technique de l'utilisateur à modifier
     * @param request nouvelles informations du compte
     * @param currentUsername utilisateur ADMIN exécutant la modification
     * @return utilisateur actualisé
     */
    @Override
    @Transactional
    public UserResponse updateUser(
            Long userId,
            UpdateUserRequest request,
            String currentUsername
    ) {
        AppUserEntity user = appUserRepository
                .findById(userId)
                .orElseThrow(
                        () -> new UserNotFoundException(userId)
                );

        UserRole currentRole = user.getRole();
        UserRole requestedRole = request.role();

        boolean currentUserIsTarget =
                user.getUsername().equalsIgnoreCase(
                        currentUsername
                );

        boolean adminRoleIsBeingRemoved =
                currentRole == UserRole.ADMIN
                        && requestedRole != UserRole.ADMIN;

        /*
         * L'administrateur connecté ne peut pas retirer
         * son propre rôle ADMIN.
         */
        if (
                currentUserIsTarget
                        && adminRoleIsBeingRemoved
        ) {
            throw new InvalidUserOperationException(
                    "Vous ne pouvez pas retirer votre propre rôle ADMIN."
            );
        }

        /*
         * Même si la modification concerne un autre compte,
         * l'application doit toujours conserver au moins
         * un administrateur actif.
         */
        if (adminRoleIsBeingRemoved) {
            protectLastActiveAdmin(user);
        }

        String normalizedFullName = request.fullName().trim();

        user.setFullName(normalizedFullName);
        user.setRole(requestedRole);

        /*
        * mémorise les anciennes valeurs avant modification
        */
        String previousFullName = user.getFullName();
        UserRole previousRole = user.getRole();

        AppUserEntity updatedUser = appUserRepository.saveAndFlush(user);

        auditService.record(
            new AuditRecordCommand(
                AuditEventType.USER_UPDATED,
                AuditResourceType.USER,
                updatedUser.getId().toString(),
                null,
                currentUsername,
                "Modification du compte utilisateur "
                        + updatedUser.getUsername()
                        + ".",
                Map.of(
                    "userId",
                    updatedUser.getId(),
                    "username",
                    updatedUser.getUsername(),
                    "previousFullName",
                    previousFullName,
                    "newFullName",
                    updatedUser.getFullName(),
                    "previousRole",
                    previousRole.name(),
                    "newRole",
                    updatedUser.getRole().name()
                )
            )
        );

        return appUserMapper.toResponse(updatedUser);
    }

    /**
     * Active ou désactive un compte sans suppression physique.
     */
    @Override
    @Transactional
    public UserResponse changeUserStatus(Long userId, ChangeUserStatusRequest request, String currentUsername) {
        AppUserEntity user = findUser(userId);

        boolean requestedActiveStatus = request.active();

        if (user.isActive() == requestedActiveStatus) {
            return appUserMapper.toResponse(user);
        }

        if (!requestedActiveStatus && user.getUsername().equalsIgnoreCase(currentUsername)
        ) {
            throw new InvalidUserOperationException( "Vous ne pouvez pas désactiver votre propre compte.");
        }

        if (!requestedActiveStatus && user.getRole() == UserRole.ADMIN) {
            protectLastActiveAdmin(user);
        }

        user.setActive(requestedActiveStatus);

        AppUserEntity updatedUser = appUserRepository.saveAndFlush(user);

        /*
        Après la sauvegarde, détermine l’événement
         */
        AuditEventType auditEventType =
            updatedUser.isActive()
                ? AuditEventType.USER_ACTIVATED
                : AuditEventType.USER_DEACTIVATED;

        auditService.record(
            new AuditRecordCommand(
                auditEventType,
                AuditResourceType.USER,
                updatedUser.getId().toString(),
                null,
                currentUsername,
                updatedUser.isActive()
                    ? "Activation du compte utilisateur "
                      + updatedUser.getUsername()
                      + "."
                    : "Désactivation du compte utilisateur "
                      + updatedUser.getUsername()
                      + ".",
                Map.of(
                    "userId",
                    updatedUser.getId(),
                    "username",
                    updatedUser.getUsername(),
                    "active",
                    updatedUser.isActive(),
                    "role",
                    updatedUser.getRole().name()
                )
            )
        );

        return appUserMapper.toResponse(updatedUser);
    }

    private AppUserEntity findUser(Long userId) {
        return appUserRepository
            .findById(userId)
            .orElseThrow(
                () -> new UserNotFoundException(userId)
            );
    }

    /**
     * Empêche toute opération qui laisserait l'application
     * sans administrateur actif.
     */
    private void protectLastActiveAdmin(AppUserEntity user) {
        if (
            user.isActive()
                && appUserRepository
                .countByRoleAndActiveTrue(UserRole.ADMIN) <= 1
        ) {
            throw new InvalidUserOperationException("Le dernier administrateur actif doit être conservé.");
        }
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}