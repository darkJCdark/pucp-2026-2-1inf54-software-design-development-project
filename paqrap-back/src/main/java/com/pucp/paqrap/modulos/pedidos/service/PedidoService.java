package com.pucp.paqrap.modulos.pedidos.service;

import com.pucp.paqrap.comun.api.PaginaResponse;
import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.pedidos.dto.CargaPedidosMultipleResponse;
import org.springframework.data.domain.PageImpl;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.escenarios.service.MotorEscenarios;
import com.pucp.paqrap.modulos.pedidos.dto.CargaPedidosResponse;
import com.pucp.paqrap.modulos.pedidos.dto.PedidoDetalleResponse;
import com.pucp.paqrap.modulos.pedidos.dto.PedidoResponse;
import com.pucp.paqrap.modulos.pedidos.dto.RegistrarPedidoRequest;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.entity.PedidoRegistrado;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Registro manual y por archivo de pedidos (CU-01 a CU-03) y consulta con su trazabilidad (CU-04). */
@Service
@Transactional(readOnly = true)
public class PedidoService {

    /** Nombre oficial de los archivos mensuales: ventas.AAAAMM.txt (p. ej. ventas.202601.txt). */
    private static final Pattern NOMBRE_ARCHIVO = Pattern.compile("^ventas\\.(\\d{4})(\\d{2})\\.txt$",
            Pattern.CASE_INSENSITIVE);
    private static final int PLAZO_REGULAR = 36;
    private static final Set<ScenarioStatus> ESCENARIOS_ACTIVOS = Set.of(ScenarioStatus.RUNNING, ScenarioStatus.PAUSED);
    private static final ZoneId ZONA = ShiftSchedule.DEFAULT_ZONE;
    /** Campos de la API por los que se puede ordenar el listado, con su atributo en la entidad. */
    private static final Map<String, String> ATRIBUTOS_ORDENABLES = new LinkedHashMap<>();

    static {
        ATRIBUTOS_ORDENABLES.put("id", "orderId");
        ATRIBUTOS_ORDENABLES.put("clienteId", "clientId");
        ATRIBUTOS_ORDENABLES.put("paquetes", "packages");
        ATRIBUTOS_ORDENABLES.put("registradoEn", "registeredAt");
        ATRIBUTOS_ORDENABLES.put("vence", "deadline");
        ATRIBUTOS_ORDENABLES.put("estado", "status");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.pucp.paqrap.modulos.escenarios.service.EstadoOperativoEscenarios estadoOperativo;

    private final OrderRepository pedidoRepository;
    private final OrderStatusHistoryRepository historialRepository;
    private final ApplicationEventPublisher eventos;
    private final ScenarioExecutionRepository ejecucionRepository;
    private final MotorEscenarios motorEscenarios;
    private final CargadorPedidos cargador = new CargadorPedidos();

    public PedidoService(OrderRepository pedidoRepository, OrderStatusHistoryRepository historialRepository,
                         ApplicationEventPublisher eventos, ScenarioExecutionRepository ejecucionRepository,
                         MotorEscenarios motorEscenarios) {
        this.pedidoRepository = pedidoRepository;
        this.historialRepository = historialRepository;
        this.eventos = eventos;
        this.ejecucionRepository = ejecucionRepository;
        this.motorEscenarios = motorEscenarios;
    }

    @Transactional
    public PedidoResponse registrar(RegistrarPedidoRequest request) {
        return registrar(request, null);
    }

    /** Sin id explícito se prefiere Día a Día; con una sola simulación se conserva el uso anterior. */
    @Transactional
    public PedidoResponse registrar(RegistrarPedidoRequest request, Long ejecucionId) {
        ScenarioExecutionEntity destino = resolverDestino(ejecucionId, true);
        int horas = horasPrometidas(request.tipoEntrega(), request.horasPrometidas());
        Instant registradoEn = request.registradoEn() != null ? request.registradoEn()
                : destino != null ? motorEscenarios.instanteSimulado(destino.getExecutionId()).orElseGet(Instant::now)
                : Instant.now();
        if (destino != null && registradoEn.isBefore(destino.getSimulationStartedAt())) {
            throw new ReglaNegocioException("El pedido está registrado antes del inicio del escenario activo ("
                    + destino.getSimulationStartedAt() + "); use una fecha igual o posterior");
        }
        Long idDestino = destino == null ? null : destino.getExecutionId();
        String prefijo = destino != null && destino.getScenarioType() != ScenarioType.DAY_TO_DAY
                ? "SIM-" + idDestino + "-" : "M-";
        OrderEntity pedido = new OrderEntity(prefijo + UUID.randomUUID(), request.clienteId(),
                new Location(request.x(), request.y()), request.paquetes(), registradoEn, request.tipoEntrega(), horas);
        pedidoRepository.save(pedido);
        historialRepository.save(OrderStatusHistoryEntity.registro(pedido));
        if (estadoOperativo != null) estadoOperativo.incorporarAlConfirmar(idDestino, List.of(pedido));
        eventos.publishEvent(new PedidoRegistrado(pedido.getOrderId(), registradoEn, idDestino));
        return PedidoResponse.de(pedido);
    }

    private ScenarioExecutionEntity resolverDestino(Long indicado, boolean exigirActivo) {
        if (indicado != null) {
            ScenarioExecutionEntity ejecucion = ejecucionRepository.findById(indicado)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Ejecución", indicado));
            if (exigirActivo && !ESCENARIOS_ACTIVOS.contains(ejecucion.getStatus())) {
                throw new ReglaNegocioException("La ejecución " + indicado + " no está activa");
            }
            return ejecucion;
        }
        List<ScenarioExecutionEntity> activas = ejecucionRepository.findByStatusIn(ESCENARIOS_ACTIVOS);
        return activas.stream().filter(e -> e.getScenarioType() == ScenarioType.DAY_TO_DAY).findFirst()
                .orElseGet(() -> activas.size() == 1 ? activas.getFirst() : null);
    }

    /** No devuelve el estado real cuando el cliente solicitó el estado de una simulación perdida al reiniciar. */
    private Long resolverConsulta(Long indicado) {
        ScenarioExecutionEntity destino = resolverDestino(indicado, false);
        if (destino == null) return null;
        if (destino.getScenarioType() != ScenarioType.DAY_TO_DAY
                && (estadoOperativo == null || !estadoOperativo.aislada(destino.getExecutionId()))) {
            if (indicado != null) throw new ReglaNegocioException(
                    "El detalle operativo de esta simulación no está en memoria; consulte su resultado consolidado");
            return null;
        }
        return destino.getExecutionId();
    }

    /**
     * Registra los pedidos de un archivo mensual. Los que ya existían (mismo id AAAAMM-NNNNN) se omiten, así que
     * recargar el archivo no duplica nada. Si una línea es inválida no se guarda ningún pedido.
     */
    @Transactional
    public CargaPedidosResponse cargar(String nombreArchivo, String contenido, YearMonth periodo) {
        YearMonth periodoEfectivo = periodo != null ? periodo : periodoDesdeNombre(nombreArchivo);
        List<CargadorPedidos.PedidoArchivo> leidos = cargador.cargar(
                contenido.replace("﻿", "").lines().toList(), periodoEfectivo, ZONA);

        String prefijo = String.format("%04d%02d-", periodoEfectivo.getYear(), periodoEfectivo.getMonthValue());
        Set<String> existentes = new HashSet<>(pedidoRepository.idsConPrefijo(prefijo + "%"));
        List<OrderEntity> nuevos = leidos.stream()
                .filter(leido -> !existentes.contains(leido.id()))
                .map(leido -> new OrderEntity(leido.id(), leido.clienteId(), leido.destino(), leido.paquetes(),
                        leido.registradoEn(), leido.tipoEntrega(), leido.horasPrometidas()))
                .toList();

        pedidoRepository.saveAll(nuevos);
        historialRepository.saveAll(nuevos.stream().map(OrderStatusHistoryEntity::registro).toList());
        if (estadoOperativo != null) estadoOperativo.incorporarAlConfirmar(null, nuevos);
        return new CargaPedidosResponse(nombreArchivo, periodoEfectivo, leidos.size(), nuevos.size(),
                leidos.size() - nuevos.size());
    }

    public record ArchivoMensual(String nombre, String contenido) { }

    /** Carga atómica de uno o dos archivos; nunca se borra el mes anterior. */
    @Transactional
    public CargaPedidosMultipleResponse cargarMultiples(List<ArchivoMensual> archivos) {
        if (archivos == null || archivos.isEmpty() || archivos.size() > 2) {
            throw new IllegalArgumentException("Seleccione uno o dos archivos mensuales de pedidos");
        }
        Set<YearMonth> periodos = new HashSet<>();
        // Validar todos antes de guardar el primero. Un error en el segundo no deja una carga parcial.
        for (ArchivoMensual archivo : archivos) {
            if (archivo == null || archivo.contenido() == null || archivo.contenido().isBlank()) {
                throw new IllegalArgumentException("Ningún archivo puede estar vacío");
            }
            YearMonth periodo = periodoDesdeNombre(archivo.nombre());
            if (!periodos.add(periodo)) {
                throw new IllegalArgumentException("Los dos archivos deben corresponder a meses distintos: " + periodo);
            }
            if (cargador.cargar(archivo.contenido().replace("\uFEFF", "").lines().toList(), periodo, ZONA).isEmpty()) {
                throw new IllegalArgumentException("El archivo no contiene pedidos: " + archivo.nombre());
            }
        }
        List<CargaPedidosResponse> resultados = new ArrayList<>();
        for (ArchivoMensual archivo : archivos) resultados.add(cargar(archivo.nombre(), archivo.contenido(), null));
        return CargaPedidosMultipleResponse.de(resultados);
    }

    public PaginaResponse<PedidoResponse> listar(String clienteId, OrderStatus estado, DeliveryType tipoEntrega,
                                                 Instant registradoDesde, Instant registradoHasta,
                                                 Instant venceHasta, Pageable pageable) {
        return listar(clienteId, estado, tipoEntrega, registradoDesde, registradoHasta, venceHasta, pageable, null);
    }

    public PaginaResponse<PedidoResponse> listar(String clienteId, OrderStatus estado, DeliveryType tipoEntrega,
                                                 Instant registradoDesde, Instant registradoHasta,
                                                 Instant venceHasta, Pageable pageable, Long ejecucionId) {
        if (registradoDesde != null && registradoHasta != null && !registradoHasta.isAfter(registradoDesde)) {
            throw new IllegalArgumentException("'registradoHasta' debe ser posterior a 'registradoDesde'");
        }
        Pageable orden = conOrdenDeEntidad(pageable);
        Long destino = resolverConsulta(ejecucionId);
        if (estadoOperativo != null && estadoOperativo.aislada(destino)) {
            List<OrderEntity> filtrados = estadoOperativo.catalogo(destino).stream()
                    .filter(p -> clienteId == null || clienteId.equals(p.getClientId()))
                    .filter(p -> estado == null || estado == p.getStatus())
                    .filter(p -> tipoEntrega == null || tipoEntrega == p.getDeliveryType())
                    .filter(p -> registradoDesde == null || !p.getRegisteredAt().isBefore(registradoDesde))
                    .filter(p -> registradoHasta == null || p.getRegisteredAt().isBefore(registradoHasta))
                    .filter(p -> venceHasta == null || !p.getDeadline().isAfter(venceHasta))
                    .sorted(comparador(orden.getSort())).toList();
            List<OrderEntity> pagina = filtrados.stream().skip(orden.getOffset()).limit(orden.getPageSize()).toList();
            return PaginaResponse.de(new PageImpl<>(pagina, orden, filtrados.size()), PedidoResponse::de);
        }
        return PaginaResponse.de(pedidoRepository.buscar(clienteId, estado, tipoEntrega, registradoDesde,
                registradoHasta, venceHasta, orden), PedidoResponse::de);
    }

    /** Pedidos de la BD sin el estado de ninguna ejecución (ver {@code catalogo} en el controlador). */
    public PaginaResponse<PedidoResponse> listarCatalogo(String clienteId, OrderStatus estado, DeliveryType tipoEntrega,
                                                         Instant registradoDesde, Instant registradoHasta,
                                                         Instant venceHasta, Pageable pageable) {
        if (registradoDesde != null && registradoHasta != null && !registradoHasta.isAfter(registradoDesde)) {
            throw new IllegalArgumentException("'registradoHasta' debe ser posterior a 'registradoDesde'");
        }
        return PaginaResponse.de(pedidoRepository.buscar(clienteId, estado, tipoEntrega, registradoDesde,
                registradoHasta, venceHasta, conOrdenDeEntidad(pageable)), PedidoResponse::de);
    }

    private Comparator<OrderEntity> comparador(Sort orden) {
        Comparator<OrderEntity> resultado = (a, b) -> 0;
        for (Sort.Order campo : orden) {
            Comparator<OrderEntity> siguiente = switch (campo.getProperty()) {
                case "orderId" -> Comparator.comparing(OrderEntity::getOrderId);
                case "clientId" -> Comparator.comparing(OrderEntity::getClientId);
                case "packages" -> Comparator.comparing(OrderEntity::getPackages);
                case "registeredAt" -> Comparator.comparing(OrderEntity::getRegisteredAt);
                case "deadline" -> Comparator.comparing(OrderEntity::getDeadline);
                case "status" -> Comparator.comparing(p -> p.getStatus().name());
                default -> throw new IllegalArgumentException("Campo de orden no admitido");
            };
            resultado = resultado.thenComparing(campo.isDescending() ? siguiente.reversed() : siguiente);
        }
        return resultado.thenComparing(OrderEntity::getOrderId);
    }

    /** Traduce el {@code sort} expresado con los nombres de la API a los atributos de la entidad. */
    private Pageable conOrdenDeEntidad(Pageable pageable) {
        List<Sort.Order> orden = pageable.getSort().stream()
                .map(o -> {
                    String atributo = ATRIBUTOS_ORDENABLES.get(o.getProperty());
                    if (atributo == null) {
                        throw new IllegalArgumentException("No se puede ordenar por '" + o.getProperty()
                                + "'. Use: " + String.join(", ", ATRIBUTOS_ORDENABLES.keySet()));
                    }
                    return new Sort.Order(o.getDirection(), atributo);
                })
                .toList();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orden));
    }

    /** Situación de los pedidos de una simulación iniciada en {@code desde}, vista en {@code instante}. */
    public record ResumenPedidos(long llegados, long pendientes, long vencidosSinEntregar) {
    }

    public ResumenPedidos resumen(Instant desde, Instant instante) {
        return new ResumenPedidos(
                pedidoRepository.countByRegisteredAtGreaterThanEqualAndRegisteredAtLessThanEqual(desde, instante),
                pedidoRepository.contarPendientesEnDesde(desde, instante),
                pedidoRepository.contarVencidosSinEntregar(desde, instante));
    }

    public ResumenPedidos resumen(long ejecucionId, Instant desde, Instant instante) {
        if (estadoOperativo == null) return resumen(desde, instante);
        var resumen = estadoOperativo.resumen(ejecucionId, desde, instante);
        return new ResumenPedidos(resumen.llegados(), resumen.pendientes(), resumen.vencidosSinEntregar());
    }

    public PedidoDetalleResponse obtener(String id) {
        return obtener(id, null);
    }

    /** Con {@code instante} (simulado) calcula además si un pedido aún no entregado ya incumplió su plazo. */
    public PedidoDetalleResponse obtener(String id, Instant instante) {
        return obtener(id, instante, null);
    }

    public PedidoDetalleResponse obtener(String id, Instant instante, Long ejecucionId) {
        Long destino = resolverConsulta(ejecucionId);
        boolean virtual = estadoOperativo != null && estadoOperativo.aislada(destino);
        OrderEntity pedido = (virtual ? estadoOperativo.pedido(destino, id) : pedidoRepository.findById(id))
                .filter(p -> virtual || !p.getOrderId().startsWith("SIM-"))
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", id));
        List<PedidoDetalleResponse.CambioEstado> historial =
                (virtual ? estadoOperativo.historial(destino, id)
                        : historialRepository.findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc(id)).stream()
                        .map(PedidoDetalleResponse.CambioEstado::de)
                        .toList();
        String unidadAsignada = historial.stream()
                .map(PedidoDetalleResponse.CambioEstado::vehiculoId)
                .filter(vehiculoId -> vehiculoId != null)
                .reduce((anterior, ultimo) -> ultimo)
                .orElse(null);
        boolean entregado = pedido.getDeliveredAt() != null;
        boolean incumplido = entregado
                ? pedido.getDeliveredAt().isAfter(pedido.getDeadline())
                : instante != null && instante.isAfter(pedido.getDeadline());
        Long minutosRestantes = !entregado && instante != null
                ? Duration.between(instante, pedido.getDeadline()).toMinutes()
                : null;
        return new PedidoDetalleResponse(PedidoResponse.de(pedido), historial, unidadAsignada, incumplido,
                minutosRestantes);
    }

    /** REGULAR siempre es 36 h; PRIORITY exige 4, 8, 12 o 18 h. */
    private int horasPrometidas(DeliveryType tipo, Integer horas) {
        if (tipo == DeliveryType.REGULAR) {
            if (horas != null && horas != PLAZO_REGULAR) {
                throw new IllegalArgumentException("Un pedido REGULAR siempre tiene un plazo de 36 h");
            }
            return PLAZO_REGULAR;
        }
        if (horas == null || !tipo.admite(horas)) {
            throw new IllegalArgumentException("Un pedido PRIORITY requiere 'horasPrometidas' de 4, 8, 12 o 18");
        }
        return horas;
    }

    private YearMonth periodoDesdeNombre(String nombreArchivo) {
        Matcher coincidencia = NOMBRE_ARCHIVO.matcher(nombreArchivo == null ? "" : nombreArchivo);
        if (!coincidencia.matches()) {
            throw new IllegalArgumentException("No se pudo deducir el periodo del archivo '" + nombreArchivo
                    + "': use el nombre ventas.AAAAMM.txt o indique el parámetro periodo (AAAA-MM)");
        }
        try {
            return YearMonth.of(Integer.parseInt(coincidencia.group(1)), Integer.parseInt(coincidencia.group(2)));
        } catch (DateTimeException excepcion) {
            throw new IllegalArgumentException("Mes inválido en el nombre del archivo '" + nombreArchivo + "'");
        }
    }
}
