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
import java.util.Set;

/**
 * Corta el plan vigente en un instante. Reglas:
 * <ul>
 *   <li>Liberada: no tiene programación o aún no partió ({@code salida >= ahora}); sus pedidos vuelven a planificarse.</li>
 *   <li>Terminada: ya volvió ({@code fin <= ahora}); todas sus entregas quedan hechas.</li>
 *   <li>Interrumpida: partió, no terminó y su vehículo sufrió una avería; solo cuentan las paradas
 *       completadas ({@code fin de parada <= ahora}).</li>
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

    static Resultado cortar(Collection<RutaVigente> rutas, Instant ahora, Set<String> vehiculosInterrumpidos) {
        Objects.requireNonNull(ahora, "ahora es requerido");
        List<RutaVigente> conservadas = new ArrayList<>();
        List<RutaVigente> liberadas = new ArrayList<>();
        List<RutaVigente> terminadas = new ArrayList<>();
        List<RutaVigente> interrumpidas = new ArrayList<>();
        Map<String, Integer> entregas = new HashMap<>();
        Map<String, Integer> retiros = new HashMap<>();

        for (RutaVigente ruta : rutas) {
            if (ruta.programada() == null || !ruta.ruta().departureAt().isBefore(ahora)) {
                liberadas.add(ruta);
            } else if (!ruta.programada().completedAt().isAfter(ahora)) {
                terminadas.add(ruta);
                sumar(entregas, entregas(ruta, null));
                sumar(retiros, retirosIntermedios(ruta, null));
            } else if (vehiculosInterrumpidos.contains(ruta.vehiculoId())) {
                interrumpidas.add(ruta);
                sumar(entregas, entregas(ruta, ahora));
                sumar(retiros, retirosIntermedios(ruta, ahora));
            } else {
                conservadas.add(ruta);
            }
        }
        return new Resultado(conservadas, liberadas, terminadas, interrumpidas, entregas, retiros);
    }

    /** Paquetes entregados por pedido; con {@code hasta} null cuenta todas las paradas. */
    static Map<String, Integer> entregas(RutaVigente ruta, Instant hasta) {
        Map<String, Integer> resultado = new HashMap<>();
        for (ScheduledRouteStop parada : ruta.programada().scheduledStops()) {
            if (parada.stop() instanceof DeliveryStop entrega && completada(parada, hasta)) {
                resultado.merge(entrega.order().id(), entrega.deliveredPackages(), Integer::sum);
            }
        }
        return resultado;
    }

    /** Paquetes retirados de almacenes intermedios (el central no tiene límite); con {@code hasta} null cuenta todos. */
    static Map<String, Integer> retirosIntermedios(RutaVigente ruta, Instant hasta) {
        Map<String, Integer> resultado = new HashMap<>();
        for (ScheduledRouteStop parada : ruta.programada().scheduledStops()) {
            if (parada.stop() instanceof WarehouseVisit visita && !visita.warehouse().isCentral()
                    && visita.pickupPackages() > 0 && completada(parada, hasta)) {
                resultado.merge(visita.warehouse().id(), visita.pickupPackages(), Integer::sum);
            }
        }
        return resultado;
    }

    static void sumar(Map<String, Integer> destino, Map<String, Integer> origen) {
        origen.forEach((clave, valor) -> destino.merge(clave, valor, Integer::sum));
    }

    private static boolean completada(ScheduledRouteStop parada, Instant hasta) {
        return hasta == null || !parada.completedAt().isAfter(hasta);
    }
}
