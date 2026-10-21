package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Corta el plan vigente en un instante. Reglas:
 * <ul>
 *   <li>Liberada: no tiene programación o aún no partió ({@code salida >= ahora}); sus pedidos vuelven a
 *       planificarse. También la que iba a partir después de la avería de su vehículo: no llega a salir.</li>
 *   <li>Interrumpida: partió y su vehículo se averió antes de que terminara; solo cuentan las paradas a las que
 *       llegó antes de la avería ({@code llegada < avería}), igual que el ejecutor del plan, que entrega al llegar.</li>
 *   <li>Terminada: ya volvió ({@code fin <= ahora}); todas sus entregas quedan hechas.</li>
 *   <li>Conservada: partió y no terminó; se mantiene completa (congelada).</li>
 * </ul>
 * Las entregas y retiros de las rutas que salen del plan (terminadas e interrumpidas) se devuelven para
 * acumularlos; los de las conservadas se calculan en cada corte porque todavía forman parte del plan.
 */
final class CortePlan {

    private CortePlan() {
    }

    record Resultado(List<RutaVigente> conservadas, List<RutaVigente> liberadas, List<RutaVigente> terminadas,
                     List<RutaVigente> interrumpidas, Map<String, Integer> entregasSalientes,
                     Map<String, Integer> retirosSalientes) {
    }

    /** @param averias instante de la avería de cada vehículo averiado en este corte */
    static Resultado cortar(Collection<RutaVigente> rutas, Instant ahora, Map<String, Instant> averias) {
        Objects.requireNonNull(ahora, "ahora es requerido");
        List<RutaVigente> conservadas = new ArrayList<>();
        List<RutaVigente> liberadas = new ArrayList<>();
        List<RutaVigente> terminadas = new ArrayList<>();
        List<RutaVigente> interrumpidas = new ArrayList<>();
        Map<String, Integer> entregas = new HashMap<>();
        Map<String, Integer> retiros = new HashMap<>();

        for (RutaVigente ruta : rutas) {
            Instant averia = averias.get(ruta.vehiculoId());
            if (ruta.programada() == null || !ruta.ruta().departureAt().isBefore(ahora)
                    || (averia != null && !averia.isAfter(ruta.ruta().departureAt()))) {
                liberadas.add(ruta);
            } else if (averia != null && averia.isBefore(ruta.programada().completedAt())) {
                interrumpidas.add(ruta);
                sumar(entregas, entregas(ruta, averia));
                sumar(retiros, retirosIntermedios(ruta, averia));
            } else if (!ruta.programada().completedAt().isAfter(ahora)) {
                terminadas.add(ruta);
                sumar(entregas, entregas(ruta, null));
                sumar(retiros, retirosIntermedios(ruta, null));
            } else {
                conservadas.add(ruta);
            }
        }
        return new Resultado(conservadas, liberadas, terminadas, interrumpidas, entregas, retiros);
    }

    /** Paquetes entregados por pedido en las paradas a las que se llegó antes de {@code antes}; null cuenta todas. */
    static Map<String, Integer> entregas(RutaVigente ruta, Instant antes) {
        Map<String, Integer> resultado = new HashMap<>();
        for (ScheduledRouteStop parada : ruta.programada().scheduledStops()) {
            if (parada.stop() instanceof DeliveryStop entrega && alcanzada(parada, antes)) {
                resultado.merge(entrega.order().id(), entrega.deliveredPackages(), Integer::sum);
            }
        }
        return resultado;
    }

    /** Paquetes retirados de almacenes intermedios (el central no tiene límite); con {@code antes} null cuenta todos. */
    static Map<String, Integer> retirosIntermedios(RutaVigente ruta, Instant antes) {
        Map<String, Integer> resultado = new HashMap<>();
        for (ScheduledRouteStop parada : ruta.programada().scheduledStops()) {
            if (parada.stop() instanceof WarehouseVisit visita && !visita.warehouse().isCentral()
                    && visita.pickupPackages() > 0 && alcanzada(parada, antes)) {
                resultado.merge(visita.warehouse().id(), visita.pickupPackages(), Integer::sum);
            }
        }
        return resultado;
    }

    static void sumar(Map<String, Integer> destino, Map<String, Integer> origen) {
        origen.forEach((clave, valor) -> destino.merge(clave, valor, Integer::sum));
    }

    private static boolean alcanzada(ScheduledRouteStop parada, Instant antes) {
        return antes == null || parada.arrivedAt().isBefore(antes);
    }
}
