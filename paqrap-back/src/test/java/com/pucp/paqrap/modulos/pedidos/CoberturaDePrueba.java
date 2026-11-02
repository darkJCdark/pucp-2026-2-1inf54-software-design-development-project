package com.pucp.paqrap.modulos.pedidos;

import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;

import java.time.Instant;
import java.time.YearMonth;

/**
 * Deja "cargado" un mes para que {@code POST /api/escenarios} acepte 5D y Colapso en las pruebas (ver
 * {@code CoberturaPedidosService}). Guarda un único pedido marcador {@code AAAAMM-COBERTURA} el último día del mes a
 * las 12:00 de Lima, fuera de la ventana que simulan las pruebas; se quita apenas se crea el escenario.
 */
public final class CoberturaDePrueba {

    private CoberturaDePrueba() {
    }

    /** Guarda el marcador del mes si aún no existe (OrderEntity siempre se inserta como nuevo). */
    public static void cubrirMes(OrderRepository pedidoRepository, YearMonth mes) {
        String id = idMarcador(mes);
        if (pedidoRepository.existsById(id)) {
            return;
        }
        Instant registro = mes.atEndOfMonth().atTime(12, 0).atZone(ShiftSchedule.DEFAULT_ZONE).toInstant();
        pedidoRepository.save(new OrderEntity(id, "c-cobertura", new Location(30, 14), 1, registro,
                DeliveryType.REGULAR, 36));
    }

    /** Quita el marcador después de crear el escenario: la validación solo actúa al crear, y así no altera conteos. */
    public static void quitarMarcador(OrderRepository pedidoRepository, YearMonth mes) {
        pedidoRepository.deleteById(idMarcador(mes));
    }

    private static String idMarcador(YearMonth mes) {
        return String.format("%04d%02d-COBERTURA", mes.getYear(), mes.getMonthValue());
    }
}
