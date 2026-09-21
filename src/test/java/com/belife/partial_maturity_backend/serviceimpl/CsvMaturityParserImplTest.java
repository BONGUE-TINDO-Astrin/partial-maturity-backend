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
 * Tests unitaires du nouveau contrat CSV.
 */
class CsvMaturityParserImplTest {

    private static final String VALID_HEADER =
            "num_police;nom_client;"
                    + "montant_maturite;date_fin_interets";

    private CsvMaturityParserImpl parser;

    @BeforeEach
    void setUp() {
        parser = new CsvMaturityParserImpl();
    }

    @Test
    @DisplayName(
            "Un fichier conforme doit être converti sans erreur"
    )
    void shouldParseValidCsv() {
        MockMultipartFile file = csv(
                "maturites-valides.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;2500000.00;2030-03-15
                00012458;Autre Client;3000000.00;2031-06-10
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(2);

        ParsedMaturityRow firstRow =
                result.rows().getFirst();

        assertThat(firstRow.rowNumber())
                .isEqualTo(2);

        assertThat(firstRow.policyNumber())
                .isEqualTo("POL001");

        assertThat(firstRow.clientName())
                .isEqualTo("Client Exemple");

        assertThat(firstRow.maturityAmount())
                .isEqualByComparingTo(
                        new BigDecimal("2500000.00")
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
                num_police;nom_client;montant_maturite;date_fin_interets
                00012458;Client Exemple;3000000.00;2031-06-10
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
            "Un fichier UTF-8 avec BOM doit être accepté"
    )
    void shouldParseUtf8CsvWithBom() {
        MockMultipartFile file = csvWithBom(
                "maturites-avec-bom.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();
        assertThat(result.totalRows()).isEqualTo(1);

        assertThat(
                result.rows()
                        .getFirst()
                        .policyNumber()
        ).isEqualTo("POL001");
    }

    @Test
    @DisplayName(
            "Les colonnes obligatoires peuvent être dans un ordre différent"
    )
    void shouldAllowDifferentHeaderOrder() {
        MockMultipartFile file = csv(
                "colonnes-reordonnees.csv",
                """
                nom_client;date_fin_interets;num_police;montant_maturite
                Client Exemple;2030-03-15;POL001;2500000.00
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();

        ParsedMaturityRow row =
                result.rows().getFirst();

        assertThat(row.policyNumber())
                .isEqualTo("POL001");

        assertThat(row.clientName())
                .isEqualTo("Client Exemple");

        assertThat(row.maturityAmount())
                .isEqualByComparingTo("2500000.00");
    }

    @Test
    @DisplayName(
            "Les colonnes supplémentaires doivent être ignorées"
    )
    void shouldIgnoreAdditionalColumns() {
        MockMultipartFile file = csv(
                "colonnes-supplementaires.csv",
                """
                agence;num_police;nom_client;produit;montant_maturite;date_fin_interets;observation
                Centre;POL001;Client Exemple;Epargne;2500000.00;2030-03-15;Première tranche
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();
        assertThat(result.rows()).hasSize(1);

        ParsedMaturityRow row =
                result.rows().getFirst();

        assertThat(row.policyNumber())
                .isEqualTo("POL001");

        assertThat(row.clientName())
                .isEqualTo("Client Exemple");
    }

    @Test
    @DisplayName(
            "Une colonne obligatoire absente doit entraîner un rejet"
    )
    void shouldRejectMissingRequiredHeader() {
        MockMultipartFile file = csv(
                "nom-client-absent.csv",
                """
                num_police;montant_maturite;date_fin_interets
                POL001;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .anySatisfy(error -> {
                    assertThat(error.rowNumber())
                            .isEqualTo(1);

                    assertThat(error.column())
                            .isEqualTo("nom_client");

                    assertThat(error.code())
                            .isEqualTo(
                                    "MISSING_REQUIRED_HEADER"
                            );
                });
    }

    @Test
    @DisplayName(
            "Une colonne dupliquée doit entraîner un rejet"
    )
    void shouldRejectDuplicatedHeader() {
        MockMultipartFile file = csv(
                "colonne-dupliquee.csv",
                """
                num_police;nom_client;nom_client;montant_maturite;date_fin_interets
                POL001;Client A;Client A;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .extracting("code")
                .contains("DUPLICATE_HEADER");
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
                                        + "POL001;Client Exemple;"
                                        + "2500000.00;2030-03-15"
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
                num_police;nom_client;montant_maturite;date_fin_interets
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
            "Le numéro de police est obligatoire"
    )
    void shouldRejectMissingPolicyNumber() {
        MockMultipartFile file = csv(
                "police-absente.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                ;Client Exemple;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .extracting("code")
                .contains(
                        "MISSING_POLICY_NUMBER"
                );
    }

    @Test
    @DisplayName(
            "Le nom du client est obligatoire"
    )
    void shouldRejectMissingClientName() {
        MockMultipartFile file = csv(
                "client-absent.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .anySatisfy(error -> {
                    assertThat(error.column())
                            .isEqualTo("nom_client");

                    assertThat(error.code())
                            .isEqualTo(
                                    "MISSING_CLIENT_NAME"
                            );
                });
    }

    @Test
    @DisplayName(
            "Le nom du client doit être normalisé"
    )
    void shouldNormalizeClientNameSpaces() {
        MockMultipartFile file = csv(
                "client-espaces.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;  Client    Exemple  ;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();

        assertThat(
                result.rows()
                        .getFirst()
                        .clientName()
        ).isEqualTo("Client Exemple");
    }

    @Test
    @DisplayName(
            "Une police ne peut pas avoir plusieurs noms dans un fichier"
    )
    void shouldRejectDifferentClientNamesForSamePolicy() {
        MockMultipartFile file = csv(
                "clients-incoherents.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client A;2500000.00;2030-03-15
                POL001;Client B;3000000.00;2030-03-15
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
                            .isEqualTo("nom_client");

                    assertThat(error.code())
                            .isEqualTo(
                                    "INCONSISTENT_CLIENT_NAME"
                            );
                });
    }

    @Test
    @DisplayName(
            "Les différences de casse du nom doivent être tolérées"
    )
    void shouldAllowClientNameCaseDifferences() {
        MockMultipartFile file = csv(
                "clients-casse.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;2500000.00;2030-03-15
                POL001;CLIENT EXEMPLE;3000000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();
        assertThat(result.rows()).hasSize(2);
    }

    @Test
    @DisplayName(
            "Un montant utilisant la virgule doit être refusé"
    )
    void shouldRejectCommaAsDecimalSeparator() {
        MockMultipartFile file = csv(
                "montant-virgule.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;2500000,50;2030-03-15
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
            "Un montant nul doit être refusé"
    )
    void shouldRejectZeroAmount() {
        MockMultipartFile file = csv(
                "montant-zero.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;0;2030-03-15
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
            "Un montant négatif doit être refusé"
    )
    void shouldRejectNegativeAmount() {
        MockMultipartFile file = csv(
                "montant-negatif.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;-10;2030-03-15
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
            "Un montant dépassant six décimales doit être refusé"
    )
    void shouldRejectAmountWithTooManyDecimals() {
        MockMultipartFile file = csv(
                "montant-decimales.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;2500000.1234567;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .extracting("code")
                .contains(
                        "MATURITY_AMOUNT_SCALE_EXCEEDED"
                );
    }

    @Test
    @DisplayName(
            "Une date de fin absente doit être refusée"
    )
    void shouldRejectMissingInterestEndDate() {
        MockMultipartFile file = csv(
                "date-fin-absente.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;2500000.00;
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isFalse();

        assertThat(result.errors())
                .extracting("code")
                .contains(
                        "MISSING_INTEREST_END_DATE"
                );
    }

    @Test
    @DisplayName(
            "Une date de fin mal formatée doit être refusée"
    )
    void shouldRejectInvalidInterestEndDate() {
        MockMultipartFile file = csv(
                "date-fin-invalide.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;2500000.00;15/03/2030
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
            "Une police doit conserver la même date de fin dans le fichier"
    )
    void shouldRejectDifferentInterestEndDatesForSamePolicy() {
        MockMultipartFile file = csv(
                "dates-fin-incoherentes.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;2500000.00;2030-03-15
                POL001;Client Exemple;3000000.00;2031-03-15
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
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client A;2500000.00;2030-03-15
                POL002;Client B;3000000.00;2035-06-10
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();
        assertThat(result.rows()).hasSize(2);
    }

    @Test
    @DisplayName(
            "Deux lignes identiques représentent deux maturités"
    )
    void shouldKeepIdenticalRows() {
        MockMultipartFile file = csv(
                "lignes-identiques.csv",
                """
                num_police;nom_client;montant_maturite;date_fin_interets
                POL001;Client Exemple;2500000.00;2030-03-15
                POL001;Client Exemple;2500000.00;2030-03-15
                """
        );

        CsvValidationResult result =
                parser.parse(file);

        assertThat(result.isValid()).isTrue();
        assertThat(result.rows()).hasSize(2);

        assertThat(result.rows())
                .extracting(
                        ParsedMaturityRow::rowNumber
                )
                .containsExactly(2, 3);
    }
}