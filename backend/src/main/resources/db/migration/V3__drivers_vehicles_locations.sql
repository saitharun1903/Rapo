CREATE TABLE drivers (
    id                   uuid          PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    license_number       varchar(40)   NOT NULL,
    verification_status  varchar(16)   NOT NULL DEFAULT 'PENDING',
    verified_at          timestamptz,
    verified_by          uuid          REFERENCES users (id) ON DELETE SET NULL,
    rejection_reason     varchar(255),
    availability         varchar(16)   NOT NULL DEFAULT 'OFFLINE',
    rating_avg           numeric(3, 2),
    rating_count         integer       NOT NULL DEFAULT 0,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    version              bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ux_drivers_license UNIQUE (license_number),
    CONSTRAINT ck_drivers_verification CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT ck_drivers_availability CHECK (availability IN ('OFFLINE', 'AVAILABLE', 'ON_TRIP')),
    CONSTRAINT ck_drivers_online_requires_verification
        CHECK (availability = 'OFFLINE' OR verification_status = 'VERIFIED'),
    CONSTRAINT ck_drivers_rating_range CHECK (rating_avg IS NULL OR rating_avg BETWEEN 1 AND 5),
    CONSTRAINT ck_drivers_rating_count CHECK (rating_count >= 0)
);

CREATE INDEX ix_drivers_available ON drivers (id)
    WHERE availability = 'AVAILABLE' AND verification_status = 'VERIFIED';
CREATE INDEX ix_drivers_verification ON drivers (verification_status, created_at);

CREATE TABLE vehicles (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id     uuid        NOT NULL REFERENCES drivers (id) ON DELETE CASCADE,
    make          varchar(40) NOT NULL,
    model         varchar(40) NOT NULL,
    color         varchar(30) NOT NULL,
    plate_number  varchar(20) NOT NULL,
    model_year    smallint    NOT NULL,
    category      varchar(16) NOT NULL,
    seats         smallint    NOT NULL,
    active        boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_vehicles_plate UNIQUE (plate_number),
    CONSTRAINT ck_vehicles_category CHECK (category IN ('ECONOMY', 'COMFORT', 'XL')),
    CONSTRAINT ck_vehicles_seats CHECK (seats BETWEEN 2 AND 8),
    CONSTRAINT ck_vehicles_model_year CHECK (model_year >= 1990)
);

CREATE UNIQUE INDEX ux_vehicles_one_active_per_driver ON vehicles (driver_id) WHERE active;

-- Current position only (one row per driver). Written by the batched location consumer,
-- never per GPS ping. See docs/architecture.md section 8.
CREATE TABLE driver_locations (
    driver_id    uuid                   PRIMARY KEY REFERENCES drivers (id) ON DELETE CASCADE,
    location     geography(Point, 4326) NOT NULL,
    heading_deg  smallint,
    speed_mps    numeric(5, 2),
    accuracy_m   numeric(6, 1),
    recorded_at  timestamptz            NOT NULL,
    updated_at   timestamptz            NOT NULL DEFAULT now(),
    CONSTRAINT ck_driver_locations_heading CHECK (heading_deg IS NULL OR heading_deg BETWEEN 0 AND 359),
    CONSTRAINT ck_driver_locations_speed CHECK (speed_mps IS NULL OR speed_mps >= 0)
);

CREATE INDEX ix_driver_locations_location   ON driver_locations USING GIST (location);
CREATE INDEX ix_driver_locations_updated_at ON driver_locations (updated_at);
