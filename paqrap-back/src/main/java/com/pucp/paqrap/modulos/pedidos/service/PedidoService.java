package com.pucp.paqrap.modulos.pedidos.service;

import com.pucp.paqrap.comun.api.PaginaResponse;
import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.modulos.pedidos.dto.CargaPedidosResponse;
import com.pucp.paqrap.modulos.pedidos.dto.PedidoDetalleResponse;
import com.pucp.paqrap.modulos.pedidos.dto.PedidoResponse;
import com.pucp.paqrap.modulos.pedidos.dto.RegistrarPedidoRequest;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
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

    private final OrderRepository pedidoRepository;
    private final OrderStatusHistoryRepository historialRepository;
    private final CargadorPedidos cargador = new CargadorPedidos();

    public PedidoService(OrderRepository pedidoRepository, OrderStatusHistoryRepository historialRepository) {
        this.pedidoRepository = pedidoRepository;
        this.historialRepository = historialRepository;
    }

    @Transactional
    public PedidoResponse registrar(RegistrarPedidoRequest request) {
        int horas = horasPrometidas(request.tipoEntrega(), request.horasPrometidas());
        Instant registradoEn = request.registradoEn() != null ? request.registradoEn() : Instant.now();
        OrderEntity pedido = new OrderEntity("M-" + UUID.randomUUID(), request.clienteId(),
                new Location(request.x(), request.y()), request.paquetes(), registradoEn, request.tipoEntrega(), horas);

        pedidoRepository.save(pedido);
        historialRepository.save(OrderStatusHistoryEntity.registro(pedido));
        return PedidoResponse.de(pedido);
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
        return new CargaPedidosResponse(nombreArchivo, periodoEfectivo, leidos.size(), nuevos.size(),
                leidos.size() - nuevos.size());
    }

    public PaginaResponse<PedidoResponse> listar(String clienteId, OrderStatus estado, DeliveryType tipoEntrega,
                                                 Instant registradoDesde, Instant registradoHasta,
                                                 Instant venceHasta, Pageable pageable) {
        if (registradoDesde != null && registradoHasta != null && !registradoHasta.isAfter(registradoDesde)) {
            throw new IllegalArgumentException("'registradoHasta' debe ser posterior a 'registradoDesde'");
        }
        return PaginaResponse.de(pedidoRepository.buscar(clienteId, estado, tipoEntrega, registradoDesde,
                registradoHasta, venceHasta, conOrdenDeEntidad(pageable)), PedidoResponse::de);
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

    public PedidoDetalleResponse obtener(String id) {
        OrderEntity pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", id));
        List<PedidoDetalleResponse.CambioEstado> historial =
                historialRepository.findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc(id).stream()
                        .map(PedidoDetalleResponse.CambioEstado::de)
                        .toList();
        return new PedidoDetalleResponse(PedidoResponse.de(pedido), historial);
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
