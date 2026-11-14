package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.config.RescatePreventivoProperties;
import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.WarehouseSelectionPolicy;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.TransferDirection;
import com.pucp.paqrap.modulos.planificacion.entity.TransferStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Revisa entregas ya comprometidas cuya ETA se deterioró y busca una
 * reasignación físicamente ejecutable antes de que venza el pedido.
 *
 * <p>Primero intenta mover pedidos que todavía no fueron cargados. Solo si la
 * carga ya está a bordo programa un encuentro sincronizado entre dos rutas
 * activas. El servicio no ejecuta una búsqueda aleatoria: actúa como capa
 * determinista de protección de SLA encima de ALNS.</p>
 */
final class RescatePreventivoPedidosService {

    private final OperationalPlanEvaluator evaluador;
    private final RouteScheduler scheduler;
    private final ContinuacionRutaService continuaciones;
    private final CheckpointRutaService checkpoints = new CheckpointRutaService();
    private final RescatePreventivoProperties configuracion;
    private final WarehouseSelectionPolicy warehouseSelection = new WarehouseSelectionPolicy();

    RescatePreventivoPedidosService(OperationalPlanEvaluator evaluador, RouteScheduler scheduler) {
        this(evaluador, scheduler, RescatePreventivoProperties.defaults());
    }

    RescatePreventivoPedidosService(
            OperationalPlanEvaluator evaluador,
            RouteScheduler scheduler,
            RescatePreventivoProperties configuracion
    ) {
        this.evaluador = Objects.requireNonNull(evaluador, "evaluador es requerido");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler es requerido");
        this.configuracion = Objects.requireNonNull(configuracion, "configuracion es requerida");
        this.continuaciones = new ContinuacionRutaService(scheduler);
    }

    /**
     * Compatibilidad con pruebas y llamadas antiguas.
     *
     * Para rescatar con vehículos libres se necesita un snapshot actual. Si no
     * se proporciona explícitamente, se deriva del snapshot de la primera ruta.
     */
    Resultado rescatar(
            Instant ahora,
            List<RutaVigente> rutas,
            Warehouse central,
            InventorySnapshot inventarioDisponible,
            List<RoadBlock> bloqueos,
            Map<String, Integer> reasignacionesPorPedido,
            Map<String, Instant> cooldownHasta,
            long deadlineNanos
    ) {
        OperationalSnapshot snapshotActual = rutas.isEmpty()
                ? null
                : snapshotEn(rutas.getFirst().snapshotOrigen(), ahora);
        return rescatar(
                ahora, rutas, central, inventarioDisponible, snapshotActual, bloqueos,
                reasignacionesPorPedido, cooldownHasta, deadlineNanos);
    }

    /**
     * Revisa rutas en riesgo y permite rescatar con:
     *
     * 1. otra ruta activa;
     * 2. un vehículo libre que no tenga ruta vigente;
     * 3. un transbordo hacia otra ruta activa;
     * 4. un transbordo hacia un vehículo libre.
     *
     * El snapshot debe representar el estado operativo ACTUAL de toda la flota.
     */
    Resultado rescatar(
            Instant ahora,
            List<RutaVigente> rutas,
            Warehouse central,
            InventorySnapshot inventarioDisponible,
            OperationalSnapshot snapshotActual,
            List<RoadBlock> bloqueos,
            Map<String, Integer> reasignacionesPorPedido,
            Map<String, Instant> cooldownHasta,
            long deadlineNanos
    ) {
        Objects.requireNonNull(ahora, "ahora es requerido");
        Objects.requireNonNull(rutas, "rutas son requeridas");
        Objects.requireNonNull(central, "central es requerido");
        Objects.requireNonNull(inventarioDisponible, "inventario es requerido");
        Objects.requireNonNull(bloqueos, "bloqueos son requeridos");

        if (!configuracion.enabled()) {
            return new Resultado(List.copyOf(rutas), 0, 0, Set.of());
        }

        List<RutaVigente> actuales = new ArrayList<>(rutas);
        List<CandidatoRiesgo> riesgos = detectarRiesgos(
                ahora, actuales, reasignacionesPorPedido, cooldownHasta);
        int rescates = 0;
        int transbordos = 0;
        Set<String> pedidosRescatados = new LinkedHashSet<>();

        for (CandidatoRiesgo riesgo : riesgos) {
            if (rescates >= configuracion.maxRescatesPorCiclo()
                    || System.nanoTime() >= deadlineNanos) {
                break;
            }

            Intento intento = intentarSinCarga(
                    ahora, actuales, riesgo, central, inventarioDisponible,
                    snapshotActual, bloqueos, deadlineNanos);

            if (intento == null && configuracion.transbordosEnabled()) {
                intento = intentarConTransbordo(
                        ahora, actuales, riesgo, snapshotActual, bloqueos, deadlineNanos);
            }

            if (intento == null) {
                continue;
            }

            actuales = new ArrayList<>(intento.rutas());
            reasignacionesPorPedido.merge(riesgo.pedido().id(), 1, Integer::sum);
            cooldownHasta.put(
                    riesgo.pedido().id(),
                    ahora.plus(configuracion.cooldownReasignacion()));
            pedidosRescatados.add(riesgo.pedido().id());
            rescates++;
            if (intento.transbordo()) {
                transbordos++;
            }
        }

        return new Resultado(
                List.copyOf(actuales), rescates, transbordos, Set.copyOf(pedidosRescatados));
    }

    private List<CandidatoRiesgo> detectarRiesgos(
            Instant ahora,
            List<RutaVigente> rutas,
            Map<String, Integer> reasignacionesPorPedido,
            Map<String, Instant> cooldownHasta
    ) {
        List<CandidatoRiesgo> riesgos = new ArrayList<>();

        for (RutaVigente ruta : rutas) {
            if (ruta.programada() == null) {
                continue;
            }
            CheckpointRuta checkpoint;
            try {
                checkpoint = checkpoints.crear(ruta, ahora);
            } catch (RuntimeException ignored) {
                continue;
            }

            List<ScheduledRouteStop> programadas = ruta.programada().scheduledStops();
            for (int i = checkpoint.primeraParadaFutura(); i < programadas.size(); i++) {
                ScheduledRouteStop parada = programadas.get(i);
                if (!(parada.stop() instanceof DeliveryStop entrega)
                        || !parada.arrivedAt().isAfter(ahora)) {
                    continue;
                }
                Order pedido = entrega.order();
                if (reasignacionesPorPedido.getOrDefault(pedido.id(), 0) >= configuracion.maxReasignacionesPorPedido()) {
                    continue;
                }
                Instant bloqueadoHasta = cooldownHasta.get(pedido.id());
                if (bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora)) {
                    continue;
                }
                Duration holgura = Duration.between(parada.arrivedAt(), pedido.deadline());
                if (holgura.compareTo(configuracion.ventanaRiesgo()) <= 0) {
                    riesgos.add(new CandidatoRiesgo(
                            ruta.vehiculoId(), i, pedido, entrega.deliveredPackages(),
                            parada.arrivedAt(), holgura));
                }
            }
        }

        riesgos.sort(Comparator
                .comparing(CandidatoRiesgo::holgura)
                .thenComparing(CandidatoRiesgo::etaActual)
                .thenComparing(c -> c.pedido().id()));
        return riesgos;
    }

    /**
     * Pedido todavía no cargado: elimina su pareja recarga+entrega del donante
     * y fuerza su inserción en otra ruta activa.
     */
    private Intento intentarSinCarga(
            Instant ahora,
            List<RutaVigente> rutas,
            CandidatoRiesgo riesgo,
            Warehouse central,
            InventorySnapshot inventario,
            OperationalSnapshot snapshotActual,
            List<RoadBlock> bloqueos,
            long deadlineNanos
    ) {
        int indiceDonante = indiceVehiculo(rutas, riesgo.vehiculoId());
        if (indiceDonante < 0) {
            return null;
        }

        RutaVigente donante = rutas.get(indiceDonante);
        CheckpointRuta checkpoint;
        try {
            checkpoint = checkpoints.crear(donante, ahora);
        } catch (RuntimeException e) {
            return null;
        }

        int entregaIndex = riesgo.indiceParada();
        if (entregaIndex <= checkpoint.primeraParadaFutura()) {
            return null;
        }

        List<RouteStop> stops = donante.ruta().stops();
        RouteStop anterior = stops.get(entregaIndex - 1);
        if (!(anterior instanceof WarehouseVisit visita)
                || visita.pickupPackages() != riesgo.cantidad()) {
            return null;
        }

        /*
         * El pedido todavía no fue cargado: eliminamos del donante exactamente
         * la pareja recarga + entrega que lo comprometía.
         */
        List<RouteStop> futuro = new ArrayList<>(
                stops.subList(checkpoint.primeraParadaFutura(), stops.size()));
        int relativoEntrega = entregaIndex - checkpoint.primeraParadaFutura();
        int relativoPickup = relativoEntrega - 1;
        futuro.remove(relativoEntrega);
        futuro.remove(relativoPickup);

        RutaVigente donanteModificado;
        try {
            OperationalSnapshot snapshotDonante = snapshotActual != null
                    ? snapshotActual
                    : snapshotEn(donante.snapshotOrigen(), ahora);
            ContinuacionRutaService.Resultado reprogramada = continuaciones.reprogramar(
                    donante, ahora, futuro, snapshotDonante, bloqueos);
            donanteModificado = new RutaVigente(
                    reprogramada.ruta(), snapshotDonante, reprogramada.programada(), List.of());
        } catch (RuntimeException sinAlternativa) {
            return null;
        }

        if (!rutaOperativa(donanteModificado)) {
            return null;
        }

        Order cantidadPendiente = riesgo.cantidad() == riesgo.pedido().packages()
                ? riesgo.pedido()
                : new Order(
                        riesgo.pedido().id(),
                        riesgo.pedido().destination(),
                        riesgo.cantidad(),
                        riesgo.pedido().registeredAt(),
                        riesgo.pedido().deadline());

        Intento mejor = null;

        /*
         * 1. Intento tradicional: otra ruta ya activa.
         */
        List<RutaVigente> receptoras = new ArrayList<>();
        for (int i = 0; i < rutas.size(); i++) {
            if (i != indiceDonante) {
                receptoras.add(rutas.get(i));
            }
        }

        if (!receptoras.isEmpty() && System.nanoTime() < deadlineNanos) {
            InsercionRutasActivasAlns.Resultado insercion =
                    new InsercionRutasActivasAlns(evaluador)
                            .insertar(
                                    ahora,
                                    receptoras,
                                    List.of(cantidadPendiente),
                                    central,
                                    inventario,
                                    bloqueos,
                                    deadlineNanos);

            if (insercion.pendientes().isEmpty()) {
                List<RutaVigente> combinadas = reemplazarConjunto(
                        rutas, donanteModificado, insercion.rutas(), riesgo.vehiculoId());
                Instant nuevaEta = etaPedidoEnOtraRuta(
                        combinadas, riesgo.pedido().id(), riesgo.vehiculoId());
                if (mejoraSuficiente(riesgo, nuevaEta)
                        && cambioSeguro(rutas, combinadas)) {
                    mejor = new Intento(List.copyOf(combinadas), nuevaEta, false);
                }
            }
        }

        /*
         * 2. Nuevo: vehículos realmente AVAILABLE sin ruta vigente.
         *
         * Si ningún vehículo puede llevar toda la cantidad, se permite repartir
         * el rescate entre varias unidades. Esto respeta la capacidad del caso
         * PaqRap, donde las entregas parciales son válidas.
         */
        if (snapshotActual != null && System.nanoTime() < deadlineNanos) {
            Intento libres = intentarConVehiculosLibresSinCarga(
                    ahora,
                    rutas,
                    donanteModificado,
                    riesgo,
                    inventario,
                    snapshotActual,
                    bloqueos,
                    deadlineNanos);

            if (libres != null
                    && (mejor == null || libres.nuevaEta().isBefore(mejor.nuevaEta()))) {
                mejor = libres;
            }
        }

        return mejor;
    }

    /**
     * Crea una o varias rutas nuevas usando vehículos libres.
     *
     * Un pedido de 10 paquetes, por ejemplo, puede ser rescatado por un auto de
     * capacidad 24 o, si no existe uno factible, por varias unidades mediante
     * entregas parciales siempre que TODAS las partes lleguen dentro del plazo.
     */
    private Intento intentarConVehiculosLibresSinCarga(
            Instant ahora,
            List<RutaVigente> rutasOriginales,
            RutaVigente donanteModificado,
            CandidatoRiesgo riesgo,
            InventorySnapshot inventarioInicial,
            OperationalSnapshot snapshotActual,
            List<RoadBlock> bloqueos,
            long deadlineNanos
    ) {
        Set<String> vehiculosConRuta = new LinkedHashSet<>();
        for (RutaVigente ruta : rutasOriginales) {
            vehiculosConRuta.add(ruta.vehiculoId());
        }

        List<VehicleOperationalState> libres = snapshotActual
                .vehiclesById()
                .values()
                .stream()
                .filter(estado -> estado.status() == VehicleStatus.AVAILABLE)
                .filter(estado -> !estado.availableAt().isAfter(ahora))
                .filter(estado -> !vehiculosConRuta.contains(estado.vehicle().id()))
                .filter(estado -> snapshotActual.isVehiclePlannableAt(estado.vehicle().id(), ahora))
                .sorted(Comparator
                        .comparingDouble((VehicleOperationalState estado) ->
                                estado.location().manhattanDistanceTo(riesgo.pedido().destination()))
                        .thenComparingInt(estado -> -snapshotActual.fleetProfile()
                                .parametersFor(estado.vehicle().type()).capacity())
                        .thenComparing(estado -> estado.vehicle().id()))
                .toList();

        if (libres.isEmpty()) {
            return null;
        }

        int restante = riesgo.cantidad();
        InventorySnapshot inventarioTrabajo = inventarioInicial;
        List<RutaVigente> nuevas = new ArrayList<>();
        OperationalPlan planRescate = OperationalPlan.empty();
        Instant etaCompleta = null;

        for (VehicleOperationalState estado : libres) {
            if (restante <= 0 || System.nanoTime() >= deadlineNanos) {
                break;
            }

            int capacidad = snapshotActual.fleetProfile()
                    .parametersFor(estado.vehicle().type())
                    .capacity();
            int cantidad = Math.min(restante, capacidad);

            RutaLibreCandidata candidata = construirRutaLibre(
                    ahora,
                    estado,
                    riesgo.pedido(),
                    cantidad,
                    inventarioTrabajo,
                    snapshotActual,
                    planRescate,
                    bloqueos,
                    deadlineNanos);

            if (candidata == null) {
                continue;
            }

            nuevas.add(candidata.ruta());
            planRescate = planRescate.withRoute(candidata.ruta().ruta());
            inventarioTrabajo = candidata.inventarioRestante();
            restante -= cantidad;

            if (etaCompleta == null || candidata.eta().isAfter(etaCompleta)) {
                etaCompleta = candidata.eta();
            }
        }

        if (restante > 0 || etaCompleta == null) {
            return null;
        }

        List<RutaVigente> combinadas = new ArrayList<>();
        for (RutaVigente ruta : rutasOriginales) {
            combinadas.add(
                    ruta.vehiculoId().equals(riesgo.vehiculoId())
                            ? donanteModificado
                            : ruta);
        }
        combinadas.addAll(nuevas);

        if (!mejoraSuficiente(riesgo, etaCompleta)
                || !cambioSeguro(rutasOriginales, combinadas)) {
            return null;
        }

        return new Intento(List.copyOf(combinadas), etaCompleta, false);
    }

    private RutaLibreCandidata construirRutaLibre(
            Instant ahora,
            VehicleOperationalState estado,
            Order pedido,
            int cantidad,
            InventorySnapshot inventario,
            OperationalSnapshot snapshotActual,
            OperationalPlan planRescate,
            List<RoadBlock> bloqueos,
            long deadlineNanos
    ) {
        if (cantidad <= 0 || System.nanoTime() >= deadlineNanos) {
            return null;
        }

        int capacidad = snapshotActual.fleetProfile()
                .parametersFor(estado.vehicle().type())
                .capacity();
        if (cantidad > capacidad) {
            return null;
        }

        /*
         * PaqRap transporta un único producto P. Una carga residual de un
         * vehículo libre puede reutilizarse para cualquier pedido.
         */
        int cargaInicialUtil = Math.min(estado.carriedPackages(), cantidad);
        int pickupNecesario = cantidad - cargaInicialUtil;

        List<Warehouse> fuentes = new ArrayList<>();
        if (pickupNecesario == 0) {
            fuentes.add(null);
        } else {
            fuentes.addAll(warehouseSelection.rankedSupplyWarehouses(
                    estado.location(), pedido, pickupNecesario, inventario));
        }

        for (Warehouse fuente : fuentes) {
            if (System.nanoTime() >= deadlineNanos) {
                return null;
            }

            try {
                String idRuta = "RESCATE-LIBRE-"
                        + estado.vehicle().id()
                        + "-" + pedido.id()
                        + "-" + ahora.toEpochMilli();

                DeliveryRoute ruta = DeliveryRoute.replanFromCurrentLocation(
                        idRuta,
                        estado.vehicle(),
                        estado.location(),
                        estado.carriedPackages(),
                        ahora);

                if (fuente != null) {
                    ruta = ruta.withAppendedStop(
                            new WarehouseVisit(fuente, pickupNecesario));
                }

                ruta = ruta.withAppendedStop(new DeliveryStop(pedido, cantidad));

                Warehouse terminal = warehouseSelection.bestTerminalWarehouse(
                        pedido.destination(),
                        snapshotActual,
                        planRescate,
                        pedido,
                        estado.vehicle().id());
                ruta = ruta.returningTo(terminal);

                ScheduledDeliveryRoute programada = scheduler.schedule(
                        ruta, snapshotActual, bloqueos);
                RutaVigente vigente = new RutaVigente(
                        ruta, snapshotActual, programada, List.of());

                Instant eta = etaEnRuta(vigente, pedido.id());
                if (eta == null || eta.isAfter(pedido.deadline()) || !rutaOperativa(vigente)) {
                    continue;
                }

                InventorySnapshot restante = inventario;
                if (fuente != null && !fuente.isCentral()) {
                    restante = inventario.withdraw(fuente.id(), pickupNecesario);
                }

                return new RutaLibreCandidata(vigente, eta, restante);
            } catch (RuntimeException noFactible) {
                // Probar siguiente almacén o siguiente vehículo.
            }
        }

        return null;
    }

    /** Pedido ya cargado: programa un encuentro y mueve físicamente su carga. */
    private Intento intentarConTransbordo(
            Instant ahora,
            List<RutaVigente> rutas,
            CandidatoRiesgo riesgo,
            OperationalSnapshot snapshotActual,
            List<RoadBlock> bloqueos,
            long deadlineNanos
    ) {
        int indiceDonante = indiceVehiculo(rutas, riesgo.vehiculoId());
        if (indiceDonante < 0) {
            return null;
        }
        RutaVigente donante = rutas.get(indiceDonante);
        CheckpointRuta checkpointDonante;
        try {
            checkpointDonante = checkpoints.crear(donante, ahora);
        } catch (RuntimeException e) {
            return null;
        }
        if (contieneTransbordo(donante)
                || checkpointDonante.carga() < riesgo.cantidad()
                || riesgo.indiceParada() < checkpointDonante.primeraParadaFutura()
                || !cargaEspecificaDisponible(donante, checkpointDonante, riesgo)) {
            return null;
        }

        Intento mejor = null;

        /* 1. Receptores que ya tienen una ruta activa. */
        for (int i = 0; i < rutas.size() && System.nanoTime() < deadlineNanos; i++) {
            if (i == indiceDonante) {
                continue;
            }
            RutaVigente receptora = rutas.get(i);
            if (receptora.programada() == null || contieneTransbordo(receptora)) {
                continue;
            }
            CheckpointRuta checkpointReceptor;
            try {
                checkpointReceptor = checkpoints.crear(receptora, ahora);
            } catch (RuntimeException e) {
                continue;
            }
            int capacidad = receptora.snapshotOrigen().fleetProfile()
                    .parametersFor(receptora.ruta().vehicle().type()).capacity();
            if (checkpointReceptor.carga() + riesgo.cantidad() > capacidad) {
                continue;
            }

            List<Location> nodos = List.of(
                    checkpointDonante.nodoSeguro(),
                    checkpointReceptor.nodoSeguro());
            for (Location nodo : nodos) {
                Intento candidato = construirTransbordo(
                        ahora, rutas, indiceDonante, i, riesgo, nodo,
                        checkpointDonante, checkpointReceptor, bloqueos);
                if (candidato != null
                        && (mejor == null || candidato.nuevaEta().isBefore(mejor.nuevaEta()))) {
                    mejor = candidato;
                }
            }
        }

        /*
         * 2. Nuevo: un vehículo AVAILABLE también puede recibir físicamente
         * la carga aunque no tuviera una ruta activa antes del rescate.
         */
        if (snapshotActual != null && System.nanoTime() < deadlineNanos) {
            Intento libre = intentarTransbordoConVehiculoLibre(
                    ahora,
                    rutas,
                    indiceDonante,
                    riesgo,
                    checkpointDonante,
                    snapshotActual,
                    bloqueos,
                    deadlineNanos);
            if (libre != null
                    && (mejor == null || libre.nuevaEta().isBefore(mejor.nuevaEta()))) {
                mejor = libre;
            }
        }

        return mejor;
    }

    private Intento intentarTransbordoConVehiculoLibre(
            Instant ahora,
            List<RutaVigente> rutas,
            int indiceDonante,
            CandidatoRiesgo riesgo,
            CheckpointRuta checkpointDonante,
            OperationalSnapshot snapshotActual,
            List<RoadBlock> bloqueos,
            long deadlineNanos
    ) {
        Set<String> ocupados = new LinkedHashSet<>();
        for (RutaVigente ruta : rutas) {
            ocupados.add(ruta.vehiculoId());
        }

        List<VehicleOperationalState> libres = snapshotActual
                .vehiclesById()
                .values()
                .stream()
                .filter(estado -> estado.status() == VehicleStatus.AVAILABLE)
                .filter(estado -> !estado.availableAt().isAfter(ahora))
                .filter(estado -> !ocupados.contains(estado.vehicle().id()))
                .filter(estado -> snapshotActual.isVehiclePlannableAt(estado.vehicle().id(), ahora))
                .filter(estado -> {
                    int capacidad = snapshotActual.fleetProfile()
                            .parametersFor(estado.vehicle().type()).capacity();
                    return estado.carriedPackages() + riesgo.cantidad() <= capacidad;
                })
                .sorted(Comparator
                        .comparingDouble((VehicleOperationalState estado) ->
                                estado.location().manhattanDistanceTo(checkpointDonante.nodoSeguro()))
                        .thenComparing(estado -> estado.vehicle().id()))
                .toList();

        Intento mejor = null;
        for (VehicleOperationalState libre : libres) {
            if (System.nanoTime() >= deadlineNanos) {
                break;
            }
            Intento candidato = construirTransbordoConLibre(
                    ahora,
                    rutas,
                    indiceDonante,
                    riesgo,
                    checkpointDonante,
                    libre,
                    snapshotActual,
                    bloqueos);
            if (candidato != null
                    && (mejor == null || candidato.nuevaEta().isBefore(mejor.nuevaEta()))) {
                mejor = candidato;
            }
        }
        return mejor;
    }

    private Intento construirTransbordoConLibre(
            Instant ahora,
            List<RutaVigente> rutas,
            int indiceDonante,
            CandidatoRiesgo riesgo,
            CheckpointRuta checkpointDonante,
            VehicleOperationalState libre,
            OperationalSnapshot snapshotActual,
            List<RoadBlock> bloqueos
    ) {
        RutaVigente donante = rutas.get(indiceDonante);

        List<RouteStop> futuroDonante = new ArrayList<>(donante.ruta().stops()
                .subList(checkpointDonante.primeraParadaFutura(), donante.ruta().stops().size()));
        int relativo = riesgo.indiceParada() - checkpointDonante.primeraParadaFutura();
        if (relativo < 0 || relativo >= futuroDonante.size()
                || !(futuroDonante.get(relativo) instanceof DeliveryStop)) {
            return null;
        }
        futuroDonante.remove(relativo);

        /*
         * Probamos dos nodos simples y auditables: posición segura del donante
         * y posición actual del receptor libre.
         */
        List<Location> nodos = List.of(
                checkpointDonante.nodoSeguro(),
                libre.location());

        Intento mejor = null;
        for (Location nodo : nodos) {
            String transferId = "TR-" + riesgo.pedido().id() + "-" + ahora.toEpochMilli()
                    + "-" + donante.vehiculoId() + "-" + libre.vehicle().id();

            TransferStop provisionalDonante = new TransferStop(
                    transferId, riesgo.pedido(), riesgo.cantidad(), libre.vehicle().id(),
                    TransferDirection.DONATE, nodo, ahora);
            TransferStop provisionalReceptor = new TransferStop(
                    transferId, riesgo.pedido(), riesgo.cantidad(), donante.vehiculoId(),
                    TransferDirection.RECEIVE, nodo, ahora);

            Warehouse terminal = warehouseSelection.bestTerminalWarehouse(
                    riesgo.pedido().destination(),
                    snapshotActual,
                    OperationalPlan.empty(),
                    riesgo.pedido(),
                    libre.vehicle().id());

            try {
                ContinuacionRutaService.Resultado preDonante = continuaciones.reprogramar(
                        donante,
                        ahora,
                        prepend(provisionalDonante, futuroDonante),
                        snapshotActual,
                        bloqueos);

                DeliveryRoute preRutaReceptor = DeliveryRoute.replanFromCurrentLocation(
                                "RESCATE-TR-" + libre.vehicle().id() + "-" + riesgo.pedido().id(),
                                libre.vehicle(),
                                libre.location(),
                                libre.carriedPackages(),
                                ahora)
                        .withAppendedStop(provisionalReceptor)
                        .withAppendedStop(new DeliveryStop(riesgo.pedido(), riesgo.cantidad()))
                        .returningTo(terminal);
                ScheduledDeliveryRoute preReceptor = scheduler.schedule(
                        preRutaReceptor, snapshotActual, bloqueos);

                ScheduledRouteStop paradaDonante = buscarTransfer(preDonante.programada(), transferId);
                ScheduledRouteStop paradaReceptor = buscarTransfer(preReceptor, transferId);
                if (paradaDonante == null || paradaReceptor == null) {
                    continue;
                }

                Instant encuentro = paradaDonante.arrivedAt().isAfter(paradaReceptor.arrivedAt())
                        ? paradaDonante.arrivedAt()
                        : paradaReceptor.arrivedAt();
                Duration esperaDonante = Duration.between(paradaDonante.arrivedAt(), encuentro);
                Duration esperaReceptor = Duration.between(paradaReceptor.arrivedAt(), encuentro);
                if (esperaDonante.compareTo(configuracion.esperaMaximaEncuentro()) > 0
                        || esperaReceptor.compareTo(configuracion.esperaMaximaEncuentro()) > 0) {
                    continue;
                }

                TransferStop finalDonante = new TransferStop(
                        transferId, riesgo.pedido(), riesgo.cantidad(), libre.vehicle().id(),
                        TransferDirection.DONATE, nodo, encuentro);
                TransferStop finalReceptor = new TransferStop(
                        transferId, riesgo.pedido(), riesgo.cantidad(), donante.vehiculoId(),
                        TransferDirection.RECEIVE, nodo, encuentro);

                ContinuacionRutaService.Resultado resultadoDonante = continuaciones.reprogramar(
                        donante,
                        ahora,
                        prepend(finalDonante, futuroDonante),
                        snapshotActual,
                        bloqueos);

                DeliveryRoute rutaReceptor = DeliveryRoute.replanFromCurrentLocation(
                                "RESCATE-TR-" + libre.vehicle().id() + "-" + riesgo.pedido().id(),
                                libre.vehicle(),
                                libre.location(),
                                libre.carriedPackages(),
                                ahora)
                        .withAppendedStop(finalReceptor)
                        .withAppendedStop(new DeliveryStop(riesgo.pedido(), riesgo.cantidad()))
                        .returningTo(terminal);
                ScheduledDeliveryRoute programadaReceptor = scheduler.schedule(
                        rutaReceptor, snapshotActual, bloqueos);

                RutaVigente nuevoDonante = new RutaVigente(
                        resultadoDonante.ruta(), snapshotActual, resultadoDonante.programada(), List.of());
                RutaVigente nuevoReceptor = new RutaVigente(
                        rutaReceptor, snapshotActual, programadaReceptor, List.of());

                ScheduledRouteStop syncDonante = buscarTransfer(resultadoDonante.programada(), transferId);
                ScheduledRouteStop syncReceptor = buscarTransfer(programadaReceptor, transferId);
                if (syncDonante == null || syncReceptor == null
                        || !syncDonante.serviceStartedAt().equals(encuentro)
                        || !syncReceptor.serviceStartedAt().equals(encuentro)
                        || syncDonante.arrivedAt().isAfter(encuentro)
                        || syncReceptor.arrivedAt().isAfter(encuentro)
                        || !rutaOperativa(nuevoDonante)
                        || !rutaOperativa(nuevoReceptor)) {
                    continue;
                }

                List<RutaVigente> resultado = new ArrayList<>();
                for (RutaVigente ruta : rutas) {
                    resultado.add(ruta.vehiculoId().equals(donante.vehiculoId())
                            ? nuevoDonante
                            : ruta);
                }
                resultado.add(nuevoReceptor);

                Instant nuevaEta = etaEnRuta(nuevoReceptor, riesgo.pedido().id());
                if (!mejoraSuficiente(riesgo, nuevaEta)
                        || !cambioSeguro(rutas, resultado)) {
                    continue;
                }

                Intento candidato = new Intento(List.copyOf(resultado), nuevaEta, true);
                if (mejor == null || candidato.nuevaEta().isBefore(mejor.nuevaEta())) {
                    mejor = candidato;
                }
            } catch (RuntimeException noFactible) {
                // Probar otro nodo / otro vehículo libre.
            }
        }

        return mejor;
    }

    private Intento construirTransbordo(
            Instant ahora,
            List<RutaVigente> rutas,
            int indiceDonante,
            int indiceReceptor,
            CandidatoRiesgo riesgo,
            Location nodo,
            CheckpointRuta checkpointDonante,
            CheckpointRuta checkpointReceptor,
            List<RoadBlock> bloqueos
    ) {
        RutaVigente donante = rutas.get(indiceDonante);
        RutaVigente receptora = rutas.get(indiceReceptor);

        List<RouteStop> futuroDonante = new ArrayList<>(donante.ruta().stops()
                .subList(checkpointDonante.primeraParadaFutura(), donante.ruta().stops().size()));
        int relativo = riesgo.indiceParada() - checkpointDonante.primeraParadaFutura();
        if (relativo < 0 || relativo >= futuroDonante.size()
                || !(futuroDonante.get(relativo) instanceof DeliveryStop)) {
            return null;
        }
        futuroDonante.remove(relativo);

        List<RouteStop> futuroReceptor = new ArrayList<>(receptora.ruta().stops()
                .subList(checkpointReceptor.primeraParadaFutura(), receptora.ruta().stops().size()));

        String transferId = "TR-" + riesgo.pedido().id() + "-" + ahora.toEpochMilli()
                + "-" + donante.vehiculoId() + "-" + receptora.vehiculoId();

        // Primera pasada: obtener tiempos de llegada al nodo.
        TransferStop provisionalDonante = new TransferStop(
                transferId, riesgo.pedido(), riesgo.cantidad(), receptora.vehiculoId(),
                TransferDirection.DONATE, nodo, ahora);
        TransferStop provisionalReceptor = new TransferStop(
                transferId, riesgo.pedido(), riesgo.cantidad(), donante.vehiculoId(),
                TransferDirection.RECEIVE, nodo, ahora);

        ContinuacionRutaService.Resultado preDonante;
        ContinuacionRutaService.Resultado preReceptor;
        try {
            preDonante = continuaciones.reprogramar(
                    donante, ahora, prepend(provisionalDonante, futuroDonante),
                    snapshotEn(donante.snapshotOrigen(), ahora), bloqueos);
            preReceptor = continuaciones.reprogramar(
                    receptora, ahora, prepend(provisionalReceptor,
                            prepend(new DeliveryStop(riesgo.pedido(), riesgo.cantidad()), futuroReceptor)),
                    snapshotEn(receptora.snapshotOrigen(), ahora), bloqueos);
        } catch (RuntimeException sinCamino) {
            return null;
        }

        ScheduledRouteStop paradaDonante = buscarTransfer(preDonante.programada(), transferId);
        ScheduledRouteStop paradaReceptor = buscarTransfer(preReceptor.programada(), transferId);
        if (paradaDonante == null || paradaReceptor == null) {
            return null;
        }
        Instant encuentro = paradaDonante.arrivedAt().isAfter(paradaReceptor.arrivedAt())
                ? paradaDonante.arrivedAt() : paradaReceptor.arrivedAt();
        Duration esperaDonante = Duration.between(paradaDonante.arrivedAt(), encuentro);
        Duration esperaReceptor = Duration.between(paradaReceptor.arrivedAt(), encuentro);
        if (esperaDonante.compareTo(configuracion.esperaMaximaEncuentro()) > 0
                || esperaReceptor.compareTo(configuracion.esperaMaximaEncuentro()) > 0) {
            return null;
        }

        TransferStop finalDonante = new TransferStop(
                transferId, riesgo.pedido(), riesgo.cantidad(), receptora.vehiculoId(),
                TransferDirection.DONATE, nodo, encuentro);
        TransferStop finalReceptor = new TransferStop(
                transferId, riesgo.pedido(), riesgo.cantidad(), donante.vehiculoId(),
                TransferDirection.RECEIVE, nodo, encuentro);

        try {
            ContinuacionRutaService.Resultado resultadoDonante = continuaciones.reprogramar(
                    donante, ahora, prepend(finalDonante, futuroDonante),
                    snapshotEn(donante.snapshotOrigen(), ahora), bloqueos);
            ContinuacionRutaService.Resultado resultadoReceptor = continuaciones.reprogramar(
                    receptora, ahora, prepend(finalReceptor,
                            prepend(new DeliveryStop(riesgo.pedido(), riesgo.cantidad()), futuroReceptor)),
                    snapshotEn(receptora.snapshotOrigen(), ahora), bloqueos);

            RutaVigente nuevoDonante = new RutaVigente(
                    resultadoDonante.ruta(), donante.snapshotOrigen(), resultadoDonante.programada(), List.of());
            RutaVigente nuevoReceptor = new RutaVigente(
                    resultadoReceptor.ruta(), receptora.snapshotOrigen(), resultadoReceptor.programada(), List.of());

            ScheduledRouteStop syncDonante = buscarTransfer(resultadoDonante.programada(), transferId);
            ScheduledRouteStop syncReceptor = buscarTransfer(resultadoReceptor.programada(), transferId);
            if (syncDonante == null || syncReceptor == null
                    || !syncDonante.serviceStartedAt().equals(encuentro)
                    || !syncReceptor.serviceStartedAt().equals(encuentro)
                    || syncDonante.arrivedAt().isAfter(encuentro)
                    || syncReceptor.arrivedAt().isAfter(encuentro)
                    || !rutaOperativa(nuevoDonante)
                    || !rutaOperativa(nuevoReceptor)) {
                return null;
            }

            List<RutaVigente> resultado = new ArrayList<>(rutas);
            resultado.set(indiceDonante, nuevoDonante);
            resultado.set(indiceReceptor, nuevoReceptor);
            Instant nuevaEta = etaPedidoEnVehiculo(resultado, riesgo.pedido().id(), receptora.vehiculoId());
            if (!mejoraSuficiente(riesgo, nuevaEta)
                    || !cambioSeguro(rutas, resultado)) {
                return null;
            }
            return new Intento(List.copyOf(resultado), nuevaEta, true);
        } catch (RuntimeException sinAlternativa) {
            return null;
        }
    }

    private boolean mejoraSuficiente(CandidatoRiesgo riesgo, Instant nuevaEta) {
        if (nuevaEta == null || nuevaEta.isAfter(riesgo.pedido().deadline())) {
            return false;
        }
        Duration mejora = Duration.between(nuevaEta, riesgo.etaActual());
        return !mejora.isNegative() && mejora.compareTo(configuracion.mejoraMinimaEta()) >= 0;
    }

    /**
     * Un rescate puede convivir con entregas que ya estaban tardías. Lo que no
     * puede hacer es perder paquetes, duplicarlos o volver tardía una entrega
     * que antes era factible.
     */
    private boolean cambioSeguro(List<RutaVigente> antes, List<RutaVigente> despues) {
        if (!despues.stream().allMatch(this::rutaOperativa)) {
            return false;
        }

        Map<String, Integer> cantidadAntes = cantidadesProgramadas(antes);
        Map<String, Integer> cantidadDespues = cantidadesProgramadas(despues);
        if (!cantidadAntes.equals(cantidadDespues)) {
            return false;
        }

        Map<String, Integer> aTiempoAntes = cantidadesATiempo(antes);
        Map<String, Integer> aTiempoDespues = cantidadesATiempo(despues);
        for (Map.Entry<String, Integer> entry : aTiempoAntes.entrySet()) {
            if (aTiempoDespues.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private boolean rutaOperativa(RutaVigente ruta) {
        if (ruta.programada() == null || !ruta.ruta().endsAtWarehouse()) {
            return false;
        }
        int capacidad = ruta.snapshotOrigen().fleetProfile()
                .parametersFor(ruta.ruta().vehicle().type()).capacity();
        for (ScheduledRouteStop parada : ruta.programada().scheduledStops()) {
            if (parada.loadBefore() < 0 || parada.loadAfter() < 0
                    || parada.loadBefore() > capacidad || parada.loadAfter() > capacidad) {
                return false;
            }
        }
        return !ruta.snapshotOrigen().hasVehicleDisruptionDuring(
                ruta.vehiculoId(), ruta.ruta().departureAt(), ruta.programada().completedAt());
    }

    private static Map<String, Integer> cantidadesProgramadas(List<RutaVigente> rutas) {
        Map<String, Integer> resultado = new LinkedHashMap<>();
        for (RutaVigente ruta : rutas) {
            for (RouteStop stop : ruta.ruta().stops()) {
                if (stop instanceof DeliveryStop entrega) {
                    resultado.merge(entrega.order().id(), entrega.deliveredPackages(), Integer::sum);
                }
            }
        }
        return resultado;
    }

    private static Map<String, Integer> cantidadesATiempo(List<RutaVigente> rutas) {
        Map<String, Integer> resultado = new LinkedHashMap<>();
        for (RutaVigente ruta : rutas) {
            if (ruta.programada() == null) {
                continue;
            }
            for (ScheduledRouteStop stop : ruta.programada().scheduledStops()) {
                if (stop.stop() instanceof DeliveryStop entrega
                        && !stop.arrivedAt().isAfter(entrega.order().deadline())) {
                    resultado.merge(entrega.order().id(), entrega.deliveredPackages(), Integer::sum);
                }
            }
        }
        return resultado;
    }

    /**
     * El modelo histórico guarda carga total y no un inventario por pedido.
     * Para no transferir paquetes ficticios solo aceptamos casos auditables:
     * la recarga inmediatamente asociada a la entrega ya quedó en el prefijo
     * ejecutado, o la entrega era la primera y salió cargada desde Central.
     */
    private static boolean cargaEspecificaDisponible(
            RutaVigente donante,
            CheckpointRuta checkpoint,
            CandidatoRiesgo riesgo
    ) {
        int entrega = riesgo.indiceParada();
        List<RouteStop> stops = donante.ruta().stops();

        if (entrega > 0
                && entrega - 1 < checkpoint.primeraParadaFutura()
                && stops.get(entrega - 1) instanceof WarehouseVisit visita
                && visita.pickupPackages() >= riesgo.cantidad()) {
            return true;
        }

        return entrega == 0
                && donante.ruta().initialLoad() >= riesgo.cantidad()
                && checkpoint.primeraParadaFutura() == 0;
    }

    private static OperationalSnapshot snapshotEn(OperationalSnapshot original, Instant ahora) {
        return new OperationalSnapshot(
                ahora,
                original.fleetProfile(),
                original.inventory(),
                original.vehiclesById(),
                original.maintenanceCalendar(),
                original.shiftSchedule(),
                original.breakdowns());
    }

    private static List<RouteStop> prepend(RouteStop stop, List<RouteStop> tail) {
        List<RouteStop> resultado = new ArrayList<>(tail.size() + 1);
        resultado.add(stop);
        resultado.addAll(tail);
        return resultado;
    }

    private static ScheduledRouteStop buscarTransfer(ScheduledDeliveryRoute ruta, String transferId) {
        return ruta.scheduledStops().stream()
                .filter(stop -> stop.stop() instanceof TransferStop transfer
                        && transfer.transferId().equals(transferId))
                .findFirst().orElse(null);
    }

    private static boolean contieneTransbordo(RutaVigente ruta) {
        return ruta.ruta().stops().stream().anyMatch(TransferStop.class::isInstance);
    }

    private static int indiceVehiculo(List<RutaVigente> rutas, String vehiculoId) {
        for (int i = 0; i < rutas.size(); i++) {
            if (rutas.get(i).vehiculoId().equals(vehiculoId)) {
                return i;
            }
        }
        return -1;
    }

    private static List<RutaVigente> reemplazarConjunto(
            List<RutaVigente> originales,
            RutaVigente donante,
            List<RutaVigente> receptoras,
            String vehiculoDonante
    ) {
        Map<String, RutaVigente> porVehiculo = new LinkedHashMap<>();
        originales.forEach(ruta -> porVehiculo.put(ruta.vehiculoId(), ruta));
        porVehiculo.put(vehiculoDonante, donante);
        receptoras.forEach(ruta -> porVehiculo.put(ruta.vehiculoId(), ruta));
        return originales.stream().map(ruta -> porVehiculo.get(ruta.vehiculoId())).toList();
    }

    private static Instant etaPedidoEnOtraRuta(
            List<RutaVigente> rutas,
            String pedidoId,
            String vehiculoExcluido
    ) {
        return rutas.stream()
                .filter(ruta -> !ruta.vehiculoId().equals(vehiculoExcluido))
                .map(ruta -> etaEnRuta(ruta, pedidoId))
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
    }

    private static Instant etaPedidoEnVehiculo(
            List<RutaVigente> rutas,
            String pedidoId,
            String vehiculoId
    ) {
        return rutas.stream()
                .filter(ruta -> ruta.vehiculoId().equals(vehiculoId))
                .map(ruta -> etaEnRuta(ruta, pedidoId))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private static Instant etaEnRuta(RutaVigente ruta, String pedidoId) {
        if (ruta.programada() == null) {
            return null;
        }
        return ruta.programada().scheduledStops().stream()
                .filter(stop -> stop.stop() instanceof DeliveryStop entrega
                        && entrega.order().id().equals(pedidoId))
                .map(ScheduledRouteStop::arrivedAt)
                .min(Instant::compareTo)
                .orElse(null);
    }

    record Resultado(
            List<RutaVigente> rutas,
            int rescates,
            int transbordos,
            Set<String> pedidosRescatados
    ) {
    }

    private record RutaLibreCandidata(
            RutaVigente ruta,
            Instant eta,
            InventorySnapshot inventarioRestante
    ) {
    }

    private record CandidatoRiesgo(
            String vehiculoId,
            int indiceParada,
            Order pedido,
            int cantidad,
            Instant etaActual,
            Duration holgura
    ) {
    }

    private record Intento(List<RutaVigente> rutas, Instant nuevaEta, boolean transbordo) {
    }
}
