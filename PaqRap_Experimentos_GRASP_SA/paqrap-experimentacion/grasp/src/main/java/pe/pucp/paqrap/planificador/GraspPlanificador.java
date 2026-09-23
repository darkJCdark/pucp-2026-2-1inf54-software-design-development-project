package pe.pucp.paqrap.planificador;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.edu.pucp.paqrap.planner.search.SearchControl;
import pe.edu.pucp.paqrap.planner.search.SearchStopped;
import pe.pucp.paqrap.modelo.ResultadoPlanificacion;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * GRASP para PaqRap sobre el dominio unico compartido (paqrap-dominio).
 *
 * <p>Esqueleto GRASP sin cambios: multi-arranque; en cada iteracion, construccion greedy
 * aleatorizada con RCL (umbral cMin + alpha*(cMax-cMin)) seguida de busqueda local de primera
 * mejora (insercion de pendientes, 2-opt, reubicacion, intercambio); se conserva la mejor
 * solucion por (factible, menos no atendidos, menor costo).
 *
 * <p>Version {@value #VERSION}. Cambios respecto de la version anterior, todos visibles aqui:
 * <ol>
 *   <li><b>Entregas divididas.</b> El enunciado admite entregas parciales (P&amp;R 13). Un pedido
 *   mayor que la mayor capacidad disponible se reparte en partes que caben en un vehiculo; si
 *   alguna parte no encuentra candidato, se deshacen todas las partes de ese pedido (nunca queda
 *   un pedido atendido a medias). Antes, esos pedidos quedaban siempre sin atender.</li>
 *   <li><b>Costo del candidato exacto.</b> El costo que ordena la RCL es la diferencia real de
 *   kilometros de la ruta completa (incluido el regreso al central) a las horas reales de paso,
 *   no una aproximacion con la hora de salida de la ruta. Evita costos infinitos que dejaban la
 *   RCL vacia (y hacian fallar la corrida) y alinea el criterio con el costo que mide el evaluador.</li>
 *   <li><b>Factibilidad en construccion alineada con el evaluador:</b> ademas de camino, 80 km por
 *   tramo, plazo y refrigerio, ahora considera el regreso final al central, el mantenimiento o
 *   averia del vehiculo durante toda la ruta, y descuenta inventario al apilar en un tramo de recarga.</li>
 *   <li><b>Consolidacion en el primer viaje.</b> Antes, el primer tramo de una ruta solo podia
 *   llevar el primer pedido asignado (se trataba la carga inicial como inmutable), asi que todo
 *   pedido adicional obligaba a volver a un almacen. GRASP construye la ruta: al apilar en ese
 *   tramo la reconstruye desde el central con una carga inicial mayor. Igual en la busqueda local.</li>
 *   <li><b>Evaluacion incremental:</b> cada vehiculo guarda el estado temporal de su ruta tras
 *   cada parada; un candidato solo recorre la parte que cambia (lo nuevo, lo que sigue y el regreso).</li>
 *   <li><b>Busqueda local:</b> memoriza la evaluacion de cada ruta ya programada (la exploracion
 *   de primera mejora vuelve a visitar muchas rutas identicas), evita recalculos repetidos dentro
 *   de los bucles, respeta el stock de almacenes intermedios, y al quitar una entrega de un tramo
 *   de recarga devuelve su carga (y elimina la recarga si quedo vacia).</li>
 * </ol>
 * Las consultas de camino repetidas se resuelven en la RoadNetwork compartida (ver su Javadoc),
 * con resultado identico al calculo original.
 */
public class GraspPlanificador {

    public static final String VERSION = "GRASP-v2 2026-09-23";

    /** Debe coincidir con OperationalPlanEvaluator.MAX_LEG_DISTANCE_KM (hoja "Flota"). */
    private static final double DISTANCIA_MAXIMA_POR_TRAMO_KM = 80.0;
    private static final int LIMITE_MEMO_RUTAS = 100_000;

    private final RoadNetwork roadNetwork;
    private final RouteScheduler scheduler;
    private final OperationalPlanEvaluator evaluator;
    private final Random random;
    private final Map<ClaveRuta, EvaluacionRuta> memoRutas = new HashMap<>();

    public GraspPlanificador(RoadNetwork roadNetwork, RouteScheduler scheduler,
                              OperationalPlanEvaluator evaluator, long semilla) {
        this.roadNetwork = Objects.requireNonNull(roadNetwork, "roadNetwork es requerido");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler es requerido");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator es requerido");
        this.random = new Random(semilla);
    }

    public ResultadoPlanificacion planificar(OperationalSnapshot snapshot, Collection<Order> pedidos,
                                              List<RoadBlock> bloqueos, double alpha, int maxIteraciones) {
        if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1 || maxIteraciones <= 0) {
            throw new IllegalArgumentException("alpha must be in [0,1] and maxIteraciones > 0");
        }
        memoRutas.clear();
        ResultadoPlanificacion mejor = null;
        try {
            for (int iter = 0; iter < maxIteraciones; iter++) {
                SearchControl.iteration();
                ResultadoPlanificacion candidata = construirGreedyAleatorizada(snapshot, pedidos, bloqueos, alpha);
                // Una construccion ya evaluada es incumbente: se conserva si la busqueda local agota el plazo.
                if (esMejorQue(candidata, mejor)) mejor = candidata;
                SearchControl.observe(candidata.esFactible(), candidata.noAtendidos().size(), candidata.costoTotal());
                candidata = busquedaLocal(candidata, snapshot, bloqueos);
                if (esMejorQue(candidata, mejor)) mejor = candidata;
                SearchControl.observe(candidata.esFactible(), candidata.noAtendidos().size(), candidata.costoTotal());
            }
        } catch (SearchStopped exhausted) {
            // Sin ninguna construccion evaluada => null, reportado explicitamente por el adaptador.
        } finally {
            memoRutas.clear();
        }
        return mejor;
    }

    private boolean esMejorQue(ResultadoPlanificacion a, ResultadoPlanificacion b) {
        if (b == null) return true;
        if (a.esFactible() != b.esFactible()) return a.esFactible();
        if (a.noAtendidos().size() != b.noAtendidos().size()) return a.noAtendidos().size() < b.noAtendidos().size();
        return a.costoTotal() < b.costoTotal();
    }

    // =========================================================
    // Fase constructiva
    // =========================================================

    private ResultadoPlanificacion construirGreedyAleatorizada(OperationalSnapshot snapshot, Collection<Order> pedidos,
                                                                List<RoadBlock> bloqueos, double alpha) {
        Warehouse central = central(snapshot);
        Map<String, EstadoConstruccion> estados = new LinkedHashMap<>();
        int capacidadMaxima = 0;
        for (VehicleOperationalState estado : snapshot.vehiclesById().values()) {
            if (snapshot.isVehiclePlannableAt(estado.vehicle().id(), snapshot.planningTime())) {
                estados.put(estado.vehicle().id(), new EstadoConstruccion(estado, horaDisponible(estado, snapshot)));
                capacidadMaxima = Math.max(capacidadMaxima, capacidad(snapshot, estado.vehicle()));
            }
        }

        InventorySnapshot inventario = snapshot.inventory();
        List<Order> pendientes = new ArrayList<>(pedidos);
        pendientes.sort(Comparator.comparing(Order::deadline));
        List<Order> noAtendidos = new ArrayList<>();

        for (Order pedido : pendientes) {
            SearchControl.checkpoint();
            List<Integer> partes = partesDe(pedido.packages(), capacidadMaxima);
            Map<String, Respaldo> respaldos = new LinkedHashMap<>();
            InventorySnapshot inventarioAntes = inventario;
            boolean atendido = !partes.isEmpty();
            for (int cantidad : partes) {
                List<Candidato> candidatos = generarCandidatos(pedido, cantidad, snapshot, estados, inventario, central, bloqueos);
                if (candidatos.isEmpty()) {
                    atendido = false;
                    break;
                }
                Candidato elegido = elegirDeLaRcl(candidatos, alpha);
                if (elegido.almacenRetiro() != null) {
                    inventario = inventario.withdraw(elegido.almacenRetiro().id(), elegido.cantidad());
                }
                EstadoConstruccion estado = estados.get(elegido.vehiculoId());
                respaldos.putIfAbsent(elegido.vehiculoId(), estado.respaldo());
                Linea linea = calcularLinea(elegido.rutaResultante(), snapshot, central, bloqueos);
                if (linea == null) {
                    throw new IllegalStateException("Candidato aceptado sin linea temporal factible: inconsistencia interna");
                }
                estado.aplicar(elegido, linea);
            }
            if (!atendido) {
                // Todas las partes o ninguna: un pedido nunca queda atendido a medias.
                respaldos.forEach((id, respaldo) -> estados.get(id).restaurar(respaldo));
                inventario = inventarioAntes;
                noAtendidos.add(pedido);
            }
        }

        OperationalPlan plan = OperationalPlan.empty();
        for (EstadoConstruccion estado : estados.values()) {
            if (estado.ruta != null) {
                plan = plan.withRoute(estado.ruta.returningTo(central));
            }
        }

        PlanEvaluation evaluacion = evaluator.evaluate(plan, snapshot, pedidosEnPlan(plan), bloqueos);
        return new ResultadoPlanificacion(plan, evaluacion, List.copyOf(noAtendidos));
    }

    /** Un pedido que cabe en algun vehiculo va entero; si no, en partes del tamano de la mayor capacidad. */
    static List<Integer> partesDe(int paquetes, int capacidadMaxima) {
        if (capacidadMaxima <= 0) return List.of();
        if (paquetes <= capacidadMaxima) return List.of(paquetes);
        List<Integer> partes = new ArrayList<>();
        int restante = paquetes;
        while (restante > 0) {
            int parte = Math.min(restante, capacidadMaxima);
            partes.add(parte);
            restante -= parte;
        }
        return partes;
    }

    private Candidato elegirDeLaRcl(List<Candidato> candidatos, double alpha) {
        double cMin = Double.POSITIVE_INFINITY;
        double cMax = Double.NEGATIVE_INFINITY;
        for (Candidato c : candidatos) {
            if (Double.isFinite(c.costo())) {
                cMin = Math.min(cMin, c.costo());
                cMax = Math.max(cMax, c.costo());
            }
        }
        List<Candidato> rcl;
        if (Double.isFinite(cMin)) {
            double umbral = cMin + alpha * (cMax - cMin);
            rcl = candidatos.stream().filter(c -> Double.isFinite(c.costo()) && c.costo() <= umbral).toList();
        } else {
            rcl = candidatos;
        }
        return rcl.get(random.nextInt(rcl.size()));
    }

    /**
     * Candidatos para asignar {@code cantidad} paquetes de {@code pedido}: (a) apilar la entrega
     * en el tramo abierto del vehiculo (desde su ultima carga), aumentando lo que se recoge en esa
     * carga: el pickup de la recarga o, en el primer tramo, la carga inicial de la ruta desde el
     * central; (b) si el vehiculo esta libre, iniciar su ruta desde el central; si ya tiene ruta,
     * recargar en el almacen con stock mas cercano y entregar.
     * El costo es el aumento exacto de km de la ruta completa por el costo/km del vehiculo.
     */
    private List<Candidato> generarCandidatos(Order pedido, int cantidad, OperationalSnapshot snapshot,
            Map<String, EstadoConstruccion> estados, InventorySnapshot inventario,
            Warehouse central, List<RoadBlock> bloqueos) {

        List<Candidato> candidatos = new ArrayList<>();
        DeliveryStop entrega = new DeliveryStop(pedido, cantidad);

        for (EstadoConstruccion estado : estados.values()) {
            Vehicle vehiculo = estado.estadoInicial.vehicle();
            int capacidad = capacidad(snapshot, vehiculo);
            if (cantidad > capacidad) continue;
            double costoKm = snapshot.fleetProfile().parametersFor(vehiculo.type()).costPerKm();

            if (estado.ruta == null) {
                DeliveryRoute base = DeliveryRoute.startScenarioAtCentral(
                        vehiculo.id() + "-R", vehiculo, central, cantidad, estado.horaDisponible);
                Paso inicio = new Paso(central.location(), estado.horaDisponible, null, 0);
                double total = recorrer(inicio, List.of(entrega), List.of(), base, snapshot, central, bloqueos);
                if (!Double.isNaN(total)) {
                    candidatos.add(new Candidato(vehiculo.id(), base.withAppendedStop(entrega), true,
                            pedido, cantidad, central, total * costoKm));
                }
                continue;
            }

            List<RouteStop> stops = estado.ruta.stops();
            int ultimoAlmacen = indiceUltimoAlmacen(stops);

            // (a) Apilar en el tramo abierto: lo recoge la ultima recarga o, si aun no hubo, la carga inicial.
            Warehouse proveedor = almacenDelTramo(estado.ruta, stops.size());
            if (proveedor != null && estado.cargaActual + cantidad <= capacidad
                    && inventario.hasStockFor(proveedor.id(), cantidad)) {
                for (int pos = ultimoAlmacen + 1; pos <= stops.size(); pos++) {
                    double total = recorrer(estado.linea.pasos().get(pos), List.of(entrega),
                            stops.subList(pos, stops.size()), estado.ruta, snapshot, central, bloqueos);
                    if (Double.isNaN(total)) continue;
                    DeliveryRoute nueva = insertar(estado.ruta, pos, entrega);
                    if (nueva == null) continue;
                    candidatos.add(new Candidato(vehiculo.id(), nueva, false, pedido, cantidad, proveedor,
                            (total - estado.linea.distanciaConRegreso()) * costoKm));
                }
            }

            // (b) Recargar en el almacen con stock mas cercano y entregar.
            Paso ultimo = estado.linea.pasos().getLast();
            Warehouse almacen = elegirAlmacen(ultimo, cantidad, inventario, vehiculo, snapshot, bloqueos);
            if (almacen != null) {
                WarehouseVisit recarga = new WarehouseVisit(almacen, cantidad);
                double total = recorrer(ultimo, List.of(recarga, entrega), List.of(), estado.ruta, snapshot, central, bloqueos);
                if (!Double.isNaN(total)) {
                    candidatos.add(new Candidato(vehiculo.id(), estado.ruta.withAppendedStop(recarga).withAppendedStop(entrega),
                            true, pedido, cantidad, almacen, (total - estado.linea.distanciaConRegreso()) * costoKm));
                }
            }
        }
        return candidatos;
    }

    private Instant horaDisponible(VehicleOperationalState estadoInicial, OperationalSnapshot snapshot) {
        return estadoInicial.availableAt().isAfter(snapshot.planningTime())
                ? estadoInicial.availableAt() : snapshot.planningTime();
    }

    /** El almacen con stock suficiente mas cercano por calles reales, desde donde y cuando queda el vehiculo. */
    private Warehouse elegirAlmacen(Paso desde, int cantidadNecesaria, InventorySnapshot inventario, Vehicle vehiculo,
                                    OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        Warehouse mejor = null;
        double mejorDistancia = Double.POSITIVE_INFINITY;
        for (Warehouse almacen : List.copyOf(snapshot.inventory().warehouses())) {
            if (!inventario.hasStockFor(almacen.id(), cantidadNecesaria)) continue;
            Optional<RoadPath> camino = camino(desde.posicion(), almacen.location(), desde.tiempo(), vehiculo, snapshot, bloqueos);
            double distancia = camino.map(c -> (double) c.distanceKm()).orElse(Double.POSITIVE_INFINITY);
            if (distancia < mejorDistancia) {
                mejorDistancia = distancia;
                mejor = almacen;
            }
        }
        return mejor;
    }

    // ---------- Linea temporal de una ruta: el mismo calculo que RouteScheduler.schedule(),
    // parada por parada, mas las reglas duras por tramo que aplica el evaluador. ----------

    /** Estado del vehiculo al salir de una parada: donde esta, cuando sale, turno en que ya tomo refrigerio y km acumulados. */
    private record Paso(Location posicion, Instant tiempo, Instant turnoConRefrigerio, double distancia) {
    }

    /** pasos().get(0) es la salida de la ruta; pasos().get(k) es el estado tras la parada k-1. */
    private record Linea(List<Paso> pasos, double distanciaConRegreso) {
    }

    /** Avanza una parada. null si no hay camino, el tramo supera 80 km o la entrega llega tarde. */
    private Paso avanzar(Paso actual, RouteStop parada, Vehicle vehiculo, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        Optional<RoadPath> camino = camino(actual.posicion(), parada.location(), actual.tiempo(), vehiculo, snapshot, bloqueos);
        if (camino.isEmpty()) return null;
        RoadPath recorrido = camino.get();
        if (recorrido.distanceKm() > DISTANCIA_MAXIMA_POR_TRAMO_KM) return null;
        Instant llegada = recorrido.arrivesAt();
        Instant turnoTomado = actual.turnoConRefrigerio();
        ShiftSchedule.ShiftWindow turno = snapshot.shiftSchedule().shiftAt(llegada);
        ShiftSchedule.ShiftWindow ventanaRefrigerio = snapshot.shiftSchedule().mealWindow(llegada);
        if (!turno.startsAt().equals(turnoTomado) && !llegada.isBefore(ventanaRefrigerio.startsAt())) {
            llegada = llegada.plus(Duration.ofHours(1));
            turnoTomado = turno.startsAt();
        }
        Instant salida;
        if (parada instanceof DeliveryStop entrega) {
            if (llegada.isAfter(entrega.order().deadline())) return null;
            salida = llegada.plus(DeliveryStop.SERVICE_TIME);
        } else {
            salida = llegada;
        }
        return new Paso(parada.location(), salida, turnoTomado, actual.distancia() + recorrido.distanceKm());
    }

    /**
     * Recorre desde {@code inicio} las paradas nuevas, luego el resto de la ruta y el regreso al
     * central. Devuelve los km totales de la ruta completa, o NaN si algo es infactible (incluido
     * mantenimiento o averia del vehiculo en cualquier momento de la ruta).
     */
    private double recorrer(Paso inicio, List<RouteStop> nuevas, List<RouteStop> resto, DeliveryRoute ruta,
                            OperationalSnapshot snapshot, Warehouse central, List<RoadBlock> bloqueos) {
        Vehicle vehiculo = ruta.vehicle();
        Paso paso = inicio;
        for (RouteStop parada : nuevas) {
            paso = avanzar(paso, parada, vehiculo, snapshot, bloqueos);
            if (paso == null) return Double.NaN;
        }
        for (RouteStop parada : resto) {
            paso = avanzar(paso, parada, vehiculo, snapshot, bloqueos);
            if (paso == null) return Double.NaN;
        }
        Paso fin = avanzar(paso, new WarehouseVisit(central, 0), vehiculo, snapshot, bloqueos);
        if (fin == null || interrumpida(vehiculo, ruta.departureAt(), fin.tiempo(), snapshot)) return Double.NaN;
        return fin.distancia();
    }

    private Linea calcularLinea(DeliveryRoute ruta, OperationalSnapshot snapshot, Warehouse central, List<RoadBlock> bloqueos) {
        List<Paso> pasos = new ArrayList<>(ruta.stops().size() + 1);
        Paso paso = new Paso(ruta.startLocation(), ruta.departureAt(), null, 0);
        pasos.add(paso);
        for (RouteStop parada : ruta.stops()) {
            paso = avanzar(paso, parada, ruta.vehicle(), snapshot, bloqueos);
            if (paso == null) return null;
            pasos.add(paso);
        }
        Paso fin = avanzar(paso, new WarehouseVisit(central, 0), ruta.vehicle(), snapshot, bloqueos);
        if (fin == null || interrumpida(ruta.vehicle(), ruta.departureAt(), fin.tiempo(), snapshot)) return null;
        return new Linea(List.copyOf(pasos), fin.distancia());
    }

    /** Mismo criterio que MAINTENANCE_OR_BREAKDOWN del evaluador: [salida de la ruta, fin del regreso). */
    private boolean interrumpida(Vehicle vehiculo, Instant salida, Instant fin, OperationalSnapshot snapshot) {
        return fin.isAfter(salida) && snapshot.hasVehicleDisruptionDuring(vehiculo.id(), salida, fin);
    }

    private Optional<RoadPath> camino(Location origen, Location destino, Instant horaSalida, Vehicle vehiculo,
                                      OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        double velocidad = snapshot.fleetProfile().parametersFor(vehiculo.type()).speedKmPerHour();
        Duration porTramo = Duration.ofMillis(Math.round(3_600_000.0 / velocidad));
        return roadNetwork.shortestPath(origen, destino, horaSalida, porTramo, bloqueos);
    }

    // ---------- Evaluacion exacta de rutas para la busqueda local (RouteScheduler), memorizada ----------

    private record ClaveRuta(String vehiculoId, Location inicio, Instant salida, int cargaInicial, List<RouteStop> paradas) {
    }

    private record EvaluacionRuta(double costo, boolean factible) {
    }

    private EvaluacionRuta evaluar(DeliveryRoute ruta, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        ClaveRuta clave = new ClaveRuta(ruta.vehicle().id(), ruta.startLocation(), ruta.departureAt(), ruta.initialLoad(), ruta.stops());
        EvaluacionRuta conocida = memoRutas.get(clave);
        if (conocida != null) return conocida;
        EvaluacionRuta evaluacion;
        try {
            ScheduledDeliveryRoute programada = scheduler.schedule(ruta, snapshot, bloqueos);
            boolean factible = true;
            for (ScheduledRouteStop parada : programada.scheduledStops()) {
                if (parada.approach().distanceKm() > DISTANCIA_MAXIMA_POR_TRAMO_KM
                        || (parada.stop() instanceof DeliveryStop entrega && parada.arrivedAt().isAfter(entrega.order().deadline()))) {
                    factible = false;
                    break;
                }
            }
            if (factible && interrumpida(ruta.vehicle(), ruta.departureAt(), programada.completedAt(), snapshot)) {
                factible = false;
            }
            evaluacion = new EvaluacionRuta(programada.totalCost(), factible);
        } catch (IllegalStateException sinCaminoFactible) {
            evaluacion = new EvaluacionRuta(Double.POSITIVE_INFINITY, false);
        }
        if (memoRutas.size() >= LIMITE_MEMO_RUTAS) memoRutas.clear();
        memoRutas.put(clave, evaluacion);
        return evaluacion;
    }

    /** Costo de la ruta si cumple las reglas por ruta (camino, 80 km, plazos, mantenimiento); vacio si no. */
    private Optional<Double> costoSiFactible(DeliveryRoute ruta, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        EvaluacionRuta evaluacion = evaluar(ruta, snapshot, bloqueos);
        return evaluacion.factible() ? Optional.of(evaluacion.costo()) : Optional.empty();
    }

    /** Defensivo: una ruta sin camino se trata como costo infinito, no tumba la corrida. */
    private double costoRuta(DeliveryRoute ruta, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        return evaluar(ruta, snapshot, bloqueos).costo();
    }

    private int indiceUltimoAlmacen(List<RouteStop> stops) {
        for (int i = stops.size() - 1; i >= 0; i--) {
            if (stops.get(i) instanceof WarehouseVisit) return i;
        }
        return -1;
    }

    private Collection<Order> pedidosEnPlan(OperationalPlan plan) {
        Map<String, Order> porId = new LinkedHashMap<>();
        for (DeliveryRoute ruta : plan.routes()) {
            for (RouteStop stop : ruta.stops()) {
                if (stop instanceof DeliveryStop entrega) porId.put(entrega.order().id(), entrega.order());
            }
        }
        return porId.values();
    }

    // =========================================================
    // Busqueda local de primera mejora: insercion de pendientes, 2-opt,
    // reubicacion e intercambio. Cada paso aplica el primer movimiento que
    // mejora y reinicia; termina cuando ninguno mejora.
    // =========================================================

    private ResultadoPlanificacion busquedaLocal(ResultadoPlanificacion actual, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        OperationalPlan plan = actual.plan();
        List<Order> pendientes = new ArrayList<>(actual.noAtendidos());
        boolean mejorado = true;
        while (mejorado) {
            SearchControl.checkpoint();
            mejorado = false;
            ResultadoInsercion insercion;
            if (!pendientes.isEmpty() && (insercion = pasoInsercionPendientes(plan, pendientes, snapshot, bloqueos)) != null) {
                plan = insercion.plan();
                pendientes.remove(insercion.pedidoInsertado());
                mejorado = true;
                continue;
            }
            OperationalPlan siguiente;
            if ((siguiente = pasoDosOpt(plan, snapshot, bloqueos)) != null) { plan = siguiente; mejorado = true; continue; }
            if ((siguiente = pasoReubicacion(plan, snapshot, bloqueos)) != null) { plan = siguiente; mejorado = true; continue; }
            if ((siguiente = pasoIntercambio(plan, snapshot, bloqueos)) != null) { plan = siguiente; mejorado = true; }
        }
        PlanEvaluation evaluacion = evaluator.evaluate(plan, snapshot, pedidosEnPlan(plan), bloqueos);
        return new ResultadoPlanificacion(plan, evaluacion, List.copyOf(pendientes));
    }

    /**
     * Rescata pedidos que la construccion dejo sin atender solo por el orden del barrido greedy:
     * los inserta enteros en un tramo existente de alguna ruta, aumentando lo que recoge la carga
     * de ese tramo, con capacidad y stock. No abre recargas ni vehiculos nuevos; si no cabe asi,
     * sigue sin atender.
     */
    ResultadoInsercion pasoInsercionPendientes(OperationalPlan plan, List<Order> pendientes,
            OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        Map<String, Integer> stock = stockRestante(plan, snapshot);
        for (Order pedido : pendientes) {
            SearchControl.checkpoint();
            DeliveryRoute mejorRuta = null;
            double mejorCosto = Double.POSITIVE_INFINITY;
            DeliveryStop entrega = new DeliveryStop(pedido, pedido.packages());
            for (DeliveryRoute ruta : plan.routes()) {
                int capacidad = capacidad(snapshot, ruta.vehicle());
                int posMax = ultimaPosicionValidaParaInsertar(ruta.stops());
                for (int pos = 0; pos <= posMax; pos++) {
                    if (cargaDelSegmento(ruta.stops(), pos) + pedido.packages() > capacidad) continue;
                    Warehouse proveedor = almacenDelTramo(ruta, pos);
                    if (proveedor == null || !hayStock(stock, proveedor, pedido.packages())) continue;
                    DeliveryRoute candidata = insertar(ruta, pos, entrega);
                    if (candidata == null) continue;
                    Optional<Double> costo = costoSiFactible(candidata, snapshot, bloqueos);
                    if (costo.isPresent() && costo.get() < mejorCosto) {
                        mejorCosto = costo.get();
                        mejorRuta = candidata;
                    }
                }
            }
            if (mejorRuta != null) {
                return new ResultadoInsercion(plan.withRoute(mejorRuta), pedido);
            }
        }
        return null;
    }

    record ResultadoInsercion(OperationalPlan plan, Order pedidoInsertado) {
    }

    /** 2-opt: invierte un tramo dentro de un mismo segmento de entregas; primer movimiento que mejora. */
    private OperationalPlan pasoDosOpt(OperationalPlan plan, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        for (DeliveryRoute ruta : plan.routes()) {
            SearchControl.checkpoint();
            double costoActual = costoRuta(ruta, snapshot, bloqueos);
            for (int[] segmento : segmentosDeEntrega(ruta.stops())) {
                for (int i = segmento[0]; i < segmento[1]; i++) {
                    for (int j = i + 1; j <= segmento[1]; j++) {
                        List<RouteStop> copia = new ArrayList<>(ruta.stops());
                        Collections.reverse(copia.subList(i, j + 1));
                        DeliveryRoute invertida = ruta.withReplacedStops(copia);
                        Optional<Double> costoNuevo = costoSiFactible(invertida, snapshot, bloqueos);
                        if (costoNuevo.isPresent() && costoNuevo.get() < costoActual) {
                            return plan.withRoute(invertida);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Reubicacion (Or-opt): mueve una entrega a otra ruta, en cualquier tramo con capacidad y
     * stock (aumenta lo que recoge la carga de ese tramo). La ruta de origen deja de recoger esa
     * cantidad (y elimina la recarga si quedo vacia); si se queda sin entregas, sale del plan.
     */
    private OperationalPlan pasoReubicacion(OperationalPlan plan, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        List<DeliveryRoute> rutas = List.copyOf(plan.routes());
        Map<String, Integer> stock = stockRestante(plan, snapshot);
        Map<String, Double> costoActualPorVehiculo = new HashMap<>();
        for (DeliveryRoute ruta : rutas) {
            costoActualPorVehiculo.put(ruta.vehicle().id(), costoRuta(ruta, snapshot, bloqueos));
        }
        for (DeliveryRoute origen : rutas) {
            double costoOrigenActual = costoActualPorVehiculo.get(origen.vehicle().id());
            for (int i : deliveryIndexes(origen.stops())) {
                SearchControl.checkpoint();
                DeliveryStop movido = (DeliveryStop) origen.stops().get(i);
                Retiro retiro = quitar(origen, i);
                DeliveryRoute origenSinParada = retiro.ruta();
                Optional<Double> costoOrigenNuevo = costoSiFactible(origenSinParada, snapshot, bloqueos);
                if (costoOrigenNuevo.isEmpty()) continue;
                double ahorro = costoOrigenActual - costoOrigenNuevo.get();

                for (DeliveryRoute destino : rutas) {
                    if (destino.vehicle().id().equals(origen.vehicle().id())) continue;
                    int capacidad = capacidad(snapshot, destino.vehicle());
                    double costoDestinoActual = costoActualPorVehiculo.get(destino.vehicle().id());
                    // Nunca despues del regreso final: toda ruta debe terminar en un almacen.
                    int posMax = ultimaPosicionValidaParaInsertar(destino.stops());
                    for (int pos = 0; pos <= posMax; pos++) {
                        if (cargaDelSegmento(destino.stops(), pos) + movido.deliveredPackages() > capacidad) continue;
                        Warehouse proveedor = almacenDelTramo(destino, pos);
                        if (proveedor == null) continue;
                        int liberado = proveedor.equals(retiro.almacenLiberado()) ? movido.deliveredPackages() : 0;
                        if (!hayStock(stock, proveedor, movido.deliveredPackages() - liberado)) continue;
                        DeliveryRoute destinoNuevo = insertar(destino, pos, movido);
                        if (destinoNuevo == null) continue;
                        Optional<Double> costoDestinoNuevo = costoSiFactible(destinoNuevo, snapshot, bloqueos);
                        if (costoDestinoNuevo.isEmpty()) continue;
                        double costoIncremental = costoDestinoNuevo.get() - costoDestinoActual;
                        if (costoIncremental < ahorro) {
                            OperationalPlan planConDestinoActualizado = plan.withRoute(destinoNuevo);
                            boolean origenQuedaVacio = deliveryIndexes(origenSinParada.stops()).isEmpty();
                            return origenQuedaVacio
                                    ? planConDestinoActualizado.withoutRoute(origen.vehicle().id())
                                    : planConDestinoActualizado.withRoute(origenSinParada);
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Intercambio: dos entregas entre dos rutas distintas, ajustando las recargas que las gobiernan. */
    private OperationalPlan pasoIntercambio(OperationalPlan plan, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        List<DeliveryRoute> rutas = List.copyOf(plan.routes());
        Map<String, Integer> stock = stockRestante(plan, snapshot);
        for (int a = 0; a < rutas.size(); a++) {
            DeliveryRoute rutaA = rutas.get(a);
            double costoA = costoRuta(rutaA, snapshot, bloqueos);
            int capA = capacidad(snapshot, rutaA.vehicle());
            for (int b = a + 1; b < rutas.size(); b++) {
                SearchControl.checkpoint();
                DeliveryRoute rutaB = rutas.get(b);
                double costoAntes = costoA + costoRuta(rutaB, snapshot, bloqueos);
                int capB = capacidad(snapshot, rutaB.vehicle());

                for (int i : deliveryIndexes(rutaA.stops())) {
                    DeliveryStop pA = (DeliveryStop) rutaA.stops().get(i);
                    for (int j : deliveryIndexes(rutaB.stops())) {
                        DeliveryStop pB = (DeliveryStop) rutaB.stops().get(j);
                        int nuevaCargaSegA = cargaDelSegmento(rutaA.stops(), i) - pA.deliveredPackages() + pB.deliveredPackages();
                        int nuevaCargaSegB = cargaDelSegmento(rutaB.stops(), j) - pB.deliveredPackages() + pA.deliveredPackages();
                        if (nuevaCargaSegA > capA || nuevaCargaSegB > capB) continue;

                        // Si las cantidades difieren, la carga de cada tramo absorbe la diferencia.
                        int delta = pB.deliveredPackages() - pA.deliveredPackages();
                        if (delta != 0) {
                            Warehouse proveedorA = almacenDelTramo(rutaA, i);
                            Warehouse proveedorB = almacenDelTramo(rutaB, j);
                            if (proveedorA == null || proveedorB == null) continue;
                            if (!stockParaIntercambio(stock, proveedorA, delta, proveedorB)) continue;
                        }
                        List<RouteStop> stopsA = new ArrayList<>(rutaA.stops());
                        List<RouteStop> stopsB = new ArrayList<>(rutaB.stops());
                        stopsA.set(i, new DeliveryStop(pB.order(), pB.deliveredPackages()));
                        stopsB.set(j, new DeliveryStop(pA.order(), pA.deliveredPackages()));
                        DeliveryRoute rutaANueva = conCargaAjustada(rutaA, stopsA, i, delta);
                        DeliveryRoute rutaBNueva = conCargaAjustada(rutaB, stopsB, j, -delta);
                        if (rutaANueva == null || rutaBNueva == null) continue;

                        Optional<Double> costoANuevo = costoSiFactible(rutaANueva, snapshot, bloqueos);
                        if (costoANuevo.isEmpty()) continue;
                        Optional<Double> costoBNuevo = costoSiFactible(rutaBNueva, snapshot, bloqueos);
                        if (costoBNuevo.isEmpty()) continue;

                        if (costoANuevo.get() + costoBNuevo.get() < costoAntes) {
                            return plan.withRoute(rutaANueva).withRoute(rutaBNueva);
                        }
                    }
                }
            }
        }
        return null;
    }

    // ---------- Auxiliares de tramos, carga e inventario ----------

    /*
     * Cada tramo de entregas lo abastece una carga: la carga inicial de la ruta (primer tramo) o
     * el pickup de la recarga que lo abre. Insertar, quitar o cambiar una entrega ajusta esa
     * carga para que siga igual a lo que el tramo entrega (sin carga negativa ni stock retirado de
     * mas). La carga inicial se ajusta reconstruyendo la ruta desde el central con otro valor; una
     * ruta replanificada desde la posicion del vehiculo (sin almacen inicial) no la puede cambiar.
     */

    /** Almacen que abastece el tramo de la posicion: la recarga previa o el almacen de salida (si hay). */
    private Warehouse almacenDelTramo(DeliveryRoute ruta, int posicion) {
        int indiceAlmacen = indiceAlmacenQueAbreSegmento(ruta.stops(), posicion);
        if (indiceAlmacen >= 0) return ((WarehouseVisit) ruta.stops().get(indiceAlmacen)).warehouse();
        return ruta.initialWarehouse().orElse(null);
    }

    /** La ruta con {@code paradas} y la carga del tramo de {@code posicion} cambiada en delta; null si no es posible. */
    private DeliveryRoute conCargaAjustada(DeliveryRoute ruta, List<RouteStop> paradas, int posicion, int delta) {
        if (delta == 0) return ruta.withReplacedStops(paradas);
        int indiceAlmacen = indiceAlmacenQueAbreSegmento(paradas, posicion);
        if (indiceAlmacen >= 0) {
            WarehouseVisit visita = (WarehouseVisit) paradas.get(indiceAlmacen);
            int nuevoPickup = visita.pickupPackages() + delta;
            if (nuevoPickup < 0) return null;
            List<RouteStop> copia = new ArrayList<>(paradas);
            copia.set(indiceAlmacen, new WarehouseVisit(visita.warehouse(), nuevoPickup));
            return ruta.withReplacedStops(copia);
        }
        Optional<Warehouse> inicial = ruta.initialWarehouse();
        int nuevaCarga = ruta.initialLoad() + delta;
        if (inicial.isEmpty() || nuevaCarga < 0 || nuevaCarga > ruta.vehicle().type().capacity()) return null;
        return DeliveryRoute.startScenarioAtCentral(ruta.id(), ruta.vehicle(), inicial.get(), nuevaCarga, ruta.departureAt())
                .withReplacedStops(paradas);
    }

    /** Inserta la entrega en pos y aumenta la carga de su tramo; null si esa carga no es ajustable. */
    private DeliveryRoute insertar(DeliveryRoute ruta, int pos, DeliveryStop entrega) {
        List<RouteStop> paradas = new ArrayList<>(ruta.stops());
        paradas.add(pos, entrega);
        return conCargaAjustada(ruta, paradas, pos, entrega.deliveredPackages());
    }

    /** Resultado de quitar una entrega: la ruta nueva y el almacen que deja de entregar esa carga. */
    private record Retiro(DeliveryRoute ruta, Warehouse almacenLiberado) {
    }

    /**
     * Quita la entrega i y reduce la carga de su tramo en esa cantidad; si una recarga queda sin
     * carga ni entregas y no es el regreso final, se elimina.
     */
    private Retiro quitar(DeliveryRoute ruta, int i) {
        List<RouteStop> paradas = new ArrayList<>(ruta.stops());
        DeliveryStop quitada = (DeliveryStop) paradas.remove(i);
        Warehouse proveedor = almacenDelTramo(ruta, i);
        DeliveryRoute ajustada = conCargaAjustada(ruta, paradas, i, -quitada.deliveredPackages());
        if (ajustada == null) {
            return new Retiro(ruta.withReplacedStops(paradas), null);
        }
        int indiceAlmacen = indiceAlmacenQueAbreSegmento(paradas, i);
        if (indiceAlmacen >= 0) {
            WarehouseVisit visita = (WarehouseVisit) ajustada.stops().get(indiceAlmacen);
            boolean esRegresoFinal = indiceAlmacen == ajustada.stops().size() - 1;
            if (visita.pickupPackages() == 0 && !esRegresoFinal && !tramoTieneEntregas(ajustada.stops(), indiceAlmacen)) {
                List<RouteStop> sinRecargaVacia = new ArrayList<>(ajustada.stops());
                sinRecargaVacia.remove(indiceAlmacen);
                ajustada = ajustada.withReplacedStops(sinRecargaVacia);
            }
        }
        return new Retiro(ajustada, proveedor);
    }

    private boolean tramoTieneEntregas(List<RouteStop> stops, int indiceAlmacen) {
        for (int k = indiceAlmacen + 1; k < stops.size(); k++) {
            if (stops.get(k) instanceof WarehouseVisit) return false;
            if (stops.get(k) instanceof DeliveryStop) return true;
        }
        return false;
    }

    /** Stock de cada almacen intermedio que el plan todavia no retira (el central no tiene limite). */
    private Map<String, Integer> stockRestante(OperationalPlan plan, OperationalSnapshot snapshot) {
        Map<String, Integer> restante = new HashMap<>();
        for (Warehouse almacen : snapshot.inventory().warehouses()) {
            if (!almacen.isCentral()) restante.put(almacen.id(), snapshot.inventory().availableStock(almacen.id()));
        }
        for (DeliveryRoute ruta : plan.routes()) {
            ruta.initialWarehouse().ifPresent(almacen -> {
                if (!almacen.isCentral()) restante.merge(almacen.id(), -ruta.initialLoad(), Integer::sum);
            });
            for (RouteStop parada : ruta.stops()) {
                if (parada instanceof WarehouseVisit visita && !visita.warehouse().isCentral()) {
                    restante.merge(visita.warehouse().id(), -visita.pickupPackages(), Integer::sum);
                }
            }
        }
        return restante;
    }

    private boolean hayStock(Map<String, Integer> restante, Warehouse almacen, int adicional) {
        return adicional <= 0 || almacen.isCentral() || restante.getOrDefault(almacen.id(), 0) >= adicional;
    }

    /** A recoge delta mas y B delta menos (delta puede ser negativo). */
    private boolean stockParaIntercambio(Map<String, Integer> restante, Warehouse almacenA, int delta, Warehouse almacenB) {
        if (almacenA.equals(almacenB)) return true;
        return delta > 0 ? hayStock(restante, almacenA, delta) : hayStock(restante, almacenB, -delta);
    }

    private int indiceAlmacenQueAbreSegmento(List<RouteStop> stops, int posicion) {
        for (int k = posicion - 1; k >= 0; k--) {
            if (stops.get(k) instanceof WarehouseVisit) return k;
        }
        return -1;
    }

    private int ultimaPosicionValidaParaInsertar(List<RouteStop> stops) {
        boolean terminaEnAlmacen = !stops.isEmpty() && stops.get(stops.size() - 1) instanceof WarehouseVisit;
        return terminaEnAlmacen ? stops.size() - 1 : stops.size();
    }

    private List<Integer> deliveryIndexes(List<RouteStop> stops) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) if (stops.get(i) instanceof DeliveryStop) indices.add(i);
        return indices;
    }

    private int cargaDelSegmento(List<RouteStop> stops, int posicion) {
        int inicio = 0;
        for (int k = posicion - 1; k >= 0; k--) {
            if (stops.get(k) instanceof WarehouseVisit) { inicio = k + 1; break; }
        }
        int fin = stops.size();
        for (int k = posicion; k < stops.size(); k++) {
            if (stops.get(k) instanceof WarehouseVisit) { fin = k; break; }
        }
        int total = 0;
        for (int k = inicio; k < fin; k++) {
            if (stops.get(k) instanceof DeliveryStop d) total += d.deliveredPackages();
        }
        return total;
    }

    private List<int[]> segmentosDeEntrega(List<RouteStop> stops) {
        List<int[]> segmentos = new ArrayList<>();
        int inicio = -1;
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i) instanceof DeliveryStop) {
                if (inicio == -1) inicio = i;
            } else if (inicio != -1) {
                segmentos.add(new int[]{inicio, i - 1});
                inicio = -1;
            }
        }
        if (inicio != -1) segmentos.add(new int[]{inicio, stops.size() - 1});
        return segmentos;
    }

    private static Warehouse central(OperationalSnapshot snapshot) {
        return snapshot.inventory().warehouses().stream()
                .filter(Warehouse::isCentral).findFirst()
                .orElseThrow(() -> new IllegalStateException("El snapshot no tiene almacen central"));
    }

    private static int capacidad(OperationalSnapshot snapshot, Vehicle vehiculo) {
        return snapshot.fleetProfile().parametersFor(vehiculo.type()).capacity();
    }

    // =========================================================
    // Estado auxiliar de construccion
    // =========================================================

    private record Respaldo(DeliveryRoute ruta, int cargaActual, Linea linea) {
    }

    private static final class EstadoConstruccion {
        final VehicleOperationalState estadoInicial;
        final Instant horaDisponible;
        DeliveryRoute ruta;
        /** Paquetes comprometidos en el tramo abierto (desde la ultima carga). */
        int cargaActual;
        Linea linea;

        EstadoConstruccion(VehicleOperationalState estadoInicial, Instant horaDisponible) {
            this.estadoInicial = estadoInicial;
            this.horaDisponible = horaDisponible;
        }

        Respaldo respaldo() {
            return new Respaldo(ruta, cargaActual, linea);
        }

        void restaurar(Respaldo respaldo) {
            ruta = respaldo.ruta();
            cargaActual = respaldo.cargaActual();
            linea = respaldo.linea();
        }

        void aplicar(Candidato c, Linea nuevaLinea) {
            cargaActual = c.abreTramo() ? c.cantidad() : cargaActual + c.cantidad();
            ruta = c.rutaResultante();
            linea = nuevaLinea;
        }
    }

    /** abreTramo: la entrega inicia un tramo nuevo (ruta nueva o recarga). */
    private record Candidato(String vehiculoId, DeliveryRoute rutaResultante, boolean abreTramo,
                             Order pedido, int cantidad, Warehouse almacenRetiro, double costo) {
    }
}
