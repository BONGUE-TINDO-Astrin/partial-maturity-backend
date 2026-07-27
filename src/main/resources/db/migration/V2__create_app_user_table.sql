CREATE TABLE partial_maturity.app_user
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    username NVARCHAR(100) NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    full_name NVARCHAR(200) NOT NULL,
    role_code VARCHAR(20) NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    last_login_at DATETIME2(3) NULL,

    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by NVARCHAR(100) NULL,
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by NVARCHAR(100) NULL,

    CONSTRAINT pk_app_user
        PRIMARY KEY (id),

    CONSTRAINT uq_app_user_username
        UNIQUE (username),

    CONSTRAINT ck_app_user_role
        CHECK (role_code IN ('ADMIN', 'COMPTABILITE'))
);
GO