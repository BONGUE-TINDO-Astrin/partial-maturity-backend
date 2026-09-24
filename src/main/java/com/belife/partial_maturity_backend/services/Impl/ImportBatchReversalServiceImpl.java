package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.dtos.requests.ReverseImportBatchRequest;
import com.belife.partial_maturity_backend.dtos.responses.ImportBatchDetailResponse;
import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.exceptions.ConcurrentImportBatchOperationException;
import com.belife.partial_maturity_backend.exceptions.ImportBatchNotFoundException;
import com.belife.partial_maturity_backend.exceptions.ImportBatchReversalNotAllowedException;
import com.belife.partial_maturity_backend.repositories.ImportBatchRepository;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.AuditService;
import com.belife.partial_maturity_backend.services.ImportBatchReversalService;
import com.belife.partial_maturity_backend.services.models.AuditRecordCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Implémente la réversion transactionnelle
 * d'un chargement CSV.
 *
 * <p>Le lot reste dans l'historique. Seules les maturités
 * qu'il avait effectivement introduites sont retirées.</p>
 */
@Service
@RequiredArgsConstructor
public class ImportBatchReversalServiceImpl implements ImportBatchReversalService {

    private final ImportBatchRepository importBatchRepository;

    private final PolicyMaturityRepository policyMaturityRepository;

    private final PaymentRepository paymentRepository;

    private final AuditService auditService;

    /**
     * Horloge technique réelle.
     *
     * <p>La date métier configurable ne doit pas modifier
     * l'instant d'audit de la réversion.</p>
     */
    private final Clock clock;

    @Override
    @Transactional
    public ImportBatchDetailResponse reverseImportBatch(
            Long batchId,
            ReverseImportBatchRequest request,
            String currentUsername
    ) {
        String reason = request.reason().trim();

        ImportBatchEntity batch = lockBatch(batchId);

        validateBatchStatus(batch);

        /*
         * Cette première lecture permet d'identifier
         * les polices qui devront être verrouillées.
         */
        List<PolicyMaturityEntity> initialBatchMaturities =
                policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
                                batchId
                        );

        if (initialBatchMaturities.isEmpty()) {
            throw new ImportBatchReversalNotAllowedException(
                    "Le chargement ne contient aucune maturité "
                            + "active pouvant être retirée."
            );
        }

        List<String> affectedPolicyNumbers =
                initialBatchMaturities.stream()
                        .map(
                                PolicyMaturityEntity
                                        ::getPolicyNumber
                        )
                        .distinct()
                        .sorted(
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .toList();

        /*
         * L'ordre alphabétique est déterministe afin
         * de réduire les risques de deadlock lorsque
         * plusieurs opérations concernent des polices
         * communes.
         */
        Map<String, List<PolicyMaturityEntity>>
                lockedMaturitiesByPolicy =
                lockAffectedPolicies(
                        affectedPolicyNumbers
                );

        /*
         * Les entités réellement supprimées sont reprises
         * depuis les collections verrouillées et non depuis
         * la première lecture.
         */
        List<PolicyMaturityEntity> maturitiesToDelete =
                findMaturitiesFromBatch(
                        batchId,
                        lockedMaturitiesByPolicy
                );

        if (maturitiesToDelete.isEmpty()) {
            throw new ImportBatchReversalNotAllowedException(
                    "Les maturités du chargement ont déjà été "
                            + "retirées ou modifiées par une autre opération."
            );
        }

        validateNoBlockingPaidPayments(
                maturitiesToDelete,
                affectedPolicyNumbers
        );

        validateRemainingPolicyStates(
                batchId,
                lockedMaturitiesByPolicy
        );

        /*
         * La suppression intervient uniquement après
         * l'ensemble des contrôles.
         */
        policyMaturityRepository.deleteAllInBatch(
                maturitiesToDelete
        );

        Instant reversalTime =
                Instant.now(clock);

        batch.setStatus(
                ImportBatchStatus.REVERSED
        );
        batch.setReversedAt(reversalTime);
        batch.setReversedBy(
                currentUsername.trim()
        );
        batch.setReversalReason(reason);

        ImportBatchEntity reversedBatch =
                importBatchRepository.saveAndFlush(
                        batch
                );

        recordAudit(
                reversedBatch,
                maturitiesToDelete.size(),
                affectedPolicyNumbers
        );

        return toDetailResponse(
                reversedBatch
        );
    }

    private ImportBatchEntity lockBatch(
            Long batchId
    ) {
        try {
            return importBatchRepository
                    .findByIdForUpdate(batchId)
                    .orElseThrow(
                            () ->
                                    new ImportBatchNotFoundException(
                                            batchId
                                    )
                    );
        } catch (
                PessimisticLockingFailureException exception
        ) {
            throw new ConcurrentImportBatchOperationException();
        }
    }

    /**
     * Seul un chargement au statut IMPORTED
     * peut être annulé.
     */
    private void validateBatchStatus(
            ImportBatchEntity batch
    ) {
        if (
                batch.getStatus()
                        == ImportBatchStatus.REVERSED
        ) {
            throw new ImportBatchReversalNotAllowedException(
                    "Le chargement a déjà été annulé."
            );
        }

        if (
                batch.getStatus()
                        != ImportBatchStatus.IMPORTED
        ) {
            throw new ImportBatchReversalNotAllowedException(
                    "Seul un chargement au statut IMPORTED "
                            + "peut être annulé."
            );
        }
    }

    private Map<String, List<PolicyMaturityEntity>>
    lockAffectedPolicies(
            List<String> affectedPolicyNumbers
    ) {
        Map<String, List<PolicyMaturityEntity>>
                lockedMaturitiesByPolicy =
                new LinkedHashMap<>();

        try {
            for (
                    String policyNumber
                    : affectedPolicyNumbers
            ) {
                List<PolicyMaturityEntity>
                        lockedMaturities =
                        policyMaturityRepository
                                .findAllByPolicyNumberForPaymentUpdate(
                                        policyNumber
                                );

                lockedMaturitiesByPolicy.put(
                        normalizePolicyNumber(
                                policyNumber
                        ),
                        lockedMaturities
                );
            }
        } catch (
                PessimisticLockingFailureException exception
        ) {
            throw new ConcurrentImportBatchOperationException();
        }

        return lockedMaturitiesByPolicy;
    }

    private List<PolicyMaturityEntity>
    findMaturitiesFromBatch(
            Long batchId,
            Map<String, List<PolicyMaturityEntity>>
                    lockedMaturitiesByPolicy
    ) {
        return lockedMaturitiesByPolicy.values()
                .stream()
                .flatMap(List::stream)
                .filter(maturity ->
                        maturity.getImportBatch() != null
                                && batchId.equals(
                                maturity
                                        .getImportBatch()
                                        .getId()
                        )
                )
                .toList();
    }

    /**
     * Refuse la réversion uniquement lorsqu'un paiement PAID
     * a réellement pu utiliser une maturité introduite
     * par le chargement.
     *
     * <p>Un ancien paiement antérieur à toutes les maturités
     * du lot ne bloque pas leur retrait.</p>
     */
    private void validateNoBlockingPaidPayments(
            List<PolicyMaturityEntity> maturitiesToDelete,
            List<String> affectedPolicyNumbers
    ) {
        List<PaymentEntity> paidPayments =
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                affectedPolicyNumbers,
                                PaymentStatus.PAID
                        );

        if (paidPayments.isEmpty()) {
            return;
        }

        Map<String, LocalDate>
                firstBatchMaturityDateByPolicy =
                buildFirstBatchMaturityDateIndex(
                        maturitiesToDelete
                );

        List<String> blockedPolicyNumbers =
                paidPayments.stream()
                        .filter(payment ->
                                paymentUsesBatchMaturity(
                                        payment,
                                        firstBatchMaturityDateByPolicy
                                )
                        )
                        .map(
                                PaymentEntity
                                        ::getPolicyNumber
                        )
                        .distinct()
                        .sorted(
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .toList();

        if (blockedPolicyNumbers.isEmpty()) {
            return;
        }

        throw new ImportBatchReversalNotAllowedException(
                "Le chargement ne peut pas être annulé, "
                        + "car au moins une maturité qu'il a "
                        + "introduite a déjà participé à un "
                        + "paiement valide pour les polices "
                        + "suivantes : "
                        + String.join(
                        ", ",
                        blockedPolicyNumbers
                )
                        + "."
        );
    }

    /**
     * Retourne la première date de maturité introduite
     * par le chargement pour chaque police.
     */
    private Map<String, LocalDate>
    buildFirstBatchMaturityDateIndex(
            List<PolicyMaturityEntity> batchMaturities
    ) {
        Map<String, LocalDate>
                firstMaturityDateByPolicy =
                new LinkedHashMap<>();

        for (
                PolicyMaturityEntity maturity
                : batchMaturities
        ) {
            String normalizedPolicyNumber =
                    normalizePolicyNumber(
                            maturity.getPolicyNumber()
                    );

            firstMaturityDateByPolicy.merge(
                    normalizedPolicyNumber,
                    maturity.getMaturityDate(),
                    (currentEarliestDate,
                     candidateDate) ->
                            candidateDate.isBefore(
                                    currentEarliestDate
                            )
                                    ? candidateDate
                                    : currentEarliestDate
            );
        }

        return firstMaturityDateByPolicy;
    }

    /**
     * Vérifie si un paiement valide est intervenu
     * le jour ou après la première maturité du lot
     * pour la police concernée.
     */
    private boolean paymentUsesBatchMaturity(
            PaymentEntity payment,
            Map<String, LocalDate>
                    firstBatchMaturityDateByPolicy
    ) {
        LocalDate firstBatchMaturityDate =
                firstBatchMaturityDateByPolicy.get(
                        normalizePolicyNumber(
                                payment.getPolicyNumber()
                        )
                );

        if (firstBatchMaturityDate == null) {
            return false;
        }

        return !payment.getPaymentDate()
                .isBefore(firstBatchMaturityDate);
    }

    /**
     * Simule les données restant en base après suppression
     * des maturités du lot.
     */
    private void validateRemainingPolicyStates(
            Long batchId,
            Map<String, List<PolicyMaturityEntity>>
                    lockedMaturitiesByPolicy
    ) {
        for (
                Map.Entry<String,
                        List<PolicyMaturityEntity>>
                        entry
                : lockedMaturitiesByPolicy.entrySet()
        ) {
            List<PolicyMaturityEntity>
                    remainingMaturities =
                    entry.getValue()
                            .stream()
                            .filter(maturity ->
                                    maturity.getImportBatch()
                                            == null
                                            || !batchId.equals(
                                            maturity
                                                    .getImportBatch()
                                                    .getId()
                                    )
                            )
                            .sorted(
                                    Comparator.comparingInt(
                                            PolicyMaturityEntity
                                                    ::getMaturityRank
                                    )
                            )
                            .toList();

            /*
             * Une police sans aucune maturité restante
             * disparaît naturellement de la consultation.
             */
            if (remainingMaturities.isEmpty()) {
                continue;
            }

            validateRemainingRanks(
                    entry.getKey(),
                    remainingMaturities
            );

            validateRemainingInterestEndDate(
                    entry.getKey(),
                    remainingMaturities
            );
        }
    }

    private void validateRemainingRanks(
            String policyNumber,
            List<PolicyMaturityEntity> maturities
    ) {
        for (
                int index = 0;
                index < maturities.size();
                index++
        ) {
            int expectedRank = index + 1;

            int actualRank =
                    maturities.get(index)
                            .getMaturityRank();

            if (actualRank != expectedRank) {
                throw new ImportBatchReversalNotAllowedException(
                        "Le chargement ne peut pas être annulé, "
                                + "car la police "
                                + policyNumber
                                + " conserverait une séquence "
                                + "de maturités incomplète. "
                                + "Le rang "
                                + expectedRank
                                + " serait absent."
                );
            }
        }
    }

    
    private void validateRemainingInterestEndDate(
            String policyNumber,
            List<PolicyMaturityEntity> maturities
    ) {
        LocalDate expectedInterestEndDate =
                maturities.getFirst()
                        .getInterestEndDate();

        boolean inconsistentDate =
                maturities.stream()
                        .anyMatch(maturity ->
                                !expectedInterestEndDate
                                        .equals(
                                                maturity
                                                        .getInterestEndDate()
                                        )
                        );

        if (inconsistentDate) {
            throw new ImportBatchReversalNotAllowedException(
                    "Le chargement ne peut pas être annulé, "
                            + "car la police "
                            + policyNumber
                            + " conserverait plusieurs dates "
                            + "de fin des intérêts."
            );
        }
    }

    private void recordAudit(
            ImportBatchEntity batch,
            int removedMaturityCount,
            List<String> affectedPolicyNumbers
    ) {
        auditService.record(
                new AuditRecordCommand(
                        AuditEventType
                                .FILE_IMPORT_REVERSED,
                        AuditResourceType
                                .IMPORT_BATCH,
                        batch.getId().toString(),
                        null,
                        batch.getReversedBy(),
                        "Annulation du chargement "
                                + batch.getOriginalFileName()
                                + ".",
                        Map.of(
                                "batchId",
                                batch.getId(),
                                "fileName",
                                batch.getOriginalFileName(),
                                "removedMaturityCount",
                                removedMaturityCount,
                                "affectedPolicyNumbers",
                                affectedPolicyNumbers,
                                "previousStatus",
                                ImportBatchStatus
                                        .IMPORTED
                                        .name(),
                                "newStatus",
                                ImportBatchStatus
                                        .REVERSED
                                        .name(),
                                "reversedAt",
                                batch.getReversedAt()
                                        .toString(),
                                "reversedBy",
                                batch.getReversedBy(),
                                "reversalReason",
                                batch.getReversalReason()
                        )
                )
        );
    }

    private ImportBatchDetailResponse toDetailResponse(ImportBatchEntity batch) {
        return new ImportBatchDetailResponse(
                batch.getId(),
                batch.getOriginalFileName(),
                batch.getFileSha256(),
                batch.getFileSizeBytes(),
                batch.getTotalRows(),
                batch.getInsertedRows(),
                batch.getExistingRows(),
                batch.getErrorRows(),
                batch.getStatus(),
                batch.getImportedAt(),
                batch.getImportedBy(),
                batch.getReversedAt(),
                batch.getReversedBy(),
                batch.getReversalReason(),
                false,
                "Le chargement a déjà été annulé.",
                batch.getCreatedAt(),
                batch.getCreatedBy(),
                batch.getUpdatedAt(),
                batch.getUpdatedBy(),
                List.of()
        );
    }

    private String normalizePolicyNumber(
            String policyNumber
    ) {
        return policyNumber
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}