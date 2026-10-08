-- ============================================================
-- PaqRap
-- Validación de Base de Datos V2 FINAL
--
-- Producto operativo:
--   - Simulated Annealing como único algoritmo.
--   - Sin selector de algoritmo en BD.
--   - Sin parámetros internos de SA en BD.
-- ============================================================

USE paqrap;


-- ============================================================
-- 1. VALIDACIÓN ESTRUCTURAL
-- ============================================================

SELECT COUNT(*) AS total_tables
FROM information_schema.tables
WHERE table_schema = 'paqrap';

-- Esperado: 15


-- Las columnas antiguas de selección de algoritmo ya no deben existir.

SELECT COUNT(*) AS obsolete_algorithm_columns
FROM information_schema.columns
WHERE table_schema = 'paqrap'
  AND (
        (table_name = 'scenario_executions'
         AND column_name = 'algorithm_mode')
     OR (table_name = 'plans'
         AND column_name = 'algorithm')
     OR (table_name = 'scenario_results'
         AND column_name = 'algorithm')
  );

-- Esperado: 0


-- Estado operativo nuevo de vehículos.

SELECT COUNT(*) AS vehicle_operational_columns
FROM information_schema.columns
WHERE table_schema = 'paqrap'
  AND table_name = 'vehicles'
  AND column_name IN (
      'operational_status',
      'current_x',
      'current_y',
      'current_load',
      'available_at'
  );

-- Esperado: 5


SELECT COUNT(*) AS old_vehicle_available_column
FROM information_schema.columns
WHERE table_schema = 'paqrap'
  AND table_name = 'vehicles'
  AND column_name = 'available';

-- Esperado: 0


-- Estado de rutas.

SELECT COUNT(*) AS route_state_columns
FROM information_schema.columns
WHERE table_schema = 'paqrap'
  AND table_name = 'routes'
  AND column_name IN (
      'status',
      'completed_at'
  );

-- Esperado: 2


-- Averías asociables a una ejecución.

SELECT COUNT(*) AS breakdown_execution_column
FROM information_schema.columns
WHERE table_schema = 'paqrap'
  AND table_name = 'breakdown_events'
  AND column_name = 'execution_id';

-- Esperado: 1


SELECT COUNT(*) AS breakdown_execution_fk
FROM information_schema.table_constraints
WHERE constraint_schema = 'paqrap'
  AND table_name = 'breakdown_events'
  AND constraint_name = 'fk_breakdown_execution'
  AND constraint_type = 'FOREIGN KEY';

-- Esperado: 1


-- ============================================================
-- 2. PRUEBAS POSITIVAS
-- Todos los cambios se revierten al final.
-- ============================================================

START TRANSACTION;


-- ------------------------------------------------------------
-- 2.1 Pedido prioritario consistente
-- ------------------------------------------------------------

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
    'TEST-V2-FINAL-ORDER',
    'cV2FINAL',
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
    'POSITIVA - PEDIDO' AS prueba,
    COUNT(*) AS registros
FROM orders
WHERE order_id = 'TEST-V2-FINAL-ORDER';

-- Esperado: 1


-- ------------------------------------------------------------
-- 2.2 Historial del pedido
-- ------------------------------------------------------------

INSERT INTO order_status_history (
    order_id,
    status,
    changed_at,
    vehicle_id,
    warehouse_id
)
VALUES (
    'TEST-V2-FINAL-ORDER',
    'REGISTERED',
    '2026-10-08 08:00:00',
    NULL,
    NULL
);

SELECT
    'POSITIVA - HISTORIAL' AS prueba,
    COUNT(*) AS registros
FROM order_status_history
WHERE order_id = 'TEST-V2-FINAL-ORDER';

-- Esperado: 1


-- ------------------------------------------------------------
-- 2.3 Ejecución
-- Ya no existe algorithm_mode.
-- ------------------------------------------------------------

INSERT INTO scenario_executions (
    scenario_type,
    status,
    started_at,
    simulation_started_at
)
VALUES (
    'DAY_TO_DAY',
    'RUNNING',
    '2026-10-08 08:00:00',
    '2026-01-01 00:00:00'
);

SET @test_execution_id = LAST_INSERT_ID();

SELECT
    'POSITIVA - EJECUCION' AS prueba,
    COUNT(*) AS registros
FROM scenario_executions
WHERE execution_id = @test_execution_id;

-- Esperado: 1


-- ------------------------------------------------------------
-- 2.4 Pausar y reanudar una ejecución
-- ------------------------------------------------------------

UPDATE scenario_executions
SET status = 'PAUSED'
WHERE execution_id = @test_execution_id;

SELECT status AS status_paused
FROM scenario_executions
WHERE execution_id = @test_execution_id;

-- Esperado: PAUSED


UPDATE scenario_executions
SET status = 'RUNNING'
WHERE execution_id = @test_execution_id;

SELECT status AS status_running_again
FROM scenario_executions
WHERE execution_id = @test_execution_id;

-- Esperado: RUNNING


-- ------------------------------------------------------------
-- 2.5 Estado operativo del vehículo
-- ------------------------------------------------------------

UPDATE vehicles
SET
    operational_status = 'IN_ROUTE',
    current_x = 30,
    current_y = 20,
    current_load = 10,
    available_at = '2026-10-08 12:00:00'
WHERE vehicle_id = 'TA01';

SELECT
    vehicle_id,
    operational_status,
    current_x,
    current_y,
    current_load,
    available_at
FROM vehicles
WHERE vehicle_id = 'TA01';

-- Esperado:
-- TA01 / IN_ROUTE / 30 / 20 / 10 / fecha


-- ------------------------------------------------------------
-- 2.6 Plan
-- Ya no existe columna algorithm.
-- ------------------------------------------------------------

INSERT INTO plans (
    execution_id,
    plan_type,
    feasible,
    total_distance_km,
    total_cost
)
VALUES (
    @test_execution_id,
    'INITIAL',
    TRUE,
    20.00,
    118.00
);

SET @test_plan_id = LAST_INSERT_ID();

SELECT
    'POSITIVA - PLAN' AS prueba,
    COUNT(*) AS registros
FROM plans
WHERE plan_id = @test_plan_id;

-- Esperado: 1


-- ------------------------------------------------------------
-- 2.7 Ruta
-- ------------------------------------------------------------

INSERT INTO routes (
    plan_id,
    vehicle_id,
    start_warehouse_id,
    origin_x,
    origin_y,
    departure_at,
    status,
    completed_at,
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
    'IN_PROGRESS',
    NULL,
    20.00,
    118.00
);

SET @test_route_id = LAST_INSERT_ID();

SELECT
    'POSITIVA - RUTA' AS prueba,
    COUNT(*) AS registros
FROM routes
WHERE route_id = @test_route_id;

-- Esperado: 1


-- ------------------------------------------------------------
-- 2.8 Parada DELIVERY
-- ------------------------------------------------------------

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
    'TEST-V2-FINAL-ORDER',
    NULL,
    5,
    20,
    20,
    '2026-10-08 09:00:00'
);

SELECT
    'POSITIVA - DELIVERY PARCIAL' AS prueba,
    COUNT(*) AS registros
FROM route_stops
WHERE route_id = @test_route_id
  AND sequence_no = 1;

-- Esperado: 1


-- ------------------------------------------------------------
-- 2.9 Segunda entrega parcial del mismo pedido
-- ------------------------------------------------------------

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
    'DELIVERY',
    'TEST-V2-FINAL-ORDER',
    NULL,
    5,
    20,
    20,
    '2026-10-08 10:00:00'
);

SELECT
    SUM(delivered_packages) AS total_delivered
FROM route_stops
WHERE order_id = 'TEST-V2-FINAL-ORDER';

-- Esperado: 10


-- ------------------------------------------------------------
-- 2.10 Kardex
-- ------------------------------------------------------------

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
    'TEST-V2-FINAL-ORDER',
    'DISPATCH',
    10,
    '2026-10-08 08:05:00'
);

SELECT
    'POSITIVA - INVENTARIO' AS prueba,
    COUNT(*) AS registros
FROM inventory_movements
WHERE execution_id = @test_execution_id;

-- Esperado: 1


-- ------------------------------------------------------------
-- 2.11 Resultado de ejecución
-- Ya no existe columna algorithm.
-- ------------------------------------------------------------

INSERT INTO scenario_results (
    execution_id,
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
    'POSITIVA - RESULTADO' AS prueba,
    COUNT(*) AS registros
FROM scenario_results
WHERE execution_id = @test_execution_id;

-- Esperado: 1


-- ------------------------------------------------------------
-- 2.12 Completar ruta
-- ------------------------------------------------------------

UPDATE routes
SET
    status = 'COMPLETED',
    completed_at = '2026-10-08 10:30:00'
WHERE route_id = @test_route_id;

SELECT
    status,
    completed_at
FROM routes
WHERE route_id = @test_route_id;

-- Esperado: COMPLETED


-- ------------------------------------------------------------
-- 2.13 Detención manual soportada
-- ------------------------------------------------------------

UPDATE scenario_executions
SET
    status = 'STOPPED',
    finished_at = '2026-10-08 11:00:00'
WHERE execution_id = @test_execution_id;

SELECT status AS execution_final_status
FROM scenario_executions
WHERE execution_id = @test_execution_id;

-- Esperado: STOPPED


-- ============================================================
-- 3. ROLLBACK DE PRUEBAS POSITIVAS
-- ============================================================

ROLLBACK;


-- ============================================================
-- 4. CONTROL DEL ROLLBACK
-- ============================================================

SELECT COUNT(*) AS test_orders_remaining
FROM orders
WHERE order_id = 'TEST-V2-FINAL-ORDER';

SELECT COUNT(*) AS test_history_remaining
FROM order_status_history
WHERE order_id = 'TEST-V2-FINAL-ORDER';

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

-- Todos los resultados anteriores deben ser 0.


-- ============================================================
-- 5. CONTROL DE RESTAURACIÓN DEL VEHÍCULO
-- ============================================================

SELECT
    vehicle_id,
    operational_status,
    current_x,
    current_y,
    current_load,
    available_at
FROM vehicles
WHERE vehicle_id = 'TA01';

-- Como el UPDATE estaba dentro de la transacción,
-- debe haberse restaurado el estado previo de TA01.



-- ============================================================
-- 6. PRUEBAS NEGATIVAS
--
-- IMPORTANTE:
-- Ejecutar cada prueba individualmente.
-- No ejecutar todo este bloque de una sola vez.
-- ============================================================


-- ------------------------------------------------------------
-- NEGATIVA 1
-- Deadline inconsistente con promised_hours.
-- Esperado: chk_orders_deadline_consistency
-- ------------------------------------------------------------

/*

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
    'INVALID-V2-DEADLINE',
    'c9999',
    20,
    20,
    5,
    '2026-10-08 08:00:00',
    '2026-10-08 17:00:00',
    'REGISTERED',
    'PRIORITY',
    8,
    NULL
);

*/


-- ------------------------------------------------------------
-- NEGATIVA 2
-- Estado de ejecución inexistente.
-- CANCELLED no se utiliza:
-- una detención manual se representa mediante STOPPED.
--
-- Esperado: chk_scenario_execution_status
-- ------------------------------------------------------------

/*

INSERT INTO scenario_executions (
    scenario_type,
    status,
    simulation_started_at
)
VALUES (
    'DAY_TO_DAY',
    'CANCELLED',
    '2026-01-01 00:00:00'
);

*/


-- ------------------------------------------------------------
-- NEGATIVA 3
-- Estado operativo de vehículo inválido.
-- Esperado: chk_vehicles_operational_status
-- ------------------------------------------------------------

/*

UPDATE vehicles
SET operational_status = 'FLYING'
WHERE vehicle_id = 'TA01';

*/


-- ------------------------------------------------------------
-- NEGATIVA 4
-- Posición del vehículo fuera del mapa.
-- Esperado: chk_vehicles_current_x
-- ------------------------------------------------------------

/*

UPDATE vehicles
SET current_x = 71
WHERE vehicle_id = 'TA01';

*/

-- ------------------------------------------------------------
-- NEGATIVA 5
-- Estado de ruta inválido.
-- Esperado: ERROR por chk_routes_status
-- ------------------------------------------------------------

START TRANSACTION;

INSERT INTO scenario_executions (
    scenario_type,
    status,
    simulation_started_at
)
VALUES (
    'DAY_TO_DAY',
    'RUNNING',
    '2026-01-01 00:00:00'
);

SET @neg_execution_id = LAST_INSERT_ID();

INSERT INTO plans (
    execution_id,
    plan_type,
    feasible,
    total_distance_km,
    total_cost
)
VALUES (
    @neg_execution_id,
    'INITIAL',
    TRUE,
    10.00,
    50.00
);

SET @neg_plan_id = LAST_INSERT_ID();

INSERT INTO routes (
    plan_id,
    vehicle_id,
    start_warehouse_id,
    origin_x,
    origin_y,
    departure_at,
    status,
    completed_at,
    total_distance_km,
    total_cost
)
VALUES (
    @neg_plan_id,
    'TA01',
    'CENTRAL',
    27,
    14,
    '2026-10-08 08:00:00',
    'PLANNED',
    NULL,
    10.00,
    50.00
);

SET @neg_route_id = LAST_INSERT_ID();

-- Ejecutar esta sentencia individualmente.
-- Debe fallar por chk_routes_status.

UPDATE routes
SET status = 'UNKNOWN'
WHERE route_id = @neg_route_id;

ROLLBACK;


-- ------------------------------------------------------------
-- NEGATIVA 6
-- completed_at anterior a departure_at.
-- Esperado: ERROR por chk_routes_completed_at
-- ------------------------------------------------------------

START TRANSACTION;

INSERT INTO scenario_executions (
    scenario_type,
    status,
    simulation_started_at
)
VALUES (
    'DAY_TO_DAY',
    'RUNNING',
    '2026-01-01 00:00:00'
);

SET @neg_execution_id = LAST_INSERT_ID();

INSERT INTO plans (
    execution_id,
    plan_type,
    feasible,
    total_distance_km,
    total_cost
)
VALUES (
    @neg_execution_id,
    'INITIAL',
    TRUE,
    10.00,
    50.00
);

SET @neg_plan_id = LAST_INSERT_ID();

INSERT INTO routes (
    plan_id,
    vehicle_id,
    start_warehouse_id,
    origin_x,
    origin_y,
    departure_at,
    status,
    completed_at,
    total_distance_km,
    total_cost
)
VALUES (
    @neg_plan_id,
    'TA01',
    'CENTRAL',
    27,
    14,
    '2026-10-08 08:00:00',
    'PLANNED',
    NULL,
    10.00,
    50.00
);

SET @neg_route_id = LAST_INSERT_ID();

-- Ejecutar esta sentencia individualmente.
-- Debe fallar por chk_routes_completed_at.

UPDATE routes
SET
    status = 'COMPLETED',
    completed_at = '2026-10-08 07:00:00'
WHERE route_id = @neg_route_id;

ROLLBACK;


-- ------------------------------------------------------------
-- NEGATIVA 7
-- Dos resultados para una misma ejecución.
-- execution_id es PRIMARY KEY en scenario_results.
-- Esperado: ERROR 1062 Duplicate entry / PRIMARY
-- ------------------------------------------------------------

START TRANSACTION;

INSERT INTO scenario_executions (
    scenario_type,
    status,
    simulation_started_at
)
VALUES (
    'DAY_TO_DAY',
    'COMPLETED',
    '2026-01-01 00:00:00'
);

SET @neg_execution_id = LAST_INSERT_ID();

-- Primer resultado: debe insertarse correctamente.

INSERT INTO scenario_results (
    execution_id
)
VALUES (
    @neg_execution_id
);

-- Segundo resultado para la misma ejecución:
-- debe fallar por PRIMARY KEY.

INSERT INTO scenario_results (
    execution_id
)
VALUES (
    @neg_execution_id
);

ROLLBACK;


-- ============================================================
-- 7. CONTROL FINAL
-- ============================================================

SELECT COUNT(*) AS total_tables
FROM information_schema.tables
WHERE table_schema = 'paqrap';

-- Esperado: 15