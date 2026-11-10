package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

/**
 * Herramienta de diagnóstico (solo pruebas): explica por qué un pedido quedó sin atender en un ciclo.
 * No modifica el planificador; solo lo consulta con el mismo evaluador que usa el servicio.
 *
 * <p>Orden de las comprobaciones (la primera que se cumple decide la causa):
 * <ol>
 *   <li>{@link Causa#VENCIDO}: el plazo ya pasó a la hora del ciclo.</li>
 *   <li>{@link Causa#IMPOSIBLE_FISICO}: ningún vehículo con capacidad suficiente, saliendo ahora del central,
 *       entrega a tiempo (con bloqueos, turnos y demás reglas del evaluador).</li>
 *   <li>{@link Causa#LIBRE_VIABLE_NO_ASIGNADO}: un vehículo libre sí lo atiende solo; es un fallo del planificador.</li>
 *   <li>{@link Causa#ENCOLABLE_EN_RUTA_ACTIVA}: ningún libre lo atiende, pero cabe como parada futura de una ruta en
 *       marcha sin romper sus plazos; el servicio no lo hace porque conserva las rutas completas.</li>
 *   <li>{@link Causa#SIN_VEHICULOS_LIBRES}: alcanzable saliendo del central, pero todos los vehículos aptos están en
 *       ruta y el pedido no cabe en ninguna.</li>
 * </ol>
 *
 * <p>Limitaciones: un pedido se evalúa en un solo vehículo con capacidad mayor o igual al total de sus paquetes (no se
 * prueban entregas divididas); el inventario del central se toma completo; la ruta candidata se evalúa sola.
 */
final class ClasificadorNoAtendidos {

    enum Causa {
        VENCIDO,
        IMPOSIBLE_FISICO,
        LIBRE_VIABLE_NO_ASIGNADO,
        ENCOLABLE_EN_RUTA_ACTIVA,
        SIN_VEHICULOS_LIBRES
    }

    record Resultado(Causa causa, String detalle) {
    }

    private final OperationalPlanEvaluator evaluador;

    ClasificadorNoAtendidos(OperationalPlanEvaluator evaluador) {
        this.evaluador = Objects.requireNonNull(evaluador, "evaluador es requerido");
    }

    /**
     * @param base    snapshot con toda la flota libre en el central a {@code ahora}
     * @param activas rutas vigentes del servicio en {@code ahora} (los vehículos que las llevan están ocupados)
     */
    Resultado clasificar(Order pedido, Instant ahora, OperationalSnapshot base, Warehouse central,
                         List<Vehicle> flota, List<RutaVigente> activas, List<RoadBlock> bloqueos) {
        if (!pedido.deadline().isAfter(ahora)) {
            return new Resultado(Causa.VENCIDO, "plazo " + local(pedido.deadline()) + " no es posterior a " + local(ahora));
        }
        Set<String> ocupados = activas.stream().map(RutaVigente::vehiculoId).collect(Collectors.toSet());
        List<Vehicle> aptos = flota.stream()
                .filter(v -> v.type().capacity() >= pedido.packages())
                .sorted(Comparator.comparing(Vehicle::id))
                .toList();
        List<String> alcanzan = new ArrayList<>();
        for (Vehicle vehiculo : aptos) {
            if (sinViolaciones(evaluarSolo(vehiculo, pedido, ahora, base, central, bloqueos))) {
                alcanzan.add(vehiculo.id());
            }
        }
        if (alcanzan.isEmpty()) {
            return new Resultado(Causa.IMPOSIBLE_FISICO, "ninguno de los " + aptos.size()
                    + " vehículos con capacidad >= " + pedido.packages() + " llega antes de " + local(pedido.deadline())
                    + " saliendo del central a " + local(ahora));
        }
        List<String> libres = alcanzan.stream().filter(id -> !ocupados.contains(id)).toList();
        if (!libres.isEmpty()) {
            return new Resultado(Causa.LIBRE_VIABLE_NO_ASIGNADO,
                    "libres que lo atienden solos: " + resumirIds(libres));
        }
        Optional<String> encolado = buscarEncolado(pedido, ahora, activas, bloqueos);
        if (encolado.isPresent()) {
            return new Resultado(Causa.ENCOLABLE_EN_RUTA_ACTIVA, encolado.get());
        }
        return new Resultado(Causa.SIN_VEHICULOS_LIBRES, "lo atenderían desde el central " + resumirIds(alcanzan)
                + ", pero todos están en ruta y no cabe en ninguna ruta activa");
    }

    /** Evalúa el pedido solo, en una ruta nueva del vehículo saliendo del central a {@code ahora} y volviendo a él. */
    PlanEvaluation evaluarSolo(Vehicle vehiculo, Order pedido, Instant ahora, OperationalSnapshot base,
                               Warehouse central, List<RoadBlock> bloqueos) {
        DeliveryRoute ruta = DeliveryRoute.startScenarioAtCentral("DIAG-" + vehiculo.id(), vehiculo, central,
                        pedido.packages(), ahora)
                .withAppendedStop(new DeliveryStop(pedido, pedido.packages()))
                .returningTo(central);
        return evaluador.evaluate(OperationalPlan.empty().withRoute(ruta), base, List.of(pedido), bloqueos);
    }

    /**
     * Busca una ruta en marcha donde el pedido cabe como parada futura (cualquier posición no iniciada, incluida
     * la última antes del regreso). Devuelve la primera que el evaluador acepta sin violaciones.
     */
    private Optional<String> buscarEncolado(Order pedido, Instant ahora, List<RutaVigente> activas,
                                            List<RoadBlock> bloqueos) {
        List<RutaVigente> ordenadas = activas.stream().sorted(Comparator.comparing(RutaVigente::vehiculoId)).toList();
        for (RutaVigente vigente : ordenadas) {
            DeliveryRoute ruta = vigente.ruta();
            Vehicle vehiculo = ruta.vehicle();
            int carga = ruta.initialLoad() + pedido.packages();
            if (ruta.initialWarehouse().isEmpty() || carga > vehiculo.type().capacity()) {
                continue;
            }
            int desde = primeraPosicionInsertable(vigente, ahora);
            int hasta = ruta.endsAtWarehouse() ? ruta.stops().size() - 1 : ruta.stops().size();
            Map<String, Order> requeridos = new LinkedHashMap<>();
            for (RouteStop parada : ruta.stops()) {
                if (parada instanceof DeliveryStop entrega) {
                    requeridos.putIfAbsent(entrega.order().id(), entrega.order());
                }
            }
            requeridos.put(pedido.id(), pedido);
            for (int posicion = desde; posicion <= hasta; posicion++) {
                List<RouteStop> paradas = new ArrayList<>(ruta.stops());
                paradas.add(posicion, new DeliveryStop(pedido, pedido.packages()));
                DeliveryRoute candidata = DeliveryRoute.startScenarioAtCentral(ruta.id(), vehiculo,
                        ruta.initialWarehouse().get(), carga, ruta.departureAt()).withReplacedStops(paradas);
                PlanEvaluation evaluacion = evaluador.evaluate(OperationalPlan.empty().withRoute(candidata),
                        vigente.snapshotOrigen(), requeridos.values(), bloqueos);
                if (sinViolaciones(evaluacion)) {
                    return Optional.of("cabe en la ruta de " + vehiculo.id() + " en la posición " + posicion
                            + " de " + ruta.stops().size() + " paradas");
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Primera posición donde se puede insertar una parada sin desviar al vehículo a mitad de un tramo: si ya salió
     * hacia la próxima parada, solo se admite después de ella; si está en el central esperando la salida o atendiendo
     * una parada, se admite justo antes de la próxima.
     */
    static int primeraPosicionInsertable(RutaVigente vigente, Instant ahora) {
        int proxima = primeraParadaFutura(vigente, ahora);
        if (vigente.programada() == null || proxima >= vigente.programada().scheduledStops().size()) {
            return proxima;
        }
        Instant ultimaSalida = proxima == 0 ? vigente.ruta().departureAt()
                : vigente.programada().scheduledStops().get(proxima - 1).completedAt();
        return ultimaSalida.isBefore(ahora) ? proxima + 1 : proxima;
    }

    /** Índice de la primera parada cuya llegada es posterior a {@code ahora}; sin programación, solo la última. */
    static int primeraParadaFutura(RutaVigente vigente, Instant ahora) {
        if (vigente.programada() == null) {
            return vigente.ruta().stops().size();
        }
        List<ScheduledRouteStop> paradas = vigente.programada().scheduledStops();
        for (int i = 0; i < paradas.size(); i++) {
            if (paradas.get(i).arrivedAt().isAfter(ahora)) {
                return i;
            }
        }
        return paradas.size();
    }

    /** Sin violaciones, ignorando PARTIAL_DELIVERY_MISMATCH (una ruta puede llevar solo una parte de un pedido). */
    static boolean sinViolaciones(PlanEvaluation evaluacion) {
        return violaciones(evaluacion).isEmpty();
    }

    static List<PlanViolation> violaciones(PlanEvaluation evaluacion) {
        return evaluacion.violations().stream()
                .filter(v -> v.type() != PlanViolationType.PARTIAL_DELIVERY_MISMATCH)
                .toList();
    }

    /** Snapshot con toda la flota disponible en el almacén central a {@code hora}, como el de PlanificadorSaService. */
    static OperationalSnapshot snapshotEnCentral(FuenteDatosOperativos fuente, Warehouse central, Instant hora) {
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle vehiculo : fuente.flota()) {
            estados.put(vehiculo.id(),
                    new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), hora));
        }
        return new OperationalSnapshot(hora, fuente.perfilFlota(), InventorySnapshot.from(fuente.almacenes()), estados,
                fuente.calendarioMantenimiento(), new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), fuente.averias(null));
    }

    /** Hora local de Lima, sin segundos, para los textos del diagnóstico. */
    static String local(Instant instante) {
        return instante.atZone(ShiftSchedule.DEFAULT_ZONE).toLocalDateTime().toString();
    }

    private static String resumirIds(List<String> ids) {
        Set<String> unicos = new LinkedHashSet<>(ids);
        return unicos.size() <= 6 ? unicos.toString() : unicos.stream().limit(6).toList() + " y "
                + (unicos.size() - 6) + " más";
    }
}
