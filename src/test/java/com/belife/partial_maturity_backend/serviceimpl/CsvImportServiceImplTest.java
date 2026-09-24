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
import com.belife.partial_maturity_backend.services.models.MaturityImportRow;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de l'orchestration des imports.
 */
@ExtendWith(MockitoExtension.class)
class CsvImportServiceImplTest {

    private static final String DEFAULT_MATURITY_DATE =
            "2026-09-18";

    private static final String DEFAULT_END_DATE =
            "2030-03-15";

    @Mock
    private CsvMaturityParser csvMaturityParser;

    @Mock
    private PolicyMaturityRepository
            policyMaturityRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CsvImportPersistenceService
            persistenceService;

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
            "Une nouvelle police doit commencer au rang un"
    )
    void shouldAssignFirstRankToNewPolicy() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow source =
                row(
                        2,
                        "POL001",
                        "Client Exemple",
                        DEFAULT_MATURITY_DATE,
                        "2500000.00",
                        DEFAULT_END_DATE
                );

        configureParsedRows(
                file,
                List.of(source)
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of());

        CsvImportResponse expectedResponse =
                importedResponse(1);

        configureImportedResponse(
                1,
                expectedResponse
        );

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response)
                .isSameAs(expectedResponse);

        assertThat(captureImportedRows())
                .singleElement()
                .satisfies(importedRow -> {
                    assertThat(importedRow.rowNumber())
                            .isEqualTo(2);

                    assertThat(importedRow.policyNumber())
                            .isEqualTo("POL001");

                    assertThat(importedRow.clientName())
                            .isEqualTo("Client Exemple");

                    assertThat(importedRow.maturityRank())
                            .isEqualTo(1);

                    assertThat(importedRow.maturityType())
                            .isEqualTo("MATURITE_1");

                    assertThat(importedRow.maturityDate())
                            .isEqualTo(
                                    LocalDate.parse(
                                            DEFAULT_MATURITY_DATE
                                    )
                            );

                    assertThat(importedRow.maturityAmount())
                            .isEqualByComparingTo(
                                    "2500000.00"
                            );

                    assertThat(importedRow.interestEndDate())
                            .isEqualTo(
                                    LocalDate.parse(
                                            DEFAULT_END_DATE
                                    )
                            );
                });
    }

    @Test
    @DisplayName(
            "Chaque maturité doit conserver la date fournie dans le fichier"
    )
    void shouldPreserveMaturityDateFromEachCsvRow() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client A",
                                "2025-01-10",
                                "1000000.00",
                                DEFAULT_END_DATE
                        ),
                        row(
                                3,
                                "POL002",
                                "Client B",
                                "2026-06-15",
                                "2000000.00",
                                "2032-03-15"
                        ),
                        row(
                                4,
                                "POL001",
                                "Client A",
                                "2027-02-20",
                                "500000.00",
                                DEFAULT_END_DATE
                        )
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of());

        configureImportedResponse(
                3,
                importedResponse(3)
        );

        csvImportService.importFile(
                file,
                "admin"
        );

        List<MaturityImportRow> importedRows =
                captureImportedRows();

        assertThat(importedRows)
                .extracting(
                        MaturityImportRow::maturityDate
                )
                .containsExactly(
                        LocalDate.of(2025, 1, 10),
                        LocalDate.of(2026, 6, 15),
                        LocalDate.of(2027, 2, 20)
                );
    }

    @Test
    @DisplayName(
            "Le prochain rang doit suivre le rang maximal existant"
    )
    void shouldAssignRankAfterExistingMaximum() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
                                "2026-09-18",
                                "500000.00",
                                DEFAULT_END_DATE
                        )
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(
                        maturity(
                                "POL001",
                                "Client Exemple",
                                1,
                                "2024-01-01",
                                DEFAULT_END_DATE
                        ),
                        maturity(
                                "POL001",
                                "Client Exemple",
                                7,
                                "2025-01-01",
                                DEFAULT_END_DATE
                        )
                )
        );

        configureImportedResponse(
                1,
                importedResponse(1)
        );

        csvImportService.importFile(
                file,
                "admin"
        );

        assertThat(captureImportedRows())
                .singleElement()
                .satisfies(importedRow -> {
                    assertThat(importedRow.maturityRank())
                            .isEqualTo(8);

                    assertThat(importedRow.maturityType())
                            .isEqualTo("MATURITE_8");

                    assertThat(importedRow.maturityDate())
                            .isEqualTo(
                                    LocalDate.of(
                                            2026,
                                            9,
                                            18
                                    )
                            );
                });
    }

    @Test
    @DisplayName(
            "Plusieurs lignes doivent recevoir des rangs selon l'ordre du fichier"
    )
    void shouldAssignRanksInFileOrder() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
                                "2026-01-10",
                                "1000000.00",
                                DEFAULT_END_DATE
                        ),
                        row(
                                3,
                                "POL002",
                                "Autre Client",
                                "2026-02-15",
                                "2000000.00",
                                "2032-03-15"
                        ),
                        row(
                                4,
                                "POL001",
                                "Client Exemple",
                                "2026-06-20",
                                "3000000.00",
                                DEFAULT_END_DATE
                        ),
                        row(
                                5,
                                "POL001",
                                "Client Exemple",
                                "2027-01-05",
                                "4000000.00",
                                DEFAULT_END_DATE
                        )
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(
                        maturity(
                                "POL001",
                                "Client Exemple",
                                2,
                                "2025-01-01",
                                DEFAULT_END_DATE
                        )
                )
        );

        configureImportedResponse(
                4,
                importedResponse(4)
        );

        csvImportService.importFile(
                file,
                "admin"
        );

        List<MaturityImportRow> importedRows =
                captureImportedRows();

        assertThat(importedRows)
                .extracting(
                        MaturityImportRow::policyNumber
                )
                .containsExactly(
                        "POL001",
                        "POL002",
                        "POL001",
                        "POL001"
                );

        assertThat(importedRows)
                .extracting(
                        MaturityImportRow::maturityRank
                )
                .containsExactly(
                        3,
                        1,
                        4,
                        5
                );

        assertThat(importedRows)
                .extracting(
                        MaturityImportRow::maturityType
                )
                .containsExactly(
                        "MATURITE_3",
                        "MATURITE_1",
                        "MATURITE_4",
                        "MATURITE_5"
                );

        assertThat(importedRows)
                .extracting(
                        MaturityImportRow::maturityDate
                )
                .containsExactly(
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 2, 15),
                        LocalDate.of(2026, 6, 20),
                        LocalDate.of(2027, 1, 5)
                );
    }

    @Test
    @DisplayName(
            "Deux lignes identiques doivent créer deux maturités"
    )
    void shouldKeepIdenticalRowsAsDifferentMaturities() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow first =
                row(
                        2,
                        "POL001",
                        "Client Exemple",
                        "2026-09-18",
                        "2500000.00",
                        DEFAULT_END_DATE
                );

        ParsedMaturityRow second =
                row(
                        3,
                        "POL001",
                        "Client Exemple",
                        "2026-09-18",
                        "2500000.00",
                        DEFAULT_END_DATE
                );

        configureParsedRows(
                file,
                List.of(
                        first,
                        second
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(List.of());

        configureImportedResponse(
                2,
                importedResponse(2)
        );

        csvImportService.importFile(
                file,
                "admin"
        );

        List<MaturityImportRow> importedRows =
                captureImportedRows();

        assertThat(importedRows)
                .hasSize(2);

        assertThat(importedRows)
                .extracting(
                        MaturityImportRow::maturityRank
                )
                .containsExactly(1, 2);

        assertThat(importedRows)
                .extracting(
                        MaturityImportRow::maturityDate
                )
                .containsOnly(
                        LocalDate.of(
                                2026,
                                9,
                                18
                        )
                );

        assertThat(importedRows)
                .extracting(
                        MaturityImportRow::maturityAmount
                )
                .allSatisfy(amount ->
                        assertThat(amount)
                                .isEqualByComparingTo(
                                        "2500000.00"
                                )
                );
    }

    @Test
    @DisplayName(
            "Un rang supérieur à quatre doit rester autorisé"
    )
    void shouldAllowUnlimitedMaturityRanks() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
                                "2026-09-18",
                                "500000.00",
                                DEFAULT_END_DATE
                        )
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(
                        maturity(
                                "POL001",
                                "Client Exemple",
                                25,
                                "2025-01-01",
                                DEFAULT_END_DATE
                        )
                )
        );

        configureImportedResponse(
                1,
                importedResponse(1)
        );

        csvImportService.importFile(
                file,
                "admin"
        );

        assertThat(captureImportedRows())
                .singleElement()
                .satisfies(importedRow -> {
                    assertThat(importedRow.maturityRank())
                            .isEqualTo(26);

                    assertThat(importedRow.maturityType())
                            .isEqualTo(
                                    "MATURITE_26"
                            );
                });
    }

    @Test
    @DisplayName(
            "Une erreur du parseur doit historiser un rejet"
    )
    void shouldRejectParsingErrors() {
        MockMultipartFile file =
                createUploadedFile();

        List<CsvValidationError> errors =
                List.of(
                        new CsvValidationError(
                                2,
                                "date_maturite",
                                "MISSING_MATURITY_DATE",
                                "La date de maturité est obligatoire."
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

        verifyNoInteractions(
                policyMaturityRepository
        );

        verifyNoInteractions(
                paymentRepository
        );

        verify(
                persistenceService,
                never()
        ).saveImportedBatch(
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
            "Un nom différent de celui enregistré doit rejeter le fichier"
    )
    void shouldRejectDifferentExistingClientName() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Nouveau Client",
                                "2026-09-18",
                                "500000.00",
                                DEFAULT_END_DATE
                        )
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(
                        maturity(
                                "POL001",
                                "Client Existant",
                                1,
                                "2025-01-01",
                                DEFAULT_END_DATE
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
                .anySatisfy(error -> {
                    assertThat(error.column())
                            .isEqualTo("nom_client");

                    assertThat(error.code())
                            .isEqualTo(
                                    "INCONSISTENT_CLIENT_NAME"
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
                anyList(),
                anyString()
        );
    }

    @Test
    @DisplayName(
            "La valeur historique du client ne doit pas bloquer un vrai nom"
    )
    void shouldAllowClientNameAfterHistoricalPlaceholder() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
                                "2026-09-18",
                                "500000.00",
                                DEFAULT_END_DATE
                        )
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(
                        maturity(
                                "POL001",
                                "CLIENT NON RENSEIGNE",
                                1,
                                "2025-01-01",
                                DEFAULT_END_DATE
                        )
                )
        );

        configureImportedResponse(
                1,
                importedResponse(1)
        );

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.IMPORTED
                );

        assertThat(captureImportedRows())
                .singleElement()
                .extracting(
                        MaturityImportRow::clientName
                )
                .isEqualTo("Client Exemple");
    }

    @Test
    @DisplayName(
            "Une date de fin différente de la base doit rejeter le fichier"
    )
    void shouldRejectDifferentExistingInterestEndDate() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
                                "2026-09-18",
                                "500000.00",
                                "2031-03-15"
                        )
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(
                        maturity(
                                "POL001",
                                "Client Exemple",
                                1,
                                "2025-01-01",
                                DEFAULT_END_DATE
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
                .anySatisfy(error -> {
                    assertThat(error.column())
                            .isEqualTo(
                                    "date_fin_interets"
                            );

                    assertThat(error.code())
                            .isEqualTo(
                                    "INCONSISTENT_INTEREST_END_DATE"
                            );
                });
    }

    @Test
    @DisplayName(
            "Une maturité postérieure au dernier paiement doit être acceptée"
    )
    void shouldAllowMaturityAfterLastPaidPayment() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
                                "2026-09-18",
                                "500000.00",
                                DEFAULT_END_DATE
                        )
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(
                        maturity(
                                "POL001",
                                "Client Exemple",
                                1,
                                "2025-01-01",
                                DEFAULT_END_DATE
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
                                "POL001",
                                "2026-09-17"
                        )
                )
        );

        configureImportedResponse(
                1,
                importedResponse(1)
        );

        CsvImportResponse response =
                csvImportService.importFile(
                        file,
                        "admin"
                );

        assertThat(response.status())
                .isEqualTo(
                        ImportBatchStatus.IMPORTED
                );

        assertThat(captureImportedRows())
                .singleElement()
                .extracting(
                        MaturityImportRow::maturityDate
                )
                .isEqualTo(
                        LocalDate.of(
                                2026,
                                9,
                                18
                        )
                );
    }

    @Test
    @DisplayName(
            "Une maturité le jour du dernier paiement doit être rejetée"
    )
    void shouldRejectMaturityOnLastPaidPaymentDate() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
                                "2026-09-18",
                                "500000.00",
                                DEFAULT_END_DATE
                        )
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(
                        maturity(
                                "POL001",
                                "Client Exemple",
                                1,
                                "2025-01-01",
                                DEFAULT_END_DATE
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
                                "POL001",
                                "2026-09-18"
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
                .anySatisfy(error -> {
                    assertThat(error.column())
                            .isEqualTo(
                                    "date_maturite"
                            );

                    assertThat(error.code())
                            .isEqualTo(
                                    "MATURITY_NOT_AFTER_LAST_PAYMENT"
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
                anyList(),
                anyString()
        );
    }

    @Test
    @DisplayName(
            "Une maturité antérieure au dernier paiement doit être rejetée"
    )
    void shouldRejectMaturityBeforeLastPaidPayment() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
                                "2026-09-17",
                                "500000.00",
                                DEFAULT_END_DATE
                        )
                )
        );

        when(
                policyMaturityRepository
                        .findAllByPolicyNumberIn(
                                anyCollection()
                        )
        ).thenReturn(
                List.of(
                        maturity(
                                "POL001",
                                "Client Exemple",
                                1,
                                "2025-01-01",
                                DEFAULT_END_DATE
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
                                "POL001",
                                "2026-09-18"
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
                .anySatisfy(error -> {
                    assertThat(error.column())
                            .isEqualTo(
                                    "date_maturite"
                            );

                    assertThat(error.code())
                            .isEqualTo(
                                    "MATURITY_NOT_AFTER_LAST_PAYMENT"
                            );
                });
    }

    private void configureParsedRows(
            MockMultipartFile file,
            List<ParsedMaturityRow> rows
    ) {
        when(csvMaturityParser.parse(file))
                .thenReturn(
                        new CsvValidationResult(
                                rows.size(),
                                rows,
                                List.of()
                        )
                );
    }

    private void configureImportedResponse(
            int totalRows,
            CsvImportResponse response
    ) {
        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(totalRows),
                                anyList(),
                                eq("admin")
                        )
        ).thenReturn(response);
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

    @SuppressWarnings("unchecked")
    private List<MaturityImportRow>
    captureImportedRows() {
        ArgumentCaptor<List<MaturityImportRow>>
                rowsCaptor =
                ArgumentCaptor.forClass(
                        List.class
                );

        verify(persistenceService)
                .saveImportedBatch(
                        eq("maturites.csv"),
                        argThat(hash ->
                                hash != null
                                        && hash.length() == 64
                        ),
                        anyLong(),
                        anyInt(),
                        rowsCaptor.capture(),
                        eq("admin")
                );

        return rowsCaptor.getValue();
    }

    private MockMultipartFile
    createUploadedFile() {
        return csv(
                "maturites.csv",
                """
                num_police;nom_client;date_maturite;montant_maturite;date_fin_interets
                POL001;Client Exemple;2026-09-18;2500000.00;2030-03-15
                """
        );
    }

    private ParsedMaturityRow row(
            int rowNumber,
            String policyNumber,
            String clientName,
            String maturityDate,
            String amount,
            String interestEndDate
    ) {
        return new ParsedMaturityRow(
                rowNumber,
                policyNumber,
                clientName,
                LocalDate.parse(maturityDate),
                new BigDecimal(amount),
                LocalDate.parse(interestEndDate)
        );
    }

    private PolicyMaturityEntity maturity(
            String policyNumber,
            String clientName,
            int rank,
            String maturityDate,
            String interestEndDate
    ) {
        PolicyMaturityEntity entity =
                new PolicyMaturityEntity();

        entity.setId(100L + rank);
        entity.setPolicyNumber(policyNumber);
        entity.setClientName(clientName);
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
                new BigDecimal("1000000.00")
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

    private CsvImportResponse importedResponse(
            int insertedRows
    ) {
        return new CsvImportResponse(
                1L,
                "maturites.csv",
                ImportBatchStatus.IMPORTED,
                insertedRows,
                insertedRows,
                0,
                0,
                Instant.parse(
                        "2026-09-18T10:00:00Z"
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
                        "2026-09-18T10:00:00Z"
                ),
                errors
        );
    }
}