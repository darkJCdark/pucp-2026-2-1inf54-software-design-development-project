package com.pucp.paqrap.modulos.pedidos.controller;

import com.pucp.paqrap.comun.api.PaginaResponse;
import com.pucp.paqrap.modulos.pedidos.dto.CargaPedidosResponse;
import com.pucp.paqrap.modulos.pedidos.dto.PedidoDetalleResponse;
import com.pucp.paqrap.modulos.pedidos.dto.PedidoResponse;
import com.pucp.paqrap.modulos.pedidos.dto.RegistrarPedidoRequest;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
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

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse registrar(@Valid @RequestBody RegistrarPedidoRequest request) {
        return pedidoService.registrar(request);
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

    /**
     * Paginado con {@code page} (desde 0), {@code size} (máx. 500) y {@code sort} con nombres de la API:
     * id, clienteId, paquetes, registradoEn, vence o estado (p. ej. {@code sort=vence,asc}).
     */
    @GetMapping
    public PaginaResponse<PedidoResponse> listar(
            @RequestParam(required = false) String clienteId,
            @RequestParam(required = false) OrderStatus estado,
            @RequestParam(required = false) DeliveryType tipoEntrega,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant registradoDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant registradoHasta,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant venceHasta,
            @PageableDefault(size = 50, sort = {"registradoEn", "id"}, direction = Sort.Direction.ASC)
            Pageable pageable) {
        return pedidoService.listar(clienteId, estado, tipoEntrega, registradoDesde, registradoHasta, venceHasta,
                pageable);
    }

    @GetMapping("/{id}")
    public PedidoDetalleResponse obtener(@PathVariable String id) {
        return pedidoService.obtener(id);
    }
}
