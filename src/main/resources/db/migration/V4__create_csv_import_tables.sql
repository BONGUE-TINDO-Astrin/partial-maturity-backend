/*
 * BeLife Insurance
 * Gestion des chargements CSV et des maturités de polices.
 *
 * Principes :
 * - un fichier chargé correspond à un IMPORT_BATCH ;
 * - une maturité est identifiée par la police et son rang ;
 * - aucune ligne de maturité n'est supprimée physiquement ;
 * - les dates techniques utilisent DATETIMEOFFSET(7).
 */

CREATE TABLE partial_maturity.import_batch
(
    id BIGINT IDENTITY(1,1) NOT NULL,

    original_file_name NVARCHAR(255) NOT NULL,

    file_sha256 CHAR(64) NOT NULL,

    file_size_bytes BIGINT NOT NULL,

    total_rows INT NOT NULL
        CONSTRAINT df_import_batch_total_rows
        DEFAULT 0,

    inserted_rows INT NOT NULL
        CONSTRAINT df_import_batch_inserted_rows
        DEFAULT 0,

    existing_rows INT NOT NULL
        CONSTRAINT df_import_batch_existing_rows
        DEFAULT 0,

    error_rows INT NOT NULL
        CONSTRAINT df_import_batch_error_rows
        DEFAULT 0,

    status_code VARCHAR(20) NOT NULL,

    error_summary NVARCHAR(MAX) NULL,

    imported_at DATETIMEOFFSET(7) NULL,

    imported_by NVARCHAR(100) NULL,

    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_import_batch_created_at
        DEFAULT SYSDATETIMEOFFSET(),

    created_by NVARCHAR(100) NULL,

    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_import_batch_updated_at
        DEFAULT SYSDATETIMEOFFSET(),

    updated_by NVARCHAR(100) NULL,

    row_version ROWVERSION NOT NULL,

    CONSTRAINT pk_import_batch
        PRIMARY KEY (id),

    CONSTRAINT ck_import_batch_status
        CHECK (
            status_code IN (
                    'PROCESSING',
                    'IMPORTED',
                    'REJECTED'
                )
            ),

    CONSTRAINT ck_import_batch_counts
        CHECK (
            total_rows >= 0
                AND inserted_rows >= 0
                AND existing_rows >= 0
                AND error_rows >= 0
            ),

    CONSTRAINT ck_import_batch_file_size
        CHECK (file_size_bytes >= 0)
);
GO


CREATE INDEX ix_import_batch_created_at
    ON partial_maturity.import_batch(created_at DESC);
GO

CREATE INDEX ix_import_batch_status
    ON partial_maturity.import_batch(status_code);
GO

CREATE INDEX ix_import_batch_file_sha256
    ON partial_maturity.import_batch(file_sha256);
GO


CREATE TABLE partial_maturity.policy_maturity
(
    id BIGINT IDENTITY(1,1) NOT NULL,

    import_batch_id BIGINT NOT NULL,

    policy_number NVARCHAR(100) NOT NULL,

    maturity_type VARCHAR(50) NOT NULL,

    maturity_rank INT NOT NULL,

    maturity_date DATE NOT NULL,

    maturity_amount DECIMAL(19,6) NOT NULL,

    source_row_number INT NOT NULL,

    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_policy_maturity_created_at
        DEFAULT SYSDATETIMEOFFSET(),

    created_by NVARCHAR(100) NULL,

    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_policy_maturity_updated_at
        DEFAULT SYSDATETIMEOFFSET(),

    updated_by NVARCHAR(100) NULL,

    row_version ROWVERSION NOT NULL,

    CONSTRAINT pk_policy_maturity
        PRIMARY KEY (id),

    CONSTRAINT fk_policy_maturity_import_batch
        FOREIGN KEY (import_batch_id)
            REFERENCES partial_maturity.import_batch(id),

    CONSTRAINT ck_policy_maturity_rank
        CHECK (maturity_rank > 0),

    CONSTRAINT ck_policy_maturity_amount
        CHECK (maturity_amount > 0),

    CONSTRAINT ck_policy_maturity_source_row
        CHECK (source_row_number >= 2),

    /*
     * Une police ne peut avoir qu'une seule maturité
     * pour un rang déterminé.
     */
    CONSTRAINT uq_policy_maturity_policy_rank
        UNIQUE (
            policy_number,
            maturity_rank
        )
);
GO


CREATE INDEX ix_policy_maturity_policy_date
    ON partial_maturity.policy_maturity(
        policy_number,
        maturity_date
    );
GO

CREATE INDEX ix_policy_maturity_import_batch
    ON partial_maturity.policy_maturity(
        import_batch_id
    );
GO