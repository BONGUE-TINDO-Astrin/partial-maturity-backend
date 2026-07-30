/*
 * BeLife Insurance
 * Paiements totaux des situations de maturités partielles.
 *
 * Principes métier :
 * - un paiement est toujours recalculé côté backend ;
 * - un paiement initial possède le statut PAID ;
 * - une annulation ne supprime aucune donnée ;
 * - PAYMENT_DETAIL conserve la justification du montant payé ;
 * - tous les montants sont enregistrés avec six décimales.
 */

CREATE TABLE partial_maturity.payment
(
    id BIGINT IDENTITY(1,1) NOT NULL,

    policy_number NVARCHAR(100) NOT NULL,

    payment_date DATE NOT NULL,

    calculation_date DATE NOT NULL,

    annual_rate DECIMAL(10,9) NOT NULL,

    capital_amount DECIMAL(19,6) NOT NULL,

    interest_amount DECIMAL(19,6) NOT NULL,

    paid_amount DECIMAL(19,6) NOT NULL,

    completed_cycles BIGINT NOT NULL,

    status_code VARCHAR(20) NOT NULL,

    cancelled_at DATETIMEOFFSET(7) NULL,

    cancelled_by NVARCHAR(100) NULL,

    cancellation_reason NVARCHAR(500) NULL,

    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_payment_created_at
        DEFAULT SYSDATETIMEOFFSET(),

    created_by NVARCHAR(100) NULL,

    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_payment_updated_at
        DEFAULT SYSDATETIMEOFFSET(),

    updated_by NVARCHAR(100) NULL,

    /*
     * Version numérique utilisée par Hibernate pour
     * détecter les modifications concurrentes.
     */
    version BIGINT NOT NULL
        CONSTRAINT df_payment_version
        DEFAULT 0,

    CONSTRAINT pk_payment
        PRIMARY KEY (id),

    CONSTRAINT ck_payment_status
        CHECK (status_code IN ('PAID', 'CANCELLED')),

    CONSTRAINT ck_payment_annual_rate
        CHECK (
            annual_rate > 0
                AND annual_rate < 1
            ),

    CONSTRAINT ck_payment_capital_amount
        CHECK (capital_amount >= 0),

    CONSTRAINT ck_payment_interest_amount
        CHECK (interest_amount >= 0),

    CONSTRAINT ck_payment_paid_amount
        CHECK (paid_amount > 0),

    CONSTRAINT ck_payment_completed_cycles
        CHECK (completed_cycles >= 0),

    CONSTRAINT ck_payment_amount_consistency
        CHECK (paid_amount = capital_amount + interest_amount),

    /*
     * Un paiement annulé doit posséder toutes
     * les informations d'annulation.
     *
     * Un paiement PAID ne doit en posséder aucune.
     */
    CONSTRAINT ck_payment_cancellation_data
        CHECK (
            (
                status_code = 'PAID'
                    AND cancelled_at IS NULL
                    AND cancelled_by IS NULL
                    AND cancellation_reason IS NULL
                )
                OR
            (
                status_code = 'CANCELLED'
                    AND cancelled_at IS NOT NULL
                    AND cancelled_by IS NOT NULL
                    AND cancellation_reason IS NOT NULL
                    AND LEN(LTRIM(RTRIM(cancellation_reason))) > 0
                )
            )
);
GO


CREATE INDEX ix_payment_policy_date
    ON partial_maturity.payment(policy_number, payment_date, id);
GO


CREATE INDEX ix_payment_policy_status
    ON partial_maturity.payment(policy_number, status_code);
GO


CREATE INDEX ix_payment_created_at
    ON partial_maturity.payment(created_at DESC);
GO


/*
 * PAYMENT_DETAIL conserve chaque étape ayant permis
 * de justifier le montant du paiement.
 *
 * Les lignes sont des copies immuables du résultat
 * de calcul au moment exact du paiement.
 */
CREATE TABLE partial_maturity.payment_detail
(
    id BIGINT IDENTITY(1,1) NOT NULL,

    payment_id BIGINT NOT NULL,

    sequence_number INT NOT NULL,

    event_date DATE NOT NULL,

    event_type VARCHAR(30) NOT NULL,

    description NVARCHAR(500) NOT NULL,

    cycle_number BIGINT NULL,

    balance_before DECIMAL(19,6) NOT NULL,

    capital_added DECIMAL(19,6) NOT NULL,

    annual_rate DECIMAL(10,9) NULL,

    interest_amount DECIMAL(19,6) NOT NULL,

    paid_amount DECIMAL(19,6) NOT NULL,

    balance_after DECIMAL(19,6) NOT NULL,

    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_payment_detail_created_at
        DEFAULT SYSDATETIMEOFFSET(),

    created_by NVARCHAR(100) NULL,

    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_payment_detail_updated_at
        DEFAULT SYSDATETIMEOFFSET(),

    updated_by NVARCHAR(100) NULL,

    CONSTRAINT pk_payment_detail
        PRIMARY KEY (id),

    CONSTRAINT fk_payment_detail_payment
        FOREIGN KEY (payment_id)
            REFERENCES partial_maturity.payment(id),

    CONSTRAINT uq_payment_detail_sequence
        UNIQUE (payment_id, sequence_number),

    CONSTRAINT ck_payment_detail_sequence
        CHECK (sequence_number > 0),

    CONSTRAINT ck_payment_detail_event_type
        CHECK (
            event_type IN (
                           'MATURITY_ADDED',
                           'INTEREST_APPLIED',
                           'PAYMENT_APPLIED'
                )
            ),

    CONSTRAINT ck_payment_detail_cycle
        CHECK (cycle_number IS NULL OR cycle_number > 0),

    CONSTRAINT ck_payment_detail_amounts
        CHECK (
            balance_before >= 0
                AND capital_added >= 0
                AND interest_amount >= 0
                AND paid_amount >= 0
                AND balance_after >= 0
            )
);
GO


CREATE INDEX ix_payment_detail_payment
    ON partial_maturity.payment_detail(payment_id, sequence_number);
GO