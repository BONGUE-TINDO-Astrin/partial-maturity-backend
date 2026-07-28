package com.belife.partial_maturity_backend.controllers;


import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.dtos.responses.ImportBatchDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.ImportBatchSummaryResponse;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyMaturityResponse;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.security.RestAccessDeniedHandler;
import com.belife.partial_maturity_backend.security.RestAuthenticationEntryPoint;
import com.belife.partial_maturity_backend.services.CsvImportService;
import com.belife.partial_maturity_backend.services.ImportHistoryService;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests MVC du contrôleur de chargement CSV.
 *
 * <p>Le test charge uniquement la couche MVC et une configuration
 * de sécurité minimale. Le filtre JWT, JPA, Flyway et SQL Server
 * ne sont volontairement pas chargés.</p>
 *
 * <p>Les règles ADMIN et COMPTABILITE restent réellement testées
 * grâce à Spring Security et à {@code @PreAuthorize}.</p>
 */
@WebMvcTest(
        controllers = CsvImportController.class
)
@Import({
        CsvImportControllerTest.TestSecurityConfiguration.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class CsvImportControllerTest {

    private static final String IMPORTS_URL =
            "/api/v1/admin/imports";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Service d'import remplacé par un mock dans le contexte MVC.
     */
    @MockitoBean
    private CsvImportService csvImportService;

    /**
     * Service d'historique remplacé par un mock.
     */
    @MockitoBean
    private ImportHistoryService importHistoryService;

    @Test
    @DisplayName(
            "Un utilisateur anonyme ne peut pas consulter les imports"
    )
    void shouldRejectAnonymousHistoryAccess()
            throws Exception {

        mockMvc.perform(
                        get(IMPORTS_URL)
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                "application/json"
                        )
                )
                .andExpect(
                        jsonPath("$.status").value(401)
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED")
                );

        verify(
                importHistoryService,
                never()
        ).getImportHistory(
                anyInt(),
                anyInt()
        );
    }

    @Test
    @DisplayName(
            "COMPTABILITE ne peut pas consulter les imports"
    )
    void shouldRejectAccountingHistoryAccess()
            throws Exception {

        mockMvc.perform(
                        get(IMPORTS_URL)
                                .with(
                                        user("comptable01")
                                                .roles(
                                                        "COMPTABILITE"
                                                )
                                )
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                "application/json"
                        )
                )
                .andExpect(
                        jsonPath("$.status").value(403)
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED")
                );

        verify(
                importHistoryService,
                never()
        ).getImportHistory(
                anyInt(),
                anyInt()
        );
    }

    @Test
    @DisplayName(
            "ADMIN peut consulter l'historique paginé"
    )
    void shouldReturnImportHistoryForAdmin()
            throws Exception {

        ImportBatchSummaryResponse summary =
                new ImportBatchSummaryResponse(
                        12L,
                        "maturites-valides.csv",
                        512L,
                        3,
                        3,
                        0,
                        0,
                        ImportBatchStatus.IMPORTED,
                        Instant.parse(
                                "2026-07-28T10:00:00Z"
                        ),
                        "admin",
                        Instant.parse(
                                "2026-07-28T10:00:00Z"
                        ),
                        "admin"
                );

        PageResponse<ImportBatchSummaryResponse> page =
                new PageResponse<>(
                        List.of(summary),
                        0,
                        20,
                        1,
                        1,
                        true,
                        true
                );

        when(
                importHistoryService.getImportHistory(
                        0,
                        20
                )
        ).thenReturn(page);

        mockMvc.perform(
                        get(IMPORTS_URL)
                                .param("page", "0")
                                .param("size", "20")
                                .with(
                                        user("admin")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                "application/json"
                        )
                )
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(12)
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].originalFileName"
                        ).value(
                                "maturites-valides.csv"
                        )
                )
                .andExpect(
                        jsonPath("$.content[0].status")
                                .value("IMPORTED")
                )
                .andExpect(
                        jsonPath("$.content[0].insertedRows")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.page").value(0)
                )
                .andExpect(
                        jsonPath("$.size").value(20)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.first").value(true)
                )
                .andExpect(
                        jsonPath("$.last").value(true)
                );

        verify(
                importHistoryService
        ).getImportHistory(
                0,
                20
        );
    }

    @Test
    @DisplayName(
            "Les valeurs de pagination par défaut sont utilisées"
    )
    void shouldUseDefaultPaginationValues()
            throws Exception {

        when(
                importHistoryService.getImportHistory(
                        0,
                        20
                )
        ).thenReturn(
                new PageResponse<>(
                        List.of(),
                        0,
                        20,
                        0,
                        0,
                        true,
                        true
                )
        );

        mockMvc.perform(
                        get(IMPORTS_URL)
                                .with(
                                        user("admin")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.page").value(0)
                )
                .andExpect(
                        jsonPath("$.size").value(20)
                );

        verify(
                importHistoryService
        ).getImportHistory(
                0,
                20
        );
    }

    @Test
    @DisplayName(
            "ADMIN peut importer un fichier CSV valide"
    )
    void shouldImportValidCsvForAdmin()
            throws Exception {

        MockMultipartFile file =
                validCsvFile();

        CsvImportResponse response =
                new CsvImportResponse(
                        20L,
                        "maturites-valides.csv",
                        ImportBatchStatus.IMPORTED,
                        3,
                        3,
                        0,
                        0,
                        Instant.parse(
                                "2026-07-28T10:00:00Z"
                        ),
                        List.of()
                );

        when(
                csvImportService.importFile(
                        any(),
                        eq("admin")
                )
        ).thenReturn(response);

        mockMvc.perform(
                        multipart(
                                IMPORTS_URL + "/csv"
                        )
                                .file(file)
                                .with(
                                        user("admin")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                "application/json"
                        )
                )
                .andExpect(
                        jsonPath("$.batchId").value(20)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("IMPORTED")
                )
                .andExpect(
                        jsonPath("$.totalRows")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.insertedRows")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.existingRows")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.errorRows")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.errors.length()")
                                .value(0)
                );

        verify(csvImportService).importFile(
                any(),
                eq("admin")
        );
    }

    @Test
    @DisplayName(
            "Un fichier rejeté retourne HTTP 422"
    )
    void shouldReturnUnprocessableEntityForRejectedCsv()
            throws Exception {

        MockMultipartFile file =
                validCsvFile();

        CsvValidationError validationError =
                new CsvValidationError(
                        2,
                        "date_maturite",
                        "INVALID_MATURITY_DATE",
                        "La date doit respecter le format yyyy-MM-dd."
                );

        CsvImportResponse response =
                new CsvImportResponse(
                        21L,
                        "maturites-invalides.csv",
                        ImportBatchStatus.REJECTED,
                        1,
                        0,
                        0,
                        1,
                        Instant.parse(
                                "2026-07-28T10:00:00Z"
                        ),
                        List.of(validationError)
                );

        when(
                csvImportService.importFile(
                        any(),
                        eq("admin")
                )
        ).thenReturn(response);

        mockMvc.perform(
                        multipart(
                                IMPORTS_URL + "/csv"
                        )
                                .file(file)
                                .with(
                                        user("admin")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(
                        status().isUnprocessableEntity()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("REJECTED")
                )
                .andExpect(
                        jsonPath("$.insertedRows")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.errorRows")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.errors.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.errors[0].rowNumber")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.errors[0].column")
                                .value("date_maturite")
                )
                .andExpect(
                        jsonPath("$.errors[0].code")
                                .value(
                                        "INVALID_MATURITY_DATE"
                                )
                );
    }

    @Test
    @DisplayName(
            "COMPTABILITE ne peut pas importer de CSV"
    )
    void shouldRejectAccountingCsvImport()
            throws Exception {

        mockMvc.perform(
                        multipart(
                                IMPORTS_URL + "/csv"
                        )
                                .file(validCsvFile())
                                .with(
                                        user("comptable01")
                                                .roles(
                                                        "COMPTABILITE"
                                                )
                                )
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED")
                );

        verify(
                csvImportService,
                never()
        ).importFile(
                any(),
                anyString()
        );
    }

    @Test
    @DisplayName(
            "ADMIN peut consulter le détail d'un lot rejeté"
    )
    void shouldReturnRejectedImportDetail()
            throws Exception {

        CsvValidationError validationError =
                new CsvValidationError(
                        3,
                        "montant_maturite",
                        "INVALID_MATURITY_AMOUNT",
                        "Le montant est invalide."
                );

        ImportBatchDetailResponse detail =
                new ImportBatchDetailResponse(
                        30L,
                        "fichier-invalide.csv",
                        "a".repeat(64),
                        350L,
                        2,
                        0,
                        0,
                        1,
                        ImportBatchStatus.REJECTED,
                        null,
                        null,
                        Instant.parse(
                                "2026-07-28T10:00:00Z"
                        ),
                        "admin",
                        Instant.parse(
                                "2026-07-28T10:00:00Z"
                        ),
                        "admin",
                        List.of(validationError)
                );

        when(
                importHistoryService.getImportDetail(
                        30L
                )
        ).thenReturn(detail);

        mockMvc.perform(
                        get(IMPORTS_URL + "/30")
                                .with(
                                        user("admin")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.id").value(30)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("REJECTED")
                )
                .andExpect(
                        jsonPath("$.errors.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.errors[0].code")
                                .value(
                                        "INVALID_MATURITY_AMOUNT"
                                )
                );

        verify(
                importHistoryService
        ).getImportDetail(30L);
    }

    @Test
    @DisplayName(
            "ADMIN peut consulter les maturités d'un lot"
    )
    void shouldReturnImportedMaturities()
            throws Exception {

        PolicyMaturityResponse maturity =
                new PolicyMaturityResponse(
                        100L,
                        "00012458",
                        "MATURITE_1",
                        1,
                        LocalDate.parse(
                                "2021-06-10"
                        ),
                        new BigDecimal(
                                "3000000.000000"
                        ),
                        3,
                        Instant.parse(
                                "2026-07-28T10:00:00Z"
                        ),
                        "admin"
                );

        when(
                importHistoryService
                        .getImportedMaturities(40L)
        ).thenReturn(
                List.of(maturity)
        );

        mockMvc.perform(
                        get(
                                IMPORTS_URL
                                        + "/40/maturities"
                        )
                                .with(
                                        user("admin")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$[0].policyNumber")
                                .value("00012458")
                )
                .andExpect(
                        jsonPath("$[0].maturityRank")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$[0].maturityDate")
                                .value("2021-06-10")
                )
                .andExpect(
                        jsonPath("$[0].sourceRowNumber")
                                .value(3)
                );

        verify(
                importHistoryService
        ).getImportedMaturities(40L);
    }

    /**
     * Crée un fichier CSV multipart pour les tests MVC.
     */
    private MockMultipartFile validCsvFile() {
        return new MockMultipartFile(
                "file",
                "maturites-valides.csv",
                "text/csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite
                POL001;MATURITE_1;2020-03-15;2500000.00
                POL002;MATURITE_1;2021-06-10;3000000.00
                POL003;MATURITE_1;2024-01-01;1500000.50
                """.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Configuration de sécurité réservée aux tests MVC.
     *
     * <p>Cette configuration vérifie l'authentification et laisse
     * {@code @PreAuthorize("hasRole('ADMIN')")} appliquer les
     * autorisations du contrôleur.</p>
     *
     * <p>Le filtre JWT de production n'est volontairement pas chargé.
     * Les utilisateurs sont ajoutés aux requêtes avec le support
     * Spring Security Test.</p>
     */
    @TestConfiguration(
            proxyBeanMethods = false
    )
    @EnableMethodSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain testSecurityFilterChain(
                HttpSecurity http,
                RestAuthenticationEntryPoint authenticationEntryPoint,
                RestAccessDeniedHandler accessDeniedHandler
        ) throws Exception {

            http
                    .csrf(csrf -> csrf.disable())

                    .sessionManagement(session -> session
                            .sessionCreationPolicy(
                                    SessionCreationPolicy.STATELESS
                            )
                    )

                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(
                                    authenticationEntryPoint
                            )
                            .accessDeniedHandler(
                                    accessDeniedHandler
                            )
                    )

                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(
                                    HttpMethod.OPTIONS,
                                    "/**"
                            ).permitAll()

                            .anyRequest()
                            .authenticated()
                    );

            return http.build();
        }
    }
}