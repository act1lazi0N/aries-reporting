ALTER TABLE email_logs
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;

UPDATE email_logs
SET created_at = sent_at
WHERE sent_at IS NOT NULL;

ALTER TABLE email_logs
    ALTER COLUMN sent_at DROP NOT NULL;

UPDATE email_logs
SET sent_at = NULL
WHERE status <> 'SENT';

UPDATE email_logs
SET lease_until = NOW()
WHERE status = 'SENDING';

CREATE INDEX IF NOT EXISTS idx_email_delivery_due
    ON email_logs (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'SENDING');
