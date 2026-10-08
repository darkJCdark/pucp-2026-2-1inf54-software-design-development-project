-- ============================================================
-- PaqRap
-- Validación de Base de Datos V2
-- BLOQUE 1: PRUEBAS POSITIVAS
-- ============================================================

USE paqrap;

START TRANSACTION;

-- ============================================================
-- 1. PEDIDO PRIORITARIO VÁLIDO
-- ============================================================

INSERT INTO orders (
    order_id,
    client_id,
    destination_x,
    destination_y,
    packages,
    registered_at,
    deadline,
    status,
    delivery_type,
    promised_hours,
    delivered_at
)
VALUES (
    'TEST-V2-ORDER-001',
    'cV2TEST',
    20,
    20,
    10,
    '2026-10-08 08:00:00',
    '2026-10-08 16:00:00',
    'REGISTERED',
    'PRIORITY',
    8,
    NULL
);

SELECT
    'PRUEBA POSITIVA - PEDIDO V2' AS prueba,
    COUNT(*) AS registros
FROM orders
WHERE order_id = 'TEST-V2-ORDER-001';


-- ============================================================
-- 2. HISTORIAL DEL PEDIDO
-- ============================================================

INSERT INTO order_status_history (
    order_id,
    status,
    changed_at,
    vehicle_id,
    warehouse_id
)
VALUES (
    'TEST-V2-ORDER-001',
    'REGISTERED',
    '2026-10-08 08:00:00',
    NULL,
    NULL
);

SELECT
    'PRUEBA POSITIVA - HISTORIAL' AS prueba,
    COUNT(*) AS registros
FROM order_status_history
WHERE order_id = 'TEST-V2-ORDER-001';


-- ============================================================
-- 3. EJECUCIÓN DE ESCENARIO
-- ============================================================

INSERT INTO scenario_executions (
    scenario_type,
    algorithm_mode,
    status,
    started_at,
    simulation_started_at
)
VALUES (
    'FIVE_DAY',
    'BOTH',
    'RUNNING',
    '2026-10-08 08:00:00',
    '2026-01-01 00:00:00'
);

SET @test_execution_id = LAST_INSERT_ID();

SELECT
    'PRUEBA POSITIVA - EJECUCION' AS prueba,
    COUNT(*) AS registros
FROM scenario_executions
WHERE execution_id = @test_execution_id;


-- ============================================================
-- 4. PLAN
-- ============================================================

INSERT INTO plans (
    execution_id,
    algorithm,
    plan_type,
    created_at,
    feasible,
    total_distance_km,
    total_cost
)
VALUES (
    @test_execution_id,
    'GRASP',
    'INITIAL',
    '2026-10-08 08:01:00',
    TRUE,
    20.00,
    118.00
);

SET @test_plan_id = LAST_INSERT_ID();

SELECT
    'PRUEBA POSITIVA - PLAN' AS prueba,
    COUNT(*) AS registros
FROM plans
WHERE plan_id = @test_plan_id;


-- ============================================================
-- 5. RUTA
-- ============================================================

INSERT INTO routes (
    plan_id,
    vehicle_id,
    start_warehouse_id,
    origin_x,
    origin_y,
    departure_at,
    total_distance_km,
    total_cost
)
VALUES (
    @test_plan_id,
    'TA01',
    'CENTRAL',
    27,
    14,
    '2026-10-08 08:05:00',
    20.00,
    118.00
);

SET @test_route_id = LAST_INSERT_ID();

SELECT
    'PRUEBA POSITIVA - RUTA' AS prueba,
    COUNT(*) AS registros
FROM routes
WHERE route_id = @test_route_id;


-- ============================================================
-- 6. PARADA DE ENTREGA
-- ============================================================

INSERT INTO route_stops (
    route_id,
    sequence_no,
    stop_type,
    order_id,
    warehouse_id,
    delivered_packages,
    x,
    y,
    planned_arrival_at
)
VALUES (
    @test_route_id,
    1,
    'DELIVERY',
    'TEST-V2-ORDER-001',
    NULL,
    10,
    20,
    20,
    '2026-10-08 09:00:00'
);

SELECT
    'PRUEBA POSITIVA - PARADA DELIVERY' AS prueba,
    COUNT(*) AS registros
FROM route_stops
WHERE route_id = @test_route_id
  AND sequence_no = 1;


-- ============================================================
-- 7. PARADA DE ALMACÉN
-- ============================================================

INSERT INTO route_stops (
    route_id,
    sequence_no,
    stop_type,
    order_id,
    warehouse_id,
    delivered_packages,
    x,
    y,
    planned_arrival_at
)
VALUES (
    @test_route_id,
    2,
    'WAREHOUSE',
    NULL,
    'EAST',
    NULL,
    57,
    27,
    '2026-10-08 10:00:00'
);

SELECT
    'PRUEBA POSITIVA - PARADA WAREHOUSE' AS prueba,
    COUNT(*) AS registros
FROM route_stops
WHERE route_id = @test_route_id
  AND sequence_no = 2;


-- ============================================================
-- 8. MOVIMIENTO DE INVENTARIO
-- ============================================================

INSERT INTO inventory_movements (
    warehouse_id,
    execution_id,
    order_id,
    movement_type,
    quantity,
    occurred_at
)
VALUES (
    'CENTRAL',
    @test_execution_id,
    'TEST-V2-ORDER-001',
    'DISPATCH',
    10,
    '2026-10-08 08:05:00'
);

SELECT
    'PRUEBA POSITIVA - INVENTARIO' AS prueba,
    COUNT(*) AS registros
FROM inventory_movements
WHERE execution_id = @test_execution_id;


-- ============================================================
-- 9. CAMBIOS DE ESTADO DEL PEDIDO
-- ============================================================

UPDATE orders
SET status = 'ASSIGNED'
WHERE order_id = 'TEST-V2-ORDER-001';

INSERT INTO order_status_history (
    order_id,
    status,
    changed_at,
    vehicle_id,
    warehouse_id
)
VALUES (
    'TEST-V2-ORDER-001',
    'ASSIGNED',
    '2026-10-08 08:05:00',
    'TA01',
    'CENTRAL'
);


UPDATE orders
SET status = 'IN_TRANSIT'
WHERE order_id = 'TEST-V2-ORDER-001';

INSERT INTO order_status_history (
    order_id,
    status,
    changed_at,
    vehicle_id,
    warehouse_id
)
VALUES (
    'TEST-V2-ORDER-001',
    'IN_TRANSIT',
    '2026-10-08 08:10:00',
    'TA01',
    'CENTRAL'
);


UPDATE orders
SET
    status = 'DELIVERED',
    delivered_at = '2026-10-08 09:00:00'
WHERE order_id = 'TEST-V2-ORDER-001';

INSERT INTO order_status_history (
    order_id,
    status,
    changed_at,
    vehicle_id,
    warehouse_id
)
VALUES (
    'TEST-V2-ORDER-001',
    'DELIVERED',
    '2026-10-08 09:00:00',
    'TA01',
    NULL
);


SELECT
    status,
    delivery_type,
    promised_hours,
    delivered_at
FROM orders
WHERE order_id = 'TEST-V2-ORDER-001';


SELECT
    status,
    changed_at,
    vehicle_id,
    warehouse_id
FROM order_status_history
WHERE order_id = 'TEST-V2-ORDER-001'
ORDER BY changed_at;


-- ============================================================
-- 10. RESULTADO DEL ESCENARIO
-- ============================================================

INSERT INTO scenario_results (
    execution_id,
    algorithm,
    total_orders,
    delivered_orders,
    on_time_orders,
    undelivered_orders,
    total_distance_km,
    total_cost,
    computation_time_ms,
    breakdown_count,
    road_block_count,
    replanning_count
)
VALUES (
    @test_execution_id,
    'GRASP',
    1,
    1,
    1,
    0,
    20.00,
    118.00,
    100,
    0,
    0,
    0
);

SELECT
    'PRUEBA POSITIVA - RESULTADO' AS prueba,
    COUNT(*) AS registros
FROM scenario_results
WHERE execution_id = @test_execution_id
  AND algorithm = 'GRASP';


-- ============================================================
-- 11. COMPROBACIÓN DEL MODELO COMPLETO
-- ============================================================

SELECT
    se.execution_id,
    se.scenario_type,
    se.algorithm_mode,
    p.plan_id,
    p.algorithm,
    r.route_id,
    r.vehicle_id,
    rs.sequence_no,
    rs.stop_type,
    rs.order_id,
    rs.warehouse_id
FROM scenario_executions se
JOIN plans p
    ON p.execution_id = se.execution_id
JOIN routes r
    ON r.plan_id = p.plan_id
JOIN route_stops rs
    ON rs.route_id = r.route_id
WHERE se.execution_id = @test_execution_id
ORDER BY rs.sequence_no;


-- ============================================================
-- 12. ROLLBACK
-- Ningún dato TEST debe permanecer en la BD.
-- ============================================================

ROLLBACK;


-- ============================================================
-- 13. COMPROBACIÓN DEL ROLLBACK
-- Todos deben devolver 0.
-- ============================================================

SELECT COUNT(*) AS test_orders_remaining
FROM orders
WHERE order_id = 'TEST-V2-ORDER-001';

SELECT COUNT(*) AS test_history_remaining
FROM order_status_history
WHERE order_id = 'TEST-V2-ORDER-001';

SELECT COUNT(*) AS test_executions_remaining
FROM scenario_executions
WHERE execution_id = @test_execution_id;

SELECT COUNT(*) AS test_plans_remaining
FROM plans
WHERE plan_id = @test_plan_id;

SELECT COUNT(*) AS test_routes_remaining
FROM routes
WHERE route_id = @test_route_id;

SELECT COUNT(*) AS test_route_stops_remaining
FROM route_stops
WHERE route_id = @test_route_id;

SELECT COUNT(*) AS test_inventory_remaining
FROM inventory_movements
WHERE execution_id = @test_execution_id;

SELECT COUNT(*) AS test_results_remaining
FROM scenario_results
WHERE execution_id = @test_execution_id;