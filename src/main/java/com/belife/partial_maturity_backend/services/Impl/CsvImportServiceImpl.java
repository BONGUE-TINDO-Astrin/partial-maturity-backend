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
 * Orchestre l'importation des maturités.
 */
@Service
@RequiredArgsConstructor
public class CsvImportServiceImpl implements CsvImportService {

    private static final String UNKNOWN_CLIENT_NAME = "CLIENT NON RENSEIGNE";

    private final CsvMaturityParser csvMaturityParser;

    private final PolicyMaturityRepository policyMaturityRepository;

    private final PaymentRepository paymentRepository;

    private final CsvImportPersistenceService persistenceService;

    @Override
    public CsvImportResponse importFile( MultipartFile file, String currentUsername) {
        String fileName = resolveFileName(file);

        long fileSize = resolveFileSize(file);

        String fileSha256 = calculateSha256(file);

        CsvValidationResult parsingResult = csvMaturityParser.parse(file);

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
                        analysis.newRows(),
                        currentUsername
                );
    }

    private MaturityImportAnalysis
    analyzeAgainstDatabase(List<ParsedMaturityRow> rows) {
        List<CsvValidationError> errors = new ArrayList<>();

        Set<String> policyNumbers = rows.stream()
                        .map(ParsedMaturityRow::policyNumber)
                        .collect(Collectors.toSet());

        List<PolicyMaturityEntity> existingMaturities =
                policyNumbers.isEmpty()
                        ? List.of()
                        : policyMaturityRepository.findAllByPolicyNumberIn(policyNumbers);

        List<PaymentEntity> paidPayments =
                policyNumbers.isEmpty()
                        ? List.of()
                        : paymentRepository.findAllByPolicyNumbersAndStatus(
                                  policyNumbers, PaymentStatus.PAID
                          );

        Map<String, ExistingPolicyState> states = buildPolicyStates(existingMaturities);

        Map<String, LocalDate> lastPaymentDates = buildLastPaymentDates(paidPayments);

        Map<String, Integer> nextRanks = buildNextRanks(states);

        /*
         * Cette map évolue selon l'ordre physique du fichier.
         * Elle garantit des dates non décroissantes par police.
         */
        Map<String, LocalDate> lastMaturityDates = buildLastMaturityDateIndex(states);

        List<MaturityImportRow> importRows = new ArrayList<>();

        for (ParsedMaturityRow row : rows) {
            String policyKey = normalizePolicyKey(row.policyNumber());

            ExistingPolicyState state = states.get(policyKey);

            validateClientName(row, state, errors);

            validateInterestEndDate(row, state, errors);

            validateMaturityChronology(row, lastMaturityDates.get(policyKey), errors);

            validateLastPayment(row, lastPaymentDates.get(policyKey), errors);

            int assignedRank = nextRanks.getOrDefault(policyKey, 1);

            importRows.add(MaturityImportRow.from(row, assignedRank)
            );

            nextRanks.put(policyKey, Math.addExact(assignedRank, 1));

            /*
             * La ligne courante devient la référence
             * chronologique de la ligne suivante.
             */
            lastMaturityDates.put(policyKey, row.maturityDate());
        }

        return new MaturityImportAnalysis(importRows, errors);
    }

    private Map<String, ExistingPolicyState>
    buildPolicyStates(List<PolicyMaturityEntity> maturities) {
        Map<String, ExistingPolicyState> states = new HashMap<>();

        for (PolicyMaturityEntity maturity : maturities) {
            String policyKey = normalizePolicyKey(maturity.getPolicyNumber());

            ExistingPolicyState current = states.get(policyKey);

            if (current == null) {
                states.put(policyKey, ExistingPolicyState.from(maturity));
                continue;
            }

            states.put(policyKey, current.merge(maturity));
        }

        return states;
    }

    private Map<String, Integer> buildNextRanks(Map<String, ExistingPolicyState> states) {
        Map<String, Integer> nextRanks = new HashMap<>();

        states.forEach((policyKey, state) -> nextRanks.put(policyKey, Math.addExact(state.maximumRank(), 1)));

        return nextRanks;
    }

    private Map<String, LocalDate> buildLastMaturityDateIndex(Map<String, ExistingPolicyState> states) {
        Map<String, LocalDate> dates = new HashMap<>();

        states.forEach((policyKey, state) -> dates.put(policyKey, state.lastMaturityDate()));

        return dates;
    }

    private Map<String, LocalDate> buildLastPaymentDates(List<PaymentEntity> payments) {
        Map<String, LocalDate> dates = new HashMap<>();

        for (PaymentEntity payment : payments) {
            String policyKey = normalizePolicyKey(payment.getPolicyNumber());

            dates.merge(
                    policyKey,
                    payment.getPaymentDate(),
                    (current, candidate) -> candidate.isAfter(current) ? candidate : current);
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
                !normalizeClientKey(state.clientName()).equals(
                        normalizeClientKey(row.clientName())
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
        if (state == null) {
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

    private void validateMaturityChronology(
            ParsedMaturityRow row,
            LocalDate previousMaturityDate,
            List<CsvValidationError> errors
    ) {
        if (previousMaturityDate == null) {
            return;
        }

        if (row.maturityDate().isBefore(previousMaturityDate)) {
            errors.add(
                    new CsvValidationError(
                            row.rowNumber(),
                            "date_maturite",
                            "MATURITY_BEFORE_PREVIOUS_MATURITY",
                            "La date de maturité "
                                    + row.maturityDate()
                                    + " ne peut pas précéder "
                                    + "la maturité précédente du "
                                    + previousMaturityDate
                                    + " pour la police "
                                    + row.policyNumber()
                                    + "."
                    )
            );
        }
    }

    private void validateLastPayment(
            ParsedMaturityRow row,
            LocalDate lastPaymentDate,
            List<CsvValidationError> errors
    ) {
        if (lastPaymentDate == null) {
            return;
        }

        if (!row.maturityDate().isAfter(lastPaymentDate)) {
            errors.add(
                    new CsvValidationError(
                            row.rowNumber(),
                            "date_maturite",
                            "MATURITY_NOT_AFTER_LAST_PAYMENT",
                            "La date de maturité doit être "
                                    + "strictement postérieure au "
                                    + "dernier paiement valide du "
                                    + lastPaymentDate
                                    + "."
                    )
            );
        }
    }

    private String calculateSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(file.getBytes()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new CsvFileProcessingException(
                    "Impossible de calculer " + "l'empreinte du fichier.",
                    exception
            );
        }
    }

    private String resolveFileName(MultipartFile file) {
        return Optional.ofNullable(file == null ? null : file.getOriginalFilename())
                .filter(name -> !name.isBlank())
                .map(this::removePathInformation)
                .orElse("fichier-sans-nom.csv");
    }

    private String removePathInformation(String fileName) {
        String normalized = fileName.replace('\\', '/');

        int separator = normalized.lastIndexOf('/');

        return separator >= 0 ? normalized.substring(separator + 1)
                : normalized;
    }

    private long resolveFileSize(MultipartFile file) {
        return file == null ? 0 : file.getSize();
    }

    private boolean isUnknownClientName(String clientName) {
        return UNKNOWN_CLIENT_NAME.equalsIgnoreCase(
                normalizeClientName(clientName)
        );
    }

    private String normalizePolicyKey(String policyNumber) {
        return policyNumber.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeClientName(String clientName) {
        if (clientName == null) {
            return "";
        }

        return clientName.trim().replaceAll("\\s+", " ");
    }

    private String normalizeClientKey(String clientName) {
        return normalizeClientName(clientName).toUpperCase(Locale.ROOT);
    }

    private record ExistingPolicyState(
            int maximumRank,
            LocalDate lastMaturityDate,
            LocalDate interestEndDate,
            String clientName
    ) {

        private static ExistingPolicyState from(PolicyMaturityEntity maturity) {
            validateRank(maturity);

            return new ExistingPolicyState(
                    maturity.getMaturityRank(),
                    maturity.getMaturityDate(),
                    maturity.getInterestEndDate(),
                    normalizeStoredClientName(maturity.getClientName())
            );
        }

        private ExistingPolicyState merge(PolicyMaturityEntity maturity) {
            validateRank(maturity);

            if (!interestEndDate.equals(maturity.getInterestEndDate())) {
                throw new IllegalStateException(
                        "La police "
                                + maturity.getPolicyNumber()
                                + " possède plusieurs dates "
                                + "de fin des intérêts."
                );
            }

            LocalDate mergedLastDate =
                    maturity.getMaturityDate()
                            .isAfter(lastMaturityDate)
                            ? maturity.getMaturityDate()
                            : lastMaturityDate;

            return new ExistingPolicyState(
                    Math.max(maximumRank, maturity.getMaturityRank()),
                    mergedLastDate,
                    interestEndDate,
                    mergeClientName(maturity)
            );
        }

        private String mergeClientName(PolicyMaturityEntity maturity) {
            String candidate = normalizeStoredClientName(maturity.getClientName());

            if (candidate.isBlank() || UNKNOWN_CLIENT_NAME.equalsIgnoreCase(candidate)) {
                return clientName;
            }

            if (
                    clientName == null
                            || clientName.isBlank()
                            || UNKNOWN_CLIENT_NAME
                            .equalsIgnoreCase(clientName)
            ) {
                return candidate;
            }

            if (!clientName.equalsIgnoreCase(candidate)) {
                throw new IllegalStateException(
                        "La police "
                                + maturity.getPolicyNumber()
                                + " possède plusieurs noms "
                                + "de client."
                );
            }

            return clientName;
        }

        private static void validateRank(PolicyMaturityEntity maturity) {
            if (maturity.getMaturityRank() <= 0) {
                throw new IllegalStateException(
                        "La police "
                                + maturity.getPolicyNumber()
                                + " possède un rang invalide."
                );
            }
        }

        private static String normalizeStoredClientName(String clientName) {
            if (clientName == null) {
                return "";
            }

            return clientName.trim().replaceAll("\\s+", " ");
        }
    }
}