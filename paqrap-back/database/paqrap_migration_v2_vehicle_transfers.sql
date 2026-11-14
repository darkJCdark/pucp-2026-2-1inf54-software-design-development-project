-- PaqRap: soporte auditable de transbordos programados entre vehÃ­culos.
-- Ejecutar una sola vez sobre una base V2 existente antes de iniciar el backend.

ALTER TABLE route_stops
    DROP CHECK chk_route_stops_type,
    DROP CHECK chk_route_stops_delivery;

ALTER TABLE route_stops
    ADD COLUMN transfer_packages INT UNSIGNED NULL AFTER delivered_packages,
    ADD COLUMN transfer_id VARCHAR(96) NULL AFTER transfer_packages,
    ADD COLUMN counterparty_vehicle_id VARCHAR(8) NULL AFTER transfer_id,
    ADD COLUMN transfer_direction VARCHAR(12) NULL AFTER counterparty_vehicle_id,
    ADD COLUMN planned_service_start_at TIMESTAMP(6) NULL AFTER planned_arrival_at,
    ADD KEY idx_route_stops_transfer (transfer_id),
    ADD KEY idx_route_stops_counterparty_vehicle (counterparty_vehicle_id),
    ADD CONSTRAINT fk_route_stops_counterparty_vehicle
        FOREIGN KEY (counterparty_vehicle_id)
        REFERENCES vehicles (vehicle_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT;

ALTER TABLE route_stops
    ADD CONSTRAINT chk_route_stops_type
        CHECK (stop_type IN ('DELIVERY', 'WAREHOUSE', 'TRANSFER')),
    ADD CONSTRAINT chk_route_stops_payload
        CHECK (
            (
                stop_type = 'DELIVERY'
                AND order_id IS NOT NULL
                AND warehouse_id IS NULL
                AND delivered_packages IS NOT NULL
                AND delivered_packages > 0
                AND transfer_packages IS NULL
                AND transfer_id IS NULL
                AND counterparty_vehicle_id IS NULL
                AND transfer_direction IS NULL
            )
            OR
            (
                stop_type = 'WAREHOUSE'
                AND warehouse_id IS NOT NULL
                AND order_id IS NULL
                AND delivered_packages IS NULL
                AND transfer_packages IS NULL
                AND transfer_id IS NULL
                AND counterparty_vehicle_id IS NULL
                AND transfer_direction IS NULL
            )
            OR
            (
                stop_type = 'TRANSFER'
                AND order_id IS NOT NULL
                AND warehouse_id IS NULL
                AND delivered_packages IS NULL
                AND transfer_packages IS NOT NULL
                AND transfer_packages > 0
                AND transfer_id IS NOT NULL
                AND counterparty_vehicle_id IS NOT NULL
                AND transfer_direction IN ('DONATE', 'RECEIVE')
                AND planned_service_start_at IS NOT NULL
            )
        );

