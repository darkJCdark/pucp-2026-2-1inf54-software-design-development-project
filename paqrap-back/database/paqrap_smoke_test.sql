-- ============================================================
-- PaqRap - Smoke test de una instalación limpia V2
-- ============================================================

USE paqrap;

SELECT COUNT(*) AS total_tables
FROM information_schema.tables
WHERE table_schema = 'paqrap';
-- Esperado: 15

SELECT COUNT(*) AS total_vehicle_types
FROM vehicle_type_parameters;
-- Esperado: 3

SELECT COUNT(*) AS total_warehouses
FROM warehouses;
-- Esperado: 3

SELECT COUNT(*) AS total_vehicles
FROM vehicles;
-- Esperado: 37

SELECT vehicle_type, COUNT(*) AS total
FROM vehicles
GROUP BY vehicle_type
ORDER BY vehicle_type;
-- Esperado: BICYCLE=12, CAR=10, MOTORCYCLE=15

SELECT warehouse_id, warehouse_kind, x, y, initial_stock
FROM warehouses
ORDER BY warehouse_id;

SELECT vehicle_type, capacity_packages, speed_kmh, cost_per_km
FROM vehicle_type_parameters
ORDER BY vehicle_type;

-- Una instalación base no debe inventar datos operativos.
SELECT
    (SELECT COUNT(*) FROM orders) AS orders_count,
    (SELECT COUNT(*) FROM maintenance_days) AS maintenance_count,
    (SELECT COUNT(*) FROM road_blocks) AS road_blocks_count,
    (SELECT COUNT(*) FROM breakdown_events) AS breakdowns_count,
    (SELECT COUNT(*) FROM scenario_executions) AS executions_count,
    (SELECT COUNT(*) FROM plans) AS plans_count,
    (SELECT COUNT(*) FROM routes) AS routes_count;
-- Esperado: todos 0
