-- DEMO DATA ONLY. Applied solely when the "demo" Spring profile is active (see application-demo.yml).
-- Contains accounts and verified drivers with vehicles. It deliberately contains NO rides, payments,
-- ratings or analytics: that data is produced by actually running rides (e.g. with the simulator).
-- Passwords are hashed in the database from the ${demo_password} Flyway placeholder (env DEMO_USER_PASSWORD).
-- Emails use the reserved example.com domain.

INSERT INTO users (id, email, phone, password_hash, full_name, role)
VALUES
    ('00000000-0000-4000-a000-000000000001', 'admin@rideflow.example.com',     NULL, '{bcrypt}' || crypt('${demo_password}', gen_salt('bf', 10)), 'Demo Admin',     'ADMIN'),
    ('00000000-0000-4000-a000-000000000101', 'ananya@rideflow.example.com',    NULL, '{bcrypt}' || crypt('${demo_password}', gen_salt('bf', 10)), 'Ananya Iyer',    'PASSENGER'),
    ('00000000-0000-4000-a000-000000000102', 'rahul@rideflow.example.com',     NULL, '{bcrypt}' || crypt('${demo_password}', gen_salt('bf', 10)), 'Rahul Verma',    'PASSENGER'),
    ('00000000-0000-4000-a000-000000000103', 'meera@rideflow.example.com',     NULL, '{bcrypt}' || crypt('${demo_password}', gen_salt('bf', 10)), 'Meera Nair',     'PASSENGER'),
    ('00000000-0000-4000-a000-000000000201', 'driver.arjun@rideflow.example.com',  NULL, '{bcrypt}' || crypt('${demo_password}', gen_salt('bf', 10)), 'Arjun Reddy',   'DRIVER'),
    ('00000000-0000-4000-a000-000000000202', 'driver.farhan@rideflow.example.com', NULL, '{bcrypt}' || crypt('${demo_password}', gen_salt('bf', 10)), 'Farhan Ali',    'DRIVER'),
    ('00000000-0000-4000-a000-000000000203', 'driver.lakshmi@rideflow.example.com', NULL, '{bcrypt}' || crypt('${demo_password}', gen_salt('bf', 10)), 'Lakshmi Rao', 'DRIVER'),
    ('00000000-0000-4000-a000-000000000204', 'driver.vikram@rideflow.example.com', NULL, '{bcrypt}' || crypt('${demo_password}', gen_salt('bf', 10)), 'Vikram Singh',  'DRIVER'),
    ('00000000-0000-4000-a000-000000000205', 'driver.sneha@rideflow.example.com',  NULL, '{bcrypt}' || crypt('${demo_password}', gen_salt('bf', 10)), 'Sneha Kulkarni', 'DRIVER'),
    ('00000000-0000-4000-a000-000000000206', 'driver.karthik@rideflow.example.com', NULL, '{bcrypt}' || crypt('${demo_password}', gen_salt('bf', 10)), 'Karthik M',   'DRIVER')
ON CONFLICT (id) DO NOTHING;

INSERT INTO drivers (id, license_number, verification_status, verified_at, verified_by)
VALUES
    ('00000000-0000-4000-a000-000000000201', 'TS0120190001001', 'VERIFIED', now(), '00000000-0000-4000-a000-000000000001'),
    ('00000000-0000-4000-a000-000000000202', 'TS0920180002002', 'VERIFIED', now(), '00000000-0000-4000-a000-000000000001'),
    ('00000000-0000-4000-a000-000000000203', 'TS0720200003003', 'VERIFIED', now(), '00000000-0000-4000-a000-000000000001'),
    ('00000000-0000-4000-a000-000000000204', 'TS0820170004004', 'VERIFIED', now(), '00000000-0000-4000-a000-000000000001'),
    ('00000000-0000-4000-a000-000000000205', 'TS1020210005005', 'VERIFIED', now(), '00000000-0000-4000-a000-000000000001'),
    ('00000000-0000-4000-a000-000000000206', 'TS1120220006006', 'PENDING',  NULL,  NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO vehicles (id, driver_id, make, model, color, plate_number, model_year, category, seats)
VALUES
    ('00000000-0000-4000-b000-000000000201', '00000000-0000-4000-a000-000000000201', 'Maruti Suzuki', 'Dzire',   'White',  'TS09EA1201', 2021, 'ECONOMY', 4),
    ('00000000-0000-4000-b000-000000000202', '00000000-0000-4000-a000-000000000202', 'Hyundai',       'Aura',    'Silver', 'TS09EB2202', 2022, 'ECONOMY', 4),
    ('00000000-0000-4000-b000-000000000203', '00000000-0000-4000-a000-000000000203', 'Honda',         'City',    'Grey',   'TS07FC3203', 2023, 'COMFORT', 4),
    ('00000000-0000-4000-b000-000000000204', '00000000-0000-4000-a000-000000000204', 'Toyota',        'Innova Crysta', 'Black', 'TS08GD4204', 2022, 'XL', 6),
    ('00000000-0000-4000-b000-000000000205', '00000000-0000-4000-a000-000000000205', 'Tata',          'Tigor EV', 'Blue',  'TS10HE5205', 2024, 'ECONOMY', 4),
    ('00000000-0000-4000-b000-000000000206', '00000000-0000-4000-a000-000000000206', 'Kia',           'Carens',  'Red',    'TS11JF6206', 2024, 'XL', 6)
ON CONFLICT (id) DO NOTHING;
