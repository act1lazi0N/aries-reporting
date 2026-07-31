ALTER TABLE email_logs
    ADD COLUMN IF NOT EXISTS claim_token UUID NOT NULL DEFAULT gen_random_uuid();
