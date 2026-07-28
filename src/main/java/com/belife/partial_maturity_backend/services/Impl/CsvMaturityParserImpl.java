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
import java.util.*;
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
public class CsvMaturityParserImpl implements CsvMaturityParser {

    private static final List<String> REQUIRED_HEADERS =
        List.of("num_police", "type_maturite", "date_maturite", "montant_maturite");

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
            return new CsvValidationResult(totalRows, rows, errors);
        }

        try (
//            Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
            Reader reader = createUtf8Reader(file);

            CSVParser parser = CSV_FORMAT.parse(reader)
        ) {
            validateHeaders(parser.getHeaderNames(), errors);

            if (!errors.isEmpty()) {
                return new CsvValidationResult(totalRows ,rows, errors);
            }

            for (CSVRecord record : parser) {
                totalRows++;
                parseRecord(record, rows, errors);
            }

            validateInternalDuplicates(rows,errors);

            if (rows.isEmpty() && errors.isEmpty()) {
                errors.add(
                    new CsvValidationError(
                        0,
                        "",
                        "EMPTY_FILE",
                        "Le fichier ne contient aucune ligne de maturité."
                    )
                );
            }

            return new CsvValidationResult(totalRows, rows, errors);

        } catch (IOException exception) {
            throw new CsvFileProcessingException(
                "Le fichier CSV ne peut pas être lu.",
                exception
            );
        } catch (IllegalArgumentException exception) {
        log.error(
                "Structure CSV invalide pour le fichier '{}'. En-têtes ou colonnes non accessibles.",
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
     * Vérifie les propriétés générales du fichier avant son ouverture.
     */
    private void validateFileMetadata(MultipartFile file, List<CsvValidationError> errors) {
        if (file == null || file.isEmpty()) {
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

        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("");

        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
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
     * Exige exactement les quatre colonnes du contrat,
     * dans l'ordre convenu pour le MVP.
     */
    private void validateHeaders(List<String> actualHeaders, List<CsvValidationError> errors) {
        List<String> normalizedHeaders = actualHeaders
            .stream()
            .map(this::normalizeHeader)
            .toList();

        if (!REQUIRED_HEADERS.equals(normalizedHeaders)) {
            errors.add(
                new CsvValidationError(
                    1,
                    "",
                    "INVALID_HEADER",
                    "L'en-tête attendu est : " + String.join(";", REQUIRED_HEADERS)
                )
            );
        }
    }

    /**
     * Convertit une ligne CSV en objets Java fortement typés.
     *
     * La ligne est ajoutée à la liste uniquement si tous
     * ses champs sont valides.
     */
    private void parseRecord(CSVRecord record, List<ParsedMaturityRow> rows, List<CsvValidationError> errors) {
        int sourceRowNumber = Math.toIntExact(record.getRecordNumber() + 1);

        int errorsBeforeCurrentRow = errors.size();

        String policyNumber = normalizePolicyNumber(record.get("num_police"));

        String maturityType = normalizeMaturityType(record.get("type_maturite"));

        String maturityDateValue = record.get("date_maturite").trim();

        String maturityAmountValue = record.get("montant_maturite").trim();

        validatePolicyNumber(sourceRowNumber, policyNumber, errors);

        Integer maturityRank = parseMaturityRank(sourceRowNumber, maturityType, errors);

        LocalDate maturityDate = parseMaturityDate(sourceRowNumber, maturityDateValue, errors);

        BigDecimal maturityAmount = parseMaturityAmount(sourceRowNumber, maturityAmountValue, errors);

        boolean currentRowIsValid = errors.size() == errorsBeforeCurrentRow;

        if (currentRowIsValid) {
            rows.add(
                new ParsedMaturityRow(
                    sourceRowNumber,
                    policyNumber,
                    maturityType,
                    maturityRank,
                    maturityDate,
                    maturityAmount
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

    private Integer parseMaturityRank(int rowNumber, String maturityType, List<CsvValidationError> errors) {
        Matcher matcher = MATURITY_TYPE_PATTERN.matcher(maturityType);

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
            return Integer.parseInt(matcher.group(1));
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

    private LocalDate parseMaturityDate(int rowNumber, String value, List<CsvValidationError> errors) {
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
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            errors.add(
                new CsvValidationError(
                        rowNumber,
                    "date_maturite",
                    "INVALID_MATURITY_DATE",
                    "La date doit respecter le format yyyy-MM-dd."
                )
            );

            return null;
        }
    }

    private BigDecimal parseMaturityAmount(int rowNumber, String value, List<CsvValidationError> errors) {
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
            BigDecimal amount = new BigDecimal(value);

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

        } catch (NumberFormatException exception) {
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
     * Contrôle les répétitions à l'intérieur du fichier courant.
     *
     * Une répétition strictement identique est tolérée et sera
     * dédupliquée lors de l'importation.
     *
     * Deux lignes partageant la même police et le même rang,
     * mais avec des valeurs différentes, sont contradictoires.
     */
    private void validateInternalDuplicates(List<ParsedMaturityRow> rows, List<CsvValidationError> errors) {
        Map<String, ParsedMaturityRow> firstRowsByKey = new HashMap<>();

        for (ParsedMaturityRow row : rows) {
            String businessKey = buildBusinessKey(row.policyNumber(), row.maturityRank());

            ParsedMaturityRow previousRow = firstRowsByKey.putIfAbsent(businessKey, row);

            if (previousRow != null && !areStrictlyIdentical(previousRow, row)
            ) {
                errors.add(
                    new CsvValidationError(
                        row.rowNumber(),
                        "type_maturite",
                        "CONTRADICTORY_MATURITY",
                        "La police "
                            + row.policyNumber()
                            + " contient plusieurs valeurs différentes pour la maturité de rang "
                            + row.maturityRank()
                            + "."
                    )
                );
            }
        }
    }

    private boolean areStrictlyIdentical(ParsedMaturityRow first, ParsedMaturityRow second) {
        return first.policyNumber()
                .equalsIgnoreCase(second.policyNumber())
                && first.maturityType()
                .equalsIgnoreCase(second.maturityType())
                && first.maturityRank() == second.maturityRank()
                && first.maturityDate()
                .equals(second.maturityDate())
                && first.maturityAmount()
                .compareTo(second.maturityAmount()) == 0;
    }

    private String buildBusinessKey(String policyNumber, int maturityRank) {
        return policyNumber.toUpperCase(Locale.ROOT)+ "#"+ maturityRank;
    }

    private String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }

        return header
                /*
                 * Supprime un éventuel BOM UTF-8 présent
                 * au début du premier en-tête.
                 */
                .replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePolicyNumber(String policyNumber) {
        if (policyNumber == null) {
            return "";
        }

        /*
         * Le numéro reste une chaîne afin de conserver
         * les éventuels zéros initiaux.
         */
        return policyNumber.trim();
    }

    private String normalizeMaturityType(String maturityType) {
        if (maturityType == null) {
            return "";
        }

        return maturityType.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Crée un lecteur UTF-8 en supprimant, lorsqu'il existe,
     * le marqueur BOM placé au début du fichier.
     *
     * <p>Certains logiciels, notamment Excel et certains outils
     * d'exportation, ajoutent les octets EF BB BF au début d'un
     * fichier UTF-8. Après décodage, ces octets deviennent le
     * caractère Unicode U+FEFF.</p>
     *
     * <p>Sans cette suppression, Apache Commons CSV considère
     * le premier en-tête comme "\uFEFFnum_police" au lieu de
     * "num_police", ce qui empêche ensuite l'accès à la colonne
     * par son nom.</p>
     *
     * @param file fichier CSV reçu
     * @return lecteur positionné après le BOM éventuel
     */
    private Reader createUtf8Reader(MultipartFile file) {
        try {
            PushbackReader reader =
                    new PushbackReader(
                            new InputStreamReader(
                                    file.getInputStream(),
                                    StandardCharsets.UTF_8
                            ),
                            1
                    );

            int firstCharacter = reader.read();

            /*
             * Si le premier caractère n'est pas un BOM,
             * il est replacé dans le flux pour ne perdre
             * aucun caractère du fichier.
             */
            if (firstCharacter != -1 && firstCharacter != '\uFEFF') {
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