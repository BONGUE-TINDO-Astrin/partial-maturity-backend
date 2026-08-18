package com.belife.partial_maturity_backend.serviceimpl;

import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.Impl.ImportBatchReversalEligibilityServiceImpl;
import com.belife.partial_maturity_backend.services.models.ImportBatchReversalEligibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportBatchReversalEligibilityServiceImplTest {

    @Mock
    private PolicyMaturityRepository
            policyMaturityRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private ImportBatchReversalEligibilityServiceImpl
            eligibilityService;

    @BeforeEach
    void setUp() {
        eligibilityService =
                new ImportBatchReversalEligibilityServiceImpl(
                        policyMaturityRepository,
                        paymentRepository
                );
    }

    @Test
    void shouldAllowImportedBatchWithoutPaidPayment() {
        ImportBatchEntity batch =
                batch(
                        10L,
                        ImportBatchStatus.IMPORTED,
                        1
                );

        PolicyMaturityEntity maturity =
                maturity(
                        101L,
                        batch,
                        "POL001",
                        1
                );

        when(
                policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
                                10L
                        )
        ).thenReturn(List.of(maturity));

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(List.of());

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of(maturity));

        ImportBatchReversalEligibility result =
                eligibilityService.evaluate(batch);

        assertThat(result.reversible())
                .isTrue();

        assertThat(result.blockedReason())
                .isNull();
    }

//    @Test
//    void shouldBlockBatchUsedByPaidPayment() {
//        ImportBatchEntity batch =
//                batch(
//                        10L,
//                        ImportBatchStatus.IMPORTED,
//                        1
//                );
//
//        PolicyMaturityEntity maturity =
//                maturity(
//                        101L,
//                        batch,
//                        "POL001",
//                        1
//                );
//
//        PaymentEntity payment =
//                new PaymentEntity();
//
//        payment.setPolicyNumber("POL001");
//        payment.setStatus(PaymentStatus.PAID);
//
//        when(
//                policyMaturityRepository
//                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
//                                10L
//                        )
//        ).thenReturn(List.of(maturity));
//
//        when(
//                paymentRepository
//                        .findAllByPolicyNumbersAndStatus(
//                                anyCollection(),
//                                eq(PaymentStatus.PAID)
//                        )
//        ).thenReturn(List.of(payment));
//
//        ImportBatchReversalEligibility result =
//                eligibilityService.evaluate(batch);
//
//        assertThat(result.reversible())
//                .isFalse();
//
//        assertThat(result.blockedReason())
//                .contains("POL001")
//                .contains("paiement valide");
//    }

    @Test
    void shouldBlockBatchWithoutInsertedRows() {
        ImportBatchEntity batch =
                batch(
                        10L,
                        ImportBatchStatus.IMPORTED,
                        0
                );

        ImportBatchReversalEligibility result =
                eligibilityService.evaluate(batch);

        assertThat(result.reversible())
                .isFalse();

        assertThat(result.blockedReason())
                .contains(
                        "aucune nouvelle maturité"
                );

        verifyNoInteractions(
                policyMaturityRepository,
                paymentRepository
        );
    }

    @Test
    void shouldBlockReversedBatchWithoutAdditionalQueries() {
        ImportBatchEntity batch =
                batch(
                        10L,
                        ImportBatchStatus.REVERSED,
                        2
                );

        ImportBatchReversalEligibility result =
                eligibilityService.evaluate(batch);

        assertThat(result.reversible())
                .isFalse();

        verifyNoInteractions(
                policyMaturityRepository,
                paymentRepository
        );
    }

    private ImportBatchEntity batch(
            Long id,
            ImportBatchStatus status,
            int insertedRows
    ) {
        ImportBatchEntity batch =
                new ImportBatchEntity();

        batch.setId(id);
        batch.setStatus(status);
        batch.setInsertedRows(insertedRows);

        return batch;
    }

    private PolicyMaturityEntity maturity(
            Long id,
            ImportBatchEntity batch,
            String policyNumber,
            int rank
    ) {
        PolicyMaturityEntity maturity =
                new PolicyMaturityEntity();

        maturity.setId(id);
        maturity.setImportBatch(batch);
        maturity.setPolicyNumber(policyNumber);
        maturity.setMaturityRank(rank);
        maturity.setMaturityType(
                "MATURITE_" + rank
        );
        maturity.setMaturityDate(
                LocalDate.of(
                        2024 + rank,
                        1,
                        1
                )
        );
        maturity.setMaturityAmount(
                new BigDecimal("1000000.00")
        );
        maturity.setInterestEndDate(
                LocalDate.of(
                        2030,
                        1,
                        1
                )
        );

        return maturity;
    }

    @Test
    void shouldRemainEligibleWhenPaymentPredatesBatchMaturity() {
        ImportBatchEntity firstBatch =
                batch(
                        1L,
                        ImportBatchStatus.IMPORTED,
                        1
                );

        ImportBatchEntity secondBatch =
                batch(
                        2L,
                        ImportBatchStatus.IMPORTED,
                        1
                );

        PolicyMaturityEntity rankOne =
                maturity(
                        101L,
                        firstBatch,
                        "POL001",
                        1
                );

        PolicyMaturityEntity rankTwo =
                maturity(
                        102L,
                        secondBatch,
                        "POL001",
                        2
                );

        /*
         * Le helper génère ici une date correspondant
         * au rang. Adapte explicitement pour le scénario.
         */
        rankOne.setMaturityDate(
                LocalDate.of(
                        2021,
                        3,
                        15
                )
        );

        rankTwo.setMaturityDate(
                LocalDate.of(
                        2024,
                        3,
                        15
                )
        );

        PaymentEntity oldPayment =
                new PaymentEntity();

        oldPayment.setPolicyNumber("POL001");
        oldPayment.setPaymentDate(
                LocalDate.of(
                        2023,
                        3,
                        15
                )
        );
        oldPayment.setStatus(
                PaymentStatus.PAID
        );

        when(
                policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
                                2L
                        )
        ).thenReturn(List.of(rankTwo));

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(List.of(oldPayment));

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(
                        rankOne,
                        rankTwo
                )
        );

        ImportBatchReversalEligibility result =
                eligibilityService.evaluate(
                        secondBatch
                );

        assertThat(result.reversible())
                .isTrue();

        assertThat(result.blockedReason())
                .isNull();
    }
}