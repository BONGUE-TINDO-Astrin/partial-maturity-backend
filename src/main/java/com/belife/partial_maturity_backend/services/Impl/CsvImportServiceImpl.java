package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.exceptions.CsvFileProcessingException;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.BusinessDateProvider;
import com.belife.partial_maturity_backend.services.CsvImportPersistenceService;
import com.belife.partial_maturity_backend.services.CsvImportService;
import com.belife.partial_maturity_backend.services.CsvMaturityParser;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;
import com.belife.partial_maturity_backend.services.models.CsvValidationResult;
import com.belife.partial_maturity_backend.services.models.MaturityImportAnalysis;
import com.belife.partial_maturity_backend.services.models.MaturityImportRow;
import com.belife.partial_maturity_backend.services.models.ParsedMaturityRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestre l'importation d'un fichier CSV de maturités.
 */
@Service
@RequiredArgsConstructor
public class CsvImportServiceImpl implements CsvImportService {

    private static final String UNKNOWN_CLIENT_NAME =  "CLIENT NON RENSEIGNE";

    private final CsvMaturityParser csvMaturityParser;

    private final PolicyMaturityRepository policyMaturityRepository;

    private final PaymentRepository paymentRepository;

    private final CsvImportPersistenceService persistenceService;

    private final BusinessDateProvider businessDateProvider;

    @Override
    public CsvImportResponse importFile(
            MultipartFile file,
            String currentUsername
    ) {
        String fileName = resolveFileName(file);
        long fileSize = resolveFileSize(file);
        String fileSha256 = calculateSha256(file);

        CsvValidationResult parsingResult =
                csvMaturityParser.parse(file);

        if (!parsingResult.isValid()) {
            return persistenceService
                    .saveRejectedBatch(
                            fileName,
                            fileSha256,
                            fileSize,
                            parsingResult.totalRows(),
                            parsingResult.errors(),
                            currentUsername
                    );
        }

        LocalDate maturityDate =
                businessDateProvider.currentDate();

        MaturityImportAnalysis analysis =
                analyzeAgainstDatabase(
                        parsingResult.rows(),
                        maturityDate
                );

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
                        analysis.newRows(),
                        currentUsername
                );
    }

    private MaturityImportAnalysis
    analyzeAgainstDatabase(
            List<ParsedMaturityRow> parsedRows,
            LocalDate maturityDate
    ) {
        List<CsvValidationError> errors =
                new ArrayList<>();

        Set<String> policyNumbers =
                parsedRows.stream()
                        .map(
                                ParsedMaturityRow
                                        ::policyNumber
                        )
                        .collect(Collectors.toSet());

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

        Map<String, ExistingPolicyState>
                stateByPolicy =
                buildExistingPolicyStateIndex(
                        existingMaturities
                );

        Map<String, LocalDate>
                lastPaymentDateByPolicy =
                buildLastPaymentDateIndex(
                        paidPayments
                );

        Map<String, Integer> nextRankByPolicy =
                buildNextRankIndex(
                        stateByPolicy
                );

        List<MaturityImportRow> newRows =
                new ArrayList<>();

        for (ParsedMaturityRow row : parsedRows) {
            String policyKey =
                    normalizePolicyKey(
                            row.policyNumber()
                    );

            ExistingPolicyState existingState =
                    stateByPolicy.get(policyKey);

            validateClientName(
                    row,
                    existingState,
                    errors
            );

            validateInterestEndDate(
                    row,
                    existingState,
                    errors
            );

            validateMaturityDate(
                    row,
                    maturityDate,
                    existingState,
                    errors
            );

            validateAgainstLastPayment(
                    row,
                    maturityDate,
                    lastPaymentDateByPolicy.get(
                            policyKey
                    ),
                    errors
            );

            int assignedRank =
                    nextRankByPolicy.getOrDefault(
                            policyKey,
                            1
                    );

            newRows.add(
                    MaturityImportRow.from(
                            row,
                            assignedRank,
                            maturityDate
                    )
            );

            nextRankByPolicy.put(
                    policyKey,
                    Math.addExact(
                            assignedRank,
                            1
                    )
            );
        }

        return new MaturityImportAnalysis(
                newRows,
                errors
        );
    }

    private Map<String, ExistingPolicyState>
    buildExistingPolicyStateIndex(
            List<PolicyMaturityEntity> maturities
    ) {
        Map<String, ExistingPolicyStateBuilder>
                builders =
                new HashMap<>();

        for (PolicyMaturityEntity maturity : maturities) {
            String policyKey =
                    normalizePolicyKey(
                            maturity.getPolicyNumber()
                    );

            builders.computeIfAbsent(
                    policyKey,
                    ignored ->
                            new ExistingPolicyStateBuilder()
            ).accept(maturity);
        }

        Map<String, ExistingPolicyState> states =
                new HashMap<>();

        builders.forEach(
                (policyKey, builder) ->
                        states.put(
                                policyKey,
                                builder.build(policyKey)
                        )
        );

        return states;
    }

    private Map<String, Integer> buildNextRankIndex(
            Map<String, ExistingPolicyState> states
    ) {
        Map<String, Integer> nextRanks =
                new HashMap<>();

        states.forEach(
                (policyKey, state) ->
                        nextRanks.put(
                                policyKey,
                                Math.addExact(
                                        state.maximumRank(),
                                        1
                                )
                        )
        );

        return nextRanks;
    }

    private Map<String, LocalDate>
    buildLastPaymentDateIndex(
            List<PaymentEntity> paidPayments
    ) {
        Map<String, LocalDate> dates =
                new HashMap<>();

        for (PaymentEntity payment : paidPayments) {
            String policyKey =
                    normalizePolicyKey(
                            payment.getPolicyNumber()
                    );

            dates.merge(
                    policyKey,
                    payment.getPaymentDate(),
                    (current, candidate) ->
                            candidate.isAfter(current)
                                    ? candidate
                                    : current
            );
        }

        return dates;
    }

    private void validateClientName(
            ParsedMaturityRow row,
            ExistingPolicyState state,
            List<CsvValidationError> errors
    ) {
        if (
                state == null
                        || state.clientName() == null
                        || isUnknownClientName(
                        state.clientName()
                )
        ) {
            return;
        }

        if (
                !normalizeClientKey(
                        state.clientName()
                ).equals(
                        normalizeClientKey(
                                row.clientName()
                        )
                )
        ) {
            errors.add(
                    new CsvValidationError(
                            row.rowNumber(),
                            "nom_client",
                            "INCONSISTENT_CLIENT_NAME",
                            "La police "
                                    + row.policyNumber()
                                    + " est déjà rattachée au client "
                                    + state.clientName()
                                    + "."
                    )
            );
        }
    }

    private void validateInterestEndDate(
            ParsedMaturityRow row,
            ExistingPolicyState state,
            List<CsvValidationError> errors
    ) {
        if (
                state == null
                        || state.interestEndDate() == null
        ) {
            return;
        }

        if (
                !state.interestEndDate().equals(
                        row.interestEndDate()
                )
        ) {
            errors.add(
                    new CsvValidationError(
                            row.rowNumber(),
                            "date_fin_interets",
                            "INCONSISTENT_INTEREST_END_DATE",
                            "La police "
                                    + row.policyNumber()
                                    + " possède déjà la date "
                                    + "de fin des intérêts "
                                    + state.interestEndDate()
                                    + "."
                    )
            );
        }
    }

    private void validateMaturityDate(
            ParsedMaturityRow row,
            LocalDate maturityDate,
            ExistingPolicyState state,
            List<CsvValidationError> errors
    ) {
        if (
                maturityDate.isAfter(
                        row.interestEndDate()
                )
        ) {
            errors.add(
                    new CsvValidationError(
                            row.rowNumber(),
                            "date_fin_interets",
                            "MATURITY_AFTER_INTEREST_END_DATE",
                            "La date de fin des intérêts "
                                    + row.interestEndDate()
                                    + " est antérieure à la date "
                                    + "métier du chargement "
                                    + maturityDate
                                    + "."
                    )
            );
        }

        if (
                state != null
                        && state.lastMaturityDate() != null
                        && maturityDate.isBefore(
                        state.lastMaturityDate()
                )
        ) {
            errors.add(
                    new CsvValidationError(
                            row.rowNumber(),
                            "date_chargement",
                            "MATURITY_BEFORE_LAST_MATURITY",
                            "La date métier du chargement "
                                    + maturityDate
                                    + " est antérieure à la dernière "
                                    + "maturité de la police "
                                    + state.lastMaturityDate()
                                    + "."
                    )
            );
        }
    }

    private void validateAgainstLastPayment(
            ParsedMaturityRow row,
            LocalDate maturityDate,
            LocalDate lastPaymentDate,
            List<CsvValidationError> errors
    ) {
        if (lastPaymentDate == null) {
            return;
        }

        if (
                !maturityDate.isAfter(
                        lastPaymentDate
                )
        ) {
            errors.add(
                    new CsvValidationError(
                            row.rowNumber(),
                            "date_chargement",
                            "MATURITY_NOT_AFTER_LAST_PAYMENT",
                            "La date métier du chargement doit "
                                    + "être strictement postérieure "
                                    + "au dernier paiement valide "
                                    + "du "
                                    + lastPaymentDate
                                    + "."
                    )
            );
        }
    }

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

            return HexFormat.of()
                    .formatHex(hash);
        } catch (
                IOException
                | NoSuchAlgorithmException exception
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
        return Optional.ofNullable(
                        file == null
                                ? null
                                : file.getOriginalFilename()
                )
                .filter(name ->
                        !name.isBlank()
                )
                .map(this::removePathInformation)
                .orElse("fichier-sans-nom.csv");
    }

    private String removePathInformation(
            String fileName
    ) {
        String normalized =
                fileName.replace('\\', '/');

        int lastSeparator =
                normalized.lastIndexOf('/');

        return lastSeparator >= 0
                ? normalized.substring(
                lastSeparator + 1
        )
                : normalized;
    }

    private long resolveFileSize(
            MultipartFile file
    ) {
        return file == null
                ? 0
                : file.getSize();
    }

    private boolean isUnknownClientName(
            String clientName
    ) {
        return UNKNOWN_CLIENT_NAME.equalsIgnoreCase(
                normalizeClientName(clientName)
        );
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
        if (clientName == null) {
            return "";
        }

        return clientName
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizeClientKey(
            String clientName
    ) {
        return normalizeClientName(clientName)
                .toUpperCase(Locale.ROOT);
    }

    private record ExistingPolicyState(
            int maximumRank,
            LocalDate lastMaturityDate,
            LocalDate interestEndDate,
            String clientName
    ) {
    }

    private static final class
    ExistingPolicyStateBuilder {

        private int maximumRank;

        private LocalDate lastMaturityDate;

        private LocalDate interestEndDate;

        private String clientName;

        private void accept(
                PolicyMaturityEntity maturity
        ) {
            if (maturity.getMaturityRank() <= 0) {
                throw new IllegalStateException(
                        "La police "
                                + maturity.getPolicyNumber()
                                + " possède un rang invalide."
                );
            }

            maximumRank =
                    Math.max(
                            maximumRank,
                            maturity.getMaturityRank()
                    );

            if (
                    lastMaturityDate == null
                            || maturity.getMaturityDate()
                            .isAfter(lastMaturityDate)
            ) {
                lastMaturityDate =
                        maturity.getMaturityDate();
            }

            acceptInterestEndDate(maturity);
            acceptClientName(maturity);
        }

        private void acceptInterestEndDate(
                PolicyMaturityEntity maturity
        ) {
            if (interestEndDate == null) {
                interestEndDate =
                        maturity.getInterestEndDate();

                return;
            }

            if (
                    !interestEndDate.equals(
                            maturity.getInterestEndDate()
                    )
            ) {
                throw new IllegalStateException(
                        "La police "
                                + maturity.getPolicyNumber()
                                + " possède plusieurs dates "
                                + "de fin des intérêts."
                );
            }
        }

        private void acceptClientName(
                PolicyMaturityEntity maturity
        ) {
            String candidate =
                    normalizeStaticClientName(
                            maturity.getClientName()
                    );

            if (
                    candidate.isBlank()
                            || UNKNOWN_CLIENT_NAME
                            .equalsIgnoreCase(candidate)
            ) {
                return;
            }

            if (
                    clientName == null
                            || clientName.isBlank()
                            || UNKNOWN_CLIENT_NAME
                            .equalsIgnoreCase(clientName)
            ) {
                clientName = candidate;
                return;
            }

            if (
                    !normalizeStaticClientName(
                            clientName
                    ).equalsIgnoreCase(candidate)
            ) {
                throw new IllegalStateException(
                        "La police "
                                + maturity.getPolicyNumber()
                                + " possède plusieurs noms "
                                + "de client."
                );
            }
        }

        private ExistingPolicyState build(
                String policyKey
        ) {
            if (maximumRank <= 0) {
                throw new IllegalStateException(
                        "La police "
                                + policyKey
                                + " ne possède aucun rang valide."
                );
            }

            return new ExistingPolicyState(
                    maximumRank,
                    lastMaturityDate,
                    interestEndDate,
                    clientName
            );
        }

        private static String
        normalizeStaticClientName(
                String clientName
        ) {
            if (clientName == null) {
                return "";
            }

            return clientName
                    .trim()
                    .replaceAll("\\s+", " ");
        }
    }
}