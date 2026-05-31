CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE report_job_status AS ENUM (
    'PENDING', 'PROCESSING', 'READY', 'FAILED', 'EXPIRED'
);

CREATE TYPE report_job_type AS ENUM (
    'ACCOUNT_STATEMENT', 'TRANSACTION_SUMMARY',
    'SPENDING_PATTERN',  'ADMIN_OVERVIEW'
);

CREATE TYPE report_format AS ENUM ('PDF', 'EXCEL');

CREATE TYPE email_status AS ENUM ('SENT', 'FAILED', 'SKIPPED');

CREATE TABLE reporting_transactions (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    original_tx_id      UUID           NOT NULL,
    from_account_id     UUID           NOT NULL,
    to_account_id       UUID           NOT NULL,
    from_owner_name     VARCHAR(100)   NOT NULL,
    to_owner_name       VARCHAR(100)   NOT NULL,
    from_account_number VARCHAR(20)    NOT NULL,
    to_account_number   VARCHAR(20)    NOT NULL,
    amount              NUMERIC(18, 2) NOT NULL,
    currency            CHAR(3)        NOT NULL DEFAULT 'VND',
    status              VARCHAR(20)    NOT NULL,
    description         VARCHAR(255),
    day_of_week         SMALLINT       NOT NULL,
    hour_of_day         SMALLINT       NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL,
    completed_at        TIMESTAMPTZ,
    synced_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_reporting_transactions      PRIMARY KEY (id),
    CONSTRAINT uk_reporting_original_tx_id    UNIQUE (original_tx_id),
    CONSTRAINT chk_reporting_amount           CHECK (amount > 0),
    CONSTRAINT chk_reporting_day_of_week      CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_reporting_hour_of_day      CHECK (hour_of_day BETWEEN 0 AND 23)
);

COMMENT ON TABLE  reporting_transactions             IS 'Denormalized copy của transactions — OLAP optimised';
COMMENT ON COLUMN reporting_transactions.day_of_week IS 'Pre-computed ISO day (1=Mon) để tránh EXTRACT() khi query';
COMMENT ON COLUMN reporting_transactions.synced_at   IS 'Timestamp khi record được sync vào reporting DB';

CREATE TABLE daily_snapshots (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    account_id       UUID           NOT NULL,
    snapshot_date    DATE           NOT NULL,
    opening_balance  NUMERIC(18, 2) NOT NULL,
    closing_balance  NUMERIC(18, 2) NOT NULL,
    total_debit      NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_credit     NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tx_count         INT            NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_daily_snapshots      PRIMARY KEY (id),
    CONSTRAINT uk_daily_account_date   UNIQUE (account_id, snapshot_date),
    CONSTRAINT chk_daily_balances      CHECK (
        opening_balance >= 0 AND closing_balance >= 0
        AND total_debit >= 0  AND total_credit >= 0
    )
);

COMMENT ON CONSTRAINT uk_daily_account_date ON daily_snapshots
    IS 'Idempotency: job chạy lại không tạo duplicate snapshot';

CREATE TABLE monthly_snapshots (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    account_id       UUID           NOT NULL,
    year             SMALLINT       NOT NULL,
    month            SMALLINT       NOT NULL,
    opening_balance  NUMERIC(18, 2) NOT NULL,
    closing_balance  NUMERIC(18, 2) NOT NULL,
    total_debit      NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_credit     NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tx_count         INT            NOT NULL DEFAULT 0,
    is_finalised     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_monthly_snapshots     PRIMARY KEY (id),
    CONSTRAINT uk_monthly_account_month UNIQUE (account_id, year, month),
    CONSTRAINT chk_monthly_month        CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT chk_monthly_year         CHECK (year >= 2020)
);

COMMENT ON COLUMN monthly_snapshots.is_finalised
    IS 'TRUE = tháng đã qua, đọc từ snapshot. FALSE = tháng hiện tại, on-the-fly';

CREATE TABLE report_jobs (
    id             UUID              NOT NULL DEFAULT gen_random_uuid(),
    requested_by   UUID              NOT NULL,    -- user id từ JWT
    job_type       report_job_type   NOT NULL,
    status         report_job_status NOT NULL DEFAULT 'PENDING',
    format         report_format     NOT NULL,
    params         JSONB,                         -- filter params (from, to, accountId...)
    file_path      VARCHAR(500),                  -- set khi status = READY
    error_message  VARCHAR(500),                  -- set khi status = FAILED
    expires_at     TIMESTAMPTZ,                   -- set khi READY, file xóa sau 24h
    created_at     TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    completed_at   TIMESTAMPTZ,

    CONSTRAINT pk_report_jobs PRIMARY KEY (id)
);

COMMENT ON COLUMN report_jobs.params    IS 'JSONB: {"accountId":"...", "from":"2025-01", "to":"2025-03"}';
COMMENT ON COLUMN report_jobs.file_path IS 'Absolute path trên server — chỉ expose download URL ra ngoài';

CREATE TABLE email_logs (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    billing_month    CHAR(7)      NOT NULL,   -- Format: 2025-03
    idempotency_key  VARCHAR(100) NOT NULL,   -- user_id::billing_month
    status           email_status NOT NULL,
    error_message    VARCHAR(500),
    sent_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_email_logs            PRIMARY KEY (id),
    CONSTRAINT uk_email_idempotency     UNIQUE (idempotency_key)
);

COMMENT ON COLUMN email_logs.idempotency_key
    IS 'Format: {userId}::{billingMonth} — đảm bảo 1 email/user/tháng';

CREATE INDEX idx_rt_from_account  ON reporting_transactions (from_account_id, created_at DESC);
CREATE INDEX idx_rt_to_account    ON reporting_transactions (to_account_id,   created_at DESC);
CREATE INDEX idx_rt_created_at    ON reporting_transactions (created_at DESC);
CREATE INDEX idx_rt_status        ON reporting_transactions (status);
CREATE INDEX idx_rt_completed_at  ON reporting_transactions (completed_at DESC)
    WHERE completed_at IS NOT NULL;

CREATE INDEX idx_ds_account_date  ON daily_snapshots (account_id, snapshot_date DESC);

CREATE INDEX idx_ms_account       ON monthly_snapshots (account_id, year DESC, month DESC);
CREATE INDEX idx_ms_not_finalised ON monthly_snapshots (account_id)
    WHERE is_finalised = FALSE;

CREATE INDEX idx_rj_requested_by  ON report_jobs (requested_by, created_at DESC);
CREATE INDEX idx_rj_status        ON report_jobs (status)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_rj_expires_at    ON report_jobs (expires_at)
    WHERE status = 'READY';

CREATE INDEX idx_el_user_id       ON email_logs (user_id, sent_at DESC);

CREATE TABLE QRTZ_JOB_DETAILS (
    SCHED_NAME        VARCHAR(120) NOT NULL,
    JOB_NAME          VARCHAR(200) NOT NULL,
    JOB_GROUP         VARCHAR(200) NOT NULL,
    DESCRIPTION       VARCHAR(250),
    JOB_CLASS_NAME    VARCHAR(250) NOT NULL,
    IS_DURABLE        BOOLEAN      NOT NULL,
    IS_NONCONCURRENT  BOOLEAN      NOT NULL,
    IS_UPDATE_DATA    BOOLEAN      NOT NULL,
    REQUESTS_RECOVERY BOOLEAN      NOT NULL,
    JOB_DATA          BYTEA,
    PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
);

CREATE TABLE QRTZ_TRIGGERS (
    SCHED_NAME     VARCHAR(120) NOT NULL,
    TRIGGER_NAME   VARCHAR(200) NOT NULL,
    TRIGGER_GROUP  VARCHAR(200) NOT NULL,
    JOB_NAME       VARCHAR(200) NOT NULL,
    JOB_GROUP      VARCHAR(200) NOT NULL,
    DESCRIPTION    VARCHAR(250),
    NEXT_FIRE_TIME BIGINT,
    PREV_FIRE_TIME BIGINT,
    PRIORITY       INTEGER,
    TRIGGER_STATE  VARCHAR(16)  NOT NULL,
    TRIGGER_TYPE   VARCHAR(8)   NOT NULL,
    START_TIME     BIGINT       NOT NULL,
    END_TIME       BIGINT,
    CALENDAR_NAME  VARCHAR(200),
    MISFIRE_INSTR  SMALLINT,
    JOB_DATA       BYTEA,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
        REFERENCES QRTZ_JOB_DETAILS (SCHED_NAME, JOB_NAME, JOB_GROUP)
);

CREATE TABLE QRTZ_SIMPLE_TRIGGERS (
    SCHED_NAME      VARCHAR(120) NOT NULL,
    TRIGGER_NAME    VARCHAR(200) NOT NULL,
    TRIGGER_GROUP   VARCHAR(200) NOT NULL,
    REPEAT_COUNT    BIGINT       NOT NULL,
    REPEAT_INTERVAL BIGINT       NOT NULL,
    TIMES_TRIGGERED BIGINT       NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
        REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE QRTZ_CRON_TRIGGERS (
    SCHED_NAME      VARCHAR(120) NOT NULL,
    TRIGGER_NAME    VARCHAR(200) NOT NULL,
    TRIGGER_GROUP   VARCHAR(200) NOT NULL,
    CRON_EXPRESSION VARCHAR(120) NOT NULL,
    TIME_ZONE_ID    VARCHAR(80),
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
        REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE QRTZ_FIRED_TRIGGERS (
    SCHED_NAME        VARCHAR(120) NOT NULL,
    ENTRY_ID          VARCHAR(95)  NOT NULL,
    TRIGGER_NAME      VARCHAR(200) NOT NULL,
    TRIGGER_GROUP     VARCHAR(200) NOT NULL,
    INSTANCE_NAME     VARCHAR(200) NOT NULL,
    FIRED_TIME        BIGINT       NOT NULL,
    SCHED_TIME        BIGINT       NOT NULL,
    PRIORITY          INTEGER      NOT NULL,
    STATE             VARCHAR(16)  NOT NULL,
    JOB_NAME          VARCHAR(200),
    JOB_GROUP         VARCHAR(200),
    IS_NONCONCURRENT  BOOLEAN,
    REQUESTS_RECOVERY BOOLEAN,
    PRIMARY KEY (SCHED_NAME, ENTRY_ID)
);

CREATE TABLE QRTZ_SCHEDULER_STATE (
    SCHED_NAME        VARCHAR(120) NOT NULL,
    INSTANCE_NAME     VARCHAR(200) NOT NULL,
    LAST_CHECKIN_TIME BIGINT       NOT NULL,
    CHECKIN_INTERVAL  BIGINT       NOT NULL,
    PRIMARY KEY (SCHED_NAME, INSTANCE_NAME)
);

CREATE TABLE QRTZ_LOCKS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    LOCK_NAME  VARCHAR(40)  NOT NULL,
    PRIMARY KEY (SCHED_NAME, LOCK_NAME)
);

CREATE INDEX IDX_QRTZ_J_REQ_RECOVERY ON QRTZ_JOB_DETAILS (SCHED_NAME, REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_T_NEXT_FIRE    ON QRTZ_TRIGGERS    (SCHED_NAME, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_STATE        ON QRTZ_TRIGGERS    (SCHED_NAME, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_FT_TRIG_GROUP  ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, TRIGGER_GROUP);
