-- ============================================================
-- PaqRap
-- Seed V2 - datos maestros iniciales
--
-- No contiene pedidos, bloqueos, averías, mantenimientos,
-- ejecuciones, planes, rutas ni resultados ficticios.
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
-- 3. VEHÍCULOS - ESTADO INICIAL EN ALMACÉN CENTRAL
-- ============================================================

INSERT IGNORE INTO vehicles (
    vehicle_id,
    vehicle_type,
    operational_status,
    current_x,
    current_y,
    current_load,
    available_at
)
VALUES
    ('TA01', 'CAR',        'AVAILABLE', 27, 14, 0, NULL),
    ('TA02', 'CAR',        'AVAILABLE', 27, 14, 0, NULL),
    ('TA03', 'CAR',        'AVAILABLE', 27, 14, 0, NULL),
    ('TA04', 'CAR',        'AVAILABLE', 27, 14, 0, NULL),
    ('TA05', 'CAR',        'AVAILABLE', 27, 14, 0, NULL),
    ('TA06', 'CAR',        'AVAILABLE', 27, 14, 0, NULL),
    ('TA07', 'CAR',        'AVAILABLE', 27, 14, 0, NULL),
    ('TA08', 'CAR',        'AVAILABLE', 27, 14, 0, NULL),
    ('TA09', 'CAR',        'AVAILABLE', 27, 14, 0, NULL),
    ('TA10', 'CAR',        'AVAILABLE', 27, 14, 0, NULL),

    ('TM01', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM02', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM03', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM04', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM05', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM06', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM07', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM08', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM09', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM10', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM11', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM12', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM13', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM14', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),
    ('TM15', 'MOTORCYCLE', 'AVAILABLE', 27, 14, 0, NULL),

    ('TB01', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL),
    ('TB02', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL),
    ('TB03', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL),
    ('TB04', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL),
    ('TB05', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL),
    ('TB06', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL),
    ('TB07', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL),
    ('TB08', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL),
    ('TB09', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL),
    ('TB10', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL),
    ('TB11', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL),
    ('TB12', 'BICYCLE',    'AVAILABLE', 27, 14, 0, NULL);
