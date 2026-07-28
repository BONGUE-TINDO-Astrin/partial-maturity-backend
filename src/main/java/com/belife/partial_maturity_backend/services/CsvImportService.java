package com.belife.partial_maturity_backend.services;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestre le contrôle et l'importation des CSV de maturités.
 */
public interface CsvImportService {

    /**
     * Traite entièrement un fichier CSV.
     *
     * <p>Le fichier est soit entièrement accepté, soit entièrement
     * rejeté. Aucune maturité n'est enregistrée partiellement.</p>
     *
     * @param file fichier CSV envoyé par l'administrateur
     * @param currentUsername utilisateur réalisant l'import
     * @return rapport final du chargement
     */
    CsvImportResponse importFile(MultipartFile file, String currentUsername);
}