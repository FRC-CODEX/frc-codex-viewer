CREATE TABLE IF NOT EXISTS stream_events (
    stream_event_id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    timepoint BIGINT NOT NULL,
    json TEXT NOT NULL,
    PRIMARY KEY (stream_event_id)
);
