package com.belife.partial_maturity_backend.testutils;

import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * Fabrique des fichiers CSV en mémoire pour les tests.
 *
 * <p>Cette classe évite de dépendre de fichiers physiques lorsque
 * le contenu exact du CSV fait partie du scénario testé.</p>
 */
public final class CsvTestFileFactory {

    private CsvTestFileFactory() {
        /*
         * Classe utilitaire : aucune instance nécessaire.
         */
    }

    /**
     * Crée un fichier CSV UTF-8 sans BOM.
     *
     * @param fileName nom du fichier simulé
     * @param content contenu complet du CSV
     * @return fichier multipart utilisable dans les tests
     */
    public static MockMultipartFile csv(String fileName, String content) {
        return new MockMultipartFile("file", fileName, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Crée un fichier UTF-8 contenant un BOM.
     *
     * <p>Le BOM est représenté par le caractère Unicode U+FEFF
     * précédant le premier en-tête.</p>
     *
     * @param fileName nom du fichier simulé
     * @param content contenu sans BOM
     * @return fichier multipart UTF-8 avec BOM
     */
    public static MockMultipartFile csvWithBom(String fileName, String content) {
        String contentWithBom = "\uFEFF" + content;

        return csv(fileName, contentWithBom);
    }
}