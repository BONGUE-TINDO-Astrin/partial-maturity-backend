package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.ImportBatchReversalEligibilityService;
import com.belife.partial_maturity_backend.services.models.ImportBatchReversalEligibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Analyse en lecture seule la possibilité d'annuler
 * un chargement.
 *
 * <p>Le résultat sert uniquement à présenter ou masquer
 * l'action dans l'interface. Il ne remplace pas les
 * contrôles transactionnels de la réversion.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImportBatchReversalEligibilityServiceImpl implements ImportBatchReversalEligibilityService {

    private final PolicyMaturityRepository policyMaturityRepository;

    private final PaymentRepository paymentRepository;

    @Override
    public ImportBatchReversalEligibility evaluate(ImportBatchEntity batch) {
        if (batch.getStatus() != ImportBatchStatus.IMPORTED) {
            return ImportBatchReversalEligibility.blocked(
                    "Seul un chargement au statut IMPORTED "
                            + "peut être annulé."
            );
        }

        if (batch.getInsertedRows() <= 0) {
            return ImportBatchReversalEligibility.blocked(
                    "Ce chargement n'a introduit aucune "
                            + "nouvelle maturité."
            );
        }

        List<PolicyMaturityEntity> batchMaturities = policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(batch.getId());

        if (batchMaturities.isEmpty()) {
            return ImportBatchReversalEligibility.blocked(
                    "Ce chargement ne possède plus aucune "
                            + "maturité active à retirer."
            );
        }

        Set<String> policyNumbers = batchMaturities.stream()
                        .map(PolicyMaturityEntity::getPolicyNumber)
                        .collect(Collectors.toSet());

        List<PaymentEntity> paidPayments = paymentRepository
                        .findAllByPolicyNumbersAndStatus(policyNumbers, PaymentStatus.PAID);

        List<String> blockingPolicyNumbers = findBlockingPolicyNumbers(batchMaturities, paidPayments);

        if (!blockingPolicyNumbers.isEmpty()) {
            return ImportBatchReversalEligibility.blocked(
                    "Le chargement ne peut plus être annulé, "
                            + "car au moins une maturité qu'il a "
                            + "introduite a déjà participé à un "
                            + "paiement valide pour les polices "
                            + "suivantes : "
                            + String.join(
                            ", ",
                            blockingPolicyNumbers
                    )
                            + "."
            );
        }

        List<PolicyMaturityEntity> allMaturities = policyMaturityRepository.findAllByPolicyNumberIn(policyNumbers);

        Map<String, List<PolicyMaturityEntity>>
                maturitiesByPolicy = allMaturities.stream()
                        .collect(
                                Collectors.groupingBy(
                                        maturity ->
                                                normalizePolicyNumber(maturity.getPolicyNumber())
                                )
                        );

        for (String policyNumber : policyNumbers) {
            List<PolicyMaturityEntity> remainingMaturities = maturitiesByPolicy
                            .getOrDefault(normalizePolicyNumber(policyNumber), List.of())
                            .stream()
                            .filter(maturity ->
                                    maturity.getImportBatch()
                                            == null
                                            || !batch.getId()
                                            .equals(maturity.getImportBatch().getId())
                            )
                            .sorted(Comparator.comparingInt(PolicyMaturityEntity::getMaturityRank))
                            .toList();

            ImportBatchReversalEligibility
                    policyEligibility = validateRemainingPolicyState(policyNumber, remainingMaturities);

            if (!policyEligibility.reversible()) {
                return policyEligibility;
            }
        }

        return ImportBatchReversalEligibility.allowed();
    }

    /**
     * Identifie les polices pour lesquelles une maturité
     * du chargement a déjà participé à un paiement PAID.
     *
     * <p>Un paiement bloque la réversion lorsque sa date
     * est postérieure ou égale à la date d'au moins une
     * maturité du lot pour la même police.</p>
     */
    private List<String> findBlockingPolicyNumbers(List<PolicyMaturityEntity> batchMaturities, List<PaymentEntity> paidPayments) {
        Map<String, LocalDate> firstBatchMaturityDateByPolicy = buildFirstBatchMaturityDateIndex(batchMaturities);

        return paidPayments.stream()
                .filter(payment ->paymentUsesBatchMaturity(payment, firstBatchMaturityDateByPolicy))
                .map(PaymentEntity::getPolicyNumber)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * Retourne la première date de maturité introduite
     * par le lot pour chaque police.
     */
    private Map<String, LocalDate> buildFirstBatchMaturityDateIndex(List<PolicyMaturityEntity> batchMaturities) {
        return batchMaturities.stream()
                .collect(Collectors.toMap(
                                maturity -> normalizePolicyNumber(maturity.getPolicyNumber()),
                                PolicyMaturityEntity::getMaturityDate,
                                (currentEarliestDate, candidateDate)
                                        -> candidateDate.isBefore(currentEarliestDate)
                                                ? candidateDate
                                                : currentEarliestDate
                        )
                );
    }

    /**
     * Vérifie si un paiement a pu utiliser une maturité
     * introduite par le lot.
     */
    private boolean paymentUsesBatchMaturity(PaymentEntity payment, Map<String, LocalDate> firstBatchMaturityDateByPolicy) {
        LocalDate firstBatchMaturityDate = firstBatchMaturityDateByPolicy.get(normalizePolicyNumber(payment.getPolicyNumber()));

        if (firstBatchMaturityDate == null) {
            return false;
        }

        /*
         * Une maturité est appliquée avant un paiement
         * lorsque les deux événements possèdent la même date.
         */
        return !payment.getPaymentDate().isBefore(firstBatchMaturityDate);
    }

    /**
     * Une police sans maturité restante est valide :
     * elle disparaîtra simplement de la consultation.
     */
    private ImportBatchReversalEligibility validateRemainingPolicyState(String policyNumber, List<PolicyMaturityEntity> maturities) {
        if (maturities.isEmpty()) {
            return ImportBatchReversalEligibility.allowed();
        }

        for (int index = 0; index < maturities.size(); index++) {
            int expectedRank = index + 1;

            int actualRank = maturities.get(index).getMaturityRank();

            if (actualRank != expectedRank) {
                return ImportBatchReversalEligibility.blocked(
                        "Ce chargement ne peut pas être annulé "
                                + "séparément, car la police "
                                + policyNumber
                                + " conserverait une séquence "
                                + "de maturités incomplète. "
                                + "Le rang "
                                + expectedRank
                                + " serait absent."
                );
            }
        }


        LocalDate expectedInterestEndDate = maturities.getFirst().getInterestEndDate();

        boolean inconsistentInterestEndDate = maturities.stream()
                        .anyMatch(maturity ->
                                !expectedInterestEndDate.equals(maturity.getInterestEndDate())
                        );

        if (inconsistentInterestEndDate) {
            return ImportBatchReversalEligibility.blocked(
                    "Ce chargement ne peut pas être annulé, "
                            + "car la police "
                            + policyNumber
                            + " conserverait plusieurs dates "
                            + "de fin des intérêts."
            );
        }

        return ImportBatchReversalEligibility.allowed();
    }

    private String normalizePolicyNumber(String policyNumber) {
        return policyNumber.trim().toUpperCase(Locale.ROOT);
    }
}