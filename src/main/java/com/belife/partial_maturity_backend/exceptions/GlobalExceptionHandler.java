package com.belife.partial_maturity_backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforme les exceptions de l'application en réponses JSON cohérentes.
 *
 * <p>Les erreurs retournées au frontend ne doivent jamais exposer
 * de mot de passe, de stack trace ou d'information technique sensible.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(
        InvalidCredentialsException exception
    ) {
        return buildResponse(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            exception.getMessage(),
            List.of()
        );
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<Map<String, Object>> handleAccountDisabled(
        AccountDisabledException exception
    ) {
        return buildResponse(
            HttpStatus.FORBIDDEN,
            "ACCOUNT_DISABLED",
            exception.getMessage(),
            List.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
        MethodArgumentNotValidException exception
    ) {
        List<String> details = exception
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::formatFieldError)
            .toList();

        return buildResponse(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Les données fournies sont invalides.",
            details
        );
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField()
            + " : "
            + fieldError.getDefaultMessage();
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
        HttpStatus status,
        String code,
        String message,
        List<String> details
    ) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        body.put("details", details);

        return ResponseEntity
            .status(status)
            .body(body);
    }
}