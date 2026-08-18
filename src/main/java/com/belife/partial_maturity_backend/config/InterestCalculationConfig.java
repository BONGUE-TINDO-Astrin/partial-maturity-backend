package com.belife.partial_maturity_backend.config;

import com.belife.partial_maturity_backend.config.properties.InterestProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Configuration du moteur de calcul des intérêts
 * et de l'horloge technique de l'application.
 */
@Configuration
@EnableConfigurationProperties(
        InterestProperties.class
)
public class InterestCalculationConfig {

    /**
     * Fournit l'horloge UTC réelle de l'application.
     *
     * <p>Cette horloge reste utilisée pour les timestamps
     * techniques. La simulation d'une date métier en
     * développement est gérée séparément par
     * BusinessDateProvider.</p>
     *
     * @return horloge système UTC
     */
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}