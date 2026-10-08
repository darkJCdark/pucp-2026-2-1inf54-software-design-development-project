package com.pucp.paqrap.modulos.almacenes.persistence;

/** Valores permitidos por {@code chk_inventory_movements_type}. */
public enum MovementType {
    /** Salida de paquetes hacia un pedido. */
    DISPATCH,
    /** Recarga diaria de un almacén intermedio hasta su capacidad. */
    RECHARGE
}
