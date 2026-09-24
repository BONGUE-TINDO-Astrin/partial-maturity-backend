package com.belife.partial_maturity_backend.exceptions;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(
            UserNotFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                exception.getMessage(),
                List.of()
        );
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUsernameAlreadyExists(
            UsernameAlreadyExistsException exception
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "USERNAME_ALREADY_EXISTS",
                exception.getMessage(),
                List.of()
        );
    }

    @ExceptionHandler(InvalidUserOperationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidUserOperation(
            InvalidUserOperationException exception
    ) {
        return buildResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_USER_OPERATION",
                exception.getMessage(),
                List.of()
        );
    }

    @ExceptionHandler(CsvImportRejectedException.class)
    public ResponseEntity<CsvImportResponse> handleCsvImportRejected(
            CsvImportRejectedException exception
    ) {
        return ResponseEntity
                .unprocessableEntity()
                .body(exception.getResponse());
    }

    @ExceptionHandler(CsvFileProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleCsvFileProcessingException(
            CsvFileProcessingException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "CSV_FILE_PROCESSING_ERROR",
                exception.getMessage(),
                List.of()
        );
    }

    @ExceptionHandler(ImportBatchNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleImportBatchNotFound(
            ImportBatchNotFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "IMPORT_BATCH_NOT_FOUND",
                exception.getMessage(),
                List.of()
        );
    }

    /**
     * Retourne HTTP 404 lorsqu'une police n'existe pas
     * dans les données de maturités importées.
     */
    @ExceptionHandler(PolicyNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePolicyNotFound(
            PolicyNotFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "POLICY_NOT_FOUND",
                exception.getMessage(),
                List.of()
        );
    }

    /**
     * Retourne HTTP 400 lorsque le numéro de police
     * fourni est invalide.
     */
    @ExceptionHandler(InvalidPolicyNumberException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPolicyNumber(
            InvalidPolicyNumberException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_POLICY_NUMBER",
                exception.getMessage(),
                List.of()
        );
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentNotFound(
            PaymentNotFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "PAYMENT_NOT_FOUND",
                exception.getMessage(),
                List.of()
        );
    }

    @ExceptionHandler(PaymentNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentNotAllowed(
            PaymentNotAllowedException exception
    ) {
        return buildResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PAYMENT_NOT_ALLOWED",
                exception.getMessage(),
                List.of()
        );
    }

    @ExceptionHandler({
            ConcurrentPaymentOperationException.class,
            ObjectOptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class
    })
    public ResponseEntity<Map<String, Object>> handleConcurrentPaymentOperation(
            RuntimeException exception
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "CONCURRENT_PAYMENT_OPERATION",
                "La situation financière a été modifiée par une autre opération. Veuillez actualiser puis recommencer.",
                List.of()
        );
    }

    /**
     * Retourne HTTP 404 lorsqu'une entrée d'audit
     * n'existe pas.
     */
    @ExceptionHandler(AuditLogNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAuditLogNotFound(
            AuditLogNotFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "AUDIT_LOG_NOT_FOUND",
                exception.getMessage(),
                List.of()
        );
    }

    @ExceptionHandler(InvalidAuditFilterException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAuditFilter(
            InvalidAuditFilterException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_AUDIT_FILTER",
                exception.getMessage(),
                List.of()
        );
    }

    /**
     * Retourne HTTP 409 lorsqu'un chargement existe,
     * mais que son état ou ses dépendances empêchent
     * sa réversion.
     */
    @ExceptionHandler(ImportBatchReversalNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handleImportBatchReversalNotAllowed(
            ImportBatchReversalNotAllowedException exception
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "IMPORT_BATCH_REVERSAL_NOT_ALLOWED",
                exception.getMessage(),
                List.of()
        );
    }

    /**
     * Retourne HTTP 409 lorsqu'une autre opération
     * modifie simultanément le lot ou une police concernée.
     */
    @ExceptionHandler(ConcurrentImportBatchOperationException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentImportBatchOperation(
            ConcurrentImportBatchOperationException exception
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "CONCURRENT_IMPORT_BATCH_OPERATION",
                exception.getMessage(),
                List.of()
        );
    }
}