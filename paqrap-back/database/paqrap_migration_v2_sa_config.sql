-- ============================================================
-- PaqRap: configuración SA por ejecución para una BD V2 existente
-- ============================================================
USE paqrap;

ALTER TABLE scenario_executions
    ADD COLUMN sa_seed BIGINT NOT NULL DEFAULT 0 AFTER collapse_at,
    ADD COLUMN sa_initial_temperature DOUBLE NOT NULL DEFAULT 1000.0 AFTER sa_seed,
    ADD COLUMN sa_minimum_temperature DOUBLE NOT NULL DEFAULT 1.0 AFTER sa_initial_temperature,
    ADD COLUMN sa_cooling_factor DOUBLE NOT NULL DEFAULT 0.95 AFTER sa_minimum_temperature,
    ADD COLUMN sa_iterations_per_temperature INT UNSIGNED NOT NULL DEFAULT 50 AFTER sa_cooling_factor,
    ADD CONSTRAINT chk_scenario_execution_sa CHECK (
        sa_initial_temperature > 0 AND sa_minimum_temperature > 0
        AND sa_minimum_temperature < sa_initial_temperature
        AND sa_cooling_factor BETWEEN 0.80 AND 0.99
        AND sa_iterations_per_temperature > 0
    );