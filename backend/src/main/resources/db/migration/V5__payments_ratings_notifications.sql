-- What happens after a ride: its payment, the ratings both sides give, and in-app notifications.

CREATE TABLE payments (
    id                 uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id            uuid           NOT NULL REFERENCES rides (id),
    amount             numeric(10, 2) NOT NULL,
    currency           varchar(3)     NOT NULL,
    method             varchar(16)    NOT NULL,
    status             varchar(16)    NOT NULL,
    platform_fee       numeric(10, 2) NOT NULL,
    driver_earnings    numeric(10, 2) NOT NULL,
    gateway            varchar(20)    NOT NULL,
    gateway_reference  varchar(80),
    created_at         timestamptz    NOT NULL DEFAULT now(),
    updated_at         timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT ux_payments_ride UNIQUE (ride_id),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0 AND platform_fee >= 0 AND driver_earnings >= 0),
    CONSTRAINT ck_payments_split CHECK (platform_fee + driver_earnings = amount),
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'CARD')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'CAPTURED', 'FAILED', 'REFUNDED')),
    CONSTRAINT ck_payments_gateway CHECK (gateway IN ('CASH', 'SANDBOX'))
);

CREATE INDEX ix_payments_status ON payments (status);

CREATE TABLE ratings (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id     uuid         NOT NULL REFERENCES rides (id),
    rater_id    uuid         NOT NULL REFERENCES users (id),
    ratee_id    uuid         NOT NULL REFERENCES users (id),
    score       smallint     NOT NULL,
    comment     varchar(500),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_ratings_ride_rater UNIQUE (ride_id, rater_id),
    CONSTRAINT ck_ratings_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT ck_ratings_not_self CHECK (rater_id <> ratee_id)
);

-- A driver's average is recomputed from their ratings whenever they receive one.
CREATE INDEX ix_ratings_ratee ON ratings (ratee_id);

CREATE TABLE notifications (
    id               uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type             varchar(40)   NOT NULL,
    title            varchar(120)  NOT NULL,
    body             varchar(500)  NOT NULL,
    ride_id          uuid          REFERENCES rides (id),
    -- The Kafka event this notification was created from. One event can notify several users, so the
    -- pair is unique: a redelivered event cannot notify the same user twice.
    source_event_id  uuid          NOT NULL,
    read_at          timestamptz,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ux_notifications_source UNIQUE (user_id, source_event_id)
);

CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
