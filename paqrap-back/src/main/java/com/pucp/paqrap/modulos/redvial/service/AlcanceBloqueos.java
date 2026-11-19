package com.pucp.paqrap.modulos.redvial.service;

import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.persistence.RoadBlockEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Qué bloqueos ve cada ejecución. La tabla {@code road_blocks} es global y acumula todos los meses cargados alguna
 * vez; una ejecución solo debe considerar los meses cuyo archivo {@code bloqueo.AAMM.txt} se cargó para ella (al
 * configurarla o, en Día a Día, durante la operación). Si no se cargó ninguno, la ejecución no tiene bloqueos aunque
 * existan en la base de datos.
 *
 * <p>Un bloqueo pertenece al mes de su archivo, que es el mes (hora de Lima) en que empieza. El alcance vive en
 * memoria: las ejecuciones activas no sobreviven a un reinicio (se cierran como FAILED al arrancar). Una ejecución
 * sin alcance registrado (creada por la API sin indicar meses, o tras un reinicio) conserva el comportamiento
 * anterior: ve todos los bloqueos de la base de datos.
 */
@Component
public class AlcanceBloqueos {

    private static final ZoneId ZONA = ShiftSchedule.DEFAULT_ZONE;

    private final Map<Long, Set<YearMonth>> periodos = new ConcurrentHashMap<>();

    /** Fija los meses de bloqueos de la ejecución; una lista vacía significa "sin bloqueos". */
    public void registrar(long ejecucionId, Collection<YearMonth> meses) {
        Set<YearMonth> conjunto = ConcurrentHashMap.newKeySet();
        conjunto.addAll(meses);
        periodos.put(ejecucionId, conjunto);
    }

    /** Agrega meses cargados durante la operación (p. ej. el archivo del mes siguiente en Día a Día). */
    public void agregar(long ejecucionId, Collection<YearMonth> meses) {
        periodos.computeIfAbsent(ejecucionId, id -> ConcurrentHashMap.newKeySet()).addAll(meses);
    }

    /** Meses habilitados; vacío si la ejecución no tiene alcance registrado (ve todos los bloqueos). */
    public Optional<Set<YearMonth>> periodos(long ejecucionId) {
        return Optional.ofNullable(periodos.get(ejecucionId)).map(Set::copyOf);
    }

    public boolean aplica(long ejecucionId, Instant inicioBloqueo) {
        Set<YearMonth> meses = periodos.get(ejecucionId);
        return meses == null || meses.contains(YearMonth.from(inicioBloqueo.atZone(ZONA)));
    }

    public List<RoadBlockEntity> filtrar(long ejecucionId, List<RoadBlockEntity> bloqueos) {
        if (!periodos.containsKey(ejecucionId)) {
            return bloqueos;
        }
        return bloqueos.stream().filter(bloqueo -> aplica(ejecucionId, bloqueo.getStartsAt())).toList();
    }
}
