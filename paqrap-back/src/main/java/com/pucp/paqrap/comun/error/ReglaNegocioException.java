package com.pucp.paqrap.comun.error;

/** Se lanza cuando una operación viola una regla del negocio o un conflicto de estado; se responde con 409. */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
