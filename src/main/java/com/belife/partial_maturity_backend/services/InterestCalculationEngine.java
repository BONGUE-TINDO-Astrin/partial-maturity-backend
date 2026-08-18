package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.responses.InterestSimulationResponse;
import com.belife.partial_maturity_backend.services.models.CalculationEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Moteur financier pur chargé de reconstituer
 * la situation d'une police.
 *
 * <p>Cette interface ne dépend ni de JPA, ni de Spring MVC,
 * ni de SQL Server.</p>
 */
public interface InterestCalculationEngine {

    /**
     * Calcule la situation financière à partir d'événements
     * chronologiques.
     *
     * <p>Les intérêts sont produits uniquement jusqu'à la date
     * de fin configurée pour la police. Les paiements intervenant
     * après cette date restent néanmoins appliqués à la situation.</p>
     *
     * @param policyNumber numéro de police
     * @param calculationDate date métier de simulation
     * @param interestEndDate date de fin de production des intérêts
     * @param annualRate taux annuel fixe
     * @param events maturités et paiements à traiter
     * @return résultat détaillé de la simulation
     */
    InterestSimulationResponse calculate(
            String policyNumber,
            LocalDate calculationDate,
            LocalDate interestEndDate,
            BigDecimal annualRate,
            List<CalculationEvent> events
    );
}