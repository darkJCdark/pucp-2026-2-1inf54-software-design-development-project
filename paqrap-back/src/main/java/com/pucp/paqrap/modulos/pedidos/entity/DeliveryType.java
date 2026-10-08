package com.pucp.paqrap.modulos.pedidos.entity;

import java.util.Set;

/** Tipo de entrega con los plazos que admite (CU-02); coincide con {@code chk_orders_promised_hours}. */
public enum DeliveryType {
    REGULAR(Set.of(36)),
    PRIORITY(Set.of(4, 8, 12, 18));

    private final Set<Integer> plazosAdmitidos;

    DeliveryType(Set<Integer> plazosAdmitidos) {
        this.plazosAdmitidos = plazosAdmitidos;
    }

    public boolean admite(int horas) {
        return plazosAdmitidos.contains(horas);
    }

    /** En los archivos de ventas solo viene el plazo: 36 h es regular y el resto, priorizado. */
    public static DeliveryType segunPlazo(int horas) {
        for (DeliveryType tipo : values()) {
            if (tipo.admite(horas)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Plazo no admitido: " + horas + " h (regular 36; priorizado 4, 8, 12 o 18)");
    }
}
