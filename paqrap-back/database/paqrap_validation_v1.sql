-- ============================================================
-- PaqRap
-- Validación del Modelo Relacional V1
-- ============================================================
--
-- Objetivo:
-- 1. Verificar estructura creada.
-- 2. Verificar datos maestros.
-- 3. Comprobar inserciones válidas.
-- 4. Comprobar restricciones CHECK, UNIQUE y FK.
--
-- Los datos de prueba válidos se ejecutan dentro de una
-- transacción y posteriormente se revierten con ROLLBACK.
-- ============================================================

USE paqrap;

-- ============================================================
-- 1. VALIDACIÓN GENERAL DEL ESQUEMA
-- ============================================================

SELECT
    TABLE_NAME,
    ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'paqrap'
ORDER BY TABLE_NAME;

-- Deben existir exactamente 8 tablas InnoDB.


-- ============================================================
-- 2. VALIDACIÓN DE CONSTRAINTS
-- ============================================================

SELECT
    TABLE_NAME,
    CONSTRAINT_NAME,
    CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = 'paqrap'
ORDER BY TABLE_NAME, CONSTRAINT_TYPE, CONSTRAINT_NAME;


-- ============================================================
-- 3. VALIDACIÓN DE DATOS MAESTROS
-- ============================================================

SELECT
    vehicle_type,
    capacity_packages,
    speed_kmh,
    cost_per_km
FROM vehicle_type_parameters
ORDER BY vehicle_type;

SELECT
    warehouse_id,
    warehouse_kind,
    x,
    y,
    initial_stock
FROM warehouses
ORDER BY warehouse_id;

SELECT
    vehicle_type,
    COUNT(*) AS total
FROM vehicles
GROUP BY vehicle_type
ORDER BY vehicle_type;

SELECT
    COUNT(*) AS total_vehicles
FROM vehicles;

-- Resultados esperados:
--
-- vehicle_type_parameters = 3 registros
-- warehouses = 3 registros
--
-- BICYCLE      = 12
-- CAR          = 10
-- MOTORCYCLE   = 15
--
-- total_vehicles = 37


-- ============================================================
-- 4. PRUEBA POSITIVA
-- Datos válidos que TODAS las restricciones deben aceptar.
-- Todo se revierte al terminar.
-- ============================================================

START TRANSACTION;

INSERT INTO orders (
    order_id,
    client_id,
    destination_x,
    destination_y,
    packages,
    registered_at,
    deadline
)
VALUES (
    'TEST-ORDER-001',
    'c9999',
    20,
    30,
    10,
    '2026-09-10 08:00:00',
    '2026-09-11 20:00:00'
);

INSERT INTO maintenance_days (
    vehicle_id,
    maintenance_date
)
VALUES (
    'TA01',
    '2026-12-01'
);

INSERT INTO breakdown_events (
    vehicle_id,
    breakdown_type,
    occurred_at,
    location_x,
    location_y
)
VALUES (
    'TM01',
    'MINOR',
    '2026-09-10 10:00:00',
    30,
    20
);

INSERT INTO road_blocks (
    road_block_id,
    starts_at,
    ends_at
)
VALUES (
    900001,
    '2026-09-10 06:00:00',
    '2026-09-10 15:00:00'
);

INSERT INTO road_block_nodes (
    road_block_id,
    sequence_no,
    x,
    y
)
VALUES
    (900001, 1, 31, 21),
    (900001, 2, 32, 21),
    (900001, 3, 33, 21),
    (900001, 4, 34, 21);

SELECT 'PRUEBA POSITIVA - ORDER' AS prueba, COUNT(*) AS registros
FROM orders
WHERE order_id = 'TEST-ORDER-001';

SELECT 'PRUEBA POSITIVA - MANTENIMIENTO' AS prueba, COUNT(*) AS registros
FROM maintenance_days
WHERE vehicle_id = 'TA01'
  AND maintenance_date = '2026-12-01';

SELECT 'PRUEBA POSITIVA - AVERIA' AS prueba, COUNT(*) AS registros
FROM breakdown_events
WHERE vehicle_id = 'TM01'
  AND occurred_at = '2026-09-10 10:00:00';

SELECT 'PRUEBA POSITIVA - BLOQUEO' AS prueba, COUNT(*) AS registros
FROM road_block_nodes
WHERE road_block_id = 900001;

ROLLBACK;


-- ============================================================
-- 5. COMPROBACIÓN DEL ROLLBACK
-- ============================================================

SELECT COUNT(*) AS test_orders_remaining
FROM orders
WHERE order_id = 'TEST-ORDER-001';

SELECT COUNT(*) AS test_maintenance_remaining
FROM maintenance_days
WHERE vehicle_id = 'TA01'
  AND maintenance_date = '2026-12-01';

SELECT COUNT(*) AS test_blocks_remaining
FROM road_blocks
WHERE road_block_id = 900001;

-- Todos deben devolver 0.


-- ============================================================
-- 6. PRUEBAS NEGATIVAS
-- ============================================================
--
-- IMPORTANTE:
-- NO ejecutes todo este bloque simultáneamente.
--
-- Ejecuta cada INSERT individualmente.
-- Cada uno DEBE producir ERROR.
--
-- Si alguno se inserta correctamente, existe un problema
-- en el esquema V1.
-- ============================================================


-- ------------------------------------------------------------
-- NEGATIVA 1
-- Pedido fuera de los límites del mapa (X = 71)
-- Esperado: ERROR por CHECK destination_x.
-- ------------------------------------------------------------

INSERT INTO orders (
    order_id,
    client_id,
    destination_x,
    destination_y,
    packages,
    registered_at,
    deadline
)
VALUES (
    'INVALID-X',
    'c9999',
    71,
    20,
    5,
    '2026-09-10 08:00:00',
    '2026-09-11 08:00:00'
);


-- ------------------------------------------------------------
-- NEGATIVA 2
-- Pedido con cantidad cero
-- Esperado: ERROR por CHECK packages > 0.
-- ------------------------------------------------------------

INSERT INTO orders (
    order_id,
    client_id,
    destination_x,
    destination_y,
    packages,
    registered_at,
    deadline
)
VALUES (
    'INVALID-PACKAGES',
    'c9999',
    20,
    20,
    0,
    '2026-09-10 08:00:00',
    '2026-09-11 08:00:00'
);


-- ------------------------------------------------------------
-- NEGATIVA 3
-- Deadline anterior al registro
-- Esperado: ERROR por CHECK deadline > registered_at.
-- ------------------------------------------------------------

INSERT INTO orders (
    order_id,
    client_id,
    destination_x,
    destination_y,
    packages,
    registered_at,
    deadline
)
VALUES (
    'INVALID-DEADLINE',
    'c9999',
    20,
    20,
    5,
    '2026-09-10 08:00:00',
    '2026-09-09 08:00:00'
);


-- ------------------------------------------------------------
-- NEGATIVA 4
-- Almacén intermedio sin stock finito
-- Esperado: ERROR por CHECK warehouses_stock.
-- ------------------------------------------------------------

INSERT INTO warehouses (
    warehouse_id,
    warehouse_kind,
    x,
    y,
    initial_stock
)
VALUES (
    'INVALID-WAREHOUSE',
    'INTERMEDIATE',
    30,
    30,
    NULL
);


-- ------------------------------------------------------------
-- NEGATIVA 5
-- Vehículo asociado a un tipo inexistente
-- Esperado: ERROR por FOREIGN KEY.
-- ------------------------------------------------------------

INSERT INTO vehicles (
    vehicle_id,
    vehicle_type,
    available
)
VALUES (
    'TX99',
    'TRUCK',
    TRUE
);


-- ------------------------------------------------------------
-- NEGATIVA 6
-- Mantenimiento de un vehículo inexistente
-- Esperado: ERROR por FOREIGN KEY.
-- ------------------------------------------------------------

INSERT INTO maintenance_days (
    vehicle_id,
    maintenance_date
)
VALUES (
    'TX99',
    '2026-12-01'
);


-- ------------------------------------------------------------
-- NEGATIVA 7
-- Tipo de avería no permitido
-- Esperado: ERROR por CHECK breakdown_type.
-- ------------------------------------------------------------

INSERT INTO breakdown_events (
    vehicle_id,
    breakdown_type,
    occurred_at,
    location_x,
    location_y
)
VALUES (
    'TA01',
    'UNKNOWN',
    '2026-09-10 10:00:00',
    20,
    20
);


-- ------------------------------------------------------------
-- NEGATIVA 8
-- Bloqueo cuyo final ocurre antes del inicio
-- Esperado: ERROR por CHECK road_blocks_period.
-- ------------------------------------------------------------

INSERT INTO road_blocks (
    starts_at,
    ends_at
)
VALUES (
    '2026-09-10 15:00:00',
    '2026-09-10 06:00:00'
);


-- ------------------------------------------------------------
-- NEGATIVA 9
-- Nodo asociado a bloqueo inexistente
-- Esperado: ERROR por FOREIGN KEY.
-- ------------------------------------------------------------

INSERT INTO road_block_nodes (
    road_block_id,
    sequence_no,
    x,
    y
)
VALUES (
    999999999,
    1,
    20,
    20
);


-- ============================================================
-- 7. CONTROL FINAL
-- ============================================================

SELECT COUNT(*) AS total_vehicle_types
FROM vehicle_type_parameters;

SELECT COUNT(*) AS total_warehouses
FROM warehouses;

SELECT COUNT(*) AS total_vehicles
FROM vehicles;

-- Debe mantenerse:
--
-- total_vehicle_types = 3
-- total_warehouses = 3
-- total_vehicles = 37