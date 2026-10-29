package com.pucp.paqrap.modulos.escenarios.dto;

/** Par?metros opcionales de SA al crear un escenario. Los valores faltantes se completan en el servicio. */
public record ConfiguracionSaRequest(Long semilla, Double temperaturaInicial, Double temperaturaMinima,
                                    Double factorEnfriamiento, Integer iteracionesPorTemperatura) {
    public ConfiguracionSaRequest {
        validarFinito(temperaturaInicial, "temperaturaInicial");
        validarFinito(temperaturaMinima, "temperaturaMinima");
        validarFinito(factorEnfriamiento, "factorEnfriamiento");
        if (temperaturaInicial != null && temperaturaInicial <= 0) {
            throw new IllegalArgumentException("temperaturaInicial debe ser positiva");
        }
        if (temperaturaMinima != null && temperaturaMinima <= 0) {
            throw new IllegalArgumentException("temperaturaMinima debe ser positiva");
        }
        if (factorEnfriamiento != null && (factorEnfriamiento < 0.80 || factorEnfriamiento > 0.99)) {
            throw new IllegalArgumentException("factorEnfriamiento debe estar entre 0.80 y 0.99");
        }
        if (iteracionesPorTemperatura != null && iteracionesPorTemperatura <= 0) {
            throw new IllegalArgumentException("iteracionesPorTemperatura debe ser positivo");
        }
    }

    private static void validarFinito(Double valor, String campo) {
        if (valor != null && !Double.isFinite(valor)) {
            throw new IllegalArgumentException(campo + " debe ser finito");
        }
    }
}
