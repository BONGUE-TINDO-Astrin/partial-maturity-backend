package com.belife.partial_maturity_backend.config;

import com.belife.partial_maturity_backend.entities.AppUserEntity;
import com.belife.partial_maturity_backend.enums.UserRole;
import com.belife.partial_maturity_backend.repositories.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Initialise les données minimales nécessaires au démarrage de l'application.
 *
 * <p>Dans le MVP, cette classe crée le premier compte ADMIN uniquement
 * lorsque la table des utilisateurs est vide.</p>
 *
 * <p>Le mot de passe provient d'une variable d'environnement et est
 * enregistré sous forme de hash BCrypt.</p>
 */
@Slf4j
@Component
public class ApplicationDataInitializer implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${application.bootstrap.admin.username}")
    private String adminUsername;

    @Value("${application.bootstrap.admin.password}")
    private String adminPassword;

    @Value("${application.bootstrap.admin.full-name}")
    private String adminFullName;

    public ApplicationDataInitializer(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Crée le premier compte administrateur si aucun utilisateur n'existe.
     *
     * @param args arguments transmis au démarrage de l'application
     */
    @Override
    public void run(String... args) {
        if (appUserRepository.count() > 0) {
            log.debug("Initialisation ignorée : un utilisateur existe déjà.");
            return;
        }

        validateBootstrapProperties();

        AppUserEntity admin = new AppUserEntity();
        admin.setUsername(normalizeUsername(adminUsername));
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFullName(adminFullName.trim());
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);

        appUserRepository.save(admin);

        log.info(
                "Le compte administrateur initial '{}' a été créé.",
                admin.getUsername()
        );
    }

    /**
     * Vérifie la présence des variables requises sans afficher le mot de passe.
     */
    private void validateBootstrapProperties() {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_USERNAME est obligatoire."
            );
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD est obligatoire."
            );
        }

        if (adminPassword.length() < 8) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD doit contenir au moins 12 caractères."
            );
        }

        if (adminFullName == null || adminFullName.isBlank()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_FULL_NAME est obligatoire."
            );
        }
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}