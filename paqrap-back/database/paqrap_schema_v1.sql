-- ============================================================
-- PaqRap
-- Modelo Relacional V1
-- Script de creación de esquema
-- Motor: MySQL 9.7+
-- ============================================================

CREATE DATABASE IF NOT EXISTS paqrap
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE paqrap;

-- ============================================================
-- 1. PEDIDOS
-- ============================================================

CREATE TABLE IF NOT EXISTS orders (
    order_id        VARCHAR(64)       NOT NULL,
    client_id       VARCHAR(32)       NOT NULL,
    destination_x   SMALLINT UNSIGNED NOT NULL,
    destination_y   SMALLINT UNSIGNED NOT NULL,
    packages        INT UNSIGNED      NOT NULL,
    registered_at   TIMESTAMP(6)      NOT NULL,
    deadline        TIMESTAMP(6)      NOT NULL,

    CONSTRAINT pk_orders
        PRIMARY KEY (order_id),

    CONSTRAINT chk_orders_destination_x
        CHECK (destination_x BETWEEN 0 AND 70),

    CONSTRAINT chk_orders_destination_y
        CHECK (destination_y BETWEEN 0 AND 50),

    CONSTRAINT chk_orders_packages
        CHECK (packages > 0),

    CONSTRAINT chk_orders_deadline
        CHECK (deadline > registered_at),

    INDEX idx_orders_client_id (client_id),
    INDEX idx_orders_registered_at (registered_at),
    INDEX idx_orders_deadline (deadline)
) ENGINE = InnoDB;


-- ============================================================
-- 2. ALMACENES
-- ============================================================

CREATE TABLE IF NOT EXISTS warehouses (
    warehouse_id    VARCHAR(32)       NOT NULL,
    warehouse_kind  VARCHAR(16)       NOT NULL,
    x               SMALLINT UNSIGNED NOT NULL,
    y               SMALLINT UNSIGNED NOT NULL,
    initial_stock   INT UNSIGNED      NULL,

    CONSTRAINT pk_warehouses
        PRIMARY KEY (warehouse_id),

    CONSTRAINT uq_warehouses_location
        UNIQUE (x, y),

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
-- 3. PARÁMETROS POR TIPO DE VEHÍCULO
-- ============================================================

CREATE TABLE IF NOT EXISTS vehicle_type_parameters (
    vehicle_type       VARCHAR(20)       NOT NULL,
    capacity_packages  SMALLINT UNSIGNED NOT NULL,
    speed_kmh          DECIMAL(6,2)      NOT NULL,
    cost_per_km        DECIMAL(8,2)      NOT NULL,
    updated_at         TIMESTAMP(6)      NOT NULL
                       DEFAULT CURRENT_TIMESTAMP(6)
                       ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_vehicle_type_parameters
        PRIMARY KEY (vehicle_type),

    CONSTRAINT chk_vehicle_type
        CHECK (
            vehicle_type IN (
                'CAR',
                'MOTORCYCLE',
                'BICYCLE'
            )
        ),

    CONSTRAINT chk_vehicle_capacity
        CHECK (capacity_packages > 0),

    CONSTRAINT chk_vehicle_speed
        CHECK (speed_kmh > 0),

    CONSTRAINT chk_vehicle_cost
        CHECK (cost_per_km >= 0)
) ENGINE = InnoDB;


-- ============================================================
-- 4. VEHÍCULOS
-- ============================================================

CREATE TABLE IF NOT EXISTS vehicles (
    vehicle_id    VARCHAR(8)  NOT NULL,
    vehicle_type  VARCHAR(20) NOT NULL,
    available     BOOLEAN     NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_vehicles
        PRIMARY KEY (vehicle_id),

    CONSTRAINT fk_vehicles_vehicle_type
        FOREIGN KEY (vehicle_type)
        REFERENCES vehicle_type_parameters (vehicle_type)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    INDEX idx_vehicles_vehicle_type (vehicle_type)
) ENGINE = InnoDB;


-- ============================================================
-- 5. MANTENIMIENTO PREVENTIVO
-- ============================================================

CREATE TABLE IF NOT EXISTS maintenance_days (
    vehicle_id        VARCHAR(8) NOT NULL,
    maintenance_date  DATE       NOT NULL,

    CONSTRAINT pk_maintenance_days
        PRIMARY KEY (vehicle_id, maintenance_date),

    CONSTRAINT fk_maintenance_vehicle
        FOREIGN KEY (vehicle_id)
        REFERENCES vehicles (vehicle_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    INDEX idx_maintenance_date (maintenance_date)
) ENGINE = InnoDB;


-- ============================================================
-- 6. AVERÍAS
-- ============================================================

CREATE TABLE IF NOT EXISTS breakdown_events (
    breakdown_id    BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    vehicle_id      VARCHAR(8)        NOT NULL,
    breakdown_type  VARCHAR(20)       NOT NULL,
    occurred_at     TIMESTAMP(6)      NOT NULL,
    location_x      SMALLINT UNSIGNED NOT NULL,
    location_y      SMALLINT UNSIGNED NOT NULL,

    CONSTRAINT pk_breakdown_events
        PRIMARY KEY (breakdown_id),

    CONSTRAINT fk_breakdown_vehicle
        FOREIGN KEY (vehicle_id)
        REFERENCES vehicles (vehicle_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_breakdown_type
        CHECK (
            breakdown_type IN (
                'MINOR',
                'INTERMEDIATE',
                'MAJOR'
            )
        ),

    CONSTRAINT chk_breakdown_x
        CHECK (location_x BETWEEN 0 AND 70),

    CONSTRAINT chk_breakdown_y
        CHECK (location_y BETWEEN 0 AND 50),

    INDEX idx_breakdown_vehicle_time (
        vehicle_id,
        occurred_at
    ),

    INDEX idx_breakdown_occurred_at (
        occurred_at
    )
) ENGINE = InnoDB;


-- ============================================================
-- 7. BLOQUEOS VIALES
-- ============================================================

CREATE TABLE IF NOT EXISTS road_blocks (
    road_block_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    starts_at      TIMESTAMP(6)    NOT NULL,
    ends_at        TIMESTAMP(6)    NOT NULL,

    CONSTRAINT pk_road_blocks
        PRIMARY KEY (road_block_id),

    CONSTRAINT chk_road_blocks_period
        CHECK (ends_at > starts_at),

    INDEX idx_road_blocks_period (
        starts_at,
        ends_at
    )
) ENGINE = InnoDB;


-- ============================================================
-- 8. NODOS DE BLOQUEOS
-- ============================================================

CREATE TABLE IF NOT EXISTS road_block_nodes (
    road_block_id  BIGINT UNSIGNED   NOT NULL,
    sequence_no    SMALLINT UNSIGNED NOT NULL,
    x              SMALLINT UNSIGNED NOT NULL,
    y              SMALLINT UNSIGNED NOT NULL,

    CONSTRAINT pk_road_block_nodes
        PRIMARY KEY (
            road_block_id,
            sequence_no
        ),

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