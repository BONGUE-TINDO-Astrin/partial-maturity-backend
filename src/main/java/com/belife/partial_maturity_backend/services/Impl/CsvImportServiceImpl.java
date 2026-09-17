package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.exceptions.CsvFileProcessingException;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestre l'importation complète d'un fichier CSV de maturités.
 *
 * <p>Cette classe ne démarre volontairement aucune transaction.
 * Les écritures atomiques sont déléguées au service de
 * persistance dédié.</p>
 *
 * <p>Les étapes sont :</p>
 *
 * <ul>
 *     <li>calcul de l'empreinte du fichier ;</li>
 *     <li>parsing et validation syntaxique ;</li>
 *     <li>chargement des maturités existantes ;</li>
 *     <li>contrôles métier ;</li>
 *     <li>import transactionnel ou historisation du rejet.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CsvImportServiceImpl implements CsvImportService {

    private final CsvMaturityParser csvMaturityParser;

    private final PolicyMaturityRepository policyMaturityRepository;

    private final PaymentRepository paymentRepository;

    private final CsvImportPersistenceService persistenceService;

    @Override
    public CsvImportResponse importFile(MultipartFile file, String currentUsername) {
        String fileName = resolveFileName(file);

        long fileSize = resolveFileSize(file);

        String fileSha256 = calculateSha256(file);

        CsvValidationResult parsingResult = csvMaturityParser.parse(file);

        int parsedRowsCount = parsingResult.totalRows();

        if (!parsingResult.isValid()) {
            return persistenceService
                    .saveRejectedBatch(
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
            return persistenceService
                    .saveRejectedBatch(
                            fileName,
                            fileSha256,
                            fileSize,
                            parsingResult.totalRows(),
                            analysis.errors(),
                            currentUsername
                    );
        }

        return persistenceService
                .saveImportedBatch(
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
     * Compare les lignes valides du fichier aux maturités
     * déjà enregistrées.
     *
     * <p>La date de fin des intérêts est une propriété stable
     * de la police. Une valeur différente de celle déjà présente
     * en base entraîne le rejet complet du fichier.</p>
     */
    private MaturityImportAnalysis
    analyzeAgainstDatabase(List<ParsedMaturityRow> parsedRows) {
        List<CsvValidationError> errors = new ArrayList<>();

        /*
         * Une répétition strictement identique dans le même
         * fichier est conservée une seule fois pour l'analyse
         * et pour l'insertion.
         */
        InternalDeduplicationResult deduplicationResult = deduplicateInternalRows(parsedRows);

        List<ParsedMaturityRow> uniqueIncomingRows = deduplicationResult.uniqueRows();

        int existingRowsCount = deduplicationResult.duplicateRowsCount();

        Set<String> policyNumbers =
                uniqueIncomingRows
                        .stream()
                        .map(ParsedMaturityRow::policyNumber
                        ).collect(Collectors.toSet());

        List<PolicyMaturityEntity>
                existingMaturities =
                policyNumbers.isEmpty()
                        ? List.of()
                        : policyMaturityRepository
                          .findAllByPolicyNumberIn(
                                  policyNumbers
                          );

        List<PaymentEntity> paidPayments =
                policyNumbers.isEmpty()
                        ? List.of()
                        : paymentRepository
                          .findAllByPolicyNumbersAndStatus(
                                  policyNumbers,
                                  PaymentStatus.PAID
                          );

        Map<String, LocalDate>
                lastPaidPaymentDateByPolicy =
                buildLastPaidPaymentDateIndex(
                        paidPayments
                );

        Map<MaturityBusinessKey,
                PolicyMaturityEntity>
                existingByKey =
                existingMaturities
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        entity ->
                                                MaturityBusinessKey
                                                        .of(
                                                                entity.getPolicyNumber(),
                                                                entity.getMaturityRank()
                                                        ),
                                        Function.identity()
                                )
                        );

        Map<String, LocalDate>
                existingInterestEndDateByPolicy =
                buildExistingInterestEndDateIndex(
                        existingMaturities
                );

        List<ParsedMaturityRow> newRows = new ArrayList<>();

        for (ParsedMaturityRow incomingRow : uniqueIncomingRows) {
            String normalizedPolicyNumber = normalizePolicyNumber(incomingRow.policyNumber());

            LocalDate existingInterestEndDate = existingInterestEndDateByPolicy.get(normalizedPolicyNumber);

            /*
             * La date de fin des intérêts est fixée lors
             * du premier import de la police.
             */
            if (
                    existingInterestEndDate != null
                            && !existingInterestEndDate.equals(
                            incomingRow.interestEndDate()
                    )
            ) {
                errors.add(inconsistentInterestEndDateError(incomingRow, existingInterestEndDate));

                continue;
            }

            MaturityBusinessKey key =
                    MaturityBusinessKey.of(
                            incomingRow.policyNumber(),
                            incomingRow.maturityRank()
                    );

            PolicyMaturityEntity existing = existingByKey.get(key);

            /*
             * Une maturité déjà enregistrée à l'identique
             * reste une ligne existante, même si un paiement
             * a été effectué ultérieurement.
             */
            if (
                    existing != null
                            && isIdentical(
                            incomingRow,
                            existing
                    )
            ) {
                existingRowsCount++;
                continue;
            }

            /*
             * Une même police et un même rang avec des données
             * différentes constituent une contradiction.
             */
            if (existing != null) {
                errors.add(
                        new CsvValidationError(
                                incomingRow.rowNumber(),
                                "type_maturite",
                                "CONTRADICTORY_MATURITY",
                                "La police "
                                        + incomingRow.policyNumber()
                                        + " possède déjà une maturité "
                                        + "de rang "
                                        + incomingRow.maturityRank()
                                        + " avec une date, un type, "
                                        + "un montant ou une date de fin "
                                        + "des intérêts différente."
                        )
                );

                continue;
            }

            /*
             * À ce stade, la ligne représente réellement
             * une nouvelle maturité.
             *
             * Elle doit être strictement postérieure au dernier
             * paiement PAID de la police.
             */
            LocalDate lastPaidPaymentDate =
                    lastPaidPaymentDateByPolicy
                            .get(normalizedPolicyNumber);

            if (
                    lastPaidPaymentDate != null
                            && !incomingRow.maturityDate()
                            .isAfter(lastPaidPaymentDate)
            ) {
                errors.add(
                        maturityNotAfterLastPaymentError(
                                incomingRow,
                                lastPaidPaymentDate
                        )
                );

                continue;
            }

            newRows.add(incomingRow);
        }

        if (errors.isEmpty()) {
            validateContinuityAndDates(
                    uniqueIncomingRows,
                    existingMaturities,
                    errors
            );
        }

        return new MaturityImportAnalysis(
                newRows,
                existingRowsCount,
                errors
        );
    }

    /**
     * Construit la date de fin des intérêts connue
     * pour chaque police déjà enregistrée.
     *
     * <p>La validation d'import garantit normalement qu'une
     * police ne possède qu'une seule date. Une vérification
     * défensive est néanmoins conservée afin de détecter
     * d'éventuelles données historiques incohérentes.</p>
     */
    private Map<String, LocalDate>
    buildExistingInterestEndDateIndex(
            Collection<PolicyMaturityEntity>
                    existingMaturities
    ) {
        Map<String, LocalDate>
                interestEndDateByPolicy =
                new HashMap<>();

        for (PolicyMaturityEntity maturity : existingMaturities) {
            String normalizedPolicyNumber = normalizePolicyNumber(maturity.getPolicyNumber());

            LocalDate previousDate =
                    interestEndDateByPolicy
                            .putIfAbsent(
                                    normalizedPolicyNumber,
                                    maturity.getInterestEndDate()
                            );

            if (
                    previousDate != null
                            && !previousDate.equals(
                            maturity.getInterestEndDate()
                    )
            ) {
                throw new IllegalStateException(
                        "La police "
                                + maturity
                                .getPolicyNumber()
                                + " possède plusieurs dates "
                                + "de fin des intérêts en base."
                );
            }
        }

        return interestEndDateByPolicy;
    }

    /**
     * Retourne la date du dernier paiement PAID
     * pour chaque police concernée par l'import.
     *
     * <p>Les paiements annulés ne sont pas fournis à cette
     * méthode et ne bloquent donc pas les nouvelles
     * maturités.</p>
     */
    private Map<String, LocalDate>
    buildLastPaidPaymentDateIndex(
            Collection<PaymentEntity> paidPayments
    ) {
        Map<String, LocalDate>
                lastPaymentDateByPolicy =
                new HashMap<>();

        for (PaymentEntity payment : paidPayments) {
            String normalizedPolicyNumber =
                    normalizePolicyNumber(
                            payment.getPolicyNumber()
                    );

            lastPaymentDateByPolicy.merge(
                    normalizedPolicyNumber,
                    payment.getPaymentDate(),
                    (currentLatestDate, candidateDate) ->
                            candidateDate.isAfter(
                                    currentLatestDate
                            )
                                    ? candidateDate
                                    : currentLatestDate
            );
        }

        return lastPaymentDateByPolicy;
    }

    /**
     * Construit l'erreur retournée lorsqu'une nouvelle
     * maturité serait insérée avant ou le jour d'un
     * paiement encore valide.
     */
    private CsvValidationError
    maturityNotAfterLastPaymentError(ParsedMaturityRow incomingRow, LocalDate lastPaidPaymentDate) {
        return new CsvValidationError(
                incomingRow.rowNumber(),
                "date_maturite",
                "MATURITY_NOT_AFTER_LAST_PAYMENT",
                "La maturité de la police "
                        + incomingRow.policyNumber()
                        + " doit être strictement postérieure "
                        + "au dernier paiement valide du "
                        + lastPaidPaymentDate
                        + "."
        );
    }


    private CsvValidationError
    inconsistentInterestEndDateError(ParsedMaturityRow incomingRow, LocalDate existingInterestEndDate) {
        return new CsvValidationError(
                incomingRow.rowNumber(),
                "date_fin_interets",
                "INCONSISTENT_INTEREST_END_DATE",
                "La police "
                        + incomingRow.policyNumber()
                        + " possède déjà la date de fin "
                        + "des intérêts "
                        + existingInterestEndDate
                        + "."
        );
    }

    /**
     * Vérifie la continuité des rangs et l'ordre
     * chronologique des maturités.
     */
    private void validateContinuityAndDates(
            List<ParsedMaturityRow> incomingRows,
            List<PolicyMaturityEntity> existingMaturities,
            List<CsvValidationError> errors
    ) {
        Map<String,
                List<ChronologicalMaturity>>
                maturitiesByPolicy = new HashMap<>();

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
            String normalizedPolicy =
                    normalizePolicyNumber(
                            incoming.policyNumber()
                    );

            List<ChronologicalMaturity>
                    maturities =
                    maturitiesByPolicy
                            .computeIfAbsent(
                                    normalizedPolicy,
                                    ignored ->
                                            new ArrayList<>()
                            );

            boolean rankAlreadyAdded =
                    maturities
                            .stream()
                            .anyMatch(
                                    maturity ->
                                            maturity.rank()
                                                    == incoming
                                                    .maturityRank()
                            );

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

        for (
                Map.Entry<String,
                        List<ChronologicalMaturity>>
                        entry
                : maturitiesByPolicy.entrySet()
        ) {
            validatePolicySequence(
                    entry.getKey(),
                    entry.getValue(),
                    errors
            );
        }
    }

    private void validatePolicySequence(
            String normalizedPolicyNumber,
            List<ChronologicalMaturity>
                    maturities,
            List<CsvValidationError> errors
    ) {
        List<ChronologicalMaturity> ordered =
                maturities
                        .stream()
                        .sorted(
                                Comparator.comparingInt(
                                        ChronologicalMaturity
                                                ::rank
                                )
                        )
                        .toList();

        if (ordered.isEmpty()) {
            return;
        }

        int maximumRank =
                ordered.getLast().rank();

        Map<Integer, ChronologicalMaturity>
                byRank =
                ordered
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        ChronologicalMaturity
                                                ::rank,
                                        Function.identity()
                                )
                        );

        for (
                int expectedRank = 1;
                expectedRank <= maximumRank;
                expectedRank++
        ) {
            if (
                    !byRank.containsKey(
                            expectedRank
                    )
            ) {
                ChronologicalMaturity
                        nextKnownMaturity =
                        findFirstAfterRank(
                                ordered,
                                expectedRank
                        );

                errors.add(
                        new CsvValidationError(
                                nextKnownMaturity != null
                                        ? nextKnownMaturity
                                          .sourceRowNumber()
                                        : 0,
                                "type_maturite",
                                "MISSING_PREVIOUS_MATURITY",
                                "La police "
                                        + normalizedPolicyNumber
                                        + " ne possède pas "
                                        + "la maturité de rang "
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
        for (
                int index = 1;
                index < ordered.size();
                index++
        ) {
            ChronologicalMaturity previous =
                    ordered.get(index - 1);

            ChronologicalMaturity current =
                    ordered.get(index);

            if (
                    !current.date()
                            .isAfter(
                                    previous.date()
                            )
            ) {
                errors.add(
                        new CsvValidationError(
                                current
                                        .sourceRowNumber(),
                                "date_maturite",
                                "INVALID_MATURITY_DATE_SEQUENCE",
                                "Pour la police "
                                        + normalizedPolicyNumber
                                        + ", la maturité de rang "
                                        + current.rank()
                                        + " doit avoir une date "
                                        + "postérieure à la maturité "
                                        + "de rang "
                                        + previous.rank()
                                        + "."
                        )
                );
            }
        }
    }

    private ChronologicalMaturity
    findFirstAfterRank(
            List<ChronologicalMaturity>
                    maturities,
            int missingRank
    ) {
        return maturities
                .stream()
                .filter(
                        maturity ->
                                maturity.rank()
                                        > missingRank
                )
                .findFirst()
                .orElse(null);
    }

    /**
     * Élimine les répétitions strictement identiques
     * du fichier.
     *
     * <p>Les contradictions internes ont déjà été détectées
     * par le parseur. Deux lignes partageant la même clé à
     * cette étape sont donc des répétitions tolérées.</p>
     */
    private InternalDeduplicationResult
    deduplicateInternalRows(
            List<ParsedMaturityRow> rows
    ) {
        Map<MaturityBusinessKey,
                ParsedMaturityRow>
                uniqueByKey =
                new LinkedHashMap<>();

        int duplicateRowsCount = 0;

        for (
                ParsedMaturityRow row
                : rows
        ) {
            MaturityBusinessKey key =
                    MaturityBusinessKey.of(
                            row.policyNumber(),
                            row.maturityRank()
                    );

            ParsedMaturityRow previous =
                    uniqueByKey.putIfAbsent(
                            key,
                            row
                    );

            if (previous != null) {
                duplicateRowsCount++;
            }
        }

        return new InternalDeduplicationResult(
                List.copyOf(
                        uniqueByKey.values()
                ),
                duplicateRowsCount
        );
    }

    /**
     * Vérifie qu'une maturité reçue est strictement
     * identique à celle déjà enregistrée.
     */
    private boolean isIdentical(
            ParsedMaturityRow incoming,
            PolicyMaturityEntity existing
    ) {
        return incoming
                .policyNumber()
                .equalsIgnoreCase(
                        existing
                                .getPolicyNumber()
                )
                && incoming
                .maturityType()
                .equalsIgnoreCase(
                        existing
                                .getMaturityType()
                )
                && incoming
                .maturityRank()
                == existing
                .getMaturityRank()
                && incoming
                .maturityDate()
                .equals(
                        existing
                                .getMaturityDate()
                )
                && incoming
                .maturityAmount()
                .compareTo(
                        existing
                                .getMaturityAmount()
                ) == 0
                && incoming
                .interestEndDate()
                .equals(
                        existing
                                .getInterestEndDate()
                );
    }

    /**
     * Calcule une empreinte SHA-256 hexadécimale
     * du fichier.
     */
    private String calculateSha256(
            MultipartFile file
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            file.getBytes()
                    );

            return HexFormat
                    .of()
                    .formatHex(hash);
        } catch (
                IOException
                | NoSuchAlgorithmException
                        exception
        ) {
            throw new CsvFileProcessingException(
                    "Impossible de calculer "
                            + "l'empreinte du fichier.",
                    exception
            );
        }
    }

    private String resolveFileName(
            MultipartFile file
    ) {
        return Optional
                .ofNullable(
                        file.getOriginalFilename()
                )
                .filter(
                        name ->
                                !name.isBlank()
                )
                .map(
                        this::removePathInformation
                )
                .orElse(
                        "fichier-sans-nom.csv"
                );
    }

    /**
     * Empêche qu'un nom transmis par le navigateur
     * contienne un chemin local complet.
     */
    private String removePathInformation(
            String fileName
    ) {
        String normalized =
                fileName.replace(
                        '\\',
                        '/'
                );

        int lastSeparator =
                normalized.lastIndexOf('/');

        if (lastSeparator >= 0) {
            return normalized.substring(
                    lastSeparator + 1
            );
        }

        return normalized;
    }

    private long resolveFileSize(
            MultipartFile file
    ) {
        return file == null
                ? 0
                : file.getSize();
    }

    private String normalizePolicyNumber(
            String policyNumber
    ) {
        return policyNumber
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Représentation minimale utilisée uniquement
     * lors du contrôle de continuité et de chronologie.
     */
    private record ChronologicalMaturity(
            int rank,
            LocalDate date,
            int sourceRowNumber
    ) {
    }

    private record InternalDeduplicationResult(
            List<ParsedMaturityRow>
            uniqueRows,
            int duplicateRowsCount
    ) {
    }
}