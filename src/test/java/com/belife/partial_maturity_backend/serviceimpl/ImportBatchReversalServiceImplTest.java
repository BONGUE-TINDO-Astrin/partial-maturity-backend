package com.belife.partial_maturity_backend.serviceimpl;

import com.belife.partial_maturity_backend.dtos.requests.ReverseImportBatchRequest;
import com.belife.partial_maturity_backend.dtos.responses.ImportBatchDetailResponse;
import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.exceptions.ImportBatchNotFoundException;
import com.belife.partial_maturity_backend.exceptions.ImportBatchReversalNotAllowedException;
import com.belife.partial_maturity_backend.repositories.ImportBatchRepository;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.AuditService;
import com.belife.partial_maturity_backend.services.Impl.ImportBatchReversalServiceImpl;
import com.belife.partial_maturity_backend.services.models.AuditRecordCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de la réversion contrôlée
 * des chargements CSV.
 *
 * <p>Ces tests n'accèdent pas à SQL Server. Les repositories
 * et le service d'audit sont simulés avec Mockito.</p>
 */
@ExtendWith(MockitoExtension.class)
class ImportBatchReversalServiceImplTest {

    private static final Instant REVERSAL_TIME =
            Instant.parse(
                    "2026-07-09T10:30:00Z"
            );

    private static final String ADMIN_USERNAME =
            "admin";

    private static final String REVERSAL_REASON =
            "Erreur détectée dans les montants du fichier.";

    @Mock
    private ImportBatchRepository
            importBatchRepository;

    @Mock
    private PolicyMaturityRepository
            policyMaturityRepository;

    @Mock
    private PaymentRepository
            paymentRepository;

    @Mock
    private AuditService auditService;

    private ImportBatchReversalServiceImpl
            reversalService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                REVERSAL_TIME,
                ZoneOffset.UTC
        );

        reversalService =
                new ImportBatchReversalServiceImpl(
                        importBatchRepository,
                        policyMaturityRepository,
                        paymentRepository,
                        auditService,
                        clock
                );
    }

    @Test
    @DisplayName(
            "Un lot importé sans paiement PAID doit être annulé"
    )
    void shouldReverseImportedBatchWithoutPaidPayment() {
        ImportBatchEntity batch =
                importedBatch(
                        10L,
                        "maturites-valides.csv"
                );

        PolicyMaturityEntity rankOne =
                maturity(
                        101L,
                        batch,
                        "POL001",
                        1,
                        "2024-01-15",
                        "1000000.00",
                        "2030-01-15"
                );

        PolicyMaturityEntity rankTwo =
                maturity(
                        102L,
                        batch,
                        "POL001",
                        2,
                        "2025-01-15",
                        "750000.00",
                        "2030-01-15"
                );

        when(
                importBatchRepository
                        .findByIdForUpdate(10L)
        ).thenReturn(
                Optional.of(batch)
        );

        when(
                policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
                                10L
                        )
        ).thenReturn(
                List.of(
                        rankOne,
                        rankTwo
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberForPaymentUpdate(
                                "POL001"
                        )
        ).thenReturn(
                List.of(
                        rankOne,
                        rankTwo
                )
        );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(List.of());

        when(
                importBatchRepository
                        .saveAndFlush(batch)
        ).thenReturn(batch);

        ImportBatchDetailResponse response =
                reversalService.reverseImportBatch(
                        10L,
                        new ReverseImportBatchRequest(
                                "  "
                                        + REVERSAL_REASON
                                        + "  "
                        ),
                        ADMIN_USERNAME
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.REVERSED
                );

        assertThat(response.reversedAt())
                .isEqualTo(REVERSAL_TIME);

        assertThat(response.reversedBy())
                .isEqualTo(ADMIN_USERNAME);

        assertThat(response.reversalReason())
                .isEqualTo(REVERSAL_REASON);

        /*
         * Les informations historiques du chargement
         * doivent rester disponibles.
         */
        assertThat(response.insertedRows())
                .isEqualTo(2);

        assertThat(response.originalFileName())
                .isEqualTo(
                        "maturites-valides.csv"
                );

        verify(
                policyMaturityRepository
        ).deleteAllInBatch(
                List.of(
                        rankOne,
                        rankTwo
                )
        );

        verify(
                importBatchRepository
        ).saveAndFlush(batch);

        verify(auditService).record(
                any(AuditRecordCommand.class)
        );
    }

    @Test
    @DisplayName(
            "L'annulation du dernier rang d'une police doit être autorisée"
    )
    void shouldAllowRemovalOfLastMaturityRank() {
        ImportBatchEntity firstBatch =
                importedBatch(
                        1L,
                        "rang-1.csv"
                );

        ImportBatchEntity secondBatch =
                importedBatch(
                        2L,
                        "rang-2.csv"
                );

        ImportBatchEntity thirdBatch =
                importedBatch(
                        3L,
                        "rang-3.csv"
                );

        PolicyMaturityEntity rankOne =
                maturity(
                        101L,
                        firstBatch,
                        "POL001",
                        1,
                        "2023-01-01",
                        "1000000.00",
                        "2030-01-01"
                );

        PolicyMaturityEntity rankTwo =
                maturity(
                        102L,
                        secondBatch,
                        "POL001",
                        2,
                        "2024-01-01",
                        "500000.00",
                        "2030-01-01"
                );

        PolicyMaturityEntity rankThree =
                maturity(
                        103L,
                        thirdBatch,
                        "POL001",
                        3,
                        "2025-01-01",
                        "600000.00",
                        "2030-01-01"
                );

        when(
                importBatchRepository
                        .findByIdForUpdate(3L)
        ).thenReturn(
                Optional.of(thirdBatch)
        );

        when(
                policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
                                3L
                        )
        ).thenReturn(
                List.of(rankThree)
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberForPaymentUpdate(
                                "POL001"
                        )
        ).thenReturn(
                List.of(
                        rankOne,
                        rankTwo,
                        rankThree
                )
        );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(List.of());

        when(
                importBatchRepository
                        .saveAndFlush(thirdBatch)
        ).thenReturn(thirdBatch);

        ImportBatchDetailResponse response =
                reversalService.reverseImportBatch(
                        3L,
                        request(),
                        ADMIN_USERNAME
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.REVERSED
                );

        verify(
                policyMaturityRepository
        ).deleteAllInBatch(
                List.of(rankThree)
        );

        verify(auditService).record(
                any(AuditRecordCommand.class)
        );
    }

    @Test
    @DisplayName(
            "Un lot inexistant doit produire une erreur not found"
    )
    void shouldRejectUnknownBatch() {
        when(
                importBatchRepository
                        .findByIdForUpdate(999L)
        ).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reversalService.reverseImportBatch(
                        999L,
                        request(),
                        ADMIN_USERNAME
                )
        )
                .isInstanceOf(
                        ImportBatchNotFoundException.class
                )
                .hasMessageContaining("999");

        verifyNoInteractions(
                policyMaturityRepository,
                paymentRepository,
                auditService
        );

        verify(
                importBatchRepository,
                never()
        ).saveAndFlush(
                any(ImportBatchEntity.class)
        );
    }

    @Test
    @DisplayName(
            "Un lot déjà annulé ne doit pas être annulé une seconde fois"
    )
    void shouldRejectAlreadyReversedBatch() {
        ImportBatchEntity batch =
                importedBatch(
                        10L,
                        "maturites.csv"
                );

        batch.setStatus(
                ImportBatchStatus.REVERSED
        );
        batch.setReversedAt(REVERSAL_TIME);
        batch.setReversedBy("other-admin");
        batch.setReversalReason(
                "Ancienne réversion."
        );

        when(
                importBatchRepository
                        .findByIdForUpdate(10L)
        ).thenReturn(
                Optional.of(batch)
        );

        assertThatThrownBy(() ->
                reversalService.reverseImportBatch(
                        10L,
                        request(),
                        ADMIN_USERNAME
                )
        )
                .isInstanceOf(
                        ImportBatchReversalNotAllowedException.class
                )
                .hasMessageContaining(
                        "déjà été annulé"
                );

        verifyNoInteractions(
                policyMaturityRepository,
                paymentRepository,
                auditService
        );

        verify(
                importBatchRepository,
                never()
        ).saveAndFlush(
                any(ImportBatchEntity.class)
        );
    }

    @Test
    @DisplayName(
            "Un lot rejeté ne doit pas pouvoir être annulé"
    )
    void shouldRejectRejectedBatch() {
        ImportBatchEntity batch =
                importedBatch(
                        10L,
                        "fichier-rejete.csv"
                );

        batch.setStatus(
                ImportBatchStatus.REJECTED
        );

        when(
                importBatchRepository
                        .findByIdForUpdate(10L)
        ).thenReturn(
                Optional.of(batch)
        );

        assertThatThrownBy(() ->
                reversalService.reverseImportBatch(
                        10L,
                        request(),
                        ADMIN_USERNAME
                )
        )
                .isInstanceOf(
                        ImportBatchReversalNotAllowedException.class
                )
                .hasMessageContaining(
                        "statut IMPORTED"
                );

        verifyNoInteractions(
                policyMaturityRepository,
                paymentRepository,
                auditService
        );
    }

    @Test
    @DisplayName(
            "Un lot importé sans maturité active ne doit pas être annulé"
    )
    void shouldRejectBatchWithoutActiveMaturity() {
        ImportBatchEntity batch =
                importedBatch(
                        10L,
                        "maturites.csv"
                );

        when(
                importBatchRepository
                        .findByIdForUpdate(10L)
        ).thenReturn(
                Optional.of(batch)
        );

        when(
                policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
                                10L
                        )
        ).thenReturn(List.of());

        assertThatThrownBy(() ->
                reversalService.reverseImportBatch(
                        10L,
                        request(),
                        ADMIN_USERNAME
                )
        )
                .isInstanceOf(
                        ImportBatchReversalNotAllowedException.class
                )
                .hasMessageContaining(
                        "aucune maturité"
                );

        verifyNoInteractions(
                paymentRepository,
                auditService
        );

        verify(
                policyMaturityRepository,
                never()
        ).deleteAllInBatch(any());

        verify(
                importBatchRepository,
                never()
        ).saveAndFlush(any());
    }

    @Test
    @DisplayName(
            "Un paiement PAID sur une police du lot doit bloquer la réversion"
    )
    void shouldRejectBatchUsedByPaidPayment() {
        ImportBatchEntity batch =
                importedBatch(
                        10L,
                        "maturites.csv"
                );

        PolicyMaturityEntity maturity =
                maturity(
                        101L,
                        batch,
                        "POL001",
                        1,
                        "2024-01-01",
                        "1000000.00",
                        "2030-01-01"
                );

        PaymentEntity paidPayment =
                paidPayment(
                        50L,
                        "POL001",
                        "2026-01-01"
                );

        when(
                importBatchRepository
                        .findByIdForUpdate(10L)
        ).thenReturn(
                Optional.of(batch)
        );

        when(
                policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
                                10L
                        )
        ).thenReturn(
                List.of(maturity)
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberForPaymentUpdate(
                                "POL001"
                        )
        ).thenReturn(
                List.of(maturity)
        );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(
                List.of(paidPayment)
        );

        assertThatThrownBy(() ->
                reversalService.reverseImportBatch(
                        10L,
                        request(),
                        ADMIN_USERNAME
                )
        )
                .isInstanceOf(
                        ImportBatchReversalNotAllowedException.class
                )
                .hasMessageContaining("POL001")
                .hasMessageContaining(
                        "a déjà participé "
                );

        verify(
                policyMaturityRepository,
                never()
        ).deleteAllInBatch(any());

        verify(
                importBatchRepository,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName(
            "Un paiement antérieur aux maturités du lot ne doit pas bloquer la réversion"
    )
    void shouldAllowReversalWhenPaidPaymentPredatesBatchMaturities() {
        ImportBatchEntity firstBatch =
                importedBatch(
                        1L,
                        "premieres-maturites.csv"
                );

        ImportBatchEntity secondBatch =
                importedBatch(
                        2L,
                        "maturites-suivantes.csv"
                );

        PolicyMaturityEntity firstMaturity =
                maturity(
                        101L,
                        firstBatch,
                        "POL001",
                        1,
                        "2021-03-15",
                        "1000000.00",
                        "2030-03-15"
                );

        PolicyMaturityEntity secondMaturity =
                maturity(
                        102L,
                        secondBatch,
                        "POL001",
                        2,
                        "2024-03-15",
                        "500000.00",
                        "2030-03-15"
                );

        PolicyMaturityEntity thirdMaturity =
                maturity(
                        103L,
                        secondBatch,
                        "POL001",
                        3,
                        "2025-03-15",
                        "750000.00",
                        "2030-03-15"
                );

        when(
                importBatchRepository
                        .findByIdForUpdate(2L)
        ).thenReturn(
                Optional.of(secondBatch)
        );

        when(
                policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
                                2L
                        )
        ).thenReturn(
                List.of(
                        secondMaturity,
                        thirdMaturity
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberForPaymentUpdate(
                                "POL001"
                        )
        ).thenReturn(
                List.of(
                        firstMaturity,
                        secondMaturity,
                        thirdMaturity
                )
        );

        /*
         * Ce paiement a été enregistré avant les maturités
         * introduites par le second lot.
         */
        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(
                List.of(
                        paidPayment(
                                50L,
                                "POL001",
                                "2023-03-15"
                        )
                )
        );

        when(
                importBatchRepository
                        .saveAndFlush(secondBatch)
        ).thenReturn(secondBatch);

        ImportBatchDetailResponse response =
                reversalService.reverseImportBatch(
                        2L,
                        request(),
                        ADMIN_USERNAME
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.REVERSED
                );

        verify(
                policyMaturityRepository
        ).deleteAllInBatch(
                List.of(
                        secondMaturity,
                        thirdMaturity
                )
        );

        verify(auditService).record(
                any(AuditRecordCommand.class)
        );
    }

    @Test
    @DisplayName(
            "Un paiement le jour d'une maturité du lot doit bloquer la réversion"
    )
    void shouldRejectReversalWhenPaymentOccursOnBatchMaturityDate() {
        ImportBatchEntity batch =
                importedBatch(
                        10L,
                        "maturites.csv"
                );

        PolicyMaturityEntity maturity =
                maturity(
                        101L,
                        batch,
                        "POL001",
                        1,
                        "2024-03-15",
                        "1000000.00",
                        "2030-03-15"
                );

        when(
                importBatchRepository
                        .findByIdForUpdate(10L)
        ).thenReturn(
                Optional.of(batch)
        );

        when(
                policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
                                10L
                        )
        ).thenReturn(
                List.of(maturity)
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberForPaymentUpdate(
                                "POL001"
                        )
        ).thenReturn(
                List.of(maturity)
        );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(
                List.of(
                        paidPayment(
                                50L,
                                "POL001",
                                "2024-03-15"
                        )
                )
        );

        assertThatThrownBy(() ->
                reversalService.reverseImportBatch(
                        10L,
                        request(),
                        ADMIN_USERNAME
                )
        )
                .isInstanceOf(
                        ImportBatchReversalNotAllowedException.class
                )
                .hasMessageContaining("POL001")
                .hasMessageContaining(
                        "a déjà participé"
                );

        verify(
                policyMaturityRepository,
                never()
        ).deleteAllInBatch(any());

        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName(
            "La réversion doit être refusée si elle crée une rupture de rang"
    )
    void shouldRejectReversalCreatingMaturityRankGap() {
        ImportBatchEntity firstBatch =
                importedBatch(
                        1L,
                        "rang-1.csv"
                );

        ImportBatchEntity secondBatch =
                importedBatch(
                        2L,
                        "rang-2.csv"
                );

        ImportBatchEntity thirdBatch =
                importedBatch(
                        3L,
                        "rang-3.csv"
                );

        PolicyMaturityEntity rankOne =
                maturity(
                        101L,
                        firstBatch,
                        "POL001",
                        1,
                        "2023-01-01",
                        "1000000.00",
                        "2030-01-01"
                );

        PolicyMaturityEntity rankTwo =
                maturity(
                        102L,
                        secondBatch,
                        "POL001",
                        2,
                        "2024-01-01",
                        "500000.00",
                        "2030-01-01"
                );

        PolicyMaturityEntity rankThree =
                maturity(
                        103L,
                        thirdBatch,
                        "POL001",
                        3,
                        "2025-01-01",
                        "600000.00",
                        "2030-01-01"
                );

        when(
                importBatchRepository
                        .findByIdForUpdate(2L)
        ).thenReturn(
                Optional.of(secondBatch)
        );

        when(
                policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
                                2L
                        )
        ).thenReturn(
                List.of(rankTwo)
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberForPaymentUpdate(
                                "POL001"
                        )
        ).thenReturn(
                List.of(
                        rankOne,
                        rankTwo,
                        rankThree
                )
        );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(List.of());

        assertThatThrownBy(() ->
                reversalService.reverseImportBatch(
                        2L,
                        request(),
                        ADMIN_USERNAME
                )
        )
                .isInstanceOf(
                        ImportBatchReversalNotAllowedException.class
                )
                .hasMessageContaining("POL001")
                .hasMessageContaining(
                        "rang 2"
                );

        verify(
                policyMaturityRepository,
                never()
        ).deleteAllInBatch(any());

        verify(
                importBatchRepository,
                never()
        ).saveAndFlush(any());

        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName(
            "L'audit doit contenir le lot, les polices et le nombre de maturités retirées"
    )
    void shouldRecordReversalAuditDetails() {
        ImportBatchEntity batch =
                importedBatch(
                        10L,
                        "maturites.csv"
                );

        PolicyMaturityEntity first =
                maturity(
                        101L,
                        batch,
                        "POL001",
                        1,
                        "2024-01-01",
                        "1000000.00",
                        "2030-01-01"
                );

        PolicyMaturityEntity second =
                maturity(
                        102L,
                        batch,
                        "POL002",
                        1,
                        "2025-01-01",
                        "2000000.00",
                        "2031-01-01"
                );

        when(
                importBatchRepository
                        .findByIdForUpdate(10L)
        ).thenReturn(
                Optional.of(batch)
        );

        when(
                policyMaturityRepository
                        .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(
                                10L
                        )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberForPaymentUpdate(
                                "POL001"
                        )
        ).thenReturn(
                List.of(first)
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberForPaymentUpdate(
                                "POL002"
                        )
        ).thenReturn(
                List.of(second)
        );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(List.of());

        when(
                importBatchRepository
                        .saveAndFlush(batch)
        ).thenReturn(batch);

        reversalService.reverseImportBatch(
                10L,
                request(),
                ADMIN_USERNAME
        );

        ArgumentCaptor<AuditRecordCommand>
                commandCaptor =
                ArgumentCaptor.forClass(
                        AuditRecordCommand.class
                );

        verify(auditService).record(
                commandCaptor.capture()
        );

        AuditRecordCommand command =
                commandCaptor.getValue();

        assertThat(command.resourceId())
                .isEqualTo("10");

        assertThat(command.actorUsername())
                .isEqualTo(ADMIN_USERNAME);

        assertThat(command.details())
                .containsEntry(
                        "removedMaturityCount",
                        2
                )
                .containsEntry(
                        "reversalReason",
                        REVERSAL_REASON
                )
                .containsEntry(
                        "previousStatus",
                        "IMPORTED"
                )
                .containsEntry(
                        "newStatus",
                        "REVERSED"
                );

        assertThat(command.details()
                .get("affectedPolicyNumbers"))
                .isEqualTo(
                        List.of(
                                "POL001",
                                "POL002"
                        )
                );
    }

    private ReverseImportBatchRequest request() {
        return new ReverseImportBatchRequest(
                REVERSAL_REASON
        );
    }

    private ImportBatchEntity importedBatch(
            Long id,
            String fileName
    ) {
        ImportBatchEntity batch =
                new ImportBatchEntity();

        batch.setId(id);
        batch.setOriginalFileName(fileName);
        batch.setFileSha256(
                "a".repeat(64)
        );
        batch.setFileSizeBytes(1024L);
        batch.setTotalRows(2);
        batch.setInsertedRows(2);
        batch.setExistingRows(0);
        batch.setErrorRows(0);
        batch.setStatus(
                ImportBatchStatus.IMPORTED
        );
        batch.setImportedAt(
                Instant.parse(
                        "2026-07-01T10:00:00Z"
                )
        );
        batch.setImportedBy("first-admin");
        batch.setCreatedAt(
                Instant.parse(
                        "2026-07-01T10:00:00Z"
                )
        );
        batch.setCreatedBy("first-admin");
        batch.setUpdatedAt(
                Instant.parse(
                        "2026-07-01T10:00:00Z"
                )
        );
        batch.setUpdatedBy("first-admin");

        return batch;
    }

    private PolicyMaturityEntity maturity(
            Long id,
            ImportBatchEntity batch,
            String policyNumber,
            int rank,
            String maturityDate,
            String amount,
            String interestEndDate
    ) {
        PolicyMaturityEntity maturity =
                new PolicyMaturityEntity();

        maturity.setId(id);
        maturity.setImportBatch(batch);
        maturity.setPolicyNumber(policyNumber);
        maturity.setMaturityType(
                "MATURITE_" + rank
        );
        maturity.setMaturityRank(rank);
        maturity.setMaturityDate(
                LocalDate.parse(
                        maturityDate
                )
        );
        maturity.setMaturityAmount(
                new BigDecimal(amount)
        );
        maturity.setInterestEndDate(
                LocalDate.parse(
                        interestEndDate
                )
        );
        maturity.setSourceRowNumber(
                rank + 1
        );

        return maturity;
    }

    private PaymentEntity paidPayment(
            Long id,
            String policyNumber,
            String paymentDate
    ) {
        PaymentEntity payment =
                new PaymentEntity();

        payment.setId(id);
        payment.setPolicyNumber(policyNumber);
        payment.setPaymentDate(
                LocalDate.parse(
                        paymentDate
                )
        );
        payment.setStatus(
                PaymentStatus.PAID
        );

        return payment;
    }
}