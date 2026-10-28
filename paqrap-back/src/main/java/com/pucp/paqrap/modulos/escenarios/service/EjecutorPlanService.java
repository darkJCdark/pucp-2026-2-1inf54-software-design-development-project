package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.almacenes.service.InventarioService;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownResolution;
import com.pucp.paqrap.modulos.incidencias.persistence.BreakdownEventEntity;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.incidencias.service.BreakdownAvailabilityCalculator;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.service.PlanVigente;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ejecuta en el tiempo simulado el plan vigente de SA: en cada tick aplica, en orden cronológico, lo que ocurrió en
 * el intervalo (desde, hasta] del reloj. Actualiza el estado y la posición de los vehículos, el estado y la entrega
 * de los pedidos (con su historial), los despachos de inventario y las métricas de la ejecución.
 *
 * <p>Solo se ejecuta el último plan: cuando el planificador entrega uno nuevo, reemplaza al anterior desde su hora
 * de planificación. Lo ya ejecutado (pedidos entregados, posiciones) no se deshace.
 */
@Service
public class EjecutorPlanService {

    private static final Logger log = LoggerFactory.getLogger(EjecutorPlanService.class);
    /** Una avería mayor inhabilita como máximo unos 3 días; las anteriores ya no afectan. */
    private static final Duration ALCANCE_AVERIAS = Duration.ofDays(4);

    private final PlanVigente planVigente;
    private final OrderRepository pedidoRepository;
    private final OrderStatusHistoryRepository historialRepository;
    private final VehicleRepository vehiculoRepository;
    private final WarehouseRepository almacenRepository;
    private final BreakdownEventRepository averiaRepository;
    private final InventarioService inventarioService;
    private final BreakdownAvailabilityCalculator calculadoraAverias =
            new BreakdownAvailabilityCalculator(ShiftSchedule.defaultSchedule());
    private final Map<Long, EstadoEjecucion> estados = new ConcurrentHashMap<>();

    public EjecutorPlanService(PlanVigente planVigente, OrderRepository pedidoRepository,
                               OrderStatusHistoryRepository historialRepository, VehicleRepository vehiculoRepository,
                               WarehouseRepository almacenRepository, BreakdownEventRepository averiaRepository,
                               InventarioService inventarioService) {
        this.planVigente = planVigente;
        this.pedidoRepository = pedidoRepository;
        this.historialRepository = historialRepository;
        this.vehiculoRepository = vehiculoRepository;
        this.almacenRepository = almacenRepository;
        this.averiaRepository = averiaRepository;
        this.inventarioService = inventarioService;
    }

    /** Resultados acumulados de una ejecución. */
    public record Metricas(int pedidosEntregados, int entregadosATiempo, int paquetesEntregados, double distanciaKm,
                           double costo) {
    }

    /**
     * Prepara una ejecución nueva: la flota vuelve al almacén central, vacía y disponible (salvo las unidades
     * marcadas UNAVAILABLE), y los pedidos del periodo simulado vuelven a REGISTERED.
     */
    @Transactional
    public void prepararInicio(long ejecucionId, Instant inicioSimulado) {
        estados.put(ejecucionId, new EstadoEjecucion());
        historialRepository.borrarEjecucionDesde(inicioSimulado);
        int pedidos = pedidoRepository.reiniciarDesde(inicioSimulado);

        Optional<WarehouseEntity> central = almacenRepository.findAll().stream()
                .filter(almacen -> almacen.getWarehouseKind() == WarehouseKind.CENTRAL)
                .findFirst();
        int vehiculos = 0;
        if (central.isEmpty()) {
            log.warn("Ejecución {}: no hay almacén central registrado; la flota no se reinicia", ejecucionId);
        } else {
            for (VehicleEntity vehiculo : vehiculoRepository.findAll()) {
                if (vehiculo.getOperationalStatus() != VehicleOperationalStatus.UNAVAILABLE) {
                    vehiculo.reiniciarEn(central.get().getX(), central.get().getY());
                    vehiculos++;
                }
            }
        }
        log.info("Ejecución {}: {} vehículos al almacén central, {} pedidos del periodo reiniciados",
                ejecucionId, vehiculos, pedidos);
    }

    /** Aplica el plan vigente en el intervalo simulado (desde, hasta]. */
    @Transactional
    public void avanzar(long ejecucionId, Instant desde, Instant hasta) {
        EstadoEjecucion estado = estados.computeIfAbsent(ejecucionId, id -> new EstadoEjecucion());
        List<BreakdownEvent> averias = averiaRepository
                .buscar(null, null, null, hasta.minus(ALCANCE_AVERIAS), hasta.plusNanos(1)).stream()
                .map(BreakdownEventEntity::toDomain)
                .toList();
        trasladarAveriadosAlCentral(averias, desde, hasta);

        Optional<PlanResponse> vigente = planVigente.ultimoPlan(ejecucionId);
        if (vigente.isEmpty()) {
            return;
        }
        PlanResponse plan = vigente.get();
        Instant inicioVentana = desde;
        if (plan != estado.plan) {
            // Plan nuevo: reemplaza al anterior desde su hora de planificación (incluida). Las rutas en curso que la
            // replanificación conservó siguen ejecutándose con su versión del plan nuevo.
            liberarVehiculosSinRuta(estado, plan);
            Map<String, RutaEnCurso> continuadas = continuadas(estado, plan);
            estado.plan = plan;
            estado.rutas.clear();
            estado.rutas.putAll(continuadas);
            estado.continuadas.clear();
            continuadas.values().forEach(enCurso -> estado.continuadas.add(enCurso.ruta));
            if (!plan.planificadoEn().isAfter(desde)) {
                inicioVentana = plan.planificadoEn().minusNanos(1);
            }
        }

        for (Evento evento : eventos(plan, inicioVentana, desde, estado.continuadas, hasta)) {
            aplicar(ejecucionId, estado, evento, averias);
        }
    }

    /** Rutas en curso que siguen en el plan nuevo (mismo vehículo, id y salida), ya ligadas a su versión nueva. */
    private Map<String, RutaEnCurso> continuadas(EstadoEjecucion estado, PlanResponse nuevo) {
        Map<String, RutaEnCurso> continuadas = new HashMap<>();
        for (RutaResponse ruta : nuevo.rutas()) {
            RutaEnCurso enCurso = estado.rutas.get(ruta.vehiculoId());
            if (enCurso != null && !enCurso.interrumpida && enCurso.ruta.id().equals(ruta.id())
                    && enCurso.ruta.salida() != null && enCurso.ruta.salida().equals(ruta.salida())) {
                enCurso.ruta = ruta;
                continuadas.put(ruta.vehiculoId(), enCurso);
            }
        }
        return continuadas;
    }

    /** Se conservan al terminar la ejecución, hasta que el backend se reinicie. */
    public Optional<Metricas> metricas(long ejecucionId) {
        return Optional.ofNullable(estados.get(ejecucionId)).map(EstadoEjecucion::metricas);
    }

    /** Los vehículos que iban en ruta y no aparecen en el plan nuevo quedan disponibles donde están. */
    private void liberarVehiculosSinRuta(EstadoEjecucion estado, PlanResponse nuevo) {
        for (RutaEnCurso enCurso : estado.rutas.values()) {
            String vehiculoId = enCurso.ruta.vehiculoId();
            boolean sigue = nuevo.rutas().stream().anyMatch(ruta -> ruta.vehiculoId().equals(vehiculoId));
            if (!sigue) {
                vehiculoRepository.findById(vehiculoId)
                        .filter(vehiculo -> vehiculo.getOperationalStatus() == VehicleOperationalStatus.IN_ROUTE)
                        .ifPresent(vehiculo -> vehiculo.setOperationalStatus(VehicleOperationalStatus.AVAILABLE));
            }
        }
    }

    // ------------------------------------------------------------------ eventos del plan

    private enum TipoEvento { SALIDA, PARTIDA_HACIA_PARADA, LLEGADA_A_PARADA, FIN_RUTA }

    /** {@code parada}: índice de la parada; -1 en SALIDA y FIN_RUTA. */
    private record Evento(Instant instante, TipoEvento tipo, RutaResponse ruta, int parada) {
    }

    /**
     * Eventos de todas las rutas programadas con instante en (desde, hasta], en orden cronológico. Las rutas
     * continuadas ya se ejecutaron hasta {@code ejecutadoHasta}: sus eventos se toman desde ahí.
     */
    private List<Evento> eventos(PlanResponse plan, Instant inicioVentana, Instant ejecutadoHasta,
                                 Set<RutaResponse> continuadas, Instant hasta) {
        List<Evento> eventos = new ArrayList<>();
        for (RutaResponse ruta : plan.rutas()) {
            if (ruta.llegadaFinal() == null || ruta.salida() == null) {
                continue; // ruta que SA no pudo programar
            }
            Instant desde = continuadas.contains(ruta) && ejecutadoHasta.isAfter(inicioVentana)
                    ? ejecutadoHasta : inicioVentana;
            agregar(eventos, ruta.salida(), TipoEvento.SALIDA, ruta, -1, desde, hasta);
            Instant partida = ruta.salida();
            for (int i = 0; i < ruta.paradas().size(); i++) {
                ParadaResponse parada = ruta.paradas().get(i);
                if (parada.llegada() == null) {
                    break;
                }
                if (ParadaResponse.ENTREGA.equals(parada.tipo())) {
                    agregar(eventos, partida, TipoEvento.PARTIDA_HACIA_PARADA, ruta, i, desde, hasta);
                }
                agregar(eventos, parada.llegada(), TipoEvento.LLEGADA_A_PARADA, ruta, i, desde, hasta);
                partida = parada.fin() != null ? parada.fin() : parada.llegada();
            }
            agregar(eventos, ruta.llegadaFinal(), TipoEvento.FIN_RUTA, ruta, -1, desde, hasta);
        }
        eventos.sort(Comparator.comparing(Evento::instante).thenComparing(Evento::tipo));
        return eventos;
    }

    private void agregar(List<Evento> eventos, Instant instante, TipoEvento tipo, RutaResponse ruta, int parada,
                         Instant desde, Instant hasta) {
        if (instante.isAfter(desde) && !instante.isAfter(hasta)) {
            eventos.add(new Evento(instante, tipo, ruta, parada));
        }
    }

    private void aplicar(long ejecucionId, EstadoEjecucion estado, Evento evento, List<BreakdownEvent> averias) {
        RutaResponse ruta = evento.ruta();
        if (evento.tipo() == TipoEvento.SALIDA) {
            iniciarRuta(ejecucionId, estado, ruta, averias);
            return;
        }
        RutaEnCurso enCurso = estado.rutas.get(ruta.vehiculoId());
        if (enCurso == null || enCurso.ruta != ruta || enCurso.interrumpida) {
            return; // la ruta no llegó a salir, es de otro plan o la interrumpió una avería
        }
        Optional<BreakdownEvent> averia = averiaDurante(averias, ruta.vehiculoId(), ruta.salida(), evento.instante());
        if (averia.isPresent()) {
            interrumpir(enCurso, averia.get());
            return;
        }
        switch (evento.tipo()) {
            case PARTIDA_HACIA_PARADA -> partirHacia(ruta.paradas().get(evento.parada()), ruta, evento.instante());
            case LLEGADA_A_PARADA -> llegarA(ejecucionId, estado, enCurso, evento.parada(), evento.instante());
            case FIN_RUTA -> terminarRuta(enCurso);
            default -> { }
        }
    }

    private void iniciarRuta(long ejecucionId, EstadoEjecucion estado, RutaResponse ruta, List<BreakdownEvent> averias) {
        VehicleEntity vehiculo = vehiculoRepository.findById(ruta.vehiculoId()).orElse(null);
        if (vehiculo == null || averiaDurante(averias, ruta.vehiculoId(), Instant.MIN, ruta.salida())
                .filter(a -> calculadoraAverias.resolve(a).unavailableUntil().isAfter(ruta.salida())).isPresent()) {
            return;
        }
        ParadaResponse primera = ruta.paradas().isEmpty() ? null : ruta.paradas().get(0);
        int cargaPlanificada = primera != null && primera.cargaAntes() != null
                ? primera.cargaAntes() : vehiculo.getCurrentLoad();
        if (!registrarCargaInicial(ejecucionId, ruta, vehiculo, cargaPlanificada)) {
            cargaPlanificada = vehiculo.getCurrentLoad();
        }

        RutaEnCurso enCurso = new RutaEnCurso(ruta, vehiculo.getCurrentX(), vehiculo.getCurrentY());
        estado.rutas.put(ruta.vehiculoId(), enCurso);
        vehiculo.setOperationalStatus(VehicleOperationalStatus.IN_ROUTE);
        vehiculo.moverA(vehiculo.getCurrentX(), vehiculo.getCurrentY(), cargaPlanificada);
        for (ParadaResponse parada : ruta.paradas()) {
            if (ParadaResponse.ENTREGA.equals(parada.tipo())) {
                pedidoRepository.findById(parada.pedidoId())
                        .filter(pedido -> pedido.getStatus() == OrderStatus.REGISTERED)
                        .ifPresent(pedido -> cambiarEstado(pedido, OrderStatus.ASSIGNED, ruta.salida(), ruta.vehiculoId()));
            }
        }
    }

    /** El almacén inicial solo se descuenta cuando la ruta realmente incrementa la carga del vehículo. */
    private boolean registrarCargaInicial(long ejecucionId, RutaResponse ruta, VehicleEntity vehiculo, int cargaPlanificada) {
        int cantidad = cargaPlanificada - vehiculo.getCurrentLoad();
        if (cantidad <= 0 || ruta.almacenInicialId() == null) {
            return true;
        }
        try {
            return inventarioService.registrarDespacho(ejecucionId, ruta.almacenInicialId(), null, cantidad, ruta.salida(),
                    claveCargaInicial(ruta));
        } catch (ReglaNegocioException sinStock) {
            log.warn("Ejecución {}: {}", ejecucionId, sinStock.getMessage());
            return false;
        }
    }
    private void partirHacia(ParadaResponse parada, RutaResponse ruta, Instant instante) {
        pedidoRepository.findById(parada.pedidoId())
                .filter(pedido -> pedido.getStatus() == OrderStatus.ASSIGNED
                        || pedido.getStatus() == OrderStatus.REGISTERED)
                .ifPresent(pedido -> cambiarEstado(pedido, OrderStatus.IN_TRANSIT, instante, ruta.vehiculoId()));
    }

    private void llegarA(long ejecucionId, EstadoEjecucion estado, RutaEnCurso enCurso, int indice, Instant instante) {
        RutaResponse ruta = enCurso.ruta;
        ParadaResponse parada = ruta.paradas().get(indice);
        double tramoKm = enCurso.factorDistancia * Math.abs(parada.x() - enCurso.x) + enCurso.factorDistancia
                * Math.abs(parada.y() - enCurso.y);
        estado.distanciaKm += tramoKm;
        estado.costo += tramoKm * enCurso.costoPorKm;
        enCurso.x = parada.x();
        enCurso.y = parada.y();

        boolean cargaConfirmada = !ParadaResponse.ALMACEN.equals(parada.tipo()) || parada.paquetes() <= 0
                || despachar(ejecucionId, ruta, indice, instante);
        vehiculoRepository.findById(ruta.vehiculoId()).ifPresent(vehiculo -> {
            int carga = cargaConfirmada && parada.cargaDespues() != null
                    ? parada.cargaDespues() : vehiculo.getCurrentLoad();
            vehiculo.moverA(parada.x(), parada.y(), carga);
        });
        if (ParadaResponse.ENTREGA.equals(parada.tipo())) {
            entregar(estado, parada, ruta.vehiculoId(), instante);
        }
    }
    /** Suma la entrega (puede ser parcial); el pedido queda DELIVERED al completar todos sus paquetes. */
    private void entregar(EstadoEjecucion estado, ParadaResponse parada, String vehiculoId, Instant llegada) {
        OrderEntity pedido = pedidoRepository.findById(parada.pedidoId()).orElse(null);
        if (pedido == null || pedido.getStatus() == OrderStatus.DELIVERED) {
            return;
        }
        int entregados = estado.paquetesPorPedido.merge(pedido.getOrderId(), parada.paquetes(), Integer::sum);
        estado.paquetesEntregados += parada.paquetes();
        if (entregados >= pedido.getPackages()) {
            pedido.entregar(llegada);
            historialRepository.save(OrderStatusHistoryEntity.cambio(pedido, OrderStatus.DELIVERED, llegada, vehiculoId));
            estado.pedidosEntregados++;
            if (pedido.entregadoATiempo()) {
                estado.entregadosATiempo++;
            }
        }
    }

    /** La carga física se descuenta al llegar al almacén; no se atribuye artificialmente a una entrega posterior. */
    private boolean despachar(long ejecucionId, RutaResponse ruta, int indice, Instant instante) {
        ParadaResponse almacen = ruta.paradas().get(indice);
        try {
            return inventarioService.registrarDespacho(ejecucionId, almacen.almacenId(), null, almacen.paquetes(), instante,
                    claveCargaIntermedia(ruta, almacen));
        } catch (ReglaNegocioException sinStock) {
            log.warn("Ejecución {}: {}", ejecucionId, sinStock.getMessage());
            return false;
        }
    }
    private static String claveCargaInicial(RutaResponse ruta) {
        return "INITIAL:ROUTE:" + rutaPersistedId(ruta);
    }

    private static String claveCargaIntermedia(RutaResponse ruta, ParadaResponse parada) {
        return "WAREHOUSE:ROUTE:" + rutaPersistedId(ruta) + ":STOP:" + parada.orden();
    }

    private static long rutaPersistedId(RutaResponse ruta) {
        if (ruta.routePersistedId() == null) {
            throw new IllegalStateException("La ruta " + ruta.id() + " no tiene identidad persistida para el kardex");
        }
        return ruta.routePersistedId();
    }
    private void terminarRuta(RutaEnCurso enCurso) {
        vehiculoRepository.findById(enCurso.ruta.vehiculoId())
                .filter(vehiculo -> vehiculo.getOperationalStatus() == VehicleOperationalStatus.IN_ROUTE)
                .ifPresent(vehiculo -> vehiculo.setOperationalStatus(VehicleOperationalStatus.AVAILABLE));
    }

    // ------------------------------------------------------------------ averías

    /** El vehículo queda en el lugar de la avería. Sus pedidos sin entregar vuelven a REGISTERED para replanificarse. */
    private void interrumpir(RutaEnCurso enCurso, BreakdownEvent averia) {
        enCurso.interrumpida = true;
        RutaResponse ruta = enCurso.ruta;
        vehiculoRepository.findById(ruta.vehiculoId()).ifPresent(vehiculo -> {
            vehiculo.moverA(averia.location().x(), averia.location().y(), vehiculo.getCurrentLoad());
            vehiculo.setOperationalStatus(VehicleOperationalStatus.AVAILABLE);
        });
        for (ParadaResponse parada : ruta.paradas()) {
            if (ParadaResponse.ENTREGA.equals(parada.tipo())) {
                pedidoRepository.findById(parada.pedidoId())
                        .filter(pedido -> pedido.getStatus() == OrderStatus.ASSIGNED
                                || pedido.getStatus() == OrderStatus.IN_TRANSIT)
                        .ifPresent(pedido -> cambiarEstado(pedido, OrderStatus.REGISTERED, averia.occurredAt(),
                                ruta.vehiculoId()));
            }
        }
    }

    /** Averías intermedias y mayores: a las 4 horas la unidad pasa al central con la carga no trasvasada. */
    private void trasladarAveriadosAlCentral(List<BreakdownEvent> averias, Instant desde, Instant hasta) {
        Optional<WarehouseEntity> central = averias.isEmpty() ? Optional.empty() : almacenRepository.findAll().stream()
                .filter(almacen -> almacen.getWarehouseKind() == WarehouseKind.CENTRAL)
                .findFirst();
        for (BreakdownEvent averia : averias) {
            BreakdownResolution resolucion = calculadoraAverias.resolve(averia);
            Instant traslado = resolucion.returnsToCentralAt();
            if (traslado != null && traslado.isAfter(desde) && !traslado.isAfter(hasta) && central.isPresent()) {
                vehiculoRepository.findById(averia.vehicleId())
                        .ifPresent(vehiculo -> vehiculo.moverA(central.get().getX(), central.get().getY(), 0));
            }
        }
    }

    private Optional<BreakdownEvent> averiaDurante(List<BreakdownEvent> averias, String vehiculoId, Instant desde,
                                                   Instant hasta) {
        return averias.stream()
                .filter(averia -> averia.vehicleId().equals(vehiculoId))
                .filter(averia -> !averia.occurredAt().isBefore(desde) && !averia.occurredAt().isAfter(hasta))
                .findFirst();
    }

    private void cambiarEstado(OrderEntity pedido, OrderStatus nuevoEstado, Instant instante, String vehiculoId) {
        pedido.cambiarEstado(nuevoEstado);
        historialRepository.save(OrderStatusHistoryEntity.cambio(pedido, nuevoEstado, instante, vehiculoId));
    }

    // ------------------------------------------------------------------ estado en memoria

    private static final class EstadoEjecucion {
        private PlanResponse plan;
        private final Map<String, RutaEnCurso> rutas = new HashMap<>();
        /** Rutas del plan vigente que ya venían en curso del plan anterior (por identidad). */
        private final Set<RutaResponse> continuadas =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<String, Integer> paquetesPorPedido = new HashMap<>();
        private int pedidosEntregados;
        private int entregadosATiempo;
        private int paquetesEntregados;
        private double distanciaKm;
        private double costo;

        private Metricas metricas() {
            return new Metricas(pedidosEntregados, entregadosATiempo, paquetesEntregados,
                    Math.round(distanciaKm * 100) / 100.0, Math.round(costo * 100) / 100.0);
        }
    }

    /**
     * Ruta que ya salió. El plan solo trae la distancia total, así que cada tramo se estima con su distancia en la
     * cuadrícula escalada para que la suma coincida con la de la ruta (los bloqueos alargan algunos tramos).
     */
    private static final class RutaEnCurso {
        /** Se actualiza cuando la replanificación conserva la ruta en un plan nuevo. */
        private RutaResponse ruta;
        private final double factorDistancia;
        private final double costoPorKm;
        private int x;
        private int y;
        private boolean interrumpida;

        private RutaEnCurso(RutaResponse ruta, int xInicial, int yInicial) {
            this.ruta = ruta;
            this.x = xInicial;
            this.y = yInicial;
            double cuadricula = 0;
            Location anterior = new Location(xInicial, yInicial);
            for (ParadaResponse parada : ruta.paradas()) {
                Location siguiente = new Location(parada.x(), parada.y());
                cuadricula += anterior.manhattanDistanceTo(siguiente);
                anterior = siguiente;
            }
            this.factorDistancia = cuadricula > 0 ? ruta.distanciaKm() / cuadricula : 0;
            this.costoPorKm = ruta.distanciaKm() > 0 ? ruta.costo() / ruta.distanciaKm() : 0;
        }
    }
}
