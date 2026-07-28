package com.belife.partial_maturity_backend.utils;

import com.belife.partial_maturity_backend.exceptions.CsvFileProcessingException;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Convertit les erreurs de validation CSV entre leur représentation
 * Java et leur représentation JSON stockée dans SQL Server.
 *
 * <p>Spring Boot 4 utilise Jackson 3 ; le mapper injecté appartient
 * donc au package tools.jackson.</p>
 */
@Component
public class CsvValidationErrorJsonCodec {

    private static final TypeReference<List<CsvValidationError>>
            ERROR_LIST_TYPE = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    public CsvValidationErrorJsonCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Sérialise les erreurs avant leur enregistrement.
     */
    public String write(List<CsvValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }

        try {
            return jsonMapper.writeValueAsString(errors);
        } catch (RuntimeException exception) {
            throw new CsvFileProcessingException(
                    "Impossible de sérialiser le rapport d'erreurs CSV.",
                    exception
            );
        }
    }

    /**
     * Reconstitue les erreurs enregistrées.
     */
    public List<CsvValidationError> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return jsonMapper.readValue(json, ERROR_LIST_TYPE);
        } catch (RuntimeException exception) {
            throw new CsvFileProcessingException(
                    "Impossible de lire le rapport d'erreurs CSV.",
                    exception
            );
        }
    }
}
