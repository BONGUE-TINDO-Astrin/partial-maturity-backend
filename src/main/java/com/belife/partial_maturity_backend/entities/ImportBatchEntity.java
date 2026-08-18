package com.belife.partial_maturity_backend.entities;

import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Représente une tentative de chargement d'un fichier CSV.
 *
 * <p>Une ligne est conservée aussi bien pour un fichier
 * importé que pour un fichier rejeté. Un chargement importé
 * peut également être annulé sans que son historique
 * disparaisse.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "import_batch",
        schema = "partial_maturity"
)
public class ImportBatchEntity
        extends AuditableEntity {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Column(
            name = "original_file_name",
            nullable = false,
            length = 255
    )
    private String originalFileName;

    @Column(
            name = "file_sha256",
            nullable = false,
            length = 64
    )
    private String fileSha256;

    @Column(
            name = "file_size_bytes",
            nullable = false
    )
    private long fileSizeBytes;

    @Column(
            name = "total_rows",
            nullable = false
    )
    private int totalRows;

    @Column(
            name = "inserted_rows",
            nullable = false
    )
    private int insertedRows;

    @Column(
            name = "existing_rows",
            nullable = false
    )
    private int existingRows;

    @Column(
            name = "error_rows",
            nullable = false
    )
    private int errorRows;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status_code",
            nullable = false,
            length = 20
    )
    private ImportBatchStatus status;

    /**
     * Résumé JSON des erreurs détectées
     * lors d'un chargement rejeté.
     */
    @Column(name = "error_summary")
    private String errorSummary;

    @Column(name = "imported_at")
    private Instant importedAt;

    @Column(
            name = "imported_by",
            length = 100
    )
    private String importedBy;

    /**
     * Instant technique auquel le chargement
     * importé a été annulé.
     */
    @Column(name = "reversed_at")
    private Instant reversedAt;

    /**
     * Utilisateur ADMIN ayant annulé le chargement.
     */
    @Column(
            name = "reversed_by",
            length = 100
    )
    private String reversedBy;

    /**
     * Justification obligatoire de la réversion.
     */
    @Column(
            name = "reversal_reason",
            length = 500
    )
    private String reversalReason;
}