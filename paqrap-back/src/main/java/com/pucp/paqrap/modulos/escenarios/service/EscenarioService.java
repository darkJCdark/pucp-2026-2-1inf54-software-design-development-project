package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.comun.api.PaginaResponse;
import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.config.EscenariosProperties;
import com.pucp.paqrap.modulos.escenarios.dto.CrearEscenarioRequest;
import com.pucp.paqrap.modulos.escenarios.dto.EscenarioResponse;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Configuración (CU-09), ejecución (CU-10) y consulta de escenarios. El ciclo de vida lo ejecuta el motor. */
@Service
public class EscenarioService {

    private final ScenarioExecutionRepository ejecucionRepository;
    private final MotorEscenarios motor;
    private final EscenariosProperties propiedades;
    private final Clock relojReal;

    public EscenarioService(ScenarioExecutionRepository ejecucionRepository, MotorEscenarios motor,
                            EscenariosProperties propiedades, Clock relojReal) {
        this.ejecucionRepository = ejecucionRepository;
        this.motor = motor;
        this.propiedades = propiedades;
        this.relojReal = relojReal;
    }

    public EscenarioResponse crear(CrearEscenarioRequest request) {
        Instant inicio;
        if (request.tipo() == ScenarioType.DAY_TO_DAY) {
            if (request.inicioSimulado() != null) {
                throw new IllegalArgumentException("DAY_TO_DAY siempre parte de la hora actual; no envíe 'inicioSimulado'");
            }
            inicio = relojReal.instant();
        } else {
            if (request.inicioSimulado() == null) {
                throw new IllegalArgumentException(request.tipo() + " requiere 'inicioSimulado'");
            }
            inicio = request.inicioSimulado();
        }
        ScenarioExecutionEntity ejecucion = ejecucionRepository.save(
                new ScenarioExecutionEntity(request.tipo(), inicio.truncatedTo(ChronoUnit.MICROS)));
        return respuesta(ejecucion);
    }

    public PaginaResponse<EscenarioResponse> listar(int pagina, int tamanio) {
        return PaginaResponse.de(ejecucionRepository.findAll(
                PageRequest.of(pagina, tamanio, Sort.by(Sort.Direction.DESC, "executionId"))), this::respuesta);
    }

    public EscenarioResponse obtener(long id) {
        return respuesta(ejecucionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ejecución", id)));
    }

    public EscenarioResponse iniciar(long id) {
        motor.iniciar(id);
        return obtener(id);
    }

    public EscenarioResponse pausar(long id) {
        motor.pausar(id);
        return obtener(id);
    }

    public EscenarioResponse reanudar(long id) {
        motor.reanudar(id);
        return obtener(id);
    }

    public EscenarioResponse detener(long id) {
        motor.detener(id);
        return obtener(id);
    }

    private EscenarioResponse respuesta(ScenarioExecutionEntity ejecucion) {
        return EscenarioResponse.de(ejecucion, propiedades.factorPara(ejecucion.getScenarioType()),
                motor.instanteSimulado(ejecucion.getExecutionId()).orElse(null));
    }
}
