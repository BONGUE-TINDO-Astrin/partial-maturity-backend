package com.belife.partial_maturity_backend.services;

import java.time.LocalDate;

/**
 * Fournit la date métier courante utilisée
 * par les calculs financiers.
 *
 * <p>Cette abstraction permet au profil de développement
 * de simuler une date sans modifier l'horloge du système.</p>
 */
public interface BusinessDateProvider {

    /**
     * Retourne la date métier courante.
     *
     * @return date utilisée pour les simulations
     *         et les paiements
     */
    LocalDate currentDate();
}