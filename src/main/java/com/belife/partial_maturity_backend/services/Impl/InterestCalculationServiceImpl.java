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
import com.belife.partial_maturity_backend.services.BusinessDateProvider;
import com.belife.partial_maturity_backend.services.InterestCalculationEngine;
import com.belife.partial_maturity_backend.services.InterestCalculationService;
import com.belife.partial_maturity_backend.services.models.CalculationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Charge les données d'une police et délègue
 * le calcul au moteur financier pur.
 *
 * <p>Les maturités et les paiements PAID sont fusionnés
 * dans une chronologie unique. Les paiements annulés
 * ne participent pas à la situation courante.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterestCalculationServiceImpl
        implements InterestCalculationService {

    private final PolicyMaturityRepository policyMaturityRepository;

    private final PaymentRepository paymentRepository;

    private final InterestCalculationEngine calculationEngine;

    private final InterestProperties interestProperties;

    private final BusinessDateProvider businessDateProvider;

    @Override
    public InterestSimulationResponse simulate(
            String policyNumber
    ) {
        String normalizedPolicyNumber =
                normalizePolicyNumber(
                        policyNumber
                );

        List<PolicyMaturityEntity> maturities =
                policyMaturityRepository
                        .findAllByPolicyNumberIgnoreCaseOrderByMaturityRankAsc(
                                normalizedPolicyNumber
                        );

        if (maturities.isEmpty()) {
            throw new PolicyNotFoundException(
                    normalizedPolicyNumber
            );
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
                .map(this::toMaturityCalculationEvent)
                .forEach(events::add);

        paidPayments.stream()
                .map(this::toPaymentCalculationEvent)
                .forEach(events::add);

        String storedPolicyNumber =
                maturities.getFirst()
                        .getPolicyNumber();

        LocalDate interestEndDate =
                resolveInterestEndDate(
                        maturities
                );

        LocalDate calculationDate = businessDateProvider.currentDate();

        return calculationEngine.calculate(
                storedPolicyNumber,
                calculationDate,
                interestEndDate,
                interestProperties.annualRate(),
                events
        );
    }

    private CalculationEvent
    toMaturityCalculationEvent(
            PolicyMaturityEntity maturity
    ) {
        return new CalculationEvent(
                maturity.getId(),
                maturity.getMaturityDate(),
                CalculationEvent.EventKind
                        .MATURITY,
                maturity.getMaturityRank(),
                maturity.getMaturityType(),
                maturity.getMaturityAmount()
        );
    }

    /**
     * Convertit un paiement valide en événement
     * chronologique.
     *
     * <p>Le montant conservé dans l'événement est celui
     * réellement enregistré en base et sera affiché dans
     * la ligne PAYMENT_APPLIED.</p>
     */
    private CalculationEvent
    toPaymentCalculationEvent(
            PaymentEntity payment
    ) {
        return new CalculationEvent(
                payment.getId(),
                payment.getPaymentDate(),
                CalculationEvent.EventKind
                        .PAYMENT,
                0,
                "Paiement n°"
                        + payment.getId(),
                payment.getPaidAmount()
        );
    }

    /**
     * Retourne la date de fin commune à toutes
     * les maturités de la police.
     *
     * <p>Le flux d'import garantit cette cohérence.
     * La vérification défensive permet néanmoins de détecter
     * rapidement une éventuelle donnée anormale en base.</p>
     */
    private LocalDate resolveInterestEndDate(
            List<PolicyMaturityEntity> maturities
    ) {
        LocalDate interestEndDate =
                maturities.getFirst()
                        .getInterestEndDate();

        boolean inconsistentDate =
                maturities.stream()
                        .anyMatch(maturity ->
                                !interestEndDate.equals(
                                        maturity
                                                .getInterestEndDate()
                                )
                        );

        if (inconsistentDate) {
            throw new IllegalStateException(
                    "La police "
                            + maturities.getFirst()
                            .getPolicyNumber()
                            + " possède plusieurs dates "
                            + "de fin des intérêts."
            );
        }

        return interestEndDate;
    }

    private String normalizePolicyNumber(
            String policyNumber
    ) {
        if (
                policyNumber == null
                        || policyNumber.isBlank()
        ) {
            throw new InvalidPolicyNumberException(
                    "Le numéro de police est obligatoire."
            );
        }

        String normalized = policyNumber.trim();

        if (normalized.length() > 100) {
            throw new InvalidPolicyNumberException(
                    "Le numéro de police ne doit pas dépasser "
                            + "100 caractères."
            );
        }

        return normalized;
    }
}