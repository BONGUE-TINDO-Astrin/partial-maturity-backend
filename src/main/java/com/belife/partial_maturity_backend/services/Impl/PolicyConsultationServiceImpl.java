package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.dtos.responses.PolicyDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyMaturityResponse;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.exceptions.InvalidPolicyNumberException;
import com.belife.partial_maturity_backend.exceptions.PolicyNotFoundException;
import com.belife.partial_maturity_backend.mappers.PolicyMaturityMapper;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.PolicyConsultationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Implémente la consultation des polices et de leurs maturités.
 *
 * <p>Les maturités sont toujours retournées dans l'ordre
 * de leur rang afin de préserver la séquence métier.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyConsultationServiceImpl implements PolicyConsultationService {

    private final PolicyMaturityRepository policyMaturityRepository;
    private final PolicyMaturityMapper policyMaturityMapper;

    /**
     * Charge les maturités et construit le résumé financier
     * de la police.
     */
    @Override
    public PolicyDetailResponse getPolicyDetails(String policyNumber) {
        String normalizedPolicyNumber = normalizePolicyNumber(policyNumber);

        List<PolicyMaturityEntity> maturities =
            policyMaturityRepository
                .findAllByPolicyNumberIgnoreCaseOrderByMaturityRankAsc(
                    normalizedPolicyNumber
                );

        if (maturities.isEmpty()) {
            throw new PolicyNotFoundException(normalizedPolicyNumber);
        }

        List<PolicyMaturityResponse> maturityResponses =
            maturities.stream()
                .map(policyMaturityMapper::toResponse)
                .toList();

        BigDecimal totalMaturityAmount =
            maturities.stream()
                .map(PolicyMaturityEntity::getMaturityAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate firstMaturityDate =
            maturities.stream()
                .map(PolicyMaturityEntity::getMaturityDate)
                .min(LocalDate::compareTo)
                .orElseThrow();

        LocalDate lastMaturityDate =
            maturities.stream()
                .map( PolicyMaturityEntity::getMaturityDate)
                .max(LocalDate::compareTo)
                .orElseThrow();

        /*
         * Le numéro enregistré dans SQL Server est retourné
         * afin de préserver sa casse et ses éventuels zéros initiaux.
         */
        String storedPolicyNumber = maturities.getFirst().getPolicyNumber();

        return new PolicyDetailResponse(
            storedPolicyNumber,
            maturities.size(),
            totalMaturityAmount,
            firstMaturityDate,
            lastMaturityDate,
            maturityResponses
        );
    }

    /**
     * Supprime les espaces extérieurs sans convertir
     * le numéro en valeur numérique.
     *
     * Les zéros initiaux doivent absolument être conservés.
     */
    private String normalizePolicyNumber(String policyNumber) {
        if (policyNumber == null || policyNumber.isBlank()
        ) {
            throw new InvalidPolicyNumberException("Le numéro de police est obligatoire.");
        }

        String normalized = policyNumber.trim();

        if (normalized.length() > 100) {
            throw new InvalidPolicyNumberException( "Le numéro de police ne doit pas dépasser 100 caractères.");
        }

        return normalized;
    }
}