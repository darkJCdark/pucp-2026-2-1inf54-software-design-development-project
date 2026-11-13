package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.escenarios.persistence.ScenarioExecutionEntity;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.service.InicializadorFlota;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.planificacion.persistence.PlanEntity;
import com.pucp.paqrap.modulos.planificacion.dto.MotivoReplanificacion;
import com.pucp.paqrap.modulos.planificacion.repository.PlanRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteRepository;
import com.pucp.paqrap.modulos.planificacion.repository.RouteStopRepository;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifica que el adaptador real no publique ni persista un candidato hasta confirmarlo. */
@SpringBootTest
@ActiveProfiles("test")
class PlanificadorCandidatoPersistenciaTest {

    private static final Instant HORA = Instant.parse("2026-09-09T12:00:00Z");

    @Autowired private PlanPersistenceService persistencia;
    @Autowired private PlanRepository planRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStopRepository routeStopRepository;
    @Autowired private ScenarioExecutionRepository ejecucionRepository;
    @Autowired private WarehouseRepository almacenRepository;
    @Autowired private VehicleRepository vehiculoRepository;
    @Autowired private OrderRepository pedidoRepository;
    @Autowired private RoadBlockRepository bloqueoRepository;

    private long ejecucionId;
    private PlanificadorSaAdapter adaptador;
    private ReplanificacionSaService replanificacion;

    @BeforeEach
    void preparar() {
        ejecucionId = ejecucionRepository.save(new ScenarioExecutionEntity(ScenarioType.FIVE_DAY, HORA)).getExecutionId();
        almacenRepository.saveAll(List.of(
                new WarehouseEntity("CENTRAL", WarehouseKind.CENTRAL, 27, 14, null),
                new WarehouseEntity("NOROESTE", WarehouseKind.INTERMEDIATE, 12, 38, 1_000),
                new WarehouseEntity("ESTE", WarehouseKind.INTERMEDIATE, 57, 27, 1_000)));
        vehiculoRepository.saveAll(InicializadorFlota.crearFlotaInicial().stream()
                .map(v -> new VehicleEntity(v.id(), v.type(), VehicleOperationalStatus.AVAILABLE)).toList());
        pedidoRepository.save(new OrderEntity("P-CANDIDATO", "cliente", new Location(30, 14), 1, HORA,
                DeliveryType.PRIORITY, 4));
        FuenteDatosOperativos fuente = new FuenteDatosOperativosMock();
        replanificacion = new ReplanificacionSaService(fuente, new PlanificadorSaService(fuente), persistencia);
        adaptador = new PlanificadorSaAdapter(replanificacion, pedidoRepository, bloqueoRepository,
                ejecucionRepository, 100, 1_500);
        adaptador.planificar(new PlanificadorPort.SolicitudCiclo(ejecucionId, HORA,
                PlanificadorPort.Motivo.INICIAL));
    }

    @AfterEach
    void limpiar() {
        routeStopRepository.deleteAll();
        routeRepository.deleteAll();
        planRepository.deleteAll();
        pedidoRepository.deleteAll();
        vehiculoRepository.deleteAll();
        almacenRepository.deleteAll();
        ejecucionRepository.deleteAll();
    }

    @Test
    void confirmaUnaVezYDescartarNoDejaSnapshotsNiRutasParciales() {
        Instant siguiente = HORA.plusSeconds(3_600);
        PlanificadorPort.SolicitudCiclo solicitud = new PlanificadorPort.SolicitudCiclo(ejecucionId, siguiente,
                PlanificadorPort.Motivo.INCIDENCIA, List.of(), List.of(),
                List.of(new BreakdownEvent("TA01", BreakdownType.MINOR, siguiente, new Location(27, 14))),
                List.of(), List.of());
        long planesIniciales = planRepository.count();
        long rutasIniciales = routeRepository.count();
        long paradasIniciales = routeStopRepository.count();

        PlanificadorCandidato.Candidato descartado = adaptador.preparar(solicitud);
        assertEquals(planesIniciales, planRepository.count());
        assertEquals(rutasIniciales, routeRepository.count());
        assertEquals(paradasIniciales, routeStopRepository.count());
        adaptador.descartar(descartado);
        assertEquals(planesIniciales, planRepository.count());
        assertEquals(rutasIniciales, routeRepository.count());
        assertEquals(paradasIniciales, routeStopRepository.count());
        assertEquals(0, replanificacion.planVigente(ejecucionId).orElseThrow().numeroReplanificacion());
        assertThrows(IllegalStateException.class, () -> adaptador.confirmar(descartado));
        assertEquals(planesIniciales, planRepository.count());

        PlanificadorCandidato.Candidato valido = adaptador.preparar(solicitud);
        adaptador.confirmar(valido);

        List<PlanEntity> snapshots = planRepository.findByExecutionExecutionIdOrderByPlanIdAsc(ejecucionId);
        assertEquals(planesIniciales + 1, snapshots.size());
        assertEquals(rutasIniciales + 1, routeRepository.count());
        assertEquals(paradasIniciales + 2, routeStopRepository.count());
        assertEquals(1, replanificacion.planVigente(ejecucionId).orElseThrow().numeroReplanificacion());
        assertEquals(MotivoReplanificacion.AVERIA,
                replanificacion.planVigente(ejecucionId).orElseThrow().motivo());
        assertThrows(IllegalStateException.class, () -> adaptador.confirmar(valido));
        assertEquals(planesIniciales + 1, planRepository.count());
    }
}
