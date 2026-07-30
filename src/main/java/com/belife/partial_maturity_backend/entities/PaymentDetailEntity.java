package com.belife.partial_maturity_backend.entities;

import com.belife.partial_maturity_backend.enums.CalculationEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Copie immuable d'une étape du calcul ayant justifié
 * un paiement.
 *
 * <p>Ces lignes permettent de reproduire l'explication
 * présentée au comptable au moment du paiement, même si
 * les données de la police évoluent plus tard.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "payment_detail",
    schema = "partial_maturity",
    uniqueConstraints = {
        @UniqueConstraint(
            name ="uq_payment_detail_sequence",
            columnNames = {"payment_id", "sequence_number"}
        )
    }
)
public class PaymentDetailEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "payment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_detail_payment")
    )
    private PaymentEntity payment;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private CalculationEventType eventType;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "cycle_number")
    private Long cycleNumber;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 6)
    private BigDecimal balanceBefore;

    @Column(name = "capital_added", nullable = false, precision = 19,scale = 6)
    private BigDecimal capitalAdded;

    @Column(name = "annual_rate",  precision = 10, scale = 9)
    private BigDecimal annualRate;

    @Column(name = "interest_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal interestAmount;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal paidAmount;

    @Column(name = "balance_after", nullable = false, precision = 19,scale = 6)
    private BigDecimal balanceAfter;
}
