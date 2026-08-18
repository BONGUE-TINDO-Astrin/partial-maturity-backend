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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse les fichiers CSV de maturités avec Apache Commons CSV.
 *
 * <p>La validation est exhaustive : le parseur continue à analyser
 * les lignes après une erreur afin de retourner un rapport complet
 * à l'administrateur.</p>
 *
 * <p>Aucune donnée n'est enregistrée dans cette classe.</p>
 */
@Slf4j
@Service
public class CsvMaturityParserImpl
        implements CsvMaturityParser {

    private static final List<String> REQUIRED_HEADERS =
            List.of("num_police", "type_maturite", "date_maturite", "montant_maturite", "date_fin_interets");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Pattern MATURITY_TYPE_PATTERN =
            Pattern.compile("^MATURITE_([1-9][0-9]*)$", Pattern.CASE_INSENSITIVE);

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
        int totalRows = 0;

        List<ParsedMaturityRow> rows = new ArrayList<>();

        List<CsvValidationError> errors = new ArrayList<>();

        validateFileMetadata(file, errors);

        if (!errors.isEmpty()) {
            return new CsvValidationResult(
                    totalRows,
                    rows,
                    errors
            );
        }

        try (
                Reader reader = createUtf8Reader(file);
                CSVParser parser = CSV_FORMAT.parse(reader)
        ) {
            validateHeaders(
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
                        rows,
                        errors
                );
            }

            validateInternalDuplicates(
                    rows,
                    errors
            );

            validatePolicyInterestEndDates(
                    rows,
                    errors
            );

            if (
                    rows.isEmpty() &&
                            errors.isEmpty()
            ) {
                errors.add(
                        new CsvValidationError(
                                0,
                                "",
                                "EMPTY_FILE",
                                "Le fichier ne contient aucune ligne de maturité."
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
                    "Structure CSV invalide pour le fichier '{}'. "
                            + "En-têtes ou colonnes non accessibles.",
                    file.getOriginalFilename(),
                    exception
            );

            throw new CsvFileProcessingException(
                    "La structure du fichier CSV est invalide.",
                    exception
            );
        }
    }

    /**
     * Vérifie les propriétés générales du fichier
     * avant son ouverture.
     */
    private void validateFileMetadata(
            MultipartFile file,
            List<CsvValidationError> errors
    ) {
        if (
                file == null ||
                        file.isEmpty()
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
                ).orElse("");

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
                            "Seuls les fichiers avec l'extension .csv sont acceptés."
                    )
            );
        }
    }

    /**
     * Exige exactement les cinq colonnes du contrat,
     * dans l'ordre convenu.
     */
    private void validateHeaders(
            List<String> actualHeaders,
            List<CsvValidationError> errors
    ) {
        List<String> normalizedHeaders =
                actualHeaders
                        .stream()
                        .map(this::normalizeHeader)
                        .toList();

        if (!REQUIRED_HEADERS.equals(
                normalizedHeaders
        )) {
            errors.add(
                    new CsvValidationError(
                            1,
                            "",
                            "INVALID_HEADER",
                            "L'en-tête attendu est : "
                                    + String.join(
                                    ";",
                                    REQUIRED_HEADERS
                            )
                    )
            );
        }
    }

    /**
     * Convertit une ligne CSV en valeurs fortement typées.
     *
     * <p>La ligne est conservée uniquement lorsque chacun
     * de ses champs respecte le contrat du fichier.</p>
     */
    private void parseRecord(
            CSVRecord record,
            List<ParsedMaturityRow> rows,
            List<CsvValidationError> errors
    ) {
        int sourceRowNumber =
                Math.toIntExact(
                        record.getRecordNumber() + 1
                );

        int errorsBeforeCurrentRow =
                errors.size();

        String policyNumber =
                normalizePolicyNumber(
                        record.get("num_police")
                );

        String maturityType =
                normalizeMaturityType(
                        record.get("type_maturite")
                );

        String maturityDateValue =
                record.get("date_maturite")
                        .trim();

        String maturityAmountValue =
                record.get("montant_maturite")
                        .trim();

        String interestEndDateValue =
                record.get("date_fin_interets")
                        .trim();

        validatePolicyNumber(
                sourceRowNumber,
                policyNumber,
                errors
        );

        Integer maturityRank =
                parseMaturityRank(
                        sourceRowNumber,
                        maturityType,
                        errors
                );

        LocalDate maturityDate =
                parseMaturityDate(
                        sourceRowNumber,
                        maturityDateValue,
                        errors
                );

        BigDecimal maturityAmount =
                parseMaturityAmount(
                        sourceRowNumber,
                        maturityAmountValue,
                        errors
                );

        LocalDate interestEndDate =
                parseInterestEndDate(
                        sourceRowNumber,
                        interestEndDateValue,
                        errors
                );

        /*
         * La comparaison n'est possible que lorsque les deux
         * dates ont été correctement converties.
         */
        if (
                maturityDate != null &&
                        interestEndDate != null
        ) {
            validateMaturityWithinInterestPeriod(
                    sourceRowNumber,
                    maturityDate,
                    interestEndDate,
                    errors
            );
        }

        boolean currentRowIsValid =
                errors.size()
                        == errorsBeforeCurrentRow;

        if (currentRowIsValid) {
            rows.add(
                    new ParsedMaturityRow(
                            sourceRowNumber,
                            policyNumber,
                            maturityType,
                            maturityRank,
                            maturityDate,
                            maturityAmount,
                            interestEndDate
                    )
            );
        }
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
                            "num_police",
                            "MISSING_POLICY_NUMBER",
                            "Le numéro de police est obligatoire."
                    )
            );

            return;
        }

        if (policyNumber.length() > 100) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            "num_police",
                            "POLICY_NUMBER_TOO_LONG",
                            "Le numéro de police ne doit pas dépasser 100 caractères."
                    )
            );
        }
    }

    private Integer parseMaturityRank(
            int rowNumber,
            String maturityType,
            List<CsvValidationError> errors
    ) {
        Matcher matcher =
                MATURITY_TYPE_PATTERN.matcher(
                        maturityType
                );

        if (!matcher.matches()) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            "type_maturite",
                            "INVALID_MATURITY_TYPE",
                            "Le type de maturité doit respecter le format MATURITE_N."
                    )
            );

            return null;
        }

        try {
            return Integer.parseInt(
                    matcher.group(1)
            );
        } catch (NumberFormatException exception) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            "type_maturite",
                            "INVALID_MATURITY_RANK",
                            "Le rang de maturité est invalide."
                    )
            );

            return null;
        }
    }

    private LocalDate parseMaturityDate(
            int rowNumber,
            String value,
            List<CsvValidationError> errors
    ) {
        if (value.isBlank()) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            "date_maturite",
                            "MISSING_MATURITY_DATE",
                            "La date de maturité est obligatoire."
                    )
            );

            return null;
        }

        try {
            return LocalDate.parse(
                    value,
                    DATE_FORMATTER
            );
        } catch (
                DateTimeParseException exception
        ) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            "date_maturite",
                            "INVALID_MATURITY_DATE",
                            "La date de maturité doit respecter le format yyyy-MM-dd."
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
                            "date_fin_interets",
                            "MISSING_INTEREST_END_DATE",
                            "La date de fin des intérêts est obligatoire."
                    )
            );

            return null;
        }

        try {
            return LocalDate.parse(
                    value,
                    DATE_FORMATTER
            );
        } catch (
                DateTimeParseException exception
        ) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            "date_fin_interets",
                            "INVALID_INTEREST_END_DATE",
                            "La date de fin des intérêts doit respecter le format yyyy-MM-dd."
                    )
            );

            return null;
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
                            "montant_maturite",
                            "MISSING_MATURITY_AMOUNT",
                            "Le montant de maturité est obligatoire."
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
                                "montant_maturite",
                                "INVALID_MATURITY_AMOUNT",
                                "Le montant de maturité doit être strictement positif."
                        )
                );

                return null;
            }

            if (amount.scale() > 6) {
                errors.add(
                        new CsvValidationError(
                                rowNumber,
                                "montant_maturite",
                                "MATURITY_AMOUNT_SCALE_EXCEEDED",
                                "Le montant ne doit pas dépasser 6 décimales."
                        )
                );

                return null;
            }

            if (amount.precision() > 19) {
                errors.add(
                        new CsvValidationError(
                                rowNumber,
                                "montant_maturite",
                                "MATURITY_AMOUNT_TOO_LARGE",
                                "Le montant dépasse la précision autorisée."
                        )
                );

                return null;
            }

            return amount;
        } catch (
                NumberFormatException exception
        ) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            "montant_maturite",
                            "INVALID_MATURITY_AMOUNT",
                            "Le montant doit utiliser le point comme séparateur décimal."
                    )
            );

            return null;
        }
    }

    /**
     * Autorise une maturité le jour de la clôture,
     * mais refuse toute maturité située après cette date.
     */
    private void validateMaturityWithinInterestPeriod(
            int rowNumber,
            LocalDate maturityDate,
            LocalDate interestEndDate,
            List<CsvValidationError> errors
    ) {
        if (
                maturityDate.isAfter(
                        interestEndDate
                )
        ) {
            errors.add(
                    new CsvValidationError(
                            rowNumber,
                            "date_maturite",
                            "MATURITY_AFTER_INTEREST_END_DATE",
                            "La date de maturité ne peut pas être postérieure à la date de fin des intérêts."
                    )
            );
        }
    }

    /**
     * Contrôle les répétitions à l'intérieur
     * du fichier courant.
     *
     * <p>Une répétition strictement identique est tolérée
     * et sera dédupliquée lors de l'importation.</p>
     *
     * <p>Deux lignes partageant la même police et le même rang,
     * mais avec des données différentes, sont contradictoires.</p>
     */
    private void validateInternalDuplicates(
            List<ParsedMaturityRow> rows,
            List<CsvValidationError> errors
    ) {
        Map<String, ParsedMaturityRow>
                firstRowsByKey =
                new HashMap<>();

        for (ParsedMaturityRow row : rows) {
            String businessKey =
                    buildBusinessKey(
                            row.policyNumber(),
                            row.maturityRank()
                    );

            ParsedMaturityRow previousRow =
                    firstRowsByKey.putIfAbsent(
                            businessKey,
                            row
                    );

            if (
                    previousRow != null &&
                            !areStrictlyIdentical(
                                    previousRow,
                                    row
                            )
            ) {
                errors.add(
                        new CsvValidationError(
                                row.rowNumber(),
                                "type_maturite",
                                "CONTRADICTORY_MATURITY",
                                "La police "
                                        + row.policyNumber()
                                        + " contient plusieurs valeurs différentes "
                                        + "pour la maturité de rang "
                                        + row.maturityRank()
                                        + "."
                        )
                );
            }
        }
    }

    /**
     * Vérifie que toutes les lignes d'une police utilisent
     * la même date de fin de production des intérêts.
     */
    private void validatePolicyInterestEndDates(
            List<ParsedMaturityRow> rows,
            List<CsvValidationError> errors
    ) {
        Map<String, LocalDate>
                interestEndDateByPolicy =
                new HashMap<>();

        for (ParsedMaturityRow row : rows) {
            String normalizedPolicyNumber =
                    normalizePolicyKey(
                            row.policyNumber()
                    );

            LocalDate expectedInterestEndDate =
                    interestEndDateByPolicy
                            .putIfAbsent(
                                    normalizedPolicyNumber,
                                    row.interestEndDate()
                            );

            if (
                    expectedInterestEndDate != null &&
                            !expectedInterestEndDate.equals(
                                    row.interestEndDate()
                            )
            ) {
                errors.add(
                        new CsvValidationError(
                                row.rowNumber(),
                                "date_fin_interets",
                                "INCONSISTENT_INTEREST_END_DATE",
                                "Toutes les maturités de la police "
                                        + row.policyNumber()
                                        + " doivent utiliser la même date "
                                        + "de fin des intérêts."
                        )
                );
            }
        }
    }

    private boolean areStrictlyIdentical(
            ParsedMaturityRow first,
            ParsedMaturityRow second
    ) {
        return first.policyNumber()
                .equalsIgnoreCase(
                        second.policyNumber()
                )
                && first.maturityType()
                .equalsIgnoreCase(
                        second.maturityType()
                )
                && first.maturityRank()
                == second.maturityRank()
                && first.maturityDate()
                .equals(
                        second.maturityDate()
                )
                && first.maturityAmount()
                .compareTo(
                        second.maturityAmount()
                ) == 0
                && first.interestEndDate()
                .equals(
                        second.interestEndDate()
                );
    }

    private String buildBusinessKey(
            String policyNumber,
            int maturityRank
    ) {
        return normalizePolicyKey(
                policyNumber
        ) + "#" + maturityRank;
    }

    private String normalizePolicyKey(
            String policyNumber
    ) {
        return policyNumber
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeHeader(
            String header
    ) {
        if (header == null) {
            return "";
        }

        /*
         * Supprime un éventuel BOM UTF-8 présent
         * au début du premier en-tête.
         */
        return header
                .replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String normalizePolicyNumber(
            String policyNumber
    ) {
        if (policyNumber == null) {
            return "";
        }

        /*
         * Le numéro reste une chaîne afin de conserver
         * les éventuels zéros initiaux.
         */
        return policyNumber.trim();
    }

    private String normalizeMaturityType(
            String maturityType
    ) {
        if (maturityType == null) {
            return "";
        }

        return maturityType
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Crée un lecteur UTF-8 en supprimant, lorsqu'il existe,
     * le marqueur BOM placé au début du fichier.
     */
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

            /*
             * Si le premier caractère n'est pas un BOM,
             * il est replacé dans le flux.
             */
            if (
                    firstCharacter != -1 &&
                            firstCharacter != '\uFEFF'
            ) {
                reader.unread(
                        firstCharacter
                );
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