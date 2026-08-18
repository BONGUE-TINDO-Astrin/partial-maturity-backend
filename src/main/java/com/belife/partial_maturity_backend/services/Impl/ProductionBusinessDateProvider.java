package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.services.BusinessDateProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Fournit la date réelle du serveur lorsque
 * l'application s'exécute hors du profil dev.
 */
@Component
@Profile("!dev")
@RequiredArgsConstructor
public class ProductionBusinessDateProvider implements BusinessDateProvider {

    private final Clock clock;

    @Override
    public LocalDate currentDate() {
        return LocalDate.now(clock);
    }
}