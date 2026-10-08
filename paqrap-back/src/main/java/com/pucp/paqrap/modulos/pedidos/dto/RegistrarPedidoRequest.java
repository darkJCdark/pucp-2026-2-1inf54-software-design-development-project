package com.pucp.paqrap.modulos.pedidos.dto;

import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * Registro manual de un pedido (CU-02). El deadline no se envía: se calcula con el plazo. {@code horasPrometidas}
 * es obligatorio en PRIORITY (4, 8, 12 o 18) y opcional en REGULAR (siempre 36). {@code registradoEn} por defecto
 * es el momento del registro.
 */
public record RegistrarPedidoRequest(
        @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9]{0,31}",
                message = "debe empezar con una letra y tener hasta 32 letras o dígitos") String clienteId,
        @NotNull @Min(0) @Max(70) Integer x,
        @NotNull @Min(0) @Max(50) Integer y,
        @NotNull @Positive Integer paquetes,
        @NotNull DeliveryType tipoEntrega,
        Integer horasPrometidas,
        Instant registradoEn) {
}
