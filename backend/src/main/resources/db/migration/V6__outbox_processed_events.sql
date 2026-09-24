-- Transactional outbox: a domain event is stored in the same transaction as the change it describes, and
-- the relay publishes it to Kafka afterwards. An event therefore reaches Kafka if and only if its
-- transaction committed (docs/architecture.md D5).
CREATE TABLE outbox_events (
    id            uuid          PRIMARY KEY,
    -- Insertion order. Events of one aggregate are written by transactions that hold its row lock one after
    -- another, so relaying in this order keeps them in order.
    seq           bigint        GENERATED ALWAYS AS IDENTITY,
    topic         varchar(150)  NOT NULL,
    message_key   varchar(100)  NOT NULL,
    event_type    varchar(60)   NOT NULL,
    payload       jsonb         NOT NULL,
    created_at    timestamptz   NOT NULL,
    published_at  timestamptz,
    attempts      integer       NOT NULL DEFAULT 0,
    last_error    varchar(500)
);

-- The relay's work queue; published rows leave the index.
CREATE INDEX ix_outbox_unpublished ON outbox_events (seq) WHERE published_at IS NULL;
-- Purging published rows.
CREATE INDEX ix_outbox_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;

-- Consumer-side idempotency: a handler inserts (consumer, event id) in the same transaction as its side
-- effect, so a redelivered event is recognised and skipped.
CREATE TABLE processed_events (
    consumer      varchar(60)  NOT NULL,
    event_id      uuid         NOT NULL,
    processed_at  timestamptz  NOT NULL,
    PRIMARY KEY (consumer, event_id)
);

CREATE INDEX ix_processed_events_processed_at ON processed_events (processed_at);
