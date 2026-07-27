package com.belife.partial_maturity_backend.repositories;

import com.belife.partial_maturity_backend.entities.AppUserEntity;
import com.belife.partial_maturity_backend.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

    Optional<AppUserEntity> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Retourne une liste stable et lisible pour l'écran
     * d'administration des utilisateurs.
     */
    List<AppUserEntity> findAllByOrderByFullNameAsc();
    /**
     * Permet de protéger le dernier compte administrateur actif.
     */
    long countByRoleAndActiveTrue(UserRole role);
}
