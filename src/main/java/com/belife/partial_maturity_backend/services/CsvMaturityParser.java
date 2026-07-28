package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.services.models.CsvValidationResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lit et valide la structure ainsi que les valeurs
 * d'un fichier CSV de maturités.
 *
 * <p>Ce service ne réalise aucune écriture en base.</p>
 */
public interface CsvMaturityParser {

    /**
     * Parse et valide entièrement le fichier.
     *
     * @param file fichier reçu depuis l'interface ADMIN
     * @return lignes converties et erreurs éventuelles
     */
    CsvValidationResult parse(MultipartFile file);
}