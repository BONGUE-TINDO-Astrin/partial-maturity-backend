package com.belife.partial_maturity_backend.config;

import com.belife.partial_maturity_backend.config.properties.InterestProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Configuration du moteur de calcul des intérêts.
 */
@Configuration
@EnableConfigurationProperties(InterestProperties.class)
public class InterestCalculationConfig {

    /**
     * Fournit l'horloge utilisée pour obtenir la date métier.
     *
     * <p>Injecter Clock au lieu d'appeler directement
     * LocalDate.now() permet de fixer la date dans les tests.</p>
     *
     * @return horloge UTC de production
     */
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}