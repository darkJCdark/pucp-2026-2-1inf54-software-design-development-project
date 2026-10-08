package com.pucp.paqrap.modulos.almacenes.dto;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;

/**
 * Definición estática del almacén. {@code stockInicial} y {@code capacidad} son nulos para el central (ilimitado).
 * El stock vigente durante una simulación se expone en el módulo de monitoreo.
 */
public record AlmacenResponse(String id, WarehouseKind tipo, int x, int y, Integer stockInicial, Integer capacidad) {

    private static final int CAPACIDAD_INTERMEDIO = 1_000;

    public static AlmacenResponse de(WarehouseEntity entity) {
        boolean central = entity.getWarehouseKind() == WarehouseKind.CENTRAL;
        return new AlmacenResponse(entity.getWarehouseId(), entity.getWarehouseKind(), entity.getX(), entity.getY(),
                entity.getInitialStock(), central ? null : CAPACIDAD_INTERMEDIO);
    }
}
