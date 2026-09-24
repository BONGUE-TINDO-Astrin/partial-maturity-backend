package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.services.BusinessDateProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Fournit une date métier configurable sous
 * le profil de développement.
 *
 * <p>Lorsque la propriété n'est pas renseignée,
 * la date réelle issue de l'horloge est utilisée.</p>
 */
@Component
@Profile("dev")
public class DevelopmentBusinessDateProvider implements BusinessDateProvider {

    private final Clock clock;
    private final LocalDate configuredBusinessDate;

    public DevelopmentBusinessDateProvider(
            Clock clock,

            @Value("${application.business-date:}")
            String configuredBusinessDate
    ) {
        this.clock = clock;

        this.configuredBusinessDate = parseConfiguredDate(configuredBusinessDate);
    }

    @Override
    public LocalDate currentDate() {
        if (configuredBusinessDate != null) {
            return configuredBusinessDate;
        }

        return LocalDate.now(clock);
    }

    /**
     * Convertit la propriété ISO yyyy-MM-dd.
     *
     * <p>Une valeur absente conserve la date réelle.
     * Une valeur présente mais incorrecte empêche
     * volontairement le démarrage de l'application.</p>
     */
    private LocalDate parseConfiguredDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException(
                    "La propriété application.business-date "
                            + "doit respecter le format yyyy-MM-dd.",
                    exception
            );
        }
    }
}