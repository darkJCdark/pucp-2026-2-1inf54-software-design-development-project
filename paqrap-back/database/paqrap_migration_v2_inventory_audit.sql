-- ============================================================
-- PaqRap - Migración incremental V2: auditoría e idempotencia de kardex
-- MySQL 8.4
-- Ejecutar una sola vez sobre una instalación ya creada con esquema V2.
-- ============================================================

USE paqrap;

ALTER TABLE inventory_movements
    ADD COLUMN stock_before INT UNSIGNED NULL AFTER quantity,
    ADD COLUMN stock_after INT UNSIGNED NULL AFTER stock_before,
    ADD COLUMN event_key VARCHAR(160) NULL AFTER stock_after;

-- En V2 previo, este CHECK obligaba order_id para todo DISPATCH. Una carga física puede abastecer varios pedidos.
ALTER TABLE inventory_movements
    DROP CHECK chk_inventory_movements_order;

ALTER TABLE inventory_movements
    ADD KEY idx_inventory_movements_execution_warehouse_time
        (execution_id, warehouse_id, occurred_at, movement_id),
    ADD UNIQUE KEY uq_inventory_movements_execution_event
        (execution_id, event_key);

-- Los NULL de event_key quedan permitidos para registros históricos anteriores a R4.