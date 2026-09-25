-- AI trip insights. The exact facts sent to the model and the deterministic observations are stored with
-- every analysis, so each result can be traced back to its input.

CREATE TABLE trip_analyses (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id         uuid          NOT NULL REFERENCES rides (id),
    status          varchar(16)   NOT NULL,
    failure_code    varchar(30),
    provider        varchar(30),
    model           varchar(80),
    prompt_version  varchar(40)   NOT NULL,
    facts           jsonb         NOT NULL,
    observations    jsonb         NOT NULL,
    result          jsonb,
    latency_ms      integer,
    input_tokens    integer,
    output_tokens   integer,
    attempts        smallint      NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    CONSTRAINT ux_trip_analyses_ride UNIQUE (ride_id),
    CONSTRAINT ck_trip_analyses_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'UNAVAILABLE')),
    CONSTRAINT ck_trip_analyses_failure CHECK (failure_code IS NULL OR failure_code IN
        ('TIMEOUT', 'PROVIDER_ERROR', 'RATE_LIMITED', 'INVALID_RESPONSE', 'REFUSED', 'BUSY', 'UNAVAILABLE')),
    CONSTRAINT ck_trip_analyses_result CHECK ((status = 'COMPLETED') = (result IS NOT NULL))
);

CREATE TABLE trip_questions (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id         uuid          NOT NULL REFERENCES rides (id),
    user_id         uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question        varchar(500)  NOT NULL,
    status          varchar(16)   NOT NULL,
    failure_code    varchar(30),
    answer          jsonb,
    provider        varchar(30),
    model           varchar(80),
    prompt_version  varchar(40)   NOT NULL,
    latency_ms      integer,
    created_at      timestamptz   NOT NULL,
    CONSTRAINT ck_trip_questions_status CHECK (status IN ('COMPLETED', 'FAILED', 'UNAVAILABLE')),
    CONSTRAINT ck_trip_questions_answer CHECK ((status = 'COMPLETED') = (answer IS NOT NULL))
);

CREATE INDEX ix_trip_questions_ride_created ON trip_questions (ride_id, created_at);
