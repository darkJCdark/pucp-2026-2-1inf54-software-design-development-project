package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.comun.api.PaginaResponse;
import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.config.EscenariosProperties;
import com.pucp.paqrap.modulos.escenarios.dto.CrearEscenarioRequest;
import com.pucp.paqrap.modulos.escenarios.dto.ConfiguracionSaRequest;
import com.pucp.paqrap.modulos.escenarios.dto.EscenarioResponse;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorSaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

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
        ConfiguracionSaRequest solicitada = request.configuracionSa();
        AnnealingConfig porDefecto = PlanificadorSaService.configuracionPorDefecto();
        long semilla = solicitada != null && solicitada.semilla() != null
                ? solicitada.semilla() : ThreadLocalRandom.current().nextLong();
        double temperaturaInicial = solicitada != null && solicitada.temperaturaInicial() != null
                ? solicitada.temperaturaInicial() : porDefecto.initialTemperature();
        double temperaturaMinima = solicitada != null && solicitada.temperaturaMinima() != null
                ? solicitada.temperaturaMinima() : porDefecto.minimumTemperature();
        double factorEnfriamiento = solicitada != null && solicitada.factorEnfriamiento() != null
                ? solicitada.factorEnfriamiento() : porDefecto.coolingFactor();
        int iteracionesPorTemperatura = solicitada != null && solicitada.iteracionesPorTemperatura() != null
                ? solicitada.iteracionesPorTemperatura() : porDefecto.iterationsPerTemperature();
        PlanificadorSaService.configuracionConParametros(temperaturaInicial, temperaturaMinima,
                factorEnfriamiento, iteracionesPorTemperatura);
        ScenarioExecutionEntity ejecucion = ejecucionRepository.save(new ScenarioExecutionEntity(request.tipo(),
                inicio.truncatedTo(ChronoUnit.MICROS), semilla, temperaturaInicial, temperaturaMinima,
                factorEnfriamiento, iteracionesPorTemperatura));
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
