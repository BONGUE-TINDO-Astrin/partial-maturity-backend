package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.exceptions.CsvFileProcessingException;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.CsvImportPersistenceService;
import com.belife.partial_maturity_backend.services.CsvImportService;
import com.belife.partial_maturity_backend.services.CsvMaturityParser;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;
import com.belife.partial_maturity_backend.services.models.CsvValidationResult;
import com.belife.partial_maturity_backend.services.models.MaturityBusinessKey;
import com.belife.partial_maturity_backend.services.models.MaturityImportAnalysis;
import com.belife.partial_maturity_backend.services.models.ParsedMaturityRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestre l'importation complète d'un fichier CSV de maturités.
 *
 * <p>Cette classe ne démarre volontairement aucune transaction.</p>
 *
 * <p>Les étapes sont :</p>
 *
 * <ol>
 *     <li>calcul de l'empreinte du fichier ;</li>
 *     <li>parsing et validation syntaxique ;</li>
 *     <li>chargement des maturités existantes ;</li>
 *     <li>contrôles métier ;</li>
 *     <li>import transactionnel ou historisation du rejet.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CsvImportServiceImpl implements CsvImportService {

    private final CsvMaturityParser csvMaturityParser;
    private final PolicyMaturityRepository policyMaturityRepository;
    private final CsvImportPersistenceService persistenceService;

    @Override
    public CsvImportResponse importFile(MultipartFile file, String currentUsername) {
        String fileName = resolveFileName(file);
        long fileSize = resolveFileSize(file);
        String fileSha256 = calculateSha256(file);

        CsvValidationResult parsingResult = csvMaturityParser.parse(file);

        int parsedRowsCount = parsingResult.totalRows();

        if (!parsingResult.isValid()) {
            return persistenceService.saveRejectedBatch(
                fileName,
                fileSha256,
                fileSize,
                parsedRowsCount,
                parsingResult.errors(),
                currentUsername
            );
        }

        MaturityImportAnalysis analysis = analyzeAgainstDatabase(parsingResult.rows());

        if (!analysis.isValid()) {
            return persistenceService.saveRejectedBatch(
                fileName,
                fileSha256,
                fileSize,
                parsingResult.totalRows(),
                analysis.errors(),
                currentUsername
            );
        }

        return persistenceService.saveImportedBatch(
            fileName,
            fileSha256,
            fileSize,
            parsingResult.totalRows(),
            analysis.existingRowsCount(),
            analysis.newRows(),
            currentUsername
        );
    }

    /**
     * Compare les lignes du fichier aux maturités déjà enregistrées.
     */
    private MaturityImportAnalysis analyzeAgainstDatabase(List<ParsedMaturityRow> parsedRows) {
        List<CsvValidationError> errors = new ArrayList<>();

        /*
         * Une répétition strictement identique dans le même fichier
         * est conservée une seule fois pour l'analyse et l'insertion.
         */
        InternalDeduplicationResult deduplicationResult = deduplicateInternalRows(parsedRows);

        List<ParsedMaturityRow> uniqueIncomingRows = deduplicationResult.uniqueRows();

        int existingRowsCount = deduplicationResult.duplicateRowsCount();

        Set<String> policyNumbers =
            uniqueIncomingRows.stream()
                .map(ParsedMaturityRow::policyNumber)
                .collect(Collectors.toSet());

        List<PolicyMaturityEntity> existingMaturities =
            policyNumbers.isEmpty()
                ? List.of()
                : policyMaturityRepository.findAllByPolicyNumberIn(policyNumbers);

        Map<MaturityBusinessKey, PolicyMaturityEntity>
            existingByKey =
            existingMaturities.stream()
                .collect(
                    Collectors.toMap(
                        entity ->
                            MaturityBusinessKey.of(
                                entity.getPolicyNumber(),
                                entity.getMaturityRank()
                            ),
                        Function.identity()
                    )
                );

        List<ParsedMaturityRow> newRows = new ArrayList<>();

        for (ParsedMaturityRow incomingRow : uniqueIncomingRows) {

            MaturityBusinessKey key =
                MaturityBusinessKey.of(
                    incomingRow.policyNumber(),
                    incomingRow.maturityRank()
                );

            PolicyMaturityEntity existing = existingByKey.get(key);

            if (existing == null) {
                newRows.add(incomingRow);
                continue;
            }

            if (isIdentical(incomingRow, existing)
            ) {
                existingRowsCount++;
                continue;
            }

            errors.add(
                new CsvValidationError(
                    incomingRow.rowNumber(),
                    "type_maturite",
                    "CONTRADICTORY_MATURITY",
                    "La police "
                            + incomingRow.policyNumber()
                            + " possède déjà une maturité de rang "
                            + incomingRow.maturityRank()
                            + " avec une date, un type ou un montant différent."
                )
            );
        }

        if (errors.isEmpty()) {
            validateContinuityAndDates(
                uniqueIncomingRows,
                existingMaturities,
                errors
            );
        }

        return new MaturityImportAnalysis(newRows, existingRowsCount, errors);
    }

    /**
     * Vérifie que les rangs précédents existent.
     *
     * Exemple :
     * MATURITE_3 ne peut être acceptée que si les rangs
     * 1 et 2 sont présents dans SQL Server ou dans le fichier.
     */
    private void validateContinuityAndDates(
            List<ParsedMaturityRow> incomingRows,
            List<PolicyMaturityEntity> existingMaturities,
            List<CsvValidationError> errors
    ) {
        Map<String, List<ChronologicalMaturity>> maturitiesByPolicy = new HashMap<>();

        for (PolicyMaturityEntity existing : existingMaturities) {
            String normalizedPolicy = normalizePolicyNumber(existing.getPolicyNumber());

            maturitiesByPolicy
                .computeIfAbsent(
                    normalizedPolicy,
                    ignored -> new ArrayList<>()
                )
                .add(
                    new ChronologicalMaturity(
                        existing.getMaturityRank(),
                        existing.getMaturityDate(),
                        0
                    )
                );
        }

        for (ParsedMaturityRow incoming : incomingRows) {
            String normalizedPolicy = normalizePolicyNumber(incoming.policyNumber());

            List<ChronologicalMaturity> maturities = maturitiesByPolicy.computeIfAbsent(
                    normalizedPolicy,
                    ignored -> new ArrayList<>()
                );

            boolean rankAlreadyAdded = maturities.stream()
                        .anyMatch(maturity -> maturity.rank() == incoming.maturityRank());

            if (!rankAlreadyAdded) {
                maturities.add(
                    new ChronologicalMaturity(
                        incoming.maturityRank(),
                        incoming.maturityDate(),
                        incoming.rowNumber()
                    )
                );
            }
        }

        for (Map.Entry<String, List<ChronologicalMaturity>> entry : maturitiesByPolicy.entrySet()) {
            validatePolicySequence(entry.getKey(), entry.getValue(), errors);
        }
    }

    private void validatePolicySequence(
            String normalizedPolicyNumber,
            List<ChronologicalMaturity> maturities,
            List<CsvValidationError> errors
    ) {
        List<ChronologicalMaturity> ordered = maturities.stream()
                        .sorted(Comparator.comparingInt(ChronologicalMaturity::rank))
                        .toList();

        if (ordered.isEmpty()) {
            return;
        }

        int maximumRank = ordered.getLast().rank();

        Map<Integer, ChronologicalMaturity> byRank = ordered.stream()
                        .collect(Collectors.toMap(ChronologicalMaturity::rank,Function.identity()));

        for (int expectedRank = 1; expectedRank <= maximumRank; expectedRank++) {

            if (!byRank.containsKey(expectedRank)) {
                ChronologicalMaturity nextKnownMaturity = findFirstAfterRank(ordered, expectedRank);

                errors.add(
                        new CsvValidationError(
                                nextKnownMaturity != null
                                        ? nextKnownMaturity.sourceRowNumber()
                                        : 0,
                                "type_maturite",
                                "MISSING_PREVIOUS_MATURITY",
                                "La police "
                                        + normalizedPolicyNumber
                                        + " ne possède pas la maturité de rang "
                                        + expectedRank
                                        + "."
                        )
                );
            }
        }

        /*
         * Une maturité de rang supérieur doit avoir une date
         * strictement postérieure à la maturité précédente.
         */
        for (int index = 1; index < ordered.size(); index++) {

            ChronologicalMaturity previous = ordered.get(index - 1);

            ChronologicalMaturity current = ordered.get(index);

            if (!current.date().isAfter(previous.date())
            ) {
                errors.add(
                        new CsvValidationError(
                                current.sourceRowNumber(),
                                "date_maturite",
                                "INVALID_MATURITY_DATE_SEQUENCE",
                                "Pour la police "
                                        + normalizedPolicyNumber
                                        + ", la maturité de rang "
                                        + current.rank()
                                        + " doit avoir une date postérieure à la maturité de rang "
                                        + previous.rank()
                                        + "."
                        )
                );
            }
        }
    }

    private ChronologicalMaturity findFirstAfterRank(List<ChronologicalMaturity> maturities, int missingRank) {
        return maturities.stream()
                .filter(maturity -> maturity.rank() > missingRank)
                .findFirst()
                .orElse(null);
    }

    /**
     * Élimine les répétitions strictement identiques du fichier.
     */
    private InternalDeduplicationResult deduplicateInternalRows(List<ParsedMaturityRow> rows) {
        Map<MaturityBusinessKey, ParsedMaturityRow> uniqueByKey = new LinkedHashMap<>();

        int duplicateRowsCount = 0;

        for (ParsedMaturityRow row : rows) {
            MaturityBusinessKey key =
                    MaturityBusinessKey.of(
                            row.policyNumber(),
                            row.maturityRank()
                    );

            ParsedMaturityRow previous = uniqueByKey.putIfAbsent(key, row);

            if (previous != null) {
                duplicateRowsCount++;
            }
        }

        return new InternalDeduplicationResult(
                List.copyOf(uniqueByKey.values()),
                duplicateRowsCount
        );
    }

    private boolean isIdentical(ParsedMaturityRow incoming, PolicyMaturityEntity existing) {
        return incoming.policyNumber()
                .equalsIgnoreCase(existing.getPolicyNumber())
                && incoming.maturityType()
                .equalsIgnoreCase(existing.getMaturityType())
                && incoming.maturityRank() == existing.getMaturityRank()
                && incoming.maturityDate()
                .equals(existing.getMaturityDate())
                && incoming.maturityAmount()
                .compareTo(existing.getMaturityAmount()) == 0;
    }

    /**
     * Calcule une empreinte SHA-256 hexadécimale du fichier.
     */
    private String calculateSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(file.getBytes());

            return HexFormat.of().formatHex(hash);

        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new CsvFileProcessingException(
                    "Impossible de calculer l'empreinte du fichier.",
                    exception
            );
        }
    }

    private String resolveFileName(MultipartFile file) {
        return Optional.ofNullable(file.getOriginalFilename())
                .filter(name -> !name.isBlank())
                .map(this::removePathInformation)
                .orElse("fichier-sans-nom.csv");
    }

    /**
     * Empêche qu'un nom transmis par le navigateur contienne
     * un chemin local complet.
     */
    private String removePathInformation(String fileName) {
        String normalized = fileName.replace('\\', '/');

        int lastSeparator = normalized.lastIndexOf('/');

        if (lastSeparator >= 0) {
            return normalized.substring(lastSeparator + 1);
        }

        return normalized;
    }

    private long resolveFileSize(MultipartFile file) {
        return file == null ? 0 : file.getSize();
    }

    private String normalizePolicyNumber(String policyNumber) {
        return policyNumber.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Représentation minimale utilisée uniquement lors
     * du contrôle de continuité et de chronologie.
     */
    private record ChronologicalMaturity(
            int rank,
            LocalDate date,
            int sourceRowNumber
    ) {
    }

    private record InternalDeduplicationResult(
            List<ParsedMaturityRow> uniqueRows,
            int duplicateRowsCount
    ) {
    }
}