-- ============================================================
-- PaqRap
-- Seed V1
-- Datos maestros iniciales
-- ============================================================

USE paqrap;

-- ============================================================
-- 1. PARÁMETROS POR TIPO DE VEHÍCULO
-- ============================================================

INSERT INTO vehicle_type_parameters (
    vehicle_type,
    capacity_packages,
    speed_kmh,
    cost_per_km
)
VALUES
    ('CAR',        24, 40.00, 8.00),
    ('MOTORCYCLE',  8, 25.00, 6.00),
    ('BICYCLE',     4, 12.00, 3.00)
ON DUPLICATE KEY UPDATE
    capacity_packages = VALUES(capacity_packages),
    speed_kmh = VALUES(speed_kmh),
    cost_per_km = VALUES(cost_per_km);

-- ============================================================
-- 2. ALMACENES
-- ============================================================

INSERT INTO warehouses (
    warehouse_id,
    warehouse_kind,
    x,
    y,
    initial_stock
)
VALUES
    ('CENTRAL',   'CENTRAL',      27, 14, NULL),
    ('NORTHWEST', 'INTERMEDIATE', 12, 38, 1000),
    ('EAST',      'INTERMEDIATE', 57, 27, 1000)
ON DUPLICATE KEY UPDATE
    warehouse_kind = VALUES(warehouse_kind),
    x = VALUES(x),
    y = VALUES(y),
    initial_stock = VALUES(initial_stock);

-- ============================================================
-- 3. VEHÍCULOS - AUTOS
-- ============================================================

INSERT IGNORE INTO vehicles (
    vehicle_id,
    vehicle_type,
    available
)
VALUES
    ('TA01', 'CAR', TRUE),
    ('TA02', 'CAR', TRUE),
    ('TA03', 'CAR', TRUE),
    ('TA04', 'CAR', TRUE),
    ('TA05', 'CAR', TRUE),
    ('TA06', 'CAR', TRUE),
    ('TA07', 'CAR', TRUE),
    ('TA08', 'CAR', TRUE),
    ('TA09', 'CAR', TRUE),
    ('TA10', 'CAR', TRUE);

-- ============================================================
-- 4. VEHÍCULOS - MOTOCICLETAS
-- ============================================================

INSERT IGNORE INTO vehicles (
    vehicle_id,
    vehicle_type,
    available
)
VALUES
    ('TM01', 'MOTORCYCLE', TRUE),
    ('TM02', 'MOTORCYCLE', TRUE),
    ('TM03', 'MOTORCYCLE', TRUE),
    ('TM04', 'MOTORCYCLE', TRUE),
    ('TM05', 'MOTORCYCLE', TRUE),
    ('TM06', 'MOTORCYCLE', TRUE),
    ('TM07', 'MOTORCYCLE', TRUE),
    ('TM08', 'MOTORCYCLE', TRUE),
    ('TM09', 'MOTORCYCLE', TRUE),
    ('TM10', 'MOTORCYCLE', TRUE),
    ('TM11', 'MOTORCYCLE', TRUE),
    ('TM12', 'MOTORCYCLE', TRUE),
    ('TM13', 'MOTORCYCLE', TRUE),
    ('TM14', 'MOTORCYCLE', TRUE),
    ('TM15', 'MOTORCYCLE', TRUE);

-- ============================================================
-- 5. VEHÍCULOS - BICICLETAS
-- ============================================================

INSERT IGNORE INTO vehicles (
    vehicle_id,
    vehicle_type,
    available
)
VALUES
    ('TB01', 'BICYCLE', TRUE),
    ('TB02', 'BICYCLE', TRUE),
    ('TB03', 'BICYCLE', TRUE),
    ('TB04', 'BICYCLE', TRUE),
    ('TB05', 'BICYCLE', TRUE),
    ('TB06', 'BICYCLE', TRUE),
    ('TB07', 'BICYCLE', TRUE),
    ('TB08', 'BICYCLE', TRUE),
    ('TB09', 'BICYCLE', TRUE),
    ('TB10', 'BICYCLE', TRUE),
    ('TB11', 'BICYCLE', TRUE),
    ('TB12', 'BICYCLE', TRUE);