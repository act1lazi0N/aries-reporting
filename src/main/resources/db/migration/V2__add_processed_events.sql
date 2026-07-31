CREATE TABLE processed_events (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_id       UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    schema_version INT          NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_processed_events PRIMARY KEY (id),
    CONSTRAINT uk_processed_events_event_id UNIQUE (event_id)
);

COMMENT ON TABLE processed_events
    IS 'Reporting-side inbox for idempotent processing of delivered integration events';
COMMENT ON COLUMN processed_events.event_id
    IS 'Stable id from the external outbox event; used to skip duplicate deliveries';
COMMENT ON COLUMN processed_events.aggregate_id
    IS 'Source aggregate id, normally the transaction id';

CREATE INDEX idx_pe_aggregate ON processed_events (aggregate_id, event_type);
CREATE INDEX idx_pe_processed_at ON processed_events (processed_at DESC);
