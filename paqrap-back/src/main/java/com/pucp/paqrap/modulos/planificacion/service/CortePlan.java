package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
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
 *   <li>Conservada: partió y no terminó; su viaje en curso se mantiene congelado y los viajes siguientes que aún
 *       no salieron del central se liberan ({@link #sinViajesPendientes}).</li>
 * </ul>
 * Las entregas y retiros de las rutas que salen del plan (terminadas e interrumpidas) se devuelven para
 * acumularlos; los de las conservadas se calculan en cada corte porque todavía forman parte del plan.
 */
final class CortePlan {

    /** Ruta que retoma una unidad tras una avería menor: sale en el futuro, pero sus pedidos ya están en el vehículo. */
    static final String SUFIJO_CONTINUACION = "-CONT";

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
            boolean continuacion = (ruta.ruta().id().endsWith(SUFIJO_CONTINUACION)
                    || ruta.ruta().id().endsWith(ReplanificacionSaService.SUFIJO_DESVIO)) && ruta.programada() != null;
            if (ruta.programada() == null || (!continuacion && !ruta.ruta().departureAt().isBefore(ahora))
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
                conservadas.add(sinViajesPendientes(ruta, ahora, false));
            }
        }
        return new Resultado(conservadas, liberadas, terminadas, interrumpidas, entregas, retiros);
    }

    /**
     * Una ruta en marcha puede encadenar varios viajes que recargan en un almacén (central o intermedio). El viaje en
     * curso queda congelado, pero los viajes que aún no salieron de su almacén se liberan para volver a planificarse,
     * igual que una ruta que no partió: la ruta conservada termina en la próxima llegada a un almacén
     * ({@code llegada >= ahora}) y lo que venía después vuelve a lo pendiente. Si ese almacén es un intermedio, la
     * unidad queda allí y el planificador puede darle un viaje que cargue en ese mismo almacén.
     */
    static RutaVigente sinViajesPendientes(RutaVigente ruta, Instant ahora, boolean tambienIntermedios) {
        List<ScheduledRouteStop> paradas = ruta.programada().scheduledStops();
        for (int corte = 0; corte < paradas.size() - 1; corte++) {
            ScheduledRouteStop parada = paradas.get(corte);
            if (!(parada.stop() instanceof WarehouseVisit visita)
                    || (!tambienIntermedios && !visita.warehouse().isCentral())
                    || parada.arrivedAt().isBefore(ahora)
                    || parada.loadBefore() != 0 // lo que sigue ya va a bordo: no es un viaje nuevo
                    || paradas.subList(corte + 1, paradas.size()).stream()
                    .noneMatch(siguiente -> siguiente.stop() instanceof DeliveryStop)) {
                continue;
            }
            WarehouseVisit regreso = new WarehouseVisit(visita.warehouse(), 0);
            List<RouteStop> stops = new ArrayList<>(ruta.ruta().stops().subList(0, corte));
            stops.add(regreso);
            DeliveryRoute recortada = ruta.ruta().withReplacedStops(stops);
            List<ScheduledRouteStop> programadas = new ArrayList<>(paradas.subList(0, corte));
            programadas.add(new ScheduledRouteStop(regreso, parada.approach(), parada.arrivedAt(),
                    parada.completedAt(), parada.loadBefore(), parada.loadBefore()));
            double distancia = programadas.stream().mapToDouble(p -> p.approach().distanceKm()).sum();
            double costoPorKm = ruta.programada().totalDistanceKm() > 0
                    ? ruta.programada().totalCost() / ruta.programada().totalDistanceKm() : 0;
            return new RutaVigente(recortada, ruta.snapshotOrigen(), new ScheduledDeliveryRoute(recortada,
                    programadas, parada.completedAt(), distancia, distancia * costoPorKm), ruta.violaciones());
        }
        return ruta;
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
