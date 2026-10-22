-- ============================================================
-- PaqRap: compatibilidad de snapshots de plan para una BD V2 ya creada
-- ============================================================
-- Las tablas operativas de una instalaci?n V2 base se entregan vac?as.
-- Si una instalaci?n las hubiera poblado manualmente, los valores hist?ricos
-- no recuperables se conservan con carga inicial 0 e id operacional LEGACY-<route_id>.

ALTER TABLE routes
    ADD COLUMN operational_route_id VARCHAR(64)
        NULL AFTER plan_id,

    ADD COLUMN initial_load INT UNSIGNED
        NOT NULL DEFAULT 0 AFTER origin_y;

UPDATE routes
SET operational_route_id = CONCAT('LEGACY-', route_id)
WHERE operational_route_id IS NULL;

ALTER TABLE routes
    MODIFY COLUMN operational_route_id VARCHAR(64)
        NOT NULL,

    ALTER COLUMN initial_load DROP DEFAULT,

    -- LEGACY-<route_id> y 0 son valores tecnicos de migracion, no datos historicos:
    -- la informacion original no puede recuperarse desde una BD V2 previa.
    ADD CONSTRAINT uq_routes_plan_operational UNIQUE (plan_id, operational_route_id);
