package pe.pucp.paqrap.planificador;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.pucp.paqrap.modelo.ResultadoPlanificacion;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * GRASP para PaqRap, operando sobre el modelo de dominio unico compartido
 * (paqrap-dominio, paquete pe.edu.pucp.paqrap.planner) -- ver README.md
 * raiz para el historial de la unificacion. Este modulo no tiene ni copia
 * ni duplica domain/route: depende de paqrap-dominio via Maven.
 *
 * Decisiones de diseño (pendientes de confirmar con Cristhian/Jorge):
 *
 * 1. "noAtendidos": se excluyen del conjunto requiredOrders pasado a
 *    OperationalPlanEvaluator, por lo que nunca hacen infactible el plan
 *    completo -- decision confirmada por el docente (ver README): un
 *    pedido sin vehiculo factible en esta corrida queda pendiente para la
 *    siguiente, sin invalidar el resto del plan ya construido. Comparación
 *    lexicográfica: primero factibilidad, luego menos noAtendidos, luego
 *    menor costo -- por eso "todos los pedidos llegan" siempre le gana a
 *    "cuesta menos" entre iteraciones. Ademas, busquedaLocal reintenta
 *    insertar cada noAtendido en el plan ya reordenado (pasoInsercionPendientes)
 *    antes de rendirse, porque el barrido greedy de la construccion puede
 *    dejar un pedido varado solo por el orden en que lo proceso, no porque
 *    fuera imposible de atender -- si sigue sin caber despues de ese intento,
 *    si es un caso real de colapso (demanda que excede la flota disponible).
 * 2. Costeo en la fase CONSTRUCTIVA: costo marginal barato para rankear el
 *    RCL + verificación exacta pierna por pierna solo para filtrar
 *    candidatos, ambas cacheadas por origen-destino-hora-velocidad (ver
 *    costoInsercionMarginal / esFactibleCandidato) en vez de recalcular la
 *    ruta completa vía RouteScheduler por cada candidato. GRASP evalúa
 *    muchos candidatos por pedido (vehículo × posición) para construir el
 *    RCL -- a diferencia de SA, que solo evalúa un vecino por iteración --
 *    así que el "recálculo completo por evaluación" que usa SA penaliza
 *    desproporcionadamente a GRASP (medido: ~22s para 50 iteraciones con
 *    solo 3 pedidos usando recalculo completo). La búsqueda LOCAL sigue
 *    recalculando la ruta completa vía RouteScheduler -- sus movimientos
 *    no se prestan a un delta simple en una red con bloqueos dependientes
 *    del tiempo. OperationalPlanEvaluator sobre el plan completo solo se
 *    llama una vez por iteración, no por candidato.
 * 3. Recarga (agregar un WarehouseVisit) es SIEMPRE una alternativa ofrecida
 *    a "seguir apilando en el tramo abierto" para cualquier vehiculo que ya
 *    tenga ruta (ver generarCandidatos, rama (b)). Version anterior lo
 *    ofrecia solo cuando estado.cargaActual==0 -- una condicion que, tal
 *    como se calcula cargaActual (solo acumula durante la construccion, no
 *    se resetea sola), nunca se cumplia, dejando la recarga inalcanzable:
 *    cualquier vehiculo con 2+ pedidos los apilaba TODOS en el mismo tramo
 *    inicial, cuyo initialLoad es inmutable y solo cubre el primer pedido
 *    -- eso producia NEGATIVE_LOAD en cuanto se probo con data real (con
 *    pocos pedidos sinteticos y 37 vehiculos casi nunca hacia falta apilar
 *    2 pedidos en un mismo vehiculo, por eso las pruebas nunca lo agarraron).
 *    El fix real tiene dos partes: (a) apilar en un tramo abierto por una
 *    recarga SI es seguro, porque su WarehouseVisit es mutable -- se le
 *    suma el pedido nuevo al pickup, igual que pasoReubicacion; (b) apilar
 *    en el tramo INICIAL nunca es seguro (initialLoad no se puede tocar),
 *    asi que esas posiciones se descartan y la recarga queda como la unica
 *    salida para el 2do+ pedido de un vehiculo -- ver indiceAlmacenQueAbreSegmento.
 * 4. Limite de 80 km por tramo (hoja "Flota"): se verifica tanto aqui
 *    (esFactibleCandidato, para no generar candidatos que de todas formas
 *    seran rechazados) como en OperationalPlanEvaluator (autoritativo) --
 *    ver LEG_DISTANCE_EXCEEDED en el dominio compartido.
 */
public class GraspPlanificador {

    /** Debe coincidir con OperationalPlanEvaluator.MAX_LEG_DISTANCE_KM del
     *  modulo dominio -- duplicado aqui solo para poder rechazar candidatos
     *  temprano sin depender de un getter publico en el evaluador. */
    private static final double DISTANCIA_MAXIMA_POR_TRAMO_KM = 80.0;

    private final RoadNetwork roadNetwork;
    private final RouteScheduler scheduler;
    private final OperationalPlanEvaluator evaluator;
    private final Random random;

    /** Cachea Location+Location+horaSalida+velocidad -> RoadPath durante UN
     *  planificar(). GRASP evalua muchos candidatos por pedido (vehiculo x
     *  posicion) que a menudo repiten la misma consulta de camino (misma
     *  ruta.horaSalida, mismo par origen-destino) -- a diferencia de SA, que
     *  solo evalua un vecino por iteracion y nunca necesito esto. Se limpia
     *  al inicio de cada planificar() por si cambian los bloqueos. */
    private final Map<ConsultaCamino, Optional<RoadPath>> cacheCaminos = new HashMap<>();

    public GraspPlanificador(RoadNetwork roadNetwork, RouteScheduler scheduler,
                              OperationalPlanEvaluator evaluator, long semilla) {
        this.roadNetwork = Objects.requireNonNull(roadNetwork, "roadNetwork es requerido");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler es requerido");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator es requerido");
        this.random = new Random(semilla);
    }

    public ResultadoPlanificacion planificar(OperationalSnapshot snapshot, Collection<Order> pedidos,
                                              List<RoadBlock> bloqueos, double alpha, int maxIteraciones) {
        cacheCaminos.clear();
        ResultadoPlanificacion mejor = null;
        for (int iter = 0; iter < maxIteraciones; iter++) {
            ResultadoPlanificacion candidata = construirGreedyAleatorizada(snapshot, pedidos, bloqueos, alpha);
            candidata = busquedaLocal(candidata, snapshot, bloqueos);
            if (esMejorQue(candidata, mejor)) mejor = candidata;
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
        Warehouse central = snapshot.inventory().warehouses().stream()
                .filter(Warehouse::isCentral).findFirst()
                .orElseThrow(() -> new IllegalStateException("El snapshot no tiene almacen central"));

        Map<String, EstadoConstruccion> estadoPorVehiculo = new LinkedHashMap<>();
        for (VehicleOperationalState estado : snapshot.vehiclesById().values()) {
            if (snapshot.isVehiclePlannableAt(estado.vehicle().id(), snapshot.planningTime())) {
                estadoPorVehiculo.put(estado.vehicle().id(), new EstadoConstruccion(estado));
            }
        }

        InventorySnapshot inventario = snapshot.inventory();
        List<Order> pendientes = new ArrayList<>(pedidos);
        pendientes.sort(Comparator.comparing(Order::deadline));
        List<Order> noAtendidos = new ArrayList<>();

        while (!pendientes.isEmpty()) {
            Order pedido = pendientes.remove(0);
            List<Candidato> candidatos = generarCandidatos(pedido, snapshot, estadoPorVehiculo, inventario, central, bloqueos);

            if (candidatos.isEmpty()) {
                noAtendidos.add(pedido);
                continue;
            }

            double cMin = candidatos.stream().mapToDouble(Candidato::costo).min().orElseThrow();
            double cMax = candidatos.stream().mapToDouble(Candidato::costo).max().orElseThrow();
            double umbral = cMin + alpha * (cMax - cMin);
            List<Candidato> rcl = candidatos.stream().filter(c -> c.costo() <= umbral).toList();
            Candidato elegido = rcl.get(random.nextInt(rcl.size()));

            if (elegido.almacenReabastecimiento() != null) {
                inventario = inventario.withdraw(elegido.almacenReabastecimiento().id(), elegido.cargaReabastecida());
            }
            estadoPorVehiculo.get(elegido.vehiculoId()).aplicar(elegido);
        }

        OperationalPlan plan = OperationalPlan.empty();
        for (EstadoConstruccion estado : estadoPorVehiculo.values()) {
            if (estado.ruta != null) {
                plan = plan.withRoute(estado.ruta.returningTo(central));
            }
        }

        PlanEvaluation evaluacion = evaluator.evaluate(plan, snapshot, pedidosEnPlan(plan), bloqueos);
        return new ResultadoPlanificacion(plan, evaluacion, List.copyOf(noAtendidos));
    }

    /** Genera, para un pedido, los candidatos de ASIGNACION DE RUTA: (a)
     *  insertar el DeliveryStop en el tramo de entregas actualmente abierto
     *  de un vehiculo, o (b) si esta vacio, agregar un WarehouseVisit de
     *  recarga (o iniciar su primera DeliveryRoute desde el central)
     *  seguido del DeliveryStop. */
    private List<Candidato> generarCandidatos(Order pedido, OperationalSnapshot snapshot,
            Map<String, EstadoConstruccion> estadoPorVehiculo, InventorySnapshot inventario,
            Warehouse central, List<RoadBlock> bloqueos) {

        List<Candidato> candidatos = new ArrayList<>();
        FleetProfile perfil = snapshot.fleetProfile();

        for (EstadoConstruccion estado : estadoPorVehiculo.values()) {
            Vehicle vehiculo = estado.estadoInicial.vehicle();
            int capacidad = perfil.parametersFor(vehiculo.type()).capacity();

            if (estado.ruta != null && estado.cargaActual + pedido.packages() <= capacidad) {
                List<RouteStop> stops = estado.ruta.stops();
                int desde = indiceUltimoAlmacen(stops) + 1;
                for (int pos = desde; pos <= stops.size(); pos++) {
                    // El tramo inicial (antes de cualquier WarehouseVisit) esta
                    // gobernado por initialLoad, fijado UNA vez al crear la ruta
                    // (rama (b) mas abajo) para cubrir exactamente el primer
                    // pedido -- es inmutable, asi que no se le puede apilar un
                    // 2do pedido sin invalidar el balance de carga (NEGATIVE_LOAD).
                    // Un tramo abierto por una recarga real si se puede ampliar.
                    int indiceAlmacen = indiceAlmacenQueAbreSegmento(stops, pos);
                    if (indiceAlmacen == -1) continue;
                    List<RouteStop> stopsCandidatos = new ArrayList<>(stops);
                    WarehouseVisit visitaOriginal = (WarehouseVisit) stopsCandidatos.get(indiceAlmacen);
                    stopsCandidatos.set(indiceAlmacen, new WarehouseVisit(visitaOriginal.warehouse(),
                            visitaOriginal.pickupPackages() + pedido.packages()));
                    stopsCandidatos.add(pos, new DeliveryStop(pedido, pedido.packages()));
                    DeliveryRoute candidataRuta = estado.ruta.withReplacedStops(stopsCandidatos);
                    if (esFactibleCandidato(candidataRuta, snapshot, bloqueos)) {
                        double costo = costoInsercionMarginal(estado.ruta, pos, pedido, snapshot, bloqueos);
                        candidatos.add(new Candidato(vehiculo.id(), candidataRuta, false, pedido, null, 0, costo));
                    }
                }
            }

            if (pedido.packages() <= capacidad) {
                if (estado.ruta == null) {
                    Instant horaDisponible = horaDisponible(estado.estadoInicial, snapshot);
                    int carga = Math.min(capacidad, pedido.packages());
                    DeliveryRoute rutaBase = DeliveryRoute.startScenarioAtCentral(
                            vehiculo.id() + "-R", vehiculo, central, carga, horaDisponible);
                    DeliveryRoute candidataRuta = rutaBase.withAppendedStop(new DeliveryStop(pedido, pedido.packages()));
                    if (esFactibleCandidato(candidataRuta, snapshot, bloqueos)) {
                        double costo = costoInsercionMarginal(rutaBase, 0, pedido, snapshot, bloqueos);
                        candidatos.add(new Candidato(vehiculo.id(), candidataRuta, true, pedido, central, carga, costo));
                    }
                } else {
                    double velocidad = perfil.parametersFor(vehiculo.type()).speedKmPerHour();
                    Warehouse mejorAlmacen = elegirAlmacen(estado.ubicacionActual(),
                            List.copyOf(snapshot.inventory().warehouses()), pedido.packages(), inventario,
                            snapshot.planningTime(), velocidad, bloqueos);
                    if (mejorAlmacen != null) {
                        int carga = Math.min(capacidad, pedido.packages());
                        DeliveryRoute rutaBase = estado.ruta.withAppendedStop(new WarehouseVisit(mejorAlmacen, carga));
                        DeliveryRoute candidataRuta = rutaBase.withAppendedStop(new DeliveryStop(pedido, pedido.packages()));
                        if (esFactibleCandidato(candidataRuta, snapshot, bloqueos)) {
                            double distanciaAlAlmacen = distanciaKmCacheada(estado.ubicacionActual(), mejorAlmacen.location(),
                                    snapshot.planningTime(), velocidad, bloqueos);
                            double costo = distanciaAlAlmacen * perfil.parametersFor(vehiculo.type()).costPerKm()
                                    + costoInsercionMarginal(rutaBase, rutaBase.stops().size(), pedido, snapshot, bloqueos);
                            candidatos.add(new Candidato(vehiculo.id(), candidataRuta, true, pedido, mejorAlmacen, carga, costo));
                        }
                    }
                }
            }
        }
        return candidatos;
    }

    private Instant horaDisponible(VehicleOperationalState estadoInicial, OperationalSnapshot snapshot) {
        return estadoInicial.availableAt().isAfter(snapshot.planningTime())
                ? estadoInicial.availableAt() : snapshot.planningTime();
    }

    /** Elige, entre los almacenes con stock suficiente, el mas cercano por
     *  distancia real de calles (RoadNetwork, consciente de bloqueos con
     *  ventana de tiempo). Usa la cache: es habitual volver a preguntar por
     *  el mismo origen-almacen entre varios pedidos seguidos. */
    private Warehouse elegirAlmacen(Location desde, List<Warehouse> elegibles, int cantidadNecesaria,
            InventorySnapshot inventario, Instant horaReferencia, double velocidadKmH, List<RoadBlock> bloqueos) {
        Warehouse mejor = null;
        double mejorDistancia = Double.POSITIVE_INFINITY;
        for (Warehouse almacen : elegibles) {
            if (!inventario.hasStockFor(almacen.id(), cantidadNecesaria)) continue;
            double distancia = distanciaKmCacheada(desde, almacen.location(), horaReferencia, velocidadKmH, bloqueos);
            if (distancia < mejorDistancia) { mejorDistancia = distancia; mejor = almacen; }
        }
        return mejor;
    }

    // ---------- costeo marginal + factibilidad pierna-por-pierna para la
    // fase constructiva -- separados a proposito: rankear muchos candidatos
    // debe ser barato (delta de 2-3 tramos, cacheado); esFactibleCandidato
    // si recorre toda la ruta, porque insertar en cualquier posicion puede
    // retrasar TODO lo que viene despues. ----

    private Optional<RoadPath> caminoCacheado(Location origen, Location destino, Instant horaSalida,
            double velocidadKmH, List<RoadBlock> bloqueos) {
        ConsultaCamino clave = new ConsultaCamino(origen, destino, horaSalida, velocidadKmH);
        return cacheCaminos.computeIfAbsent(clave, k -> {
            Duration porTramo = Duration.ofMillis(Math.round(3_600_000.0 / velocidadKmH));
            return roadNetwork.shortestPath(origen, destino, horaSalida, porTramo, bloqueos);
        });
    }

    private double distanciaKmCacheada(Location origen, Location destino, Instant horaSalida,
            double velocidadKmH, List<RoadBlock> bloqueos) {
        return caminoCacheado(origen, destino, horaSalida, velocidadKmH, bloqueos)
                .map(camino -> (double) camino.distanceKm()).orElse(Double.POSITIVE_INFINITY);
    }

    /** Costo marginal de insertar un pedido en la posicion `pos` de `ruta`
     *  (antes de insertarlo) -- 1 a 3 consultas de camino cacheadas, no un
     *  recorrido completo de la ruta. Usa ruta.departureAt() como instante
     *  de referencia para las tres distancias: no es exacto pierna por
     *  pierna, pero es intencionalmente barato porque es solo para RANKEAR
     *  dentro del RCL -- esFactibleCandidato es quien valida de verdad. */
    private double costoInsercionMarginal(DeliveryRoute ruta, int pos, Order pedido,
            OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        VehicleParameters parametros = snapshot.fleetProfile().parametersFor(ruta.vehicle().type());
        Instant horaReferencia = ruta.departureAt();
        double velocidad = parametros.speedKmPerHour();
        double costoKm = parametros.costPerKm();

        Location anterior = pos == 0 ? ruta.startLocation() : ruta.stops().get(pos - 1).location();
        double dAntNuevo = distanciaKmCacheada(anterior, pedido.destination(), horaReferencia, velocidad, bloqueos);
        if (pos == ruta.stops().size()) return dAntNuevo * costoKm;

        Location siguiente = ruta.stops().get(pos).location();
        double dNuevoSig = distanciaKmCacheada(pedido.destination(), siguiente, horaReferencia, velocidad, bloqueos);
        double dAntSig = distanciaKmCacheada(anterior, siguiente, horaReferencia, velocidad, bloqueos);
        return (dAntNuevo + dNuevoSig - dAntSig) * costoKm;
    }

    /** Verificacion EXACTA, pierna por pierna: recorre toda la ruta
     *  candidata comprobando que exista camino hacia cada parada, que
     *  ningun tramo supere el limite de 80 km (hoja "Flota"), y que ningun
     *  DeliveryStop llegue despues de su deadline. Usa la misma cache de
     *  caminos. No valida capacidad -- eso ya lo garantiza el llamador
     *  (estado.cargaActual). */
    private boolean esFactibleCandidato(DeliveryRoute candidataRuta, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        double velocidad = snapshot.fleetProfile().parametersFor(candidataRuta.vehicle().type()).speedKmPerHour();
        Location posicion = candidataRuta.startLocation();
        Instant tiempo = candidataRuta.departureAt();

        for (RouteStop stop : candidataRuta.stops()) {
            Optional<RoadPath> camino = caminoCacheado(posicion, stop.location(), tiempo, velocidad, bloqueos);
            if (camino.isEmpty()) return false;
            if (camino.get().distanceKm() > DISTANCIA_MAXIMA_POR_TRAMO_KM) return false;
            tiempo = camino.get().arrivesAt();
            if (stop instanceof DeliveryStop entrega) {
                if (tiempo.isAfter(entrega.order().deadline())) return false;
                tiempo = tiempo.plus(DeliveryStop.SERVICE_TIME);
            }
            posicion = stop.location();
        }
        return true;
    }

    private record ConsultaCamino(Location origen, Location destino, Instant horaSalida, double velocidadKmH) {
    }

    /** Costo de una ruta candidata si es factible; vacio si no lo es. Usada
     *  por la busqueda local: sus movimientos (2-opt sobre todo un tramo,
     *  reubicaciones, intercambios) no se prestan a un delta marginal
     *  simple en una red con bloqueos dependientes del tiempo, asi que
     *  siguen recalculando la ruta completa via RouteScheduler. */
    private Optional<Double> costoSiFactible(DeliveryRoute candidataRuta, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        try {
            ScheduledDeliveryRoute programada = scheduler.schedule(candidataRuta, snapshot, bloqueos);
            for (ScheduledRouteStop parada : programada.scheduledStops()) {
                if (parada.approach().distanceKm() > DISTANCIA_MAXIMA_POR_TRAMO_KM) return Optional.empty();
                if (parada.stop() instanceof DeliveryStop entrega && parada.arrivedAt().isAfter(entrega.order().deadline())) {
                    return Optional.empty();
                }
            }
            return Optional.of(programada.totalCost());
        } catch (IllegalStateException sinCaminoFactible) {
            return Optional.empty();
        }
    }

    /** Defensivo a proposito: una ruta que ya era parte de un plan valido no
     *  deberia fallar al reprogramarse, pero si ocurriera (p.ej. un bloqueo
     *  deja sin camino a una parada en el instante exacto de transito), la
     *  busqueda local debe tratarla como "no conviene", no tumbar la
     *  corrida completa. */
    private double costoRuta(DeliveryRoute ruta, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        try {
            return scheduler.schedule(ruta, snapshot, bloqueos).totalCost();
        } catch (IllegalStateException sinCaminoFactible) {
            return Double.POSITIVE_INFINITY;
        }
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
    // Busqueda local: 2-opt, reubicacion e intercambio.
    // Estrategia de primera-mejora: cada paso busca UN movimiento que
    // mejore el costo, lo aplica y reinicia el barrido; se repite hasta que
    // ninguno de los tres produzca mejora.
    // =========================================================

    private ResultadoPlanificacion busquedaLocal(ResultadoPlanificacion actual, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        OperationalPlan plan = actual.plan();
        List<Order> pendientes = new ArrayList<>(actual.noAtendidos());
        boolean mejorado = true;
        while (mejorado) {
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

    /** Repara pedidos que la fase constructiva dejo en noAtendidos solo por
     *  el orden en que el barrido greedy los proceso (branch (a) de
     *  generarCandidatos: insertar en el tramo de entregas ya abierto de
     *  alguna ruta), no porque fueran imposibles de atender. Se ejecuta
     *  DESPUES de 2-opt/reubicacion/intercambio, sobre el plan ya
     *  reordenado, con prioridad sobre el costo -- reducir noAtendidos pesa
     *  mas que el costo (ver esMejorQue), asi que se acepta la insercion
     *  factible mas barata encontrada sin comparar contra "no insertar".
     *  A proposito NO abre WarehouseVisit nuevos (esa rama de
     *  generarCandidatos consume inventario, que aqui ya no se rastrea tras
     *  la fase constructiva) -- si el pedido solo cabe abriendo una recarga
     *  o un vehiculo nuevo, sigue quedando en noAtendidos: eso ya es
     *  colapso real, no un artefacto del greedy. */
    ResultadoInsercion pasoInsercionPendientes(OperationalPlan plan, List<Order> pendientes,
            OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        for (Order pedido : pendientes) {
            DeliveryRoute mejorRuta = null;
            double mejorCosto = Double.POSITIVE_INFINITY;
            for (DeliveryRoute ruta : plan.routes()) {
                int capacidad = snapshot.fleetProfile().parametersFor(ruta.vehicle().type()).capacity();
                int posMax = ultimaPosicionValidaParaInsertar(ruta.stops());
                // Recorre TODAS las posiciones (como pasoReubicacion), no solo
                // el ultimo tramo: una ruta puede tener varios viajes/recargas.
                for (int pos = 0; pos <= posMax; pos++) {
                    if (cargaDelSegmento(ruta.stops(), pos) + pedido.packages() > capacidad) continue;
                    // El tramo inicial (antes de cualquier WarehouseVisit) esta
                    // gobernado por initialLoad, inmutable -- igual que en
                    // pasoReubicacion, se descarta ese caso.
                    int indiceAlmacen = indiceAlmacenQueAbreSegmento(ruta.stops(), pos);
                    if (indiceAlmacen == -1) continue;
                    List<RouteStop> stopsCandidatos = new ArrayList<>(ruta.stops());
                    WarehouseVisit visitaOriginal = (WarehouseVisit) stopsCandidatos.get(indiceAlmacen);
                    stopsCandidatos.set(indiceAlmacen, new WarehouseVisit(visitaOriginal.warehouse(),
                            visitaOriginal.pickupPackages() + pedido.packages()));
                    stopsCandidatos.add(pos, new DeliveryStop(pedido, pedido.packages()));
                    DeliveryRoute candidata = ruta.withReplacedStops(stopsCandidatos);
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

    /** Inversion (2-opt): invierte un tramo dentro de un mismo segmento de
     *  entregas (entre dos WarehouseVisit consecutivos) para eliminar
     *  cruces. Retorna el plan con el primer movimiento que mejora el
     *  costo, o null si ninguno mejora. */
    private OperationalPlan pasoDosOpt(OperationalPlan plan, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        for (DeliveryRoute ruta : plan.routes()) {
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

    /** Reubicacion (Or-opt): mueve un pedido de una ruta a otra (incluso
     *  entre vehiculos distintos), respetando la capacidad del SEGMENTO de
     *  destino (entre sus dos WarehouseVisit mas cercanos), ya que una
     *  DeliveryRoute puede tener mas de un viaje. Si la ruta de origen se
     *  queda sin ninguna entrega, se ELIMINA del plan en vez de dejarla
     *  como un viaje vacio (bug corregido: antes inflaba el conteo de
     *  viajes de ValidadorUtilizacionFlota sin aportar carga real). */
    private OperationalPlan pasoReubicacion(OperationalPlan plan, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        List<DeliveryRoute> rutas = List.copyOf(plan.routes());
        for (DeliveryRoute origen : rutas) {
            for (int i : deliveryIndexes(origen.stops())) {
                DeliveryStop movido = (DeliveryStop) origen.stops().get(i);
                double costoOrigenActual = costoRuta(origen, snapshot, bloqueos);
                DeliveryRoute origenSinParada = origen.withReplacedStops(sinIndice(origen.stops(), i));
                Optional<Double> costoOrigenNuevo = costoSiFactible(origenSinParada, snapshot, bloqueos);
                if (costoOrigenNuevo.isEmpty()) continue;
                double ahorro = costoOrigenActual - costoOrigenNuevo.get();

                for (DeliveryRoute destino : rutas) {
                    if (destino.vehicle().id().equals(origen.vehicle().id())) continue;
                    int capacidad = snapshot.fleetProfile().parametersFor(destino.vehicle().type()).capacity();
                    // Nunca insertar despues del ultimo WarehouseVisit si ese
                    // es el regreso final al almacen -- una ruta terminada
                    // siempre debe terminar en un WarehouseVisit (exigido por
                    // OperationalPlanEvaluator), no en un DeliveryStop.
                    int posMax = ultimaPosicionValidaParaInsertar(destino.stops());
                    for (int pos = 0; pos <= posMax; pos++) {
                        if (cargaDelSegmento(destino.stops(), pos) + movido.deliveredPackages() > capacidad) continue;
                        // El WarehouseVisit que abre este tramo debe recoger
                        // tambien lo que ahora se entrega en el; el tramo
                        // inicial (antes de cualquier WarehouseVisit) no se
                        // puede ajustar porque initialLoad es inmutable una
                        // vez creada la ruta -- ese caso simplemente se salta.
                        int indiceAlmacen = indiceAlmacenQueAbreSegmento(destino.stops(), pos);
                        if (indiceAlmacen == -1) continue;
                        List<RouteStop> stopsDestino = new ArrayList<>(destino.stops());
                        WarehouseVisit visitaOriginal = (WarehouseVisit) stopsDestino.get(indiceAlmacen);
                        stopsDestino.set(indiceAlmacen, new WarehouseVisit(visitaOriginal.warehouse(),
                                visitaOriginal.pickupPackages() + movido.deliveredPackages()));
                        stopsDestino.add(pos, movido);
                        DeliveryRoute destinoNuevo = destino.withReplacedStops(stopsDestino);
                        Optional<Double> costoDestinoNuevo = costoSiFactible(destinoNuevo, snapshot, bloqueos);
                        if (costoDestinoNuevo.isEmpty()) continue;
                        double costoIncremental = costoDestinoNuevo.get() - costoRuta(destino, snapshot, bloqueos);
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

    /** Intercambio (Swap): intercambia dos pedidos entre dos rutas distintas. */
    private OperationalPlan pasoIntercambio(OperationalPlan plan, OperationalSnapshot snapshot, List<RoadBlock> bloqueos) {
        List<DeliveryRoute> rutas = List.copyOf(plan.routes());
        for (int a = 0; a < rutas.size(); a++) {
            DeliveryRoute rutaA = rutas.get(a);
            for (int b = a + 1; b < rutas.size(); b++) {
                DeliveryRoute rutaB = rutas.get(b);
                int capA = snapshot.fleetProfile().parametersFor(rutaA.vehicle().type()).capacity();
                int capB = snapshot.fleetProfile().parametersFor(rutaB.vehicle().type()).capacity();

                for (int i : deliveryIndexes(rutaA.stops())) {
                    DeliveryStop pA = (DeliveryStop) rutaA.stops().get(i);
                    for (int j : deliveryIndexes(rutaB.stops())) {
                        DeliveryStop pB = (DeliveryStop) rutaB.stops().get(j);
                        int nuevaCargaSegA = cargaDelSegmento(rutaA.stops(), i) - pA.deliveredPackages() + pB.deliveredPackages();
                        int nuevaCargaSegB = cargaDelSegmento(rutaB.stops(), j) - pB.deliveredPackages() + pA.deliveredPackages();
                        if (nuevaCargaSegA > capA || nuevaCargaSegB > capB) continue;

                        // Si las cantidades intercambiadas difieren, el
                        // WarehouseVisit que abre cada tramo debe recoger la
                        // diferencia; si el tramo es el inicial (gobernado
                        // por initialLoad, inmutable), no hay forma de
                        // ajustarlo y el intercambio se descarta.
                        int delta = pB.deliveredPackages() - pA.deliveredPackages();
                        List<RouteStop> stopsA = new ArrayList<>(rutaA.stops());
                        List<RouteStop> stopsB = new ArrayList<>(rutaB.stops());
                        if (delta != 0) {
                            int almacenA = indiceAlmacenQueAbreSegmento(rutaA.stops(), i);
                            int almacenB = indiceAlmacenQueAbreSegmento(rutaB.stops(), j);
                            if (almacenA == -1 || almacenB == -1) continue;
                            WarehouseVisit visitaA = (WarehouseVisit) stopsA.get(almacenA);
                            WarehouseVisit visitaB = (WarehouseVisit) stopsB.get(almacenB);
                            if (visitaA.pickupPackages() + delta < 0 || visitaB.pickupPackages() - delta < 0) continue;
                            stopsA.set(almacenA, new WarehouseVisit(visitaA.warehouse(), visitaA.pickupPackages() + delta));
                            stopsB.set(almacenB, new WarehouseVisit(visitaB.warehouse(), visitaB.pickupPackages() - delta));
                        }
                        stopsA.set(i, new DeliveryStop(pB.order(), pB.deliveredPackages()));
                        stopsB.set(j, new DeliveryStop(pA.order(), pA.deliveredPackages()));
                        DeliveryRoute rutaANueva = rutaA.withReplacedStops(stopsA);
                        DeliveryRoute rutaBNueva = rutaB.withReplacedStops(stopsB);

                        Optional<Double> costoANuevo = costoSiFactible(rutaANueva, snapshot, bloqueos);
                        Optional<Double> costoBNuevo = costoSiFactible(rutaBNueva, snapshot, bloqueos);
                        if (costoANuevo.isEmpty() || costoBNuevo.isEmpty()) continue;

                        double costoAntes = costoRuta(rutaA, snapshot, bloqueos) + costoRuta(rutaB, snapshot, bloqueos);
                        double costoDespues = costoANuevo.get() + costoBNuevo.get();
                        if (costoDespues < costoAntes) {
                            return plan.withRoute(rutaANueva).withRoute(rutaBNueva);
                        }
                    }
                }
            }
        }
        return null;
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

    private List<RouteStop> sinIndice(List<RouteStop> stops, int indice) {
        List<RouteStop> copia = new ArrayList<>(stops);
        copia.remove(indice);
        return copia;
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

    // =========================================================
    // Estado auxiliar de construccion
    // =========================================================

    private static final class EstadoConstruccion {
        final VehicleOperationalState estadoInicial;
        DeliveryRoute ruta;
        int cargaActual;

        EstadoConstruccion(VehicleOperationalState estadoInicial) {
            this.estadoInicial = estadoInicial;
            this.ruta = null;
            this.cargaActual = 0;
        }

        Location ubicacionActual() {
            return ruta == null ? estadoInicial.location() : ruta.stops().getLast().location();
        }

        void aplicar(Candidato c) {
            this.cargaActual = c.esRutaNueva() ? c.pedido().packages() : this.cargaActual + c.pedido().packages();
            this.ruta = c.rutaResultante();
        }
    }

    private record Candidato(String vehiculoId, DeliveryRoute rutaResultante, boolean esRutaNueva,
                              Order pedido, Warehouse almacenReabastecimiento, int cargaReabastecida, double costo) {
    }
}
