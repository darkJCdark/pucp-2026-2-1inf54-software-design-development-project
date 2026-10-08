-- ============================================================
-- PaqRap
-- Migración de Base de Datos V1 -> V2
-- Versión final
--
-- Producto operativo:
--   - Simulated Annealing como único algoritmo de planificación.
--   - La configuración interna de SA no se persiste en MySQL.
-- ============================================================

USE paqrap;


-- ============================================================
-- 1. EXTENSIÓN DE ORDERS
-- ============================================================

ALTER TABLE orders
    ADD COLUMN status VARCHAR(20)
        NOT NULL DEFAULT 'REGISTERED',

    ADD COLUMN delivery_type VARCHAR(20)
        NOT NULL DEFAULT 'REGULAR',

    ADD COLUMN promised_hours SMALLINT UNSIGNED
        NOT NULL DEFAULT 36,

    ADD COLUMN delivered_at TIMESTAMP(6)
        NULL;


ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status
        CHECK (
            status IN (
                'REGISTERED',
                'ASSIGNED',
                'IN_TRANSIT',
                'DELIVERED'
            )
        ),

    ADD CONSTRAINT chk_orders_delivery_type
        CHECK (
            delivery_type IN (
                'REGULAR',
                'PRIORITY'
            )
        ),

    ADD CONSTRAINT chk_orders_promised_hours
        CHECK (
            (
                delivery_type = 'REGULAR'
                AND promised_hours = 36
            )
            OR
            (
                delivery_type = 'PRIORITY'
                AND promised_hours IN (4, 8, 12, 18)
            )
        ),

    ADD CONSTRAINT chk_orders_delivered_at
        CHECK (
            delivered_at IS NULL
            OR delivered_at >= registered_at
        ),

    ADD CONSTRAINT chk_orders_deadline_consistency
        CHECK (
            deadline = TIMESTAMPADD(
                HOUR,
                promised_hours,
                registered_at
            )
        );


-- ============================================================
-- 2. ESTADO OPERATIVO DE VEHÍCULOS
-- ============================================================

ALTER TABLE vehicles
    ADD COLUMN operational_status VARCHAR(20)
        NOT NULL DEFAULT 'AVAILABLE'
        AFTER vehicle_type,

    ADD COLUMN current_x SMALLINT UNSIGNED
        NOT NULL DEFAULT 27,

    ADD COLUMN current_y SMALLINT UNSIGNED
        NOT NULL DEFAULT 14,

    ADD COLUMN current_load SMALLINT UNSIGNED
        NOT NULL DEFAULT 0,

    ADD COLUMN available_at TIMESTAMP(6)
        NULL;


-- Traslada el estado disponible de V1 al nuevo modelo.
UPDATE vehicles
SET operational_status =
    CASE
        WHEN available = TRUE THEN 'AVAILABLE'
        ELSE 'UNAVAILABLE'
    END;


ALTER TABLE vehicles
    DROP COLUMN available,

    ADD CONSTRAINT chk_vehicles_operational_status
        CHECK (
            operational_status IN (
                'AVAILABLE',
                'IN_ROUTE',
                'UNAVAILABLE'
            )
        ),

    ADD CONSTRAINT chk_vehicles_current_x
        CHECK (current_x BETWEEN 0 AND 70),

    ADD CONSTRAINT chk_vehicles_current_y
        CHECK (current_y BETWEEN 0 AND 50);


-- ============================================================
-- 3. HISTORIAL DE ESTADOS DEL PEDIDO
-- ============================================================

CREATE TABLE order_status_history (
    status_history_id BIGINT UNSIGNED
        NOT NULL AUTO_INCREMENT,

    order_id VARCHAR(64)
        NOT NULL,

    status VARCHAR(20)
        NOT NULL,

    changed_at TIMESTAMP(6)
        NOT NULL,

    vehicle_id VARCHAR(8)
        NULL,

    warehouse_id VARCHAR(32)
        NULL,

    PRIMARY KEY (status_history_id),

    KEY idx_order_status_history_order (order_id),
    KEY idx_order_status_history_changed_at (changed_at),
    KEY idx_order_status_history_vehicle (vehicle_id),
    KEY idx_order_status_history_warehouse (warehouse_id),

    CONSTRAINT fk_order_status_history_order
        FOREIGN KEY (order_id)
        REFERENCES orders (order_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_order_status_history_vehicle
        FOREIGN KEY (vehicle_id)
        REFERENCES vehicles (vehicle_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_order_status_history_warehouse
        FOREIGN KEY (warehouse_id)
        REFERENCES warehouses (warehouse_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT chk_order_status_history_status
        CHECK (
            status IN (
                'REGISTERED',
                'ASSIGNED',
                'IN_TRANSIT',
                'DELIVERED'
            )
        )

) ENGINE = InnoDB;


-- ============================================================
-- 4. EJECUCIONES DE ESCENARIOS
--
-- No se guarda algoritmo porque el producto utiliza únicamente SA.
-- La semilla y los parámetros internos del algoritmo pertenecen
-- a la configuración del backend.
-- ============================================================

CREATE TABLE scenario_executions (
    execution_id BIGINT UNSIGNED
        NOT NULL AUTO_INCREMENT,

    scenario_type VARCHAR(20)
        NOT NULL,

    status VARCHAR(20)
        NOT NULL DEFAULT 'CREATED',

    started_at TIMESTAMP(6)
        NULL,

    finished_at TIMESTAMP(6)
        NULL,

    simulation_started_at TIMESTAMP(6)
        NOT NULL,

    simulation_finished_at TIMESTAMP(6)
        NULL,

    collapse_at TIMESTAMP(6)
        NULL,

    PRIMARY KEY (execution_id),

    KEY idx_scenario_executions_type (scenario_type),
    KEY idx_scenario_executions_status (status),
    KEY idx_scenario_executions_started_at (started_at),

    CONSTRAINT chk_scenario_execution_type
        CHECK (
            scenario_type IN (
                'DAY_TO_DAY',
                'FIVE_DAY',
                'COLLAPSE'
            )
        ),

    CONSTRAINT chk_scenario_execution_status
        CHECK (
            status IN (
                'CREATED',
                'RUNNING',
                'PAUSED',
                'STOPPED',
                'COMPLETED',
                'COLLAPSED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_scenario_execution_real_period
        CHECK (
            finished_at IS NULL
            OR started_at IS NULL
            OR finished_at >= started_at
        ),

    CONSTRAINT chk_scenario_execution_simulation_period
        CHECK (
            simulation_finished_at IS NULL
            OR simulation_finished_at >= simulation_started_at
        )

) ENGINE = InnoDB;


-- ============================================================
-- 5. AVERÍAS ASOCIADAS A UNA EJECUCIÓN
-- ============================================================

ALTER TABLE breakdown_events
    ADD COLUMN execution_id BIGINT UNSIGNED
        NULL AFTER breakdown_id,

    ADD KEY idx_breakdown_execution (execution_id),

    ADD CONSTRAINT fk_breakdown_execution
        FOREIGN KEY (execution_id)
        REFERENCES scenario_executions (execution_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL;


-- ============================================================
-- 6. RESULTADOS DE EJECUCIÓN
--
-- Un resultado por ejecución.
-- No existe columna algorithm porque el producto usa SA.
-- ============================================================

CREATE TABLE scenario_results (
    execution_id BIGINT UNSIGNED
        NOT NULL,

    total_orders INT UNSIGNED
        NOT NULL DEFAULT 0,

    delivered_orders INT UNSIGNED
        NOT NULL DEFAULT 0,

    on_time_orders INT UNSIGNED
        NOT NULL DEFAULT 0,

    undelivered_orders INT UNSIGNED
        NOT NULL DEFAULT 0,

    total_distance_km DECIMAL(14,2)
        NOT NULL DEFAULT 0,

    total_cost DECIMAL(14,2)
        NOT NULL DEFAULT 0,

    computation_time_ms BIGINT UNSIGNED
        NOT NULL DEFAULT 0,

    breakdown_count INT UNSIGNED
        NOT NULL DEFAULT 0,

    road_block_count INT UNSIGNED
        NOT NULL DEFAULT 0,

    replanning_count INT UNSIGNED
        NOT NULL DEFAULT 0,

    created_at TIMESTAMP(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (execution_id),

    CONSTRAINT fk_scenario_results_execution
        FOREIGN KEY (execution_id)
        REFERENCES scenario_executions (execution_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT chk_scenario_results_orders
        CHECK (
            delivered_orders <= total_orders
            AND on_time_orders <= delivered_orders
            AND undelivered_orders <= total_orders
            AND delivered_orders + undelivered_orders <= total_orders
        ),

    CONSTRAINT chk_scenario_results_distance
        CHECK (total_distance_km >= 0),

    CONSTRAINT chk_scenario_results_cost
        CHECK (total_cost >= 0)

) ENGINE = InnoDB;


-- ============================================================
-- 7. PLANES
--
-- No se guarda algoritmo; todos los planes productivos
-- corresponden a Simulated Annealing.
-- ============================================================

CREATE TABLE plans (
    plan_id BIGINT UNSIGNED
        NOT NULL AUTO_INCREMENT,

    execution_id BIGINT UNSIGNED
        NOT NULL,

    plan_type VARCHAR(20)
        NOT NULL,

    created_at TIMESTAMP(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    feasible BOOLEAN
        NOT NULL,

    total_distance_km DECIMAL(14,2)
        NOT NULL DEFAULT 0,

    total_cost DECIMAL(14,2)
        NOT NULL DEFAULT 0,

    PRIMARY KEY (plan_id),

    KEY idx_plans_execution (execution_id),
    KEY idx_plans_created_at (created_at),

    CONSTRAINT fk_plans_execution
        FOREIGN KEY (execution_id)
        REFERENCES scenario_executions (execution_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT chk_plans_type
        CHECK (
            plan_type IN (
                'INITIAL',
                'REPLANNING'
            )
        ),

    CONSTRAINT chk_plans_distance
        CHECK (total_distance_km >= 0),

    CONSTRAINT chk_plans_cost
        CHECK (total_cost >= 0)

) ENGINE = InnoDB;


-- ============================================================
-- 8. RUTAS
-- ============================================================

CREATE TABLE routes (
    route_id BIGINT UNSIGNED
        NOT NULL AUTO_INCREMENT,

    plan_id BIGINT UNSIGNED
        NOT NULL,

    vehicle_id VARCHAR(8)
        NOT NULL,

    start_warehouse_id VARCHAR(32)
        NULL,

    origin_x SMALLINT UNSIGNED
        NOT NULL,

    origin_y SMALLINT UNSIGNED
        NOT NULL,

    departure_at TIMESTAMP(6)
        NOT NULL,

    status VARCHAR(20)
        NOT NULL DEFAULT 'PLANNED',

    completed_at TIMESTAMP(6)
        NULL,

    total_distance_km DECIMAL(14,2)
        NOT NULL DEFAULT 0,

    total_cost DECIMAL(14,2)
        NOT NULL DEFAULT 0,

    PRIMARY KEY (route_id),

    KEY idx_routes_plan (plan_id),
    KEY idx_routes_vehicle (vehicle_id),
    KEY idx_routes_start_warehouse (start_warehouse_id),

    CONSTRAINT fk_routes_plan
        FOREIGN KEY (plan_id)
        REFERENCES plans (plan_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_routes_vehicle
        FOREIGN KEY (vehicle_id)
        REFERENCES vehicles (vehicle_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_routes_start_warehouse
        FOREIGN KEY (start_warehouse_id)
        REFERENCES warehouses (warehouse_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT chk_routes_origin_x
        CHECK (origin_x BETWEEN 0 AND 70),

    CONSTRAINT chk_routes_origin_y
        CHECK (origin_y BETWEEN 0 AND 50),

    CONSTRAINT chk_routes_status
        CHECK (
            status IN (
                'PLANNED',
                'IN_PROGRESS',
                'COMPLETED',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_routes_completed_at
        CHECK (
            completed_at IS NULL
            OR completed_at >= departure_at
        ),

    CONSTRAINT chk_routes_distance
        CHECK (total_distance_km >= 0),

    CONSTRAINT chk_routes_cost
        CHECK (total_cost >= 0)

) ENGINE = InnoDB;


-- ============================================================
-- 9. PARADAS DE RUTA
-- ============================================================

CREATE TABLE route_stops (
    route_stop_id BIGINT UNSIGNED
        NOT NULL AUTO_INCREMENT,

    route_id BIGINT UNSIGNED
        NOT NULL,

    sequence_no SMALLINT UNSIGNED
        NOT NULL,

    stop_type VARCHAR(20)
        NOT NULL,

    order_id VARCHAR(64)
        NULL,

    warehouse_id VARCHAR(32)
        NULL,

    delivered_packages INT UNSIGNED
        NULL,

    x SMALLINT UNSIGNED
        NOT NULL,

    y SMALLINT UNSIGNED
        NOT NULL,

    planned_arrival_at TIMESTAMP(6)
        NULL,

    PRIMARY KEY (route_stop_id),

    UNIQUE KEY uq_route_stops_sequence (
        route_id,
        sequence_no
    ),

    KEY idx_route_stops_order (order_id),
    KEY idx_route_stops_warehouse (warehouse_id),

    CONSTRAINT fk_route_stops_route
        FOREIGN KEY (route_id)
        REFERENCES routes (route_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    -- RESTRICT se usa deliberadamente para mantener compatibilidad
    -- con el CHECK chk_route_stops_delivery en MySQL 9.x.
    CONSTRAINT fk_route_stops_order
        FOREIGN KEY (order_id)
        REFERENCES orders (order_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT fk_route_stops_warehouse
        FOREIGN KEY (warehouse_id)
        REFERENCES warehouses (warehouse_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT chk_route_stops_sequence
        CHECK (sequence_no > 0),

    CONSTRAINT chk_route_stops_type
        CHECK (
            stop_type IN (
                'DELIVERY',
                'WAREHOUSE'
            )
        ),

    CONSTRAINT chk_route_stops_x
        CHECK (x BETWEEN 0 AND 70),

    CONSTRAINT chk_route_stops_y
        CHECK (y BETWEEN 0 AND 50),

    CONSTRAINT chk_route_stops_delivery
        CHECK (
            (
                stop_type = 'DELIVERY'
                AND order_id IS NOT NULL
                AND warehouse_id IS NULL
                AND delivered_packages IS NOT NULL
                AND delivered_packages > 0
            )
            OR
            (
                stop_type = 'WAREHOUSE'
                AND warehouse_id IS NOT NULL
                AND order_id IS NULL
                AND delivered_packages IS NULL
            )
        )

) ENGINE = InnoDB;


-- ============================================================
-- 10. MOVIMIENTOS DE INVENTARIO
-- ============================================================

CREATE TABLE inventory_movements (
    movement_id BIGINT UNSIGNED
        NOT NULL AUTO_INCREMENT,

    warehouse_id VARCHAR(32)
        NOT NULL,

    execution_id BIGINT UNSIGNED
        NULL,

    order_id VARCHAR(64)
        NULL,

    movement_type VARCHAR(20)
        NOT NULL,

    quantity INT UNSIGNED
        NOT NULL,

    occurred_at TIMESTAMP(6)
        NOT NULL,

    PRIMARY KEY (movement_id),

    KEY idx_inventory_movements_warehouse (warehouse_id),
    KEY idx_inventory_movements_execution (execution_id),
    KEY idx_inventory_movements_order (order_id),
    KEY idx_inventory_movements_occurred_at (occurred_at),

    CONSTRAINT fk_inventory_movements_warehouse
        FOREIGN KEY (warehouse_id)
        REFERENCES warehouses (warehouse_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_inventory_movements_execution
        FOREIGN KEY (execution_id)
        REFERENCES scenario_executions (execution_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    -- RESTRICT evita el conflicto entre acciones referenciales
    -- y el CHECK que utiliza order_id.
    CONSTRAINT fk_inventory_movements_order
        FOREIGN KEY (order_id)
        REFERENCES orders (order_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT chk_inventory_movements_type
        CHECK (
            movement_type IN (
                'DISPATCH',
                'RECHARGE'
            )
        ),

    CONSTRAINT chk_inventory_movements_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_inventory_movements_order
        CHECK (
            (
                movement_type = 'DISPATCH'
                AND order_id IS NOT NULL
            )
            OR
            (
                movement_type = 'RECHARGE'
                AND order_id IS NULL
            )
        )

) ENGINE = InnoDB;


-- ============================================================
-- 11. CONTROL FINAL
-- ============================================================

SELECT
    COUNT(*) AS total_tables
FROM information_schema.tables
WHERE table_schema = 'paqrap';

-- Esperado:
-- total_tables = 15


SELECT
    table_name
FROM information_schema.tables
WHERE table_schema = 'paqrap'
ORDER BY table_name;