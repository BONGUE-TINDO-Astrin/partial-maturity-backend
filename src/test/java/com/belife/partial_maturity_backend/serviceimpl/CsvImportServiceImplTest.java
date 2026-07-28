package com.belife.partial_maturity_backend.serviceimpl;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires de l'orchestration métier des imports.
 *
 * <p>Le parseur, les repositories et la persistance transactionnelle
 * sont simulés avec Mockito. Ces tests vérifient donc uniquement
 * les décisions prises par CsvImportServiceImpl.</p>
 */
@ExtendWith(MockitoExtension.class)
class CsvImportServiceImplTest {

    @Mock
    private CsvMaturityParser csvMaturityParser;

    @Mock
    private PolicyMaturityRepository policyMaturityRepository;

    @Mock
    private CsvImportPersistenceService persistenceService;

    private CsvImportServiceImpl csvImportService;

    @BeforeEach
    void setUp() {
        csvImportService = new CsvImportServiceImpl(csvMaturityParser, policyMaturityRepository, persistenceService);
    }

    @Test
    @DisplayName("Un fichier valide sans maturité existante doit être importé")
    void shouldImportValidFile() {
        MockMultipartFile file = createUploadedFile();

        List<ParsedMaturityRow> rows =
            List.of(
                row(2, "POL001", 1, "2020-03-15", "2500000.00"),
                row(3, "POL002", 1, "2021-06-10", "3000000.00")
            );

        when(csvMaturityParser.parse(file))
            .thenReturn( new CsvValidationResult(2, rows, List.of() ) );

        when(policyMaturityRepository.findAllByPolicyNumberIn(anyCollection())).thenReturn(List.of());

        CsvImportResponse expectedResponse = importedResponse(2, 0);

        when(
            persistenceService.saveImportedBatch(
                anyString(),
                anyString(),
                anyLong(),
                eq(2),
                eq(0),
                anyList(),
                eq("admin")
            )
        ).thenReturn(expectedResponse);

        CsvImportResponse response = csvImportService.importFile(file, "admin");

        assertThat(response).isSameAs(expectedResponse);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ParsedMaturityRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);

        verify(persistenceService)
            .saveImportedBatch(
                eq("maturites.csv"),
                argThat(hash -> hash != null && hash.length() == 64),
                eq(file.getSize()),
                eq(2),
                eq(0),
                rowsCaptor.capture(),
                eq("admin")
            );

        assertThat(rowsCaptor.getValue()).hasSize(2);

        verify(persistenceService, never())
            .saveRejectedBatch(
                anyString(),
                anyString(),
                anyLong(),
                anyInt(),
                anyList(),
                anyString()
            );
    }

    @Test
    @DisplayName("Une erreur syntaxique doit historiser un lot rejeté")
    void shouldRejectParsingErrors() {
        MockMultipartFile file = createUploadedFile();

        List<CsvValidationError> errors =
            List.of(
                new CsvValidationError(
                    2,
                    "date_maturite",
                    "INVALID_MATURITY_DATE",
                    "La date est invalide."
                )
            );

        when(csvMaturityParser.parse(file))
                .thenReturn(new CsvValidationResult(1, List.of(), errors));

        CsvImportResponse expectedResponse = rejectedResponse(1, errors);

        when(
            persistenceService.saveRejectedBatch(
                anyString(),
                anyString(),
                anyLong(),
                eq(1),
                eq(errors),
                eq("admin")
            )
        ).thenReturn(expectedResponse);

        CsvImportResponse response = csvImportService.importFile(file, "admin");

        assertThat(response).isSameAs(expectedResponse);

        verify(persistenceService)
            .saveRejectedBatch(
                eq("maturites.csv"),
                anyString(),
                eq(file.getSize()),
                eq(1),
                eq(errors),
                eq("admin")
            );

        verifyNoInteractions(policyMaturityRepository);

        verify(persistenceService, never())
            .saveImportedBatch(
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
    @DisplayName("Une maturité historique identique doit être comptée comme existante")
    void shouldCountIdenticalExistingMaturity() {
        MockMultipartFile file = createUploadedFile();

        ParsedMaturityRow incoming =
            row(
                2,
                "POL001",
                1,
                "2020-03-15",
                "2500000.00"
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
                "2500000.000000"
            );

        when(policyMaturityRepository.findAllByPolicyNumberIn(anyCollection())
        ).thenReturn(List.of(existing));

        CsvImportResponse expectedResponse = importedResponse(0, 1);

        when(
            persistenceService.saveImportedBatch(
                anyString(),
                anyString(),
                anyLong(),
                eq(1),
                eq(1),
                eq(List.of()),
                eq("admin")
            )
        ).thenReturn(expectedResponse);

        CsvImportResponse response = csvImportService.importFile(file, "admin");

        assertThat(response.insertedRows()).isZero();

        assertThat(response.existingRows()).isEqualTo(1);

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
    @DisplayName("Une maturité historique contradictoire doit rejeter tout le fichier")
    void shouldRejectHistoricalContradiction() {
        MockMultipartFile file = createUploadedFile();

        ParsedMaturityRow incoming =
            row(
                2,
                "POL001",
                1,
                "2020-03-15",
                "2500000.00"
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
                "2500000.00"
            );

        when(policyMaturityRepository.findAllByPolicyNumberIn(anyCollection()))
                .thenReturn(List.of(existing));

        when(
            persistenceService.saveRejectedBatch(
                anyString(),
                anyString(),
                anyLong(),
                eq(1),
                anyList(),
                eq("admin")
            )
        )
        .thenAnswer(invocation -> {
            List<CsvValidationError> errors = invocation.getArgument(4);

            return rejectedResponse(1, errors);
        });

        CsvImportResponse response = csvImportService.importFile(file, "admin");

        assertThat(response.status()).isEqualTo(ImportBatchStatus.REJECTED);

        assertThat(response.errors())
                .extracting("code")
                .contains("CONTRADICTORY_MATURITY");

        verify(persistenceService, never())
            .saveImportedBatch(
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
    @DisplayName("MATURITE_2 sans MATURITE_1 doit être rejetée")
    void shouldRejectMissingPreviousMaturity() {
        MockMultipartFile file = createUploadedFile();

        ParsedMaturityRow rankTwo =
            row(
                2,
                "POL-GAP",
                2,
                "2025-01-01",
                "1000000.00"
            );

        when(csvMaturityParser.parse(file))
            .thenReturn(
                new CsvValidationResult(
                    1,
                    List.of(rankTwo),
                    List.of()
                )
            );

        when(policyMaturityRepository.findAllByPolicyNumberIn(anyCollection()))
            .thenReturn(List.of());

        when(
            persistenceService.saveRejectedBatch(
                anyString(),
                anyString(),
                anyLong(),
                eq(1),
                anyList(),
                eq("admin")
            )
        ).thenAnswer(invocation -> {
            List<CsvValidationError> errors = invocation.getArgument(4);

            return rejectedResponse(1, errors);
        });

        CsvImportResponse response = csvImportService.importFile(file, "admin");

        assertThat(response.status()).isEqualTo(ImportBatchStatus.REJECTED);

        assertThat(response.errors())
                .extracting("code")
                .contains("MISSING_PREVIOUS_MATURITY");
    }

    @Test
    @DisplayName("Une maturité suivante antérieure à la précédente doit être rejetée")
    void shouldRejectInvalidDateSequence() {
        MockMultipartFile file = createUploadedFile();

        List<ParsedMaturityRow> rows =
            List.of(
                row(
                    2,
                    "POL-DATE",
                    1,
                    "2025-01-01",
                    "1000000.00"
                ),
                row(
                    3,
                    "POL-DATE",
                    2,
                    "2024-01-01",
                    "1000000.00"
                )
            );

        when(csvMaturityParser.parse(file))
            .thenReturn(new CsvValidationResult(2, rows, List.of()));

        when(policyMaturityRepository.findAllByPolicyNumberIn(anyCollection()))
            .thenReturn(List.of());

        when(
            persistenceService.saveRejectedBatch(
                anyString(),
                anyString(),
                anyLong(),
                eq(2),
                anyList(),
                eq("admin")
            )
        ).thenAnswer(invocation -> {
            List<CsvValidationError> errors = invocation.getArgument(4);

            return rejectedResponse(2, errors);
        });

        CsvImportResponse response = csvImportService.importFile(file, "admin");

        assertThat(response.status()).isEqualTo(ImportBatchStatus.REJECTED);

        assertThat(response.errors())
            .extracting("code")
            .contains("INVALID_MATURITY_DATE_SEQUENCE");
    }

    @Test
    @DisplayName("Un doublon interne identique doit être dédupliqué")
    void shouldDeduplicateIdenticalRowsInsideFile() {
        MockMultipartFile file = createUploadedFile();

        ParsedMaturityRow first =
            row(
                2,
                "POL001",
                1,
                "2020-03-15",
                "2500000.00"
            );

        ParsedMaturityRow duplicate =
            row(
                3,
                "POL001",
                1,
                "2020-03-15",
                "2500000.000000"
            );

        when(csvMaturityParser.parse(file))
            .thenReturn(
                new CsvValidationResult(
                    2,
                    List.of(first, duplicate),
                    List.of()
                )
            );

        when(policyMaturityRepository.findAllByPolicyNumberIn(anyCollection()))
            .thenReturn(List.of());

        when(
            persistenceService.saveImportedBatch(
                anyString(),
                anyString(),
                anyLong(),
                eq(2),
                eq(1),
                anyList(),
                eq("admin")
            )
        )
            .thenReturn(importedResponse(1, 1));

        CsvImportResponse response = csvImportService.importFile(file, "admin");

        assertThat(response.insertedRows()).isEqualTo(1);

        assertThat(response.existingRows()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ParsedMaturityRow>> rowsCaptor =ArgumentCaptor.forClass(List.class);

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

        assertThat(rowsCaptor.getValue()).containsExactly(first);
    }

    private MockMultipartFile createUploadedFile() {
        return csv(
            "maturites.csv",
            """
            num_police;type_maturite;date_maturite;montant_maturite
            POL001;MATURITE_1;2020-03-15;2500000.00
            """
        );
    }

    private ParsedMaturityRow row(
        int rowNumber,
        String policyNumber,
        int rank,
        String date,
        String amount
    ) {
        return new ParsedMaturityRow(
            rowNumber,
            policyNumber,
            "MATURITE_" + rank,
            rank,
            LocalDate.parse(date),
            new BigDecimal(amount)
        );
    }

    private PolicyMaturityEntity maturityEntity(
        String policyNumber,
        int rank,
        String date,
        String amount
    ) {
        PolicyMaturityEntity entity = new PolicyMaturityEntity();

        entity.setId(100L + rank);
        entity.setPolicyNumber(policyNumber);
        entity.setMaturityType("MATURITE_" + rank);
        entity.setMaturityRank(rank);
        entity.setMaturityDate(LocalDate.parse(date));
        entity.setMaturityAmount(new BigDecimal(amount));
        entity.setSourceRowNumber(2);
        entity.setImportBatch(new ImportBatchEntity());

        return entity;
    }

    private CsvImportResponse importedResponse(int insertedRows, int existingRows) {
        return new CsvImportResponse(
            1L,
            "maturites.csv",
            ImportBatchStatus.IMPORTED,
            insertedRows + existingRows,
            insertedRows,
            existingRows,
            0,
            Instant.parse("2026-07-28T10:00:00Z"),
            List.of()
        );
    }

    private CsvImportResponse rejectedResponse(int totalRows, List<CsvValidationError> errors) {
        return new CsvImportResponse(
            1L,
            "maturites.csv",
            ImportBatchStatus.REJECTED,
            totalRows,
            0,
            0,
            errors.size(),
            Instant.parse("2026-07-28T10:00:00Z"),
            errors
        );
    }
}