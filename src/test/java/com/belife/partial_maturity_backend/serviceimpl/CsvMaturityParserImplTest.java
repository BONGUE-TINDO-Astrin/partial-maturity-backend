package com.belife.partial_maturity_backend.serviceimpl;

import com.belife.partial_maturity_backend.services.Impl.CsvMaturityParserImpl;
import com.belife.partial_maturity_backend.services.models.CsvValidationResult;
import com.belife.partial_maturity_backend.services.models.ParsedMaturityRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static com.belife.partial_maturity_backend.testutils.CsvTestFileFactory.csv;
import static com.belife.partial_maturity_backend.testutils.CsvTestFileFactory.csvWithBom;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du parseur CSV de maturités.
 *
 * <p>Ces tests ne démarrent pas Spring et n'accèdent pas
 * à SQL Server. Ils vérifient uniquement le contrat du fichier
 * et la conversion des données.</p>
 */
class CsvMaturityParserImplTest {

    private static final String VALID_HEADER =
            "num_police;type_maturite;date_maturite;"
                    + "montant_maturite;date_fin_interets";

    private CsvMaturityParserImpl parser;

    @BeforeEach
    void setUp() {
        parser = new CsvMaturityParserImpl();
    }

    @Test
    @DisplayName(
            "Un CSV valide doit être converti sans erreur"
    )
    void shouldParseValidCsv() {
        MockMultipartFile file = csv(
                "maturites-valides.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;2500000.00;2030-03-15
                00012458;MATURITE_1;2021-06-10;3000000.00;2031-06-10
                POL003;MATURITE_1;2024-01-01;1500000.50;2034-01-01
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();
        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(3);

        ParsedMaturityRow firstRow =
                result.rows().getFirst();

        assertThat(firstRow.rowNumber())
                .isEqualTo(2);

        assertThat(firstRow.policyNumber())
                .isEqualTo("POL001");

        assertThat(firstRow.maturityType())
                .isEqualTo("MATURITE_1");

        assertThat(firstRow.maturityRank())
                .isEqualTo(1);

        assertThat(firstRow.maturityDate())
                .isEqualTo(
                        LocalDate.of(
                                2020,
                                3,
                                15
                        )
                );

        assertThat(firstRow.maturityAmount())
                .isEqualByComparingTo(
                        new BigDecimal(
                                "2500000.00"
                        )
                );

        assertThat(firstRow.interestEndDate())
                .isEqualTo(
                        LocalDate.of(
                                2030,
                                3,
                                15
                        )
                );
    }

    @Test
    @DisplayName(
            "Le numéro de police doit conserver ses zéros initiaux"
    )
    void shouldPreservePolicyNumberLeadingZeros() {
        MockMultipartFile file = csv(
                "police-avec-zeros.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                00012458;MATURITE_1;2021-06-10;3000000.00;2031-06-10
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();

        assertThat(
                result.rows()
                        .getFirst()
                        .policyNumber()
        ).isEqualTo("00012458");
    }

    @Test
    @DisplayName(
            "Un CSV UTF-8 avec BOM doit être accepté"
    )
    void shouldParseUtf8CsvWithBom() {
        MockMultipartFile file = csvWithBom(
                "maturites-avec-bom.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();

        assertThat(
                result.rows()
                        .getFirst()
                        .policyNumber()
        ).isEqualTo("POL001");
    }

    @Test
    @DisplayName(
            "Un ordre de colonnes incorrect doit entraîner un rejet"
    )
    void shouldRejectInvalidHeaderOrder() {
        MockMultipartFile file = csv(
                "en-tete-invalide.csv",
                """
                type_maturite;num_police;date_maturite;montant_maturite;date_fin_interets
                MATURITE_1;POL001;2020-03-15;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .anySatisfy(error -> {
                    assertThat(error.rowNumber())
                            .isEqualTo(1);

                    assertThat(error.code())
                            .isEqualTo(
                                    "INVALID_HEADER"
                            );
                });
    }

    @Test
    @DisplayName(
            "L'ancien en-tête sans date de fin doit être refusé"
    )
    void shouldRejectLegacyHeader() {
        MockMultipartFile file = csv(
                "ancien-format.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite
                POL001;MATURITE_1;2020-03-15;2500000.00
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .extracting("code")
                .contains("INVALID_HEADER");
    }

    @Test
    @DisplayName(
            "Une extension autre que CSV doit être refusée"
    )
    void shouldRejectInvalidFileExtension() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "maturites.txt",
                        "text/plain",
                        (
                                VALID_HEADER
                                        + System.lineSeparator()
                                        + "POL001;MATURITE_1;"
                                        + "2020-03-15;2500000.00;"
                                        + "2030-03-15"
                        ).getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .extracting("code")
                .contains(
                        "INVALID_FILE_EXTENSION"
                );
    }

    @Test
    @DisplayName(
            "Un fichier sans ligne de maturité doit être refusé"
    )
    void shouldRejectFileWithoutDataRows() {
        MockMultipartFile file = csv(
                "fichier-vide.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();
        assertThat(result.totalRows()).isZero();

        assertThat(result.errors())
                .extracting("code")
                .contains("EMPTY_FILE");
    }

    @Test
    @DisplayName(
            "Les erreurs de plusieurs lignes doivent être agrégées"
    )
    void shouldCollectAllRowErrors() {
        MockMultipartFile file = csv(
                "maturites-invalides.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                ;MATURITE_1;2020-03-15;2500000.00;2030-03-15
                POL002;TYPE_INCONNU;2021-06-10;3000000.00;2031-06-10
                POL003;MATURITE_1;10/06/2021;1500000.00;2031-06-10
                POL004;MATURITE_1;2022-01-01;-10;2032-01-01
                POL005;MATURITE_1;2022-01-01;1000000.00;
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();
        assertThat(result.totalRows())
                .isEqualTo(5);

        assertThat(result.errors())
                .extracting("code")
                .contains(
                        "MISSING_POLICY_NUMBER",
                        "INVALID_MATURITY_TYPE",
                        "INVALID_MATURITY_DATE",
                        "INVALID_MATURITY_AMOUNT",
                        "MISSING_INTEREST_END_DATE"
                );
    }

    @Test
    @DisplayName(
            "Le type de maturité doit être normalisé en majuscules"
    )
    void shouldNormalizeMaturityType() {
        MockMultipartFile file = csv(
                "type-minuscules.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;maturite_2;2025-03-15;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();

        ParsedMaturityRow row =
                result.rows().getFirst();

        assertThat(row.maturityType())
                .isEqualTo("MATURITE_2");

        assertThat(row.maturityRank())
                .isEqualTo(2);
    }

    @Test
    @DisplayName(
            "Un montant utilisant la virgule doit être refusé"
    )
    void shouldRejectCommaAsDecimalSeparator() {
        MockMultipartFile file = csv(
                "montant-virgule.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;2500000,50;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .anySatisfy(error -> {
                    assertThat(error.column())
                            .isEqualTo(
                                    "montant_maturite"
                            );

                    assertThat(error.code())
                            .isEqualTo(
                                    "INVALID_MATURITY_AMOUNT"
                            );
                });
    }

    @Test
    @DisplayName(
            "Un montant à zéro doit être refusé"
    )
    void shouldRejectZeroAmount() {
        MockMultipartFile file = csv(
                "montant-zero.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;0;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .extracting("code")
                .contains(
                        "INVALID_MATURITY_AMOUNT"
                );
    }

    @Test
    @DisplayName(
            "Une date de fin des intérêts absente doit être refusée"
    )
    void shouldRejectMissingInterestEndDate() {
        MockMultipartFile file = csv(
                "date-fin-absente.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;2500000.00;
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .anySatisfy(error -> {
                    assertThat(error.column())
                            .isEqualTo(
                                    "date_fin_interets"
                            );

                    assertThat(error.code())
                            .isEqualTo(
                                    "MISSING_INTEREST_END_DATE"
                            );
                });
    }

    @Test
    @DisplayName(
            "Une date de fin des intérêts mal formatée doit être refusée"
    )
    void shouldRejectInvalidInterestEndDate() {
        MockMultipartFile file = csv(
                "date-fin-invalide.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;2500000.00;15/03/2030
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .extracting("code")
                .contains(
                        "INVALID_INTEREST_END_DATE"
                );
    }

    @Test
    @DisplayName(
            "Une maturité le jour de la fin des intérêts doit être acceptée"
    )
    void shouldAllowMaturityOnInterestEndDate() {
        MockMultipartFile file = csv(
                "maturite-date-fin.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2030-03-15;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();

        assertThat(
                result.rows()
                        .getFirst()
                        .maturityDate()
        ).isEqualTo(
                result.rows()
                        .getFirst()
                        .interestEndDate()
        );
    }

    @Test
    @DisplayName(
            "Une maturité après la fin des intérêts doit être refusée"
    )
    void shouldRejectMaturityAfterInterestEndDate() {
        MockMultipartFile file = csv(
                "maturite-apres-fin.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2030-03-16;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .anySatisfy(error -> {
                    assertThat(error.column())
                            .isEqualTo(
                                    "date_maturite"
                            );

                    assertThat(error.code())
                            .isEqualTo(
                                    "MATURITY_AFTER_INTEREST_END_DATE"
                            );
                });
    }

    @Test
    @DisplayName(
            "Deux dates de fin différentes pour une police doivent être refusées"
    )
    void shouldRejectDifferentInterestEndDatesForSamePolicy() {
        MockMultipartFile file = csv(
                "dates-fin-incoherentes.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;2500000.00;2030-03-15
                POL001;MATURITE_2;2021-03-15;3000000.00;2031-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .anySatisfy(error -> {
                    assertThat(error.rowNumber())
                            .isEqualTo(3);

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
            "Deux polices peuvent avoir des dates de fin différentes"
    )
    void shouldAllowDifferentInterestEndDatesForDifferentPolicies() {
        MockMultipartFile file = csv(
                "dates-fin-par-police.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;2500000.00;2030-03-15
                POL002;MATURITE_1;2021-06-10;3000000.00;2035-06-10
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(2);
    }

    @Test
    @DisplayName(
            "Deux lignes contradictoires dans le même fichier doivent être refusées"
    )
    void shouldRejectInternalContradiction() {
        MockMultipartFile file = csv(
                "contradiction-interne.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;2500000.00;2030-03-15
                POL001;MATURITE_1;2020-03-16;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .extracting("code")
                .contains(
                        "CONTRADICTORY_MATURITY"
                );
    }

    @Test
    @DisplayName(
            "Une date de fin différente rend un doublon contradictoire"
    )
    void shouldRejectDuplicateWithDifferentInterestEndDate() {
        MockMultipartFile file = csv(
                "doublon-date-fin-differente.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;2500000.00;2030-03-15
                POL001;MATURITE_1;2020-03-15;2500000.00;2031-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .extracting("code")
                .contains(
                        "CONTRADICTORY_MATURITY",
                        "INCONSISTENT_INTEREST_END_DATE"
                );
    }

    @Test
    @DisplayName(
            "Deux lignes strictement identiques sont tolérées"
    )
    void shouldAllowStrictlyIdenticalInternalDuplicates() {
        MockMultipartFile file = csv(
                "doublon-identique.csv",
                """
                num_police;type_maturite;date_maturite;montant_maturite;date_fin_interets
                POL001;MATURITE_1;2020-03-15;2500000.00;2030-03-15
                POL001;MATURITE_1;2020-03-15;2500000.000000;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();
        assertThat(result.rows()).hasSize(2);
        assertThat(result.errors()).isEmpty();
    }
}