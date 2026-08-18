package com.belife.partial_maturity_backend.serviceimpl;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.CsvImportPersistenceService;
import com.belife.partial_maturity_backend.services.CsvMaturityParser;
import com.belife.partial_maturity_backend.services.Impl.CsvImportServiceImpl;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;
import com.belife.partial_maturity_backend.services.models.CsvValidationResult;
import com.belife.partial_maturity_backend.services.models.ParsedMaturityRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.belife.partial_maturity_backend.testutils.CsvTestFileFactory.csv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires de l'orchestration métier des imports.
 *
 * <p>Le parseur, les repositories et la persistance
 * transactionnelle sont simulés avec Mockito.</p>
 */
@ExtendWith(MockitoExtension.class)
class CsvImportServiceImplTest {

    private static final String DEFAULT_INTEREST_END_DATE =
            "2030-03-15";

    @Mock
    private CsvMaturityParser csvMaturityParser;

    @Mock
    private PolicyMaturityRepository policyMaturityRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CsvImportPersistenceService persistenceService;

    private CsvImportServiceImpl csvImportService;

    @BeforeEach
    void setUp() {
        csvImportService =
                new CsvImportServiceImpl(
                        csvMaturityParser,
                        policyMaturityRepository,
                        paymentRepository,
                        persistenceService
                );

        lenient()
                .when(
                        paymentRepository
                                .findAllByPolicyNumbersAndStatus(
                                        anyCollection(),
                                        eq(PaymentStatus.PAID)
                                )
                )
                .thenReturn(List.of());
    }

    @Test
    @DisplayName(
            "Un fichier valide sans maturité existante doit être importé"
    )
    void shouldImportValidFile() {
        MockMultipartFile file =
                createUploadedFile();

        List<ParsedMaturityRow> rows =
                List.of(
                        row(
                                2,
                                "POL001",
                                1,
                                "2020-03-15",
                                "2500000.00",
                                "2030-03-15"
                        ),
                        row(
                                3,
                                "POL002",
                                1,
                                "2021-06-10",
                                "3000000.00",
                                "2031-06-10"
                        )
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                2,
                                rows,
                                List.of()
                        )
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of());

        CsvImportResponse expectedResponse =
                importedResponse(2, 0);

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(2),
                                eq(0),
                                anyList(),
                                eq("admin")
                        )
        ).thenReturn(expectedResponse);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response)
                .isSameAs(expectedResponse);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ParsedMaturityRow>>
                rowsCaptor =
                ArgumentCaptor.forClass(
                        List.class
                );

        verify(persistenceService)
                .saveImportedBatch(
                        eq("maturites.csv"),
                        argThat(
                                hash ->
                                        hash != null
                                                && hash.length() == 64
                        ),
                        eq(file.getSize()),
                        eq(2),
                        eq(0),
                        rowsCaptor.capture(),
                        eq("admin")
                );

        assertThat(rowsCaptor.getValue())
                .containsExactlyElementsOf(rows);

        verify(
                persistenceService,
                never()
        ).saveRejectedBatch(
                anyString(),
                anyString(),
                anyLong(),
                anyInt(),
                anyList(),
                anyString()
        );
    }

    @Test
    @DisplayName(
            "Une erreur syntaxique doit historiser un lot rejeté"
    )
    void shouldRejectParsingErrors() {
        MockMultipartFile file =
                createUploadedFile();

        List<CsvValidationError> errors =
                List.of(
                        new CsvValidationError(
                                2,
                                "date_fin_interets",
                                "INVALID_INTEREST_END_DATE",
                                "La date est invalide."
                        )
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                1,
                                List.of(),
                                errors
                        )
                );

        CsvImportResponse expectedResponse =
                rejectedResponse(
                        1,
                        errors
                );

        when(
                persistenceService
                        .saveRejectedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(1),
                                eq(errors),
                                eq("admin")
                        )
        ).thenReturn(expectedResponse);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response)
                .isSameAs(expectedResponse);

        verify(persistenceService)
                .saveRejectedBatch(
                        eq("maturites.csv"),
                        anyString(),
                        eq(file.getSize()),
                        eq(1),
                        eq(errors),
                        eq("admin")
                );

        verifyNoInteractions(
                policyMaturityRepository
        );

        verify(
                persistenceService,
                never()
        ).saveImportedBatch(
                anyString(),
                anyString(),
                anyLong(),
                anyInt(),
                anyInt(),
                anyList(),
                anyString()
        );
    }

    @Test
    @DisplayName(
            "Une maturité historique identique doit être comptée comme existante"
    )
    void shouldCountIdenticalExistingMaturity() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow incoming =
                row(
                        2,
                        "POL001",
                        1,
                        "2020-03-15",
                        "2500000.00",
                        DEFAULT_INTEREST_END_DATE
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                1,
                                List.of(incoming),
                                List.of()
                        )
                );

        PolicyMaturityEntity existing =
                maturityEntity(
                        "POL001",
                        1,
                        "2020-03-15",
                        "2500000.000000",
                        DEFAULT_INTEREST_END_DATE
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of(existing));

        CsvImportResponse expectedResponse =
                importedResponse(0, 1);

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(1),
                                eq(1),
                                eq(List.of()),
                                eq("admin")
                        )
        ).thenReturn(expectedResponse);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.insertedRows())
                .isZero();

        assertThat(response.existingRows())
                .isEqualTo(1);

        verify(persistenceService)
                .saveImportedBatch(
                        eq("maturites.csv"),
                        anyString(),
                        eq(file.getSize()),
                        eq(1),
                        eq(1),
                        eq(List.of()),
                        eq("admin")
                );
    }

    @Test
    @DisplayName(
            "Une maturité historique contradictoire doit rejeter tout le fichier"
    )
    void shouldRejectHistoricalContradiction() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow incoming =
                row(
                        2,
                        "POL001",
                        1,
                        "2020-03-15",
                        "2500000.00",
                        DEFAULT_INTEREST_END_DATE
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                1,
                                List.of(incoming),
                                List.of()
                        )
                );

        PolicyMaturityEntity existing =
                maturityEntity(
                        "POL001",
                        1,
                        "2020-03-16",
                        "2500000.00",
                        DEFAULT_INTEREST_END_DATE
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of(existing));

        configureRejectedResponse(1);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.REJECTED
                );

        assertThat(response.errors())
                .extracting("code")
                .contains(
                        "CONTRADICTORY_MATURITY"
                );

        verify(
                persistenceService,
                never()
        ).saveImportedBatch(
                anyString(),
                anyString(),
                anyLong(),
                anyInt(),
                anyInt(),
                anyList(),
                anyString()
        );
    }

    @Test
    @DisplayName(
            "Une date de fin différente de la base doit rejeter le fichier"
    )
    void shouldRejectDifferentHistoricalInterestEndDate() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow incoming =
                row(
                        2,
                        "POL001",
                        2,
                        "2021-03-15",
                        "3000000.00",
                        "2031-03-15"
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                1,
                                List.of(incoming),
                                List.of()
                        )
                );

        PolicyMaturityEntity existing =
                maturityEntity(
                        "POL001",
                        1,
                        "2020-03-15",
                        "2500000.00",
                        "2030-03-15"
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of(existing));

        configureRejectedResponse(1);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.REJECTED
                );

        assertThat(response.errors())
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.code())
                            .isEqualTo(
                                    "INCONSISTENT_INTEREST_END_DATE"
                            );

                    assertThat(error.column())
                            .isEqualTo(
                                    "date_fin_interets"
                            );

                    assertThat(error.message())
                            .contains(
                                    "2030-03-15"
                            );
                });

        verify(
                persistenceService,
                never()
        ).saveImportedBatch(
                anyString(),
                anyString(),
                anyLong(),
                anyInt(),
                anyInt(),
                anyList(),
                anyString()
        );
    }

    @Test
    @DisplayName(
            "Une nouvelle maturité doit conserver la date de fin existante"
    )
    void shouldImportNewMaturityWithExistingInterestEndDate() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow incoming =
                row(
                        2,
                        "POL001",
                        2,
                        "2021-03-15",
                        "3000000.00",
                        DEFAULT_INTEREST_END_DATE
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                1,
                                List.of(incoming),
                                List.of()
                        )
                );

        PolicyMaturityEntity existing =
                maturityEntity(
                        "POL001",
                        1,
                        "2020-03-15",
                        "2500000.00",
                        DEFAULT_INTEREST_END_DATE
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of(existing));

        CsvImportResponse expectedResponse =
                importedResponse(1, 0);

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(1),
                                eq(0),
                                eq(List.of(incoming)),
                                eq("admin")
                        )
        ).thenReturn(expectedResponse);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.IMPORTED
                );

        assertThat(response.insertedRows())
                .isEqualTo(1);

        verify(persistenceService)
                .saveImportedBatch(
                        eq("maturites.csv"),
                        anyString(),
                        eq(file.getSize()),
                        eq(1),
                        eq(0),
                        eq(List.of(incoming)),
                        eq("admin")
                );
    }

    @Test
    @DisplayName(
            "Des dates de fin différentes restent autorisées pour des polices différentes"
    )
    void shouldAllowDifferentInterestEndDatesForDifferentPolicies() {
        MockMultipartFile file =
                createUploadedFile();

        List<ParsedMaturityRow> rows =
                List.of(
                        row(
                                2,
                                "POL001",
                                1,
                                "2020-03-15",
                                "2500000.00",
                                "2030-03-15"
                        ),
                        row(
                                3,
                                "POL002",
                                1,
                                "2021-06-10",
                                "3000000.00",
                                "2035-06-10"
                        )
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                2,
                                rows,
                                List.of()
                        )
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of());

        CsvImportResponse expectedResponse =
                importedResponse(2, 0);

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(2),
                                eq(0),
                                eq(rows),
                                eq("admin")
                        )
        ).thenReturn(expectedResponse);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response)
                .isSameAs(expectedResponse);
    }

    @Test
    @DisplayName(
            "MATURITE_2 sans MATURITE_1 doit être rejetée"
    )
    void shouldRejectMissingPreviousMaturity() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow rankTwo =
                row(
                        2,
                        "POL-GAP",
                        2,
                        "2025-01-01",
                        "1000000.00",
                        "2030-01-01"
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                1,
                                List.of(rankTwo),
                                List.of()
                        )
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of());

        configureRejectedResponse(1);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.REJECTED
                );

        assertThat(response.errors())
                .extracting("code")
                .contains(
                        "MISSING_PREVIOUS_MATURITY"
                );
    }

    @Test
    @DisplayName(
            "Une maturité suivante antérieure à la précédente doit être rejetée"
    )
    void shouldRejectInvalidDateSequence() {
        MockMultipartFile file =
                createUploadedFile();

        List<ParsedMaturityRow> rows =
                List.of(
                        row(
                                2,
                                "POL-DATE",
                                1,
                                "2025-01-01",
                                "1000000.00",
                                "2030-01-01"
                        ),
                        row(
                                3,
                                "POL-DATE",
                                2,
                                "2024-01-01",
                                "1000000.00",
                                "2030-01-01"
                        )
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                2,
                                rows,
                                List.of()
                        )
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of());

        configureRejectedResponse(2);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.REJECTED
                );

        assertThat(response.errors())
                .extracting("code")
                .contains(
                        "INVALID_MATURITY_DATE_SEQUENCE"
                );
    }

    @Test
    @DisplayName(
            "Un doublon interne identique doit être dédupliqué"
    )
    void shouldDeduplicateIdenticalRowsInsideFile() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow first =
                row(
                        2,
                        "POL001",
                        1,
                        "2020-03-15",
                        "2500000.00",
                        DEFAULT_INTEREST_END_DATE
                );

        ParsedMaturityRow duplicate =
                row(
                        3,
                        "POL001",
                        1,
                        "2020-03-15",
                        "2500000.000000",
                        DEFAULT_INTEREST_END_DATE
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                2,
                                List.of(
                                        first,
                                        duplicate
                                ),
                                List.of()
                        )
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of());

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(2),
                                eq(1),
                                anyList(),
                                eq("admin")
                        )
        ).thenReturn(
                importedResponse(1, 1)
        );

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.insertedRows())
                .isEqualTo(1);

        assertThat(response.existingRows())
                .isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ParsedMaturityRow>>
                rowsCaptor =
                ArgumentCaptor.forClass(
                        List.class
                );

        verify(persistenceService)
                .saveImportedBatch(
                        anyString(),
                        anyString(),
                        anyLong(),
                        eq(2),
                        eq(1),
                        rowsCaptor.capture(),
                        eq("admin")
                );

        assertThat(rowsCaptor.getValue())
                .containsExactly(first);
    }

    @Test
    @DisplayName(
            "Une nouvelle maturité postérieure au dernier paiement PAID doit être acceptée"
    )
    void shouldAllowMaturityAfterLastPaidPayment() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow incoming =
                row(
                        2,
                        "TEST-PAY-001",
                        2,
                        "2024-03-15",
                        "500000.00",
                        "2030-03-15"
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                1,
                                List.of(incoming),
                                List.of()
                        )
                );

        PolicyMaturityEntity existingMaturity =
                maturityEntity(
                        "TEST-PAY-001",
                        1,
                        "2021-03-15",
                        "1000000.00",
                        "2030-03-15"
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(existingMaturity)
        );

        PaymentEntity existingPayment =
                paidPayment(
                        10L,
                        "TEST-PAY-001",
                        "2023-03-15"
                );

        when(
                paymentRepository
                        .findAllByPolicyNumbersAndStatus(
                                anyCollection(),
                                eq(PaymentStatus.PAID)
                        )
        ).thenReturn(
                List.of(existingPayment)
        );

        CsvImportResponse expectedResponse =
                importedResponse(1, 0);

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(1),
                                eq(0),
                                eq(List.of(incoming)),
                                eq("admin")
                        )
        ).thenReturn(expectedResponse);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.IMPORTED
                );

        assertThat(response.insertedRows())
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Une nouvelle maturité antérieure au dernier paiement PAID doit être rejetée"
    )
    void shouldRejectMaturityBeforeLastPaidPayment() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow incoming =
                row(
                        2,
                        "TEST-PAY-001",
                        2,
                        "2022-03-15",
                        "500000.00",
                        "2030-03-15"
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                1,
                                List.of(incoming),
                                List.of()
                        )
                );

        PolicyMaturityEntity existingMaturity =
                maturityEntity(
                        "TEST-PAY-001",
                        1,
                        "2021-03-15",
                        "1000000.00",
                        "2030-03-15"
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(existingMaturity)
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
                                10L,
                                "TEST-PAY-001",
                                "2023-03-15"
                        )
                )
        );

        configureRejectedResponse(1);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.REJECTED
                );

        assertThat(response.errors())
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.code())
                            .isEqualTo(
                                    "MATURITY_NOT_AFTER_LAST_PAYMENT"
                            );

                    assertThat(error.column())
                            .isEqualTo(
                                    "date_maturite"
                            );

                    assertThat(error.message())
                            .contains(
                                    "2023-03-15"
                            );
                });

        verify(
                persistenceService,
                never()
        ).saveImportedBatch(
                anyString(),
                anyString(),
                anyLong(),
                anyInt(),
                anyInt(),
                anyList(),
                anyString()
        );
    }

    @Test
    @DisplayName(
            "Une nouvelle maturité le jour du dernier paiement PAID doit être rejetée"
    )
    void shouldRejectMaturityOnLastPaidPaymentDate() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow incoming =
                row(
                        2,
                        "TEST-PAY-001",
                        2,
                        "2023-03-15",
                        "500000.00",
                        "2030-03-15"
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                1,
                                List.of(incoming),
                                List.of()
                        )
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(
                        maturityEntity(
                                "TEST-PAY-001",
                                1,
                                "2021-03-15",
                                "1000000.00",
                                "2030-03-15"
                        )
                )
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
                                10L,
                                "TEST-PAY-001",
                                "2023-03-15"
                        )
                )
        );

        configureRejectedResponse(1);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.errors())
                .extracting("code")
                .containsExactly(
                        "MATURITY_NOT_AFTER_LAST_PAYMENT"
                );
    }

    @Test
    @DisplayName(
            "Une maturité historique identique reste autorisée après un paiement ultérieur"
    )
    void shouldAllowIdenticalMaturityAfterLaterPayment() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow incoming =
                row(
                        2,
                        "TEST-PAY-001",
                        1,
                        "2021-03-15",
                        "1000000.00",
                        "2030-03-15"
                );

        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                1,
                                List.of(incoming),
                                List.of()
                        )
                );

        PolicyMaturityEntity existing =
                maturityEntity(
                        "TEST-PAY-001",
                        1,
                        "2021-03-15",
                        "1000000.000000",
                        "2030-03-15"
                );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(existing)
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
                                10L,
                                "TEST-PAY-001",
                                "2023-03-15"
                        )
                )
        );

        CsvImportResponse expectedResponse =
                importedResponse(0, 1);

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(1),
                                eq(1),
                                eq(List.of()),
                                eq("admin")
                        )
        ).thenReturn(expectedResponse);

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.insertedRows())
                .isZero();

        assertThat(response.existingRows())
                .isEqualTo(1);
    }

    private void configureRejectedResponse(
            int totalRows
    ) {
        when(
                persistenceService
                        .saveRejectedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(totalRows),
                                anyList(),
                                eq("admin")
                        )
        ).thenAnswer(invocation -> {
            List<CsvValidationError> errors =
                    invocation.getArgument(4);

            return rejectedResponse(
                    totalRows,
                    errors
            );
        });
    }

    private MockMultipartFile createUploadedFile() {
        return csv(
                "maturites.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;2500000.00;2030-03-15
                """
        );
    }

    private ParsedMaturityRow row(
            int rowNumber,
            String policyNumber,
            int rank,
            String maturityDate,
            String amount,
            String interestEndDate
    ) {
        return new ParsedMaturityRow(
                rowNumber,
                policyNumber,
                "MATURITE_" + rank,
                rank,
                LocalDate.parse(
                        maturityDate
                ),
                new BigDecimal(amount),
                LocalDate.parse(
                        interestEndDate
                )
        );
    }

    private PolicyMaturityEntity maturityEntity(
            String policyNumber,
            int rank,
            String maturityDate,
            String amount,
            String interestEndDate
    ) {
        PolicyMaturityEntity entity =
                new PolicyMaturityEntity();

        entity.setId(100L + rank);
        entity.setPolicyNumber(policyNumber);
        entity.setMaturityType(
                "MATURITE_" + rank
        );
        entity.setMaturityRank(rank);
        entity.setMaturityDate(
                LocalDate.parse(
                        maturityDate
                )
        );
        entity.setMaturityAmount(
                new BigDecimal(amount)
        );
        entity.setInterestEndDate(
                LocalDate.parse(
                        interestEndDate
                )
        );
        entity.setSourceRowNumber(2);
        entity.setImportBatch(
                new ImportBatchEntity()
        );

        return entity;
    }

    private CsvImportResponse importedResponse(
            int insertedRows,
            int existingRows
    ) {
        return new CsvImportResponse(
                1L,
                "maturites.csv",
                ImportBatchStatus.IMPORTED,
                insertedRows + existingRows,
                insertedRows,
                existingRows,
                0,
                Instant.parse(
                        "2026-07-28T10:00:00Z"
                ),
                List.of()
        );
    }

    private CsvImportResponse rejectedResponse(
            int totalRows,
            List<CsvValidationError> errors
    ) {
        return new CsvImportResponse(
                1L,
                "maturites.csv",
                ImportBatchStatus.REJECTED,
                totalRows,
                0,
                0,
                errors.size(),
                Instant.parse(
                        "2026-07-28T10:00:00Z"
                ),
                errors
        );
    }

    private PaymentEntity paidPayment(
            Long id,
            String policyNumber,
            String paymentDate
    ) {
        PaymentEntity payment =
                new PaymentEntity();

        payment.setId(id);
        payment.setPolicyNumber(
                policyNumber
        );
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