package com.belife.partial_maturity_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Active l'audit automatique des entités JPA.
 *
 * <p>Cette configuration renseigne automatiquement les champs :
 * createdAt, createdBy, updatedAt et updatedBy.</p>
 *
 * <p>Lorsqu'aucun utilisateur n'est authentifié, par exemple pendant
 * la création du premier administrateur, la valeur SYSTEM est utilisée.</p>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null
                    || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                return Optional.of("SYSTEM");
            }

            return Optional.of(authentication.getName());
        };
    }
}
