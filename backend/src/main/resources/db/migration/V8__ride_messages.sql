-- In-ride chat between a ride's passenger and its assigned driver (docs/feature-spec.md section 3).
-- Messages can be sent only while a driver is engaged; they are deleted a set time after the ride ends.
CREATE TABLE ride_messages (
    id           uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id      uuid          NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    sender_id    uuid          NOT NULL REFERENCES users (id),
    sender_role  varchar(20)   NOT NULL,
    body         varchar(500)  NOT NULL,
    sent_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_ride_messages_role CHECK (sender_role IN ('PASSENGER', 'DRIVER')),
    CONSTRAINT ck_ride_messages_body CHECK (btrim(body) <> '')
);

-- A conversation is always read in order, one ride at a time.
CREATE INDEX ix_ride_messages_ride_sent ON ride_messages (ride_id, sent_at, id);
