package com.pucp.paqrap.modulos.escenarios.service;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.TramoRutaResponse;
import com.pucp.paqrap.modulos.redvial.entity.Location;

/** El ejecutor delega en la misma secuencia temporal de calles que se publica al frontend. */
class PosicionEstimadaTest {
    private static final Instant INICIO = Instant.parse("2026-09-10T12:00:00Z");
    private static Instant t(int segundos) { return INICIO.plusSeconds(segundos); }
    private static RutaResponse ruta() {
        return new RutaResponse("SA-TM06", "TM06", "MOTORCYCLE", t(0), t(600), 3, 3,
                List.of(new ParadaResponse(1,"ENTREGA","P1",null,11,12,1,t(400),t(600),1,0,t(360))),
                "CENTRAL",null,null,List.of(
                new TramoRutaResponse(1,10,10,11,10,t(0),t(60)),
                new TramoRutaResponse(1,11,10,11,11,t(180),t(240)),
                new TramoRutaResponse(1,11,11,11,12,t(240),t(360))));
    }
    @Test void avanzaPorLaCalleRealNoPorUnaL() {
        assertEquals(new Location(11,11), EjecutorPlanService.posicionEnRuta(ruta(),10,10,t(220)));
    }
    @Test void esperaEnElNodoAntesDeSalir() {
        assertEquals(new Location(11,10), EjecutorPlanService.posicionEnRuta(ruta(),10,10,t(120)));
    }
    @Test void respetaRefrigerioYServicio() {
        assertEquals(new Location(11,12), EjecutorPlanService.posicionEnRuta(ruta(),10,10,t(380)));
        assertEquals(new Location(11,12), EjecutorPlanService.posicionEnRuta(ruta(),10,10,t(500)));
    }
    @Test void noRetrocedeAlTerminar() {
        assertEquals(new Location(11,12), EjecutorPlanService.posicionEnRuta(ruta(),10,10,t(700)));
    }
    @Test void antesDeLaSalidaUsaLaPosicionConfirmada() {
        assertEquals(new Location(9,10), EjecutorPlanService.posicionEnRuta(ruta(),9,10,t(-1)));
    }
    @Test void rutaAntiguaSinTramosNoInventaMovimiento() {
        RutaResponse r = ruta();
        RutaResponse legado = new RutaResponse(r.id(),r.vehiculoId(),r.tipoVehiculo(),r.salida(),r.llegadaFinal(),
                r.distanciaKm(),r.costo(),r.paradas());
        assertEquals(new Location(10,10), EjecutorPlanService.posicionEnRuta(legado,10,10,t(220)));
    }
}
