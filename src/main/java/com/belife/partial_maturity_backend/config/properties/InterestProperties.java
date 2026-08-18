package com.belife.partial_maturity_backend.config.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Paramètres du moteur de calcul des intérêts.
 *
 * <p>Le taux annuel est exprimé sous forme décimale.</p>
 *
 * <pre>
 * 3,5 % = 0.035
 * </pre>
 *
 * @param annualRate taux annuel fixe appliqué à chaque
 *                   cycle complet de douze mois
 */
@Validated
@ConfigurationProperties(prefix = "application.interest")
public record InterestProperties(

        @NotNull(message = "Le taux annuel est obligatoire.")
        @DecimalMin(
                value = "0",
                inclusive = false,
                message = "Le taux annuel doit être positif."
        )
        @DecimalMax(
                value = "1",
                inclusive = false,
                message = "Le taux annuel doit être inférieur à 1."
        )
        BigDecimal annualRate
) {
}