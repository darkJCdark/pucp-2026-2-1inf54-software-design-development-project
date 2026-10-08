package com.pucp.paqrap.comun.error;

/** Se lanza cuando el recurso solicitado no existe; se responde con 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Object id) {
        super(recurso + " no encontrado: " + id);
    }
}
