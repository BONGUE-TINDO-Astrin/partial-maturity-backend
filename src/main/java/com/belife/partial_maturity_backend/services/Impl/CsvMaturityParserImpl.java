package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.exceptions.CsvFileProcessingException;
import com.belife.partial_maturity_backend.services.CsvMaturityParser;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;
import com.belife.partial_maturity_backend.services.models.CsvValidationResult;
import com.belife.partial_maturity_backend.services.models.ParsedMaturityRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parse et valide les fichiers CSV de maturités.
 *
 * <p>Les colonnes supplémentaires sont acceptées
 * et ignorées.</p>
 */
@Slf4j
@Service
public class CsvMaturityParserImpl implements CsvMaturityParser {

    private static final String POLICY_NUMBER_HEADER = "num_police";

    private static final String CLIENT_NAME_HEADER = "nom_client";

    private static final String MATURITY_AMOUNT_HEADER = "montant_maturite";

    private static final String INTEREST_END_DATE_HEADER = "date_fin_interets";

    private static final List<String> REQUIRED_HEADERS =
            List.of(
                    POLICY_NUMBER_HEADER,
                    CLIENT_NAME_HEADER,
                    MATURITY_AMOUNT_HEADER,
                    INTEREST_END_DATE_HEADER
            );

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final CSVFormat CSV_FORMAT =
            CSVFormat.RFC4180
                    .builder()
                    .setDelimiter(';')
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .get();

    @Override
    public CsvValidationResult parse(MultipartFile file) {
        List<ParsedMaturityRow> rows = new ArrayList<>();

        List<CsvValidationError> errors = new ArrayList<>();

        validateFileMetadata(file, errors);

        if (!errors.isEmpty()) {
            return new CsvValidationResult(
                    0,
                    rows,
                    errors
            );
        }

        int totalRows = 0;

        try (
                Reader reader =
                        createUtf8Reader(file);

                CSVParser parser =
                        CSV_FORMAT.parse(reader)
        ) {
            Map<String, String> headers =
                    validateAndMapHeaders(
                            parser.getHeaderNames(),
                            errors
                    );

            if (!errors.isEmpty()) {
                return new CsvValidationResult(
                        totalRows,
                        rows,
                        errors
                );
            }

            for (CSVRecord record : parser) {
                totalRows++;

                parseRecord(
                        record,
                        headers,
                        rows,
                        errors
                );
            }

            validatePolicyClientNames(
                    rows,
                    errors
            );

            validatePolicyInterestEndDates(
                    rows,
                    errors
            );

            if (
                    totalRows == 0
                            && errors.isEmpty()
            ) {
                errors.add(
                        new CsvValidationError(
                                0,
                                "",
                                "EMPTY_FILE",
                                "Le fichier ne contient "
                                        + "aucune ligne de maturité."
                        )
                );
            }

            return new CsvValidationResult(
                    totalRows,
                    rows,
                    errors
            );
        } catch (IOException exception) {
            throw new CsvFileProcessingException(
                    "Le fichier CSV ne peut pas être lu.",
                    exception
            );
        } catch (IllegalArgumentException exception) {
            log.error(
                    "Structure CSV invalide pour le fichier '{}'.",
                    file.getOriginalFilename(),
                    exception
            );

            throw new CsvFileProcessingException(
                    "La structure du fichier CSV est invalide.",
                    exception
            );
        }
    }

    private void validateFileMetadata(
            MultipartFile file,
            List<CsvValidationError> errors
    ) {
        if (
                file == null
                        || file.isEmpty()
        ) {
            errors.add(
                    new CsvValidationError(
                            0,
                            "",
                            "EMPTY_FILE",
                            "Le fichier CSV est vide."
                    )
            );

            return;
        }

        String fileName =
                Optional.ofNullable(
                                file.getOriginalFilename()
                        )
                        .orElse("");

        if (
                !fileName
                        .toLowerCase(Locale.ROOT)
                        .endsWith(".csv")
        ) {
            errors.add(
                    new CsvValidationError(
                            0,
                            "",
                            "INVALID_FILE_EXTENSION",
                            "Seuls les fichiers .csv "
                                    + "sont acceptés."
                    )
            );
        }
    }

    /**
     * Vérifie la présence des colonnes obligatoires.
     * Leur ordre est libre.
     */
    private Map<String, String> validateAndMapHeaders(
            List<String> actualHeaders,
            List<CsvValidationError> errors
    ) {
        Map<String, String> headerMapping =
                new LinkedHashMap<>();

        for (String actualHeader : actualHeaders) {
            String normalizedHeader =
                    normalizeHeader(actualHeader);

            if (normalizedHeader.isBlank()) {
                continue;
            }

            String previousHeader =
                    headerMapping.putIfAbsent(
                            normalizedHeader,
                            actualHeader
                    );

            if (previousHeader != null) {
                errors.add(
                        new CsvValidationError(
                                1,
                                normalizedHeader,
                                "DUPLICATE_HEADER",
                                "La colonne "
                                        + normalizedHeader
                                        + " est présente plusieurs fois."
                        )
                );
            }
        }

        for (String requiredHeader : REQUIRED_HEADERS) {
            if (
                    !headerMapping.containsKey(
                            requiredHeader
                    )
            ) {
                errors.add(
                        new CsvValidationError(
                                1,
                                requiredHeader,
                                "MISSING_REQUIRED_HEADER",
                                "La colonne obligatoire "
                                        + requiredHeader
                                        + " est absente."
                        )
                );
            }
        }

        return Map.copyOf(headerMapping);
    }

    private void parseRecord(
            CSVRecord record,
            Map<String, String> headers,
            List<ParsedMaturityRow> rows,
            List<CsvValidationError> errors
    ) {
        int rowNumber =
                Math.toIntExact(
                        record.getRecordNumber() + 1
                );

        int initialErrorCount =
                errors.size();

        String policyNumber =
                normalizePolicyNumber(
                        readValue(
                                record,
                                headers,
                                POLICY_NUMBER_HEADER
                        )
                );

        String clientName =
                normalizeClientName(
                        readValue(
                                record,
                                headers,
                                CLIENT_NAME_HEADER
                        )
                );

        String maturityAmountValue =
                readValue(
                        record,
                        headers,
                        MATURITY_AMOUNT_HEADER
                ).trim();

        String interestEndDateValue =
                readValue(
                        record,
                        headers,
                        INTEREST_END_DATE_HEADER
                ).trim();

        validatePolicyNumber(
                rowNumber,
                policyNumber,
                errors
        );

        validateClientName(
                rowNumber,
                clientName,
                errors
        );

        BigDecimal maturityAmount =
                parseMaturityAmount(
                        rowNumber,
                        maturityAmountValue,
                        errors
                );

        LocalDate interestEndDate =
                parseInterestEndDate(
                        rowNumber,
                        interestEndDateValue,
                        errors
                );

        if (errors.size() == initialErrorCount) {
            rows.add(
                    new ParsedMaturityRow(
                            rowNumber,
                            policyNumber,
                            clientName,
                            maturityAmount,
                            interestEndDate
                    )
            );
        }
    }

    private String readValue(
            CSVRecord record,
            Map<String, String> headers,
            String normalizedHeader
    ) {
        String actualHeader =
                headers.get(normalizedHeader);

        if (actualHeader == null) {
            return "";
        }

        String value =
                record.get(actualHeader);

        return value == null
                ? ""
                : value;
    }

    private void validatePolicyNumber(
            int rowNumber,
            String policyNumber,
            List<CsvValidationError> errors
    ) {
        if (policyNumber.isBlank()) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            POLICY_NUMBER_HEADER,
                            "MISSING_POLICY_NUMBER",
                            "Le numéro de police "
                                    + "est obligatoire."
                    )
            );

            return;
        }

        if (policyNumber.length() > 100) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            POLICY_NUMBER_HEADER,
                            "POLICY_NUMBER_TOO_LONG",
                            "Le numéro de police ne doit pas "
                                    + "dépasser 100 caractères."
                    )
            );
        }
    }

    private void validateClientName(
            int rowNumber,
            String clientName,
            List<CsvValidationError> errors
    ) {
        if (clientName.isBlank()) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            CLIENT_NAME_HEADER,
                            "MISSING_CLIENT_NAME",
                            "Le nom du client est obligatoire."
                    )
            );

            return;
        }

        if (clientName.length() > 200) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            CLIENT_NAME_HEADER,
                            "CLIENT_NAME_TOO_LONG",
                            "Le nom du client ne doit pas "
                                    + "dépasser 200 caractères."
                    )
            );
        }
    }

    private BigDecimal parseMaturityAmount(
            int rowNumber,
            String value,
            List<CsvValidationError> errors
    ) {
        if (value.isBlank()) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            MATURITY_AMOUNT_HEADER,
                            "MISSING_MATURITY_AMOUNT",
                            "Le montant de maturité "
                                    + "est obligatoire."
                    )
            );

            return null;
        }

        try {
            BigDecimal amount =
                    new BigDecimal(value);

            if (amount.signum() <= 0) {
                errors.add(
                        new CsvValidationError(
                                rowNumber,
                                MATURITY_AMOUNT_HEADER,
                                "INVALID_MATURITY_AMOUNT",
                                "Le montant doit être "
                                        + "strictement positif."
                        )
                );

                return null;
            }

            if (amount.scale() > 6) {
                errors.add(
                        new CsvValidationError(
                                rowNumber,
                                MATURITY_AMOUNT_HEADER,
                                "MATURITY_AMOUNT_SCALE_EXCEEDED",
                                "Le montant ne doit pas "
                                        + "dépasser 6 décimales."
                        )
                );

                return null;
            }

            if (amount.precision() > 19) {
                errors.add(
                        new CsvValidationError(
                                rowNumber,
                                MATURITY_AMOUNT_HEADER,
                                "MATURITY_AMOUNT_TOO_LARGE",
                                "Le montant dépasse la "
                                        + "précision autorisée."
                        )
                );

                return null;
            }

            return amount;
        } catch (NumberFormatException exception) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            MATURITY_AMOUNT_HEADER,
                            "INVALID_MATURITY_AMOUNT",
                            "Le montant doit utiliser le point "
                                    + "comme séparateur décimal."
                    )
            );

            return null;
        }
    }

    private LocalDate parseInterestEndDate(
            int rowNumber,
            String value,
            List<CsvValidationError> errors
    ) {
        if (value.isBlank()) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            INTEREST_END_DATE_HEADER,
                            "MISSING_INTEREST_END_DATE",
                            "La date de fin des intérêts "
                                    + "est obligatoire."
                    )
            );

            return null;
        }

        try {
            return LocalDate.parse(
                    value,
                    DATE_FORMATTER
            );
        } catch (DateTimeParseException exception) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            INTEREST_END_DATE_HEADER,
                            "INVALID_INTEREST_END_DATE",
                            "La date de fin des intérêts doit "
                                    + "respecter le format yyyy-MM-dd."
                    )
            );

            return null;
        }
    }

    private void validatePolicyClientNames(
            List<ParsedMaturityRow> rows,
            List<CsvValidationError> errors
    ) {
        Map<String, String> clientByPolicy =
                new HashMap<>();

        for (ParsedMaturityRow row : rows) {
            String policyKey =
                    normalizePolicyKey(
                            row.policyNumber()
                    );

            String expectedClient =
                    clientByPolicy.putIfAbsent(
                            policyKey,
                            row.clientName()
                    );

            if (
                    expectedClient != null
                            && !normalizeClientKey(expectedClient)
                            .equals(
                                    normalizeClientKey(
                                            row.clientName()
                                    )
                            )
            ) {
                errors.add(
                        new CsvValidationError(
                                row.rowNumber(),
                                CLIENT_NAME_HEADER,
                                "INCONSISTENT_CLIENT_NAME",
                                "Toutes les lignes de la police "
                                        + row.policyNumber()
                                        + " doivent utiliser le même "
                                        + "nom de client."
                        )
                );
            }
        }
    }

    private void validatePolicyInterestEndDates(
            List<ParsedMaturityRow> rows,
            List<CsvValidationError> errors
    ) {
        Map<String, LocalDate> endDateByPolicy =
                new HashMap<>();

        for (ParsedMaturityRow row : rows) {
            String policyKey =
                    normalizePolicyKey(
                            row.policyNumber()
                    );

            LocalDate expectedEndDate =
                    endDateByPolicy.putIfAbsent(
                            policyKey,
                            row.interestEndDate()
                    );

            if (
                    expectedEndDate != null
                            && !expectedEndDate.equals(
                            row.interestEndDate()
                    )
            ) {
                errors.add(
                        new CsvValidationError(
                                row.rowNumber(),
                                INTEREST_END_DATE_HEADER,
                                "INCONSISTENT_INTEREST_END_DATE",
                                "Toutes les lignes de la police "
                                        + row.policyNumber()
                                        + " doivent utiliser la même "
                                        + "date de fin des intérêts."
                        )
                );
            }
        }
    }

    private String normalizeHeader(
            String header
    ) {
        if (header == null) {
            return "";
        }

        return header
                .replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String normalizePolicyNumber(
            String policyNumber
    ) {
        return policyNumber == null
                ? ""
                : policyNumber.trim();
    }

    private String normalizePolicyKey(
            String policyNumber
    ) {
        return policyNumber
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeClientName(
            String clientName
    ) {
        return clientName == null
                ? ""
                : clientName
                  .trim()
                  .replaceAll("\\s+", " ");
    }

    private String normalizeClientKey(
            String clientName
    ) {
        return normalizeClientName(clientName)
                .toUpperCase(Locale.ROOT);
    }

    private Reader createUtf8Reader(
            MultipartFile file
    ) {
        try {
            PushbackReader reader =
                    new PushbackReader(
                            new InputStreamReader(
                                    file.getInputStream(),
                                    StandardCharsets.UTF_8
                            ),
                            1
                    );

            int firstCharacter =
                    reader.read();

            if (
                    firstCharacter != -1
                            && firstCharacter != '\uFEFF'
            ) {
                reader.unread(firstCharacter);
            }

            return reader;
        } catch (IOException exception) {
            throw new CsvFileProcessingException(
                    "Le fichier CSV ne peut pas être ouvert.",
                    exception
            );
        }
    }
}