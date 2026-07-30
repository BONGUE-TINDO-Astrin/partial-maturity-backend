package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.config.properties.InterestProperties;
import com.belife.partial_maturity_backend.dtos.responses.InterestSimulationResponse;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.exceptions.InvalidPolicyNumberException;
import com.belife.partial_maturity_backend.exceptions.PolicyNotFoundException;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.InterestCalculationEngine;
import com.belife.partial_maturity_backend.services.InterestCalculationService;
import com.belife.partial_maturity_backend.services.models.CalculationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Charge les données d'une police et délègue
 * le calcul au moteur financier pur.
 *
 * <p>Dans cet incrément, seules les maturités sont disponibles.
 * Les paiements PAID seront ajoutés à la chronologie lors
 * de la tranche verticale consacrée aux paiements.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterestCalculationServiceImpl implements InterestCalculationService {

    private final PolicyMaturityRepository policyMaturityRepository;
    private final PaymentRepository paymentRepository;

    private final InterestCalculationEngine calculationEngine;
    private final InterestProperties interestProperties;
    private final Clock clock;

    @Override
    public InterestSimulationResponse simulate(String policyNumber) {
        String normalizedPolicyNumber = normalizePolicyNumber(policyNumber);

        List<PolicyMaturityEntity> maturities =
            policyMaturityRepository
                .findAllByPolicyNumberIgnoreCaseOrderByMaturityRankAsc(
                    normalizedPolicyNumber
                );

        if (maturities.isEmpty()) {
            throw new PolicyNotFoundException(normalizedPolicyNumber);
        }

        List<PaymentEntity> paidPayments =
                paymentRepository
                        .findAllByPolicyNumberIgnoreCaseAndStatusOrderByPaymentDateAscIdAsc(
                                normalizedPolicyNumber,
                                PaymentStatus.PAID
                        );

        List<CalculationEvent> events =
                new ArrayList<>();

        maturities.stream()
                .map(this::toCalculationEvent)
                .forEach(events::add);

        paidPayments.stream()
                .map(this::toPaymentCalculationEvent)
                .forEach(events::add);

        String storedPolicyNumber = maturities.getFirst().getPolicyNumber();

        LocalDate calculationDate = LocalDate.now(clock);

        return calculationEngine.calculate(
            storedPolicyNumber,
            calculationDate,
            interestProperties.annualRate(),
            events
        );
    }

    private CalculationEvent toCalculationEvent(PolicyMaturityEntity maturity) {
        return new CalculationEvent(
            maturity.getId(),
            maturity.getMaturityDate(),
            CalculationEvent.EventKind.MATURITY,
            maturity.getMaturityRank(),
            maturity.getMaturityType(),
            maturity.getMaturityAmount()
        );
    }

    private String normalizePolicyNumber(String policyNumber) {
        if (policyNumber == null || policyNumber.isBlank()) {
            throw new InvalidPolicyNumberException("Le numéro de police est obligatoire.");
        }

        return policyNumber.trim();
    }

    /**
     * Convertit un paiement valide en événement chronologique.
     *
     * Le moteur n'utilise pas le montant pour remettre la situation
     * à zéro : un paiement PAID est toujours total.
     */
    private CalculationEvent toPaymentCalculationEvent(
            PaymentEntity payment
    ) {
        return new CalculationEvent(
                payment.getId(),
                payment.getPaymentDate(),
                CalculationEvent.EventKind.PAYMENT,
                0,
                "Paiement n°" + payment.getId(),
                payment.getPaidAmount()
        );
    }
}