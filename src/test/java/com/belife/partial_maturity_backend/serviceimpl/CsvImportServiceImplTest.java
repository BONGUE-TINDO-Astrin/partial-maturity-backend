package com.belife.partial_maturity_backend.serviceimpl;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import com.belife.partial_maturity_backend.repositories.PaymentRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.BusinessDateProvider;
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
import static org.mockito.ArgumentMatchers.*;
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

    private static final LocalDate BUSINESS_DATE =
            LocalDate.of(
                    2026,
                    9,
                    18
            );

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

    @Mock
    private BusinessDateProvider
            businessDateProvider;

    private CsvImportServiceImpl csvImportService;

    @BeforeEach
    void setUp() {
        csvImportService =
                new CsvImportServiceImpl(
                        csvMaturityParser,
                        policyMaturityRepository,
                        paymentRepository,
                        persistenceService,
                        businessDateProvider
                );

        lenient()
                .when(
                        businessDateProvider
                                .currentDate()
                )
                .thenReturn(BUSINESS_DATE);

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

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(1),
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

        List<MaturityImportRow> persistedRows =
                captureImportedRows();

        assertThat(persistedRows)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.rowNumber())
                            .isEqualTo(2);

                    assertThat(row.policyNumber())
                            .isEqualTo("POL001");

                    assertThat(row.clientName())
                            .isEqualTo("Client Exemple");

                    assertThat(row.maturityRank())
                            .isEqualTo(1);

                    assertThat(row.maturityType())
                            .isEqualTo("MATURITE_1");

                    assertThat(row.maturityDate())
                            .isEqualTo(BUSINESS_DATE);

                    assertThat(row.maturityAmount())
                            .isEqualByComparingTo(
                                    "2500000.00"
                            );
                });
    }

    @Test
    @DisplayName(
            "Toutes les lignes du chargement doivent utiliser la date métier"
    )
    void shouldUseBusinessDateForEveryRow() {
        MockMultipartFile file =
                createUploadedFile();

        List<ParsedMaturityRow> sourceRows =
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client A",
                                "1000000.00",
                                DEFAULT_END_DATE
                        ),
                        row(
                                3,
                                "POL002",
                                "Client B",
                                "2000000.00",
                                "2032-03-15"
                        )
                );

        configureParsedRows(
                file,
                sourceRows
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
                                anyList(),
                                eq("admin")
                        )
        ).thenReturn(
                importedResponse(2)
        );

        csvImportService.importFile(
                file,
                "admin"
        );

        List<MaturityImportRow> persistedRows =
                captureImportedRows();

        assertThat(persistedRows)
                .extracting(
                        MaturityImportRow::maturityDate
                )
                .containsOnly(BUSINESS_DATE);
    }

    @Test
    @DisplayName(
            "Le prochain rang doit suivre le rang maximal existant"
    )
    void shouldAssignRankAfterExistingMaximum() {
        MockMultipartFile file =
                createUploadedFile();

        ParsedMaturityRow source =
                row(
                        2,
                        "POL001",
                        "Client Exemple",
                        "500000.00",
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

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(1),
                                anyList(),
                                eq("admin")
                        )
        ).thenReturn(
                importedResponse(1)
        );

        csvImportService.importFile(
                file,
                "admin"
        );

        assertThat(captureImportedRows())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.maturityRank())
                            .isEqualTo(8);

                    assertThat(row.maturityType())
                            .isEqualTo("MATURITE_8");
                });
    }

    @Test
    @DisplayName(
            "Plusieurs lignes d'une police doivent recevoir des rangs successifs"
    )
    void shouldAssignRanksInFileOrder() {
        MockMultipartFile file =
                createUploadedFile();

        List<ParsedMaturityRow> sourceRows =
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
                                "1000000.00",
                                DEFAULT_END_DATE
                        ),
                        row(
                                3,
                                "POL002",
                                "Autre Client",
                                "2000000.00",
                                "2032-03-15"
                        ),
                        row(
                                4,
                                "POL001",
                                "Client Exemple",
                                "3000000.00",
                                DEFAULT_END_DATE
                        ),
                        row(
                                5,
                                "POL001",
                                "Client Exemple",
                                "4000000.00",
                                DEFAULT_END_DATE
                        )
                );

        configureParsedRows(
                file,
                sourceRows
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

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(4),
                                anyList(),
                                eq("admin")
                        )
        ).thenReturn(
                importedResponse(4)
        );

        csvImportService.importFile(
                file,
                "admin"
        );

        List<MaturityImportRow> persistedRows =
                captureImportedRows();

        assertThat(persistedRows)
                .extracting(
                        MaturityImportRow::policyNumber
                )
                .containsExactly(
                        "POL001",
                        "POL002",
                        "POL001",
                        "POL001"
                );

        assertThat(persistedRows)
                .extracting(
                        MaturityImportRow::maturityRank
                )
                .containsExactly(
                        3,
                        1,
                        4,
                        5
                );

        assertThat(persistedRows)
                .extracting(
                        MaturityImportRow::maturityType
                )
                .containsExactly(
                        "MATURITE_3",
                        "MATURITE_1",
                        "MATURITE_4",
                        "MATURITE_5"
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
                        "2500000.00",
                        DEFAULT_END_DATE
                );

        ParsedMaturityRow second =
                row(
                        3,
                        "POL001",
                        "Client Exemple",
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

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(2),
                                anyList(),
                                eq("admin")
                        )
        ).thenReturn(
                importedResponse(2)
        );

        csvImportService.importFile(
                file,
                "admin"
        );

        List<MaturityImportRow> persistedRows =
                captureImportedRows();

        assertThat(persistedRows)
                .hasSize(2);

        assertThat(persistedRows)
                .extracting(
                        MaturityImportRow::maturityRank
                )
                .containsExactly(1, 2);

        assertThat(persistedRows)
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

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(1),
                                anyList(),
                                eq("admin")
                        )
        ).thenReturn(
                importedResponse(1)
        );

        csvImportService.importFile(
                file,
                "admin"
        );

        assertThat(captureImportedRows())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.maturityRank())
                            .isEqualTo(26);

                    assertThat(row.maturityType())
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
                                "nom_client",
                                "MISSING_CLIENT_NAME",
                                "Le nom du client est obligatoire."
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

        verify(
                persistenceService,
                never()
        ).saveImportedBatch(
                anyString(),
                anyString(),
                anyLong(),
                eq(1),
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

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(1),
                                anyList(),
                                eq("admin")
                        )
        ).thenReturn(
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
            "Une date de fin antérieure au chargement doit rejeter le fichier"
    )
    void shouldRejectInterestEndDateBeforeBusinessDate() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
                                "500000.00",
                                "2026-09-17"
                        )
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

        assertThat(response.errors())
                .anySatisfy(error -> {
                    assertThat(error.column())
                            .isEqualTo(
                                    "date_fin_interets"
                            );

                    assertThat(error.code())
                            .isEqualTo(
                                    "MATURITY_AFTER_INTEREST_END_DATE"
                            );
                });
    }

    @Test
    @DisplayName(
            "Une date de fin égale au jour du chargement doit être acceptée"
    )
    void shouldAllowInterestEndDateOnBusinessDate() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
                                "500000.00",
                                BUSINESS_DATE.toString()
                        )
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
                                eq(1),
                                anyList(),
                                eq("admin")
                        )
        ).thenReturn(
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
    }

    @Test
    @DisplayName(
            "Un chargement postérieur au dernier paiement doit être accepté"
    )
    void shouldAllowImportAfterLastPaidPayment() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
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

        when(
                persistenceService
                        .saveImportedBatch(
                                anyString(),
                                anyString(),
                                anyLong(),
                                eq(1),
                                anyList(),
                                eq("admin")
                        )
        ).thenReturn(
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
    }

    @Test
    @DisplayName(
            "Un chargement le jour du dernier paiement doit être rejeté"
    )
    void shouldRejectImportOnLastPaidPaymentDate() {
        MockMultipartFile file =
                createUploadedFile();

        configureParsedRows(
                file,
                List.of(
                        row(
                                2,
                                "POL001",
                                "Client Exemple",
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
                                BUSINESS_DATE.toString()
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
                                    "date_chargement"
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
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;2500000.00;2030-03-15
                """
        );
    }

    private ParsedMaturityRow row(
            int rowNumber,
            String policyNumber,
            String clientName,
            String amount,
            String interestEndDate
    ) {
        return new ParsedMaturityRow(
                rowNumber,
                policyNumber,
                clientName,
                new BigDecimal(amount),
                LocalDate.parse(
                        interestEndDate
                )
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