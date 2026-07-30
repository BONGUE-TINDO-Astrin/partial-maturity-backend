package com.belife.partial_maturity_backend.entities;

import com.belife.partial_maturity_backend.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente le paiement total d'une situation de police.
 *
 * <p>Le montant de ce paiement est toujours recalculé
 * côté backend dans la transaction d'enregistrement.</p>
 *
 * <p>Une annulation change uniquement le statut et renseigne
 * les informations d'annulation. Le paiement et ses détails
 * ne sont jamais supprimés.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "payment",
    schema = "partial_maturity",
    indexes = {
        @Index(
            name = "ix_payment_policy_date",
            columnList = "policy_number, payment_date, id"
        ),
        @Index(
            name = "ix_payment_policy_status",
            columnList ="policy_number, status_code"
        )
    }
)
public class PaymentEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, length = 100)
    private String policyNumber;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "calculation_date", nullable = false)
    private LocalDate calculationDate;

    /**
     * Taux décimal utilisé lors du calcul.
     *
     * Exemple :
     * 3,2 % = 0.032
     */
    @Column(name = "annual_rate", nullable = false, precision = 10, scale = 9)
    private BigDecimal annualRate;

    @Column(name = "capital_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal capitalAmount;

    @Column(name = "interest_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal interestAmount;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal paidAmount;

    @Column(name = "completed_cycles", nullable = false)
    private long completedCycles;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_code", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    /**
     * Verrou optimiste numérique.
     *
     * Hibernate incrémente cette version lors de chaque
     * modification du paiement.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Détails immuables du calcul ayant produit le paiement.
     */
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("sequenceNumber ASC")
    private List<PaymentDetailEntity> details = new ArrayList<>();

    /**
     * Ajoute un détail en maintenant les deux côtés
     * de la relation JPA.
     */
    public void addDetail(PaymentDetailEntity detail) {
        details.add(detail);
        detail.setPayment(this);
    }
}
