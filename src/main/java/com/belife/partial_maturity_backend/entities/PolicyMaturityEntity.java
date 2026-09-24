package com.belife.partial_maturity_backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Représente une maturité d'une police importée
 * depuis un fichier CSV.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "policy_maturity",
        schema = "partial_maturity",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_policy_maturity_policy_rank",
                        columnNames = {"policy_number", "maturity_rank"}
                )
        }
)
public class PolicyMaturityEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Lot d'import ayant créé cette maturité.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "import_batch_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_policy_maturity_import_batch")
    )
    private ImportBatchEntity importBatch;

    @Column(name = "policy_number", nullable = false, length = 100)
    private String policyNumber;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    /**
     * Type généré automatiquement à partir du rang.
     *
     * <p>Exemples : {@code MATURITE_1},
     * {@code MATURITE_2} ou {@code MATURITE_15}.</p>
     */
    @Column(name = "maturity_type", nullable = false, length = 50)
    private String maturityType;

    /**
     * Rang séquentiel attribué automatiquement
     */
    @Column(name = "maturity_rank", nullable = false)
    private int maturityRank;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "maturity_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal maturityAmount;

    /**
     * Date de fin de production des intérêts.
     */
    @Column(name = "interest_end_date", nullable = false)
    private LocalDate interestEndDate;

    /**
     * Numéro physique de la ligne source
     * dans le fichier CSV.
     */
    @Column(name = "source_row_number", nullable = false)
    private int sourceRowNumber;
}