package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;

/**
 * Descripción en español de cada tipo de violación del evaluador original. El switch es exhaustivo:
 * si se agrega un tipo nuevo, el código deja de compilar hasta describirlo.
 */
final class DescripcionViolacion {

    private DescripcionViolacion() {
    }

    static String de(PlanViolationType tipo) {
        return switch (tipo) {
            case UNKNOWN_VEHICLE -> "El vehículo de la ruta no existe en la flota";
            case VEHICLE_UNAVAILABLE -> "El vehículo no está disponible a la hora de salida";
            case INVALID_ROUTE_START -> "La ruta no parte de un punto válido";
            case INVALID_INITIAL_LOAD -> "La carga inicial no coincide con la que lleva el vehículo";
            case ROUTE_NOT_RETURNED_TO_WAREHOUSE -> "La ruta no termina en un almacén";
            case NO_ROAD_PATH -> "No hay camino transitable hacia una parada";
            case VEHICLE_CAPACITY -> "La carga supera la capacidad del vehículo";
            case NEGATIVE_LOAD -> "Se entregan más paquetes de los que lleva el vehículo";
            case UNKNOWN_ORDER -> "La entrega no corresponde a un pedido del plan";
            case PARTIAL_DELIVERY_MISMATCH -> "La cantidad entregada no coincide con la del pedido";
            case SLA_MISSED -> "Entrega fuera del plazo del pedido";
            case INSUFFICIENT_INVENTORY -> "El almacén no tiene stock suficiente para la recarga";
            case RETURN_TO_EMPTY_WAREHOUSE -> "Regreso a un almacén intermedio sin stock";
            case MAINTENANCE_OR_BREAKDOWN -> "La ruta coincide con un mantenimiento o una avería del vehículo";
            case LEG_DISTANCE_EXCEEDED -> "Un tramo entre dos paradas supera los 80 km";
        };
    }
}
