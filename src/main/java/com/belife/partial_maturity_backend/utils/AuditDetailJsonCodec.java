package com.belife.partial_maturity_backend.utils;

import com.belife.partial_maturity_backend.exceptions.AuditRecordingException;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Sérialise et désérialise les informations complémentaires
 * du journal d'audit.
 *
 * <p>Spring Boot 4 utilise Jackson 3. Les classes Jackson
 * appartiennent donc au package {@code tools.jackson}.</p>
 */
@Component
public class AuditDetailJsonCodec {

    private static final TypeReference<Map<String, Object>> DETAIL_TYPE = new TypeReference<>() {};

    private final JsonMapper jsonMapper;

    public AuditDetailJsonCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Convertit les informations structurées en JSON.
     *
     * @param details informations à enregistrer
     * @return document JSON, ou null lorsque la Map est vide
     */
    public String write(Map<String, Object> details) {
        if (details == null || details.isEmpty()
        ) {
            return null;
        }

        try {
            return jsonMapper.writeValueAsString(details);

        } catch (RuntimeException exception) {
            throw new AuditRecordingException(
                "Impossible de sérialiser les informations du journal d'audit.",
                exception
            );
        }
    }

    /**
     * Reconstitue les informations structurées d'une entrée.
     *
     * @param json document JSON enregistré
     * @return Map immuable contenant les détails
     */
    public Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> details = jsonMapper.readValue(json, DETAIL_TYPE);

            if (details == null || details.isEmpty()) {
                return Map.of();
            }

            return Map.copyOf(details);

        } catch (RuntimeException exception) {
            throw new AuditRecordingException(
                "Impossible de lire les informations du journal d'audit.",
                exception
            );
        }
    }
}
