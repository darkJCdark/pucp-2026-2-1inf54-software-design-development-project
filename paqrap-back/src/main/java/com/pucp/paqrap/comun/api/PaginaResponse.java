package com.pucp.paqrap.comun.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Página de resultados con un formato JSON estable ({@code Page} de Spring no lo garantiza). Páginas desde 0. */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> mapeo) {
        return new PaginaResponse<>(pagina.getContent().stream().map(mapeo).toList(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }
}
