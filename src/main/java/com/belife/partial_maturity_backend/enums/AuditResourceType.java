package com.belife.partial_maturity_backend.enums;

/**
 * Types de ressources pouvant être référencées
 * par une entrée du journal d'audit.
 */
public enum AuditResourceType {

    /**
     * Compte utilisateur.
     */
    USER,

    /**
     * Lot de chargement CSV.
     */
    IMPORT_BATCH,

    /**
     * Paiement financier.
     */
    PAYMENT
}