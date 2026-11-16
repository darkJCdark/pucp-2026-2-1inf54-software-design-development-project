package com.pucp.paqrap.modulos.pedidos.controller;

import com.pucp.paqrap.comun.api.PaginaResponse;
import com.pucp.paqrap.modulos.pedidos.dto.CargaPedidosResponse;
import com.pucp.paqrap.modulos.pedidos.dto.CargaPedidosMultipleResponse;
import com.pucp.paqrap.modulos.pedidos.dto.PedidoDetalleResponse;
import com.pucp.paqrap.modulos.pedidos.dto.PedidoResponse;
import com.pucp.paqrap.modulos.pedidos.dto.PeriodoCargadoResponse;
import com.pucp.paqrap.modulos.pedidos.dto.RegistrarPedidoRequest;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.service.CoberturaPedidosService;
import com.pucp.paqrap.modulos.pedidos.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;
    private final CoberturaPedidosService coberturaPedidos;

    public PedidoController(PedidoService pedidoService, CoberturaPedidosService coberturaPedidos) {
        this.pedidoService = pedidoService;
        this.coberturaPedidos = coberturaPedidos;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse registrar(@Valid @RequestBody RegistrarPedidoRequest request,
                                     @RequestParam(required = false) Long ejecucionId) {
        return pedidoService.registrar(request, ejecucionId);
    }

    /** Recibe un archivo ventas.AAAAMM.txt en el campo multipart {@code archivo}. */
    @PostMapping(path = "/carga", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CargaPedidosResponse cargar(@RequestPart("archivo") MultipartFile archivo,
                                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo)
            throws IOException {
        if (archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        String contenido = new String(archivo.getBytes(), StandardCharsets.UTF_8);
        return pedidoService.cargar(archivo.getOriginalFilename(), contenido, periodo);
    }

    /** Un campo multipart 'archivos' por archivo: ventas.AAAAMM.txt. Conserva /carga de un archivo. */
    @PostMapping(path = "/carga-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CargaPedidosMultipleResponse cargarMultiples(@RequestParam("archivos") List<MultipartFile> archivos)
            throws IOException {
        if (archivos.isEmpty() || archivos.size() > 2) {
            throw new IllegalArgumentException("Seleccione uno o dos archivos mensuales de pedidos");
        }
        java.util.ArrayList<PedidoService.ArchivoMensual> entradas = new java.util.ArrayList<>();
        for (MultipartFile archivo : archivos) {
            if (archivo.isEmpty()) throw new IllegalArgumentException("El archivo está vacío: " + archivo.getOriginalFilename());
            entradas.add(new PedidoService.ArchivoMensual(archivo.getOriginalFilename(),
                    new String(archivo.getBytes(), StandardCharsets.UTF_8)));
        }
        return pedidoService.cargarMultiples(entradas);
    }

    /**
     * Paginado con {@code page} (desde 0), {@code size} (máx. 500) y {@code sort} con nombres de la API:
     * id, clienteId, paquetes, registradoEn, vence o estado (p. ej. {@code sort=vence,asc}).
     * Con {@code catalogo=true} se ignora el estado de cualquier ejecución y se devuelven los pedidos tal como están
     * en la BD (identidad, destino y plazo): sirve para dibujar una simulación cuyo detalle ya no está en memoria.
     */
    @GetMapping
    public PaginaResponse<PedidoResponse> listar(
            @RequestParam(required = false) Long ejecucionId,
            @RequestParam(defaultValue = "false") boolean catalogo,
            @RequestParam(required = false) String clienteId,
            @RequestParam(required = false) OrderStatus estado,
            @RequestParam(required = false) DeliveryType tipoEntrega,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant registradoDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant registradoHasta,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant venceHasta,
            @PageableDefault(size = 50, sort = {"registradoEn", "id"}, direction = Sort.Direction.ASC)
            Pageable pageable) {
        if (catalogo) {
            return pedidoService.listarCatalogo(clienteId, estado, tipoEntrega, registradoDesde, registradoHasta,
                    venceHasta, pageable);
        }
        return pedidoService.listar(clienteId, estado, tipoEntrega, registradoDesde, registradoHasta, venceHasta,
                pageable, ejecucionId);
    }

    /** Meses con pedidos cargados desde archivos ventas.AAAAMM.txt y cuántos pedidos tiene cada uno. */
    @GetMapping("/periodos-cargados")
    public List<PeriodoCargadoResponse> periodosCargados() {
        return coberturaPedidos.periodosCargados();
    }

    /** {@code instante} (opcional, simulado) permite saber si un pedido aún no entregado ya incumplió su plazo. */
    @GetMapping("/{id}")
    public PedidoDetalleResponse obtener(@PathVariable String id,
            @RequestParam(required = false) Long ejecucionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant instante) {
        return pedidoService.obtener(id, instante, ejecucionId);
    }
}
