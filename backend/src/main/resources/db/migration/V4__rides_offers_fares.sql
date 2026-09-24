-- Rides and everything that belongs to a single ride's lifecycle.
-- Coordinates are stored as plain lat/lng (what the application reads and writes) and PostGIS derives
-- a geography column from them, which the spatial indexes and queries use. Longitude comes first.

CREATE TABLE rides (
    id                    uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    passenger_id          uuid          NOT NULL REFERENCES users (id),
    driver_id             uuid          REFERENCES drivers (id),
    vehicle_id            uuid          REFERENCES vehicles (id),
    status                varchar(20)   NOT NULL,
    vehicle_category      varchar(16)   NOT NULL,
    pickup_lat            double precision NOT NULL,
    pickup_lng            double precision NOT NULL,
    pickup_location       geography(Point, 4326)
                          GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(pickup_lng, pickup_lat), 4326)::geography) STORED,
    pickup_address        varchar(255)  NOT NULL,
    dropoff_lat           double precision NOT NULL,
    dropoff_lng           double precision NOT NULL,
    dropoff_location      geography(Point, 4326)
                          GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(dropoff_lng, dropoff_lat), 4326)::geography) STORED,
    dropoff_address       varchar(255)  NOT NULL,
    payment_method        varchar(16)   NOT NULL,
    estimated_distance_m  integer       NOT NULL,
    estimated_duration_s  integer       NOT NULL,
    estimate_source       varchar(16)   NOT NULL,
    surge_multiplier      numeric(4, 2) NOT NULL,
    currency              varchar(3)    NOT NULL,
    actual_distance_m     integer,
    actual_duration_s     integer,
    distance_source       varchar(16),
    matching_round        smallint      NOT NULL DEFAULT 0,
    matching_radius_m     integer       NOT NULL DEFAULT 0,
    round_started_at      timestamptz,
    requested_at          timestamptz   NOT NULL,
    accepted_at           timestamptz,
    en_route_at           timestamptz,
    arrived_at            timestamptz,
    started_at            timestamptz,
    completed_at          timestamptz,
    cancelled_at          timestamptz,
    expired_at            timestamptz,
    cancelled_by          varchar(16),
    cancellation_reason   varchar(255),
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    version               bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_rides_status CHECK (status IN ('REQUESTED', 'MATCHING', 'DRIVER_ASSIGNED', 'DRIVER_ARRIVING',
                                                 'DRIVER_ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_rides_category CHECK (vehicle_category IN ('ECONOMY', 'COMFORT', 'XL')),
    CONSTRAINT ck_rides_payment_method CHECK (payment_method IN ('CASH', 'CARD')),
    CONSTRAINT ck_rides_estimate_source CHECK (estimate_source IN ('ROUTED', 'APPROXIMATE')),
    CONSTRAINT ck_rides_distance_source CHECK (distance_source IS NULL OR distance_source IN ('TRACKED', 'ESTIMATED')),
    CONSTRAINT ck_rides_cancelled_by CHECK (cancelled_by IS NULL OR cancelled_by IN ('PASSENGER', 'DRIVER', 'SYSTEM', 'ADMIN')),
    CONSTRAINT ck_rides_coordinates CHECK (pickup_lat BETWEEN -90 AND 90 AND pickup_lng BETWEEN -180 AND 180
                                           AND dropoff_lat BETWEEN -90 AND 90 AND dropoff_lng BETWEEN -180 AND 180),
    CONSTRAINT ck_rides_estimates_positive CHECK (estimated_distance_m > 0 AND estimated_duration_s > 0),
    CONSTRAINT ck_rides_surge CHECK (surge_multiplier >= 1),
    CONSTRAINT ck_rides_driver_when_assigned
        CHECK (driver_id IS NOT NULL OR status IN ('REQUESTED', 'MATCHING', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_rides_completed_has_actuals
        CHECK (status <> 'COMPLETED' OR (completed_at IS NOT NULL AND actual_distance_m IS NOT NULL
                                         AND actual_duration_s IS NOT NULL))
);

-- At most one active ride per passenger and per driver, enforced by the database even under races.
CREATE UNIQUE INDEX ux_rides_passenger_active ON rides (passenger_id)
    WHERE status IN ('REQUESTED', 'MATCHING', 'DRIVER_ASSIGNED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED', 'IN_PROGRESS');
CREATE UNIQUE INDEX ux_rides_driver_active ON rides (driver_id)
    WHERE status IN ('DRIVER_ASSIGNED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED', 'IN_PROGRESS');

CREATE INDEX ix_rides_passenger_requested ON rides (passenger_id, requested_at DESC);
CREATE INDEX ix_rides_driver_requested    ON rides (driver_id, requested_at DESC);
CREATE INDEX ix_rides_status_requested    ON rides (status, requested_at);
-- Surge demand: open requests near a point.
CREATE INDEX ix_rides_pickup_open ON rides USING GIST (pickup_location) WHERE status IN ('REQUESTED', 'MATCHING');

CREATE TABLE ride_offers (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id       uuid        NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    driver_id     uuid        NOT NULL REFERENCES drivers (id),
    round         smallint    NOT NULL,
    distance_m    integer     NOT NULL,
    status        varchar(16) NOT NULL,
    offered_at    timestamptz NOT NULL,
    expires_at    timestamptz NOT NULL,
    responded_at  timestamptz,
    CONSTRAINT ck_ride_offers_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ux_ride_offers_ride_driver UNIQUE (ride_id, driver_id)
);

-- A driver holds at most one pending offer; a ride has at most one accepted offer.
CREATE UNIQUE INDEX ux_ride_offers_one_pending_per_driver ON ride_offers (driver_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX ux_ride_offers_one_accepted_per_ride ON ride_offers (ride_id) WHERE status = 'ACCEPTED';
CREATE INDEX ix_ride_offers_pending_expiry ON ride_offers (expires_at) WHERE status = 'PENDING';
CREATE INDEX ix_ride_offers_ride_status ON ride_offers (ride_id, status);

CREATE TABLE ride_status_events (
    id             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ride_id        uuid        NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    from_status    varchar(20),
    to_status      varchar(20) NOT NULL,
    actor_type     varchar(16) NOT NULL,
    actor_user_id  uuid        REFERENCES users (id) ON DELETE SET NULL,
    reason         varchar(255),
    ride_version   bigint      NOT NULL,
    occurred_at    timestamptz NOT NULL,
    CONSTRAINT ck_ride_status_events_actor CHECK (actor_type IN ('PASSENGER', 'DRIVER', 'SYSTEM', 'ADMIN'))
);

CREATE INDEX ix_ride_status_events_ride ON ride_status_events (ride_id, occurred_at);

CREATE TABLE fare_breakdowns (
    id                    uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id               uuid          NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    kind                  varchar(10)   NOT NULL,
    base_fare             numeric(10, 2) NOT NULL,
    distance_charge       numeric(10, 2) NOT NULL,
    time_charge           numeric(10, 2) NOT NULL,
    subtotal              numeric(10, 2) NOT NULL,
    surge_multiplier      numeric(4, 2) NOT NULL,
    booking_fee           numeric(10, 2) NOT NULL,
    minimum_fare          numeric(10, 2) NOT NULL,
    minimum_fare_applied  boolean       NOT NULL,
    total                 numeric(10, 2) NOT NULL,
    currency              varchar(3)    NOT NULL,
    distance_m            integer       NOT NULL,
    duration_s            integer       NOT NULL,
    pricing_version       varchar(20)   NOT NULL,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_fare_breakdowns_kind CHECK (kind IN ('ESTIMATE', 'FINAL')),
    CONSTRAINT ck_fare_breakdowns_non_negative CHECK (total >= 0 AND subtotal >= 0 AND surge_multiplier >= 1),
    CONSTRAINT ux_fare_breakdowns_ride_kind UNIQUE (ride_id, kind)
);

-- Sampled positions while a trip is IN_PROGRESS; the actual trip distance is ST_Length of this line.
CREATE TABLE ride_track_points (
    id           bigint                 GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ride_id      uuid                   NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    location     geography(Point, 4326) NOT NULL,
    recorded_at  timestamptz            NOT NULL
);

CREATE INDEX ix_ride_track_points_ride ON ride_track_points (ride_id, recorded_at);
