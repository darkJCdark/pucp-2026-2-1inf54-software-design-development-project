-- ============================================================
-- PaqRap
-- Schema V2 - creación limpia desde cero
-- Producto operativo: Simulated Annealing como único algoritmo
-- ============================================================

CREATE DATABASE IF NOT EXISTS paqrap
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE paqrap;

-- ============================================================
-- 1. PARÁMETROS POR TIPO DE VEHÍCULO
-- ============================================================

CREATE TABLE vehicle_type_parameters (
    vehicle_type VARCHAR(20) NOT NULL,
    capacity_packages SMALLINT UNSIGNED NOT NULL,
    speed_kmh DECIMAL(6,2) NOT NULL,
    cost_per_km DECIMAL(8,2) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (vehicle_type),

    CONSTRAINT chk_vehicle_type
        CHECK (vehicle_type IN ('CAR', 'MOTORCYCLE', 'BICYCLE')),
    CONSTRAINT chk_vehicle_capacity
        CHECK (capacity_packages > 0),
    CONSTRAINT chk_vehicle_speed
        CHECK (speed_kmh > 0),
    CONSTRAINT chk_vehicle_cost
        CHECK (cost_per_km >= 0)
) ENGINE = InnoDB;

-- ============================================================
-- 2. ALMACENES
-- ============================================================

CREATE TABLE warehouses (
    warehouse_id VARCHAR(32) NOT NULL,
    warehouse_kind VARCHAR(20) NOT NULL,
    x SMALLINT UNSIGNED NOT NULL,
    y SMALLINT UNSIGNED NOT NULL,
    initial_stock INT UNSIGNED NULL,

    PRIMARY KEY (warehouse_id),
    UNIQUE KEY uq_warehouses_location (x, y),

    CONSTRAINT chk_warehouses_kind
        CHECK (warehouse_kind IN ('CENTRAL', 'INTERMEDIATE')),
    CONSTRAINT chk_warehouses_x
        CHECK (x BETWEEN 0 AND 70),
    CONSTRAINT chk_warehouses_y
        CHECK (y BETWEEN 0 AND 50),
    CONSTRAINT chk_warehouses_stock
        CHECK (
            (warehouse_kind = 'CENTRAL' AND initial_stock IS NULL)
            OR
            (warehouse_kind = 'INTERMEDIATE' AND initial_stock IS NOT NULL)
        )
) ENGINE = InnoDB;

-- ============================================================
-- 3. VEHÍCULOS
-- ============================================================

CREATE TABLE vehicles (
    vehicle_id VARCHAR(8) NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL,
    operational_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    current_x SMALLINT UNSIGNED NOT NULL DEFAULT 27,
    current_y SMALLINT UNSIGNED NOT NULL DEFAULT 14,
    current_load SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    available_at TIMESTAMP(6) NULL,

    PRIMARY KEY (vehicle_id),
    KEY idx_vehicles_vehicle_type (vehicle_type),

    CONSTRAINT fk_vehicles_vehicle_type
        FOREIGN KEY (vehicle_type)
        REFERENCES vehicle_type_parameters (vehicle_type)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_vehicles_operational_status
        CHECK (operational_status IN ('AVAILABLE', 'IN_ROUTE', 'UNAVAILABLE')),
    CONSTRAINT chk_vehicles_current_x
        CHECK (current_x BETWEEN 0 AND 70),
    CONSTRAINT chk_vehicles_current_y
        CHECK (current_y BETWEEN 0 AND 50)
) ENGINE = InnoDB;

-- ============================================================
-- 4. MANTENIMIENTOS PREVENTIVOS
-- ============================================================

CREATE TABLE maintenance_days (
    vehicle_id VARCHAR(8) NOT NULL,
    maintenance_date DATE NOT NULL,

    PRIMARY KEY (vehicle_id, maintenance_date),
    KEY idx_maintenance_date (maintenance_date),

    CONSTRAINT fk_maintenance_vehicle
        FOREIGN KEY (vehicle_id)
        REFERENCES vehicles (vehicle_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ============================================================
-- 5. PEDIDOS
-- ============================================================

CREATE TABLE orders (
    order_id VARCHAR(64) NOT NULL,
    client_id VARCHAR(32) NOT NULL,
    destination_x SMALLINT UNSIGNED NOT NULL,
    destination_y SMALLINT UNSIGNED NOT NULL,
    packages INT UNSIGNED NOT NULL,
    registered_at TIMESTAMP(6) NOT NULL,
    deadline TIMESTAMP(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    delivery_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR',
    promised_hours SMALLINT UNSIGNED NOT NULL DEFAULT 36,
    delivered_at TIMESTAMP(6) NULL,

    PRIMARY KEY (order_id),

    CONSTRAINT chk_orders_destination_x
        CHECK (destination_x BETWEEN 0 AND 70),
    CONSTRAINT chk_orders_destination_y
        CHECK (destination_y BETWEEN 0 AND 50),
    CONSTRAINT chk_orders_packages
        CHECK (packages > 0),
    CONSTRAINT chk_orders_deadline
        CHECK (deadline > registered_at),
    CONSTRAINT chk_orders_status
        CHECK (status IN ('REGISTERED', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED')),
    CONSTRAINT chk_orders_delivery_type
        CHECK (delivery_type IN ('REGULAR', 'PRIORITY')),
    CONSTRAINT chk_orders_promised_hours
        CHECK (
            (delivery_type = 'REGULAR' AND promised_hours = 36)
            OR
            (delivery_type = 'PRIORITY' AND promised_hours IN (4, 8, 12, 18))
        ),
    CONSTRAINT chk_orders_delivered_at
        CHECK (delivered_at IS NULL OR delivered_at >= registered_at),
    CONSTRAINT chk_orders_deadline_consistency
        CHECK (deadline = TIMESTAMPADD(HOUR, promised_hours, registered_at))
) ENGINE = InnoDB;

-- ============================================================
-- 6. BLOQUEOS VIALES
-- ============================================================

CREATE TABLE road_blocks (
    road_block_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,

    PRIMARY KEY (road_block_id),

    CONSTRAINT chk_road_blocks_period
        CHECK (ends_at > starts_at)
) ENGINE = InnoDB;

CREATE TABLE road_block_nodes (
    road_block_id BIGINT UNSIGNED NOT NULL,
    sequence_no SMALLINT UNSIGNED NOT NULL,
    x SMALLINT UNSIGNED NOT NULL,
    y SMALLINT UNSIGNED NOT NULL,

    PRIMARY KEY (road_block_id, sequence_no),

    CONSTRAINT fk_road_block_nodes_block
        FOREIGN KEY (road_block_id)
        REFERENCES road_blocks (road_block_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT chk_road_block_sequence
        CHECK (sequence_no > 0),
    CONSTRAINT chk_road_block_node_x
        CHECK (x BETWEEN 0 AND 70),
    CONSTRAINT chk_road_block_node_y
        CHECK (y BETWEEN 0 AND 50)
) ENGINE = InnoDB;

-- ============================================================
-- 7. EJECUCIONES DE ESCENARIOS
-- No se persisten semilla ni parámetros internos de SA.
-- ============================================================

CREATE TABLE scenario_executions (
    execution_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    scenario_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    started_at TIMESTAMP(6) NULL,
    finished_at TIMESTAMP(6) NULL,
    simulation_started_at TIMESTAMP(6) NOT NULL,
    simulation_finished_at TIMESTAMP(6) NULL,
    collapse_at TIMESTAMP(6) NULL,

    PRIMARY KEY (execution_id),
    KEY idx_scenario_executions_type (scenario_type),
    KEY idx_scenario_executions_status (status),
    KEY idx_scenario_executions_started_at (started_at),

    CONSTRAINT chk_scenario_execution_type
        CHECK (scenario_type IN ('DAY_TO_DAY', 'FIVE_DAY', 'COLLAPSE')),
    CONSTRAINT chk_scenario_execution_status
        CHECK (status IN ('CREATED', 'RUNNING', 'PAUSED', 'STOPPED', 'COMPLETED', 'COLLAPSED', 'FAILED')),
    CONSTRAINT chk_scenario_execution_real_period
        CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at),
    CONSTRAINT chk_scenario_execution_simulation_period
        CHECK (simulation_finished_at IS NULL OR simulation_finished_at >= simulation_started_at)
) ENGINE = InnoDB;

-- ============================================================
-- 8. AVERÍAS
-- ============================================================

CREATE TABLE breakdown_events (
    breakdown_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    execution_id BIGINT UNSIGNED NULL,
    vehicle_id VARCHAR(8) NOT NULL,
    breakdown_type VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    location_x SMALLINT UNSIGNED NOT NULL,
    location_y SMALLINT UNSIGNED NOT NULL,

    PRIMARY KEY (breakdown_id),
    KEY idx_breakdown_execution (execution_id),
    KEY idx_breakdown_vehicle (vehicle_id),
    KEY idx_breakdown_occurred_at (occurred_at),

    CONSTRAINT fk_breakdown_execution
        FOREIGN KEY (execution_id)
        REFERENCES scenario_executions (execution_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_breakdown_vehicle
        FOREIGN KEY (vehicle_id)
        REFERENCES vehicles (vehicle_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_breakdown_type
        CHECK (breakdown_type IN ('MINOR', 'INTERMEDIATE', 'MAJOR')),
    CONSTRAINT chk_breakdown_x
        CHECK (location_x BETWEEN 0 AND 70),
    CONSTRAINT chk_breakdown_y
        CHECK (location_y BETWEEN 0 AND 50)
) ENGINE = InnoDB;

-- ============================================================
-- 9. HISTORIAL DE ESTADOS DE PEDIDO
-- ============================================================

CREATE TABLE order_status_history (
    status_history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL,
    vehicle_id VARCHAR(8) NULL,
    warehouse_id VARCHAR(32) NULL,

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
        CHECK (status IN ('REGISTERED', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED'))
) ENGINE = InnoDB;

-- ============================================================
-- 10. RESULTADOS DE EJECUCIÓN
-- ============================================================

CREATE TABLE scenario_results (
    execution_id BIGINT UNSIGNED NOT NULL,
    total_orders INT UNSIGNED NOT NULL DEFAULT 0,
    delivered_orders INT UNSIGNED NOT NULL DEFAULT 0,
    on_time_orders INT UNSIGNED NOT NULL DEFAULT 0,
    undelivered_orders INT UNSIGNED NOT NULL DEFAULT 0,
    total_distance_km DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_cost DECIMAL(14,2) NOT NULL DEFAULT 0,
    computation_time_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
    breakdown_count INT UNSIGNED NOT NULL DEFAULT 0,
    road_block_count INT UNSIGNED NOT NULL DEFAULT 0,
    replanning_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

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
-- 11. PLANES
-- Todos los planes productivos corresponden a SA.
-- ============================================================

CREATE TABLE plans (
    plan_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    execution_id BIGINT UNSIGNED NOT NULL,
    plan_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    feasible BOOLEAN NOT NULL,
    total_distance_km DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_cost DECIMAL(14,2) NOT NULL DEFAULT 0,

    PRIMARY KEY (plan_id),
    KEY idx_plans_execution (execution_id),
    KEY idx_plans_created_at (created_at),

    CONSTRAINT fk_plans_execution
        FOREIGN KEY (execution_id)
        REFERENCES scenario_executions (execution_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT chk_plans_type
        CHECK (plan_type IN ('INITIAL', 'REPLANNING')),
    CONSTRAINT chk_plans_distance
        CHECK (total_distance_km >= 0),
    CONSTRAINT chk_plans_cost
        CHECK (total_cost >= 0)
) ENGINE = InnoDB;

-- ============================================================
-- 12. RUTAS
-- ============================================================

CREATE TABLE routes (
    route_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id BIGINT UNSIGNED NOT NULL,
    vehicle_id VARCHAR(8) NOT NULL,
    start_warehouse_id VARCHAR(32) NULL,
    origin_x SMALLINT UNSIGNED NOT NULL,
    origin_y SMALLINT UNSIGNED NOT NULL,
    departure_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    completed_at TIMESTAMP(6) NULL,
    total_distance_km DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_cost DECIMAL(14,2) NOT NULL DEFAULT 0,

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
        CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_routes_completed_at
        CHECK (completed_at IS NULL OR completed_at >= departure_at),
    CONSTRAINT chk_routes_distance
        CHECK (total_distance_km >= 0),
    CONSTRAINT chk_routes_cost
        CHECK (total_cost >= 0)
) ENGINE = InnoDB;

-- ============================================================
-- 13. PARADAS DE RUTA
-- ============================================================

CREATE TABLE route_stops (
    route_stop_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_id BIGINT UNSIGNED NOT NULL,
    sequence_no SMALLINT UNSIGNED NOT NULL,
    stop_type VARCHAR(20) NOT NULL,
    order_id VARCHAR(64) NULL,
    warehouse_id VARCHAR(32) NULL,
    delivered_packages INT UNSIGNED NULL,
    x SMALLINT UNSIGNED NOT NULL,
    y SMALLINT UNSIGNED NOT NULL,
    planned_arrival_at TIMESTAMP(6) NULL,

    PRIMARY KEY (route_stop_id),
    UNIQUE KEY uq_route_stops_sequence (route_id, sequence_no),
    KEY idx_route_stops_order (order_id),
    KEY idx_route_stops_warehouse (warehouse_id),

    CONSTRAINT fk_route_stops_route
        FOREIGN KEY (route_id)
        REFERENCES routes (route_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
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
        CHECK (stop_type IN ('DELIVERY', 'WAREHOUSE')),
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
-- 14. MOVIMIENTOS DE INVENTARIO / KARDEX
-- ============================================================

CREATE TABLE inventory_movements (
    movement_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    warehouse_id VARCHAR(32) NOT NULL,
    execution_id BIGINT UNSIGNED NULL,
    order_id VARCHAR(64) NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,

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
    CONSTRAINT fk_inventory_movements_order
        FOREIGN KEY (order_id)
        REFERENCES orders (order_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT chk_inventory_movements_type
        CHECK (movement_type IN ('DISPATCH', 'RECHARGE')),
    CONSTRAINT chk_inventory_movements_quantity
        CHECK (quantity > 0),
    CONSTRAINT chk_inventory_movements_order
        CHECK (
            (movement_type = 'DISPATCH' AND order_id IS NOT NULL)
            OR
            (movement_type = 'RECHARGE' AND order_id IS NULL)
        )
) ENGINE = InnoDB;
