package com.pucp.paqrap.modulos.planificacion.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;

import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.OperationalSimulatedAnnealingPlanner;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.NoAtendidoResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.planificacion.dto.ViolacionResponse;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.service.ReplanificacionService.Eventos;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.RoadLeg;
import com.pucp.paqrap.modulos.redvial.service.CargadorBloqueos;

/**
 * Simulación larga para medir cuándo colapsa el planificador (Semana 13). Usa el {@link ReplanificacionSaService}
 * real con la flota y los almacenes del mock, los pedidos y bloqueos de los archivos del curso a partir de
 * {@code paqrap.sim.inicio} y averías aleatorias con semilla fija. El planificador corre cada {@code Sa} minutos
 * (ejecución periódica, como los ejemplos del profesor) y entre ciclos el plan se ejecuta tal cual.
 *
 * <p><b>Colapso:</b> el primer pedido que no se puede entregar a tiempo: queda sin atender en un ciclo y su plazo
 * vence antes del ciclo siguiente, o una ruta vigente incumple su plazo (SLA_MISSED tras un bloqueo).
 *
 * <p>Se ejecuta solo con {@code -Dpaqrap.sim=true}. Propiedades: {@code paqrap.sim.inicio} (2026-01-01),
 * {@code paqrap.sim.dias} (150), {@code paqrap.sim.sa} (minutos entre planificaciones, 60),
 * {@code paqrap.sim.iteraciones} (tope de iteraciones del SA, 300; 0 = configuración de producción con
 * presupuesto), {@code paqrap.sim.presupuesto} (ms, 1500), {@code paqrap.sim.averiasDia} (0),
 * {@code paqrap.sim.seguir} (true: sigue después del primer colapso y cuenta fallas), {@code paqrap.sim.etiqueta}.
 * Salida en {@code target/simulacion/<etiqueta>_dias.csv} y un resumen en consola.
 */
class SimulacionColapsoTest {

    static final ZoneId ZONA = ShiftSchedule.DEFAULT_ZONE;
    static final long EJECUCION = 7L;
    static final Duration HORIZONTE_BLOQUEOS = Duration.ofHours(36);

    /** Ubicación de un vehículo en {@code t} según su ruta programada: el nodo de la calle en curso o la parada. */
    static Location ubicacionEn(List<ScheduledRouteStop> paradas, Location salida, Instant t) {
        Location ultima = salida;
        for (ScheduledRouteStop parada : paradas) {
            if (t.isBefore(parada.arrivedAt())) {
                for (RoadLeg calle : parada.approach().legs()) {
                    if (t.isBefore(calle.arrivesAt())) {
                        return calle.from();
                    }
                }
                return parada.stop().location();
            }
            ultima = parada.stop().location();
            if (t.isBefore(parada.completedAt())) {
                return ultima;
            }
        }
        return ultima;
    }

    @Test
    void ubicacionEn_devuelveLaParadaDuranteElServicio() {
        assertEquals(new Location(1, 1), ubicacionEn(List.of(), new Location(1, 1), Instant.EPOCH));
    }

    @Test
    @EnabledIfSystemProperty(named = "paqrap.sim", matches = "true")
    void simular() throws IOException {
        Path datos = Path.of(System.getProperty("paqrap.datos", "../paqrap-data"));
        LocalDate inicioFecha = LocalDate.parse(System.getProperty("paqrap.sim.inicio", "2026-01-01"));
        int dias = Integer.getInteger("paqrap.sim.dias", 150);
        int sa = Integer.getInteger("paqrap.sim.sa", 60);
        int iteraciones = Integer.getInteger("paqrap.sim.iteraciones", 300);
        long presupuesto = Long.getLong("paqrap.sim.presupuesto", 1500L);
        double averiasDia = Double.parseDouble(System.getProperty("paqrap.sim.averiasDia", "0"));
        boolean seguir = Boolean.parseBoolean(System.getProperty("paqrap.sim.seguir", "true"));
        int maxNuevos = Integer.getInteger("paqrap.sim.maxNuevos", 100);
        String politica = System.getProperty("paqrap.sim.politica", "fijo");
        boolean porEventos = "eventos".equals(politica);
        // Híbrido: plazo fijo para pedidos y bloqueos, pero una avería se replanifica en el paso siguiente.
        boolean hibrido = "hibrido".equals(politica);
        int paso = porEventos || hibrido ? Integer.getInteger("paqrap.sim.paso", 5) : sa;
        String etiqueta = System.getProperty("paqrap.sim.etiqueta", "sim_sa" + sa);
        Path salida = Path.of("target", "simulacion");
        Files.createDirectories(salida);
        Files.deleteIfExists(salida.resolve(etiqueta + "_avance.txt"));
        Files.deleteIfExists(salida.resolve(etiqueta + "_fallas_vivo.txt"));

        Instant inicio = inicioFecha.atStartOfDay(ZONA).toInstant();
        Instant fin = inicio.plus(Duration.ofDays(dias));
        List<Order> cola = new ArrayList<>();
        List<RoadBlock> bloqueos = new ArrayList<>();
        for (YearMonth mes = YearMonth.from(inicioFecha); !mes.atDay(1).atStartOfDay(ZONA).toInstant().isAfter(fin);
             mes = mes.plusMonths(1)) {
            Path ventas = datos.resolve("ventas").resolve(String.format("ventas.%04d%02d.txt", mes.getYear(),
                    mes.getMonthValue()));
            Path archivoBloqueos = datos.resolve("bloqueos").resolve(String.format("bloqueo.%02d%02d.txt",
                    mes.getYear() % 100, mes.getMonthValue()));
            if (Files.exists(ventas)) {
                cola.addAll(DiagnosticoPlanificadorTest.pedidosDesde(DiagnosticoPlanificadorTest.leer(ventas), mes));
            }
            if (Files.exists(archivoBloqueos)) {
                bloqueos.addAll(new CargadorBloqueos().cargar(DiagnosticoPlanificadorTest.leer(archivoBloqueos), mes,
                        ZONA));
            }
        }
        cola.removeIf(p -> p.registeredAt().isBefore(inicio) || !p.registeredAt().isBefore(fin));
        cola.sort(Comparator.comparing(Order::registeredAt).thenComparing(Order::id));
        bloqueos.sort(Comparator.comparing(RoadBlock::startsAt));

        AnnealingConfig config = iteraciones > 0
                ? new AnnealingConfig(1000.0, 1.0, 0.95, 50, iteraciones, iteraciones)
                : PlanificadorSaService.configuracionPorDefecto();
        long presupuestoCiclo = iteraciones > 0 ? OperationalSimulatedAnnealingPlanner.PRESUPUESTO_MAXIMO_MS
                : presupuesto;
        FuenteDatosOperativos fuente = new FuenteDatosOperativosMock();
        PlanificadorSaService planificador = new PlanificadorSaService(fuente, config);
        ReplanificacionSaService servicio = new ReplanificacionSaService(fuente, planificador,
                Mockito.mock(PlanPersistenceService.class));
        Random azar = new Random(20261111L);

        Map<String, Order> conocidos = new LinkedHashMap<>();
        Set<String> trasvases = new HashSet<>();
        Set<String> recargasIntermedio = new HashSet<>();
        Set<String> fallidos = new HashSet<>();
        List<String> detalleFallas = new ArrayList<>();
        List<BreakdownEvent> menoresActivas = new ArrayList<>();
        Instant primerColapso = null;
        String pedidoColapso = null;
        int ciclos = 0;
        long msTotal = 0;
        long msMax = 0;
        int averiasGeneradas = 0;
        Map<LocalDate, int[]> porDia = new LinkedHashMap<>(); // llegados, sin atender al cierre, ciclos, ms, max pendientes
        long reloj = System.nanoTime();

        Instant previo = null;
        Instant pasoAnterior = null;
        List<BreakdownEvent> averiasPendientes = new ArrayList<>();
        Map<String, String> afectadoPor = new HashMap<>();
        for (Instant t = inicio; !t.isAfter(fin); t = t.plus(Duration.ofMinutes(paso))) {
            // Averías del paso: el plan no cambia entre ciclos, así que se generan sobre las rutas vigentes.
            if (pasoAnterior != null) {
                Set<String> yaAveriados = new HashSet<>();
                averiasPendientes.forEach(a -> yaAveriados.add(a.vehicleId()));
                List<BreakdownEvent> delPaso = generarAverias(servicio.rutasVigentes(EJECUCION), pasoAnterior, t,
                        averiasDia, azar, yaAveriados);
                averiasGeneradas += delPaso.size();
                averiasPendientes.addAll(delPaso);
                for (BreakdownEvent averia : delPaso) {
                    servicio.rutasVigentes(EJECUCION).stream()
                            .filter(r -> r.vehiculoId().equals(averia.vehicleId()) && r.programada() != null)
                            .flatMap(r -> r.programada().scheduledStops().stream())
                            .filter(st -> st.stop() instanceof DeliveryStop && !st.arrivedAt().isBefore(averia.occurredAt()))
                            .forEach(st -> afectadoPor.put(((DeliveryStop) st.stop()).order().id(), averia.type() + " "
                                    + averia.vehicleId() + " " + DiagnosticoPlanificadorTest.hora(averia.occurredAt())));
                }
            }
            Instant ahora = t;
            boolean menorTerminada = menoresActivas.stream()
                    .anyMatch(m -> !m.occurredAt().plus(BreakdownType.MINOR.minimumUnavailable()).isAfter(ahora));
            Instant desdePaso = pasoAnterior;
            boolean bloqueoInicia = desdePaso != null && bloqueos.stream()
                    .anyMatch(b -> b.startsAt().isAfter(desdePaso) && !b.startsAt().isAfter(ahora));
            pasoAnterior = t;
            boolean toca = previo == null || !Duration.between(previo, t).minus(Duration.ofMinutes(sa)).isNegative();
            if (hibrido && !toca) {
                toca = !averiasPendientes.isEmpty() || menorTerminada;
            }
            if (porEventos && !toca) {
                toca = !DiagnosticoPlanificadorTest.tomarNuevos(cola, t, 1).isEmpty() || !averiasPendientes.isEmpty()
                        || menorTerminada || bloqueoInicia;
            }
            if (!toca) {
                continue;
            }
            List<Order> nuevos = DiagnosticoPlanificadorTest.tomarNuevos(cola, t, maxNuevos);
            cola.subList(0, nuevos.size()).clear();
            List<RoadBlock> horizonte = DiagnosticoPlanificadorTest.bloqueosRelevantes(bloqueos, t,
                    HORIZONTE_BLOQUEOS);
            ReplanificacionResponse respuesta;
            if (previo == null) {
                respuesta = servicio.iniciar(EJECUCION, new SolicitudPlanificacion(ModoOperacion.COLAPSO, t, nuevos,
                        horizonte, 42L, presupuestoCiclo, config));
            } else {
                List<BreakdownEvent> averias = new ArrayList<>(averiasPendientes);
                averiasPendientes.clear();
                List<BreakdownEvent> terminadas = new ArrayList<>();
                for (var it = menoresActivas.iterator(); it.hasNext(); ) {
                    BreakdownEvent menor = it.next();
                    if (!menor.occurredAt().plus(BreakdownType.MINOR.minimumUnavailable()).isAfter(t)) {
                        terminadas.add(menor);
                        it.remove();
                    }
                }
                averias.stream().filter(a -> a.type() == BreakdownType.MINOR).forEach(menoresActivas::add);
                Instant desdePrevio = previo;
                List<RoadBlock> terminados = bloqueos.stream()
                        .filter(b -> b.endsAt().isAfter(desdePrevio) && !b.endsAt().isAfter(ahora))
                        .toList();
                respuesta = servicio.replanificar(EJECUCION, new Eventos(nuevos, horizonte, terminados, averias,
                        terminadas, List.of()), t);
            }
            nuevos.forEach(p -> conocidos.put(p.id(), p));
            ciclos++;
            for (var ruta : respuesta.plan().rutas()) {
                for (var parada : ruta.paradas()) {
                    if ("TRASVASE".equals(parada.tipo())) {
                        trasvases.add(ruta.vehiculoId() + "@" + parada.x() + "," + parada.y() + "@" + parada.llegada());
                    } else if ("ALMACEN".equals(parada.tipo()) && parada.paquetes() > 0
                            && !"CENTRAL".equals(parada.almacenId()) && parada.llegada() != null) {
                        recargasIntermedio.add(ruta.vehiculoId() + "@" + parada.almacenId() + "@" + parada.llegada());
                    }
                }
            }
            long ms = respuesta.plan().duracionMs();
            msTotal += ms;
            msMax = Math.max(msMax, ms);

            // El próximo ciclo posible: en plazo fijo dentro de Sa; por eventos, como pronto en el paso siguiente.
            Instant siguiente = t.plus(Duration.ofMinutes(porEventos ? paso : sa));
            List<NoAtendidoResponse> noAtendidos = respuesta.plan().noAtendidos();
            for (NoAtendidoResponse no : noAtendidos) {
                if (!no.plazo().isAfter(siguiente) && fallidos.add(no.pedidoId())) {
                    Order original = conocidos.get(no.pedidoId());
                    String linea = DiagnosticoPlanificadorTest.hora(t) + " sin atender " + no.pedidoId() + " ("
                            + no.paquetes() + " paq., plazo " + DiagnosticoPlanificadorTest.hora(no.plazo())
                            + (original == null ? "" : ", registrado " + DiagnosticoPlanificadorTest.hora(original.registeredAt())
                            + " destino " + original.destination())
                            + ") averia: " + afectadoPor.getOrDefault(no.pedidoId(), "ninguna");
                    detalleFallas.add(linea);
                    Files.writeString(salida.resolve(etiqueta + "_fallas_vivo.txt"), linea + System.lineSeparator(),
                            StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
                    if (primerColapso == null) {
                        primerColapso = no.plazo();
                        pedidoColapso = no.pedidoId();
                    }
                }
            }
            for (ViolacionResponse violacion : respuesta.plan().violaciones()) {
                if (violacion.tipo().equals(PlanViolationType.SLA_MISSED.name())) {
                    String id = violacion.detalle().replaceAll(".* for ", "");
                    if (fallidos.add(id)) {
                        detalleFallas.add(DiagnosticoPlanificadorTest.hora(t) + " SLA_MISSED " + id + " ruta "
                                + violacion.rutaId() + " motivo " + respuesta.motivo());
                        servicio.rutasVigentes(EJECUCION).stream()
                                .filter(r -> r.ruta().id().equals(violacion.rutaId()) && r.programada() != null)
                                .forEach(r -> {
                                    detalleFallas.add("   salida " + DiagnosticoPlanificadorTest.hora(r.ruta().departureAt())
                                            + " origen snapshot " + DiagnosticoPlanificadorTest.hora(r.snapshotOrigen().planningTime()));
                                    for (ScheduledRouteStop parada : r.programada().scheduledStops()) {
                                        detalleFallas.add("   " + parada.stop().getClass().getSimpleName() + " "
                                                + parada.stop().location() + " llega "
                                                + DiagnosticoPlanificadorTest.hora(parada.arrivedAt())
                                                + (parada.stop() instanceof DeliveryStop e ? " plazo "
                                                + DiagnosticoPlanificadorTest.hora(e.order().deadline()) + " " + e.order().id() : "")
                                                + " km " + parada.approach().distanceKm()
                                                + " espera " + Duration.between(parada.approach().legs().isEmpty()
                                                ? parada.arrivedAt() : parada.approach().legs().getFirst().departsAt(),
                                                parada.arrivedAt()).toMinutes() + "min");
                                    }
                                });
                        if (primerColapso == null) {
                            primerColapso = t;
                            pedidoColapso = id;
                        }
                    }
                }
            }
            LocalDate dia = t.atZone(ZONA).toLocalDate();
            if (!porDia.containsKey(dia) && !porDia.isEmpty()) {
                LocalDate ayer = dia.minusDays(1);
                int[] f = porDia.get(ayer);
                String avance = String.format("[DIA %s] %s llegados=%d sin_atender=%d max=%d ms_prom=%d fallidos=%d real=%ds",
                        etiqueta, ayer, f == null ? 0 : f[0], f == null ? 0 : f[1], f == null ? 0 : f[4],
                        f == null || f[2] == 0 ? 0 : f[3] / f[2], fallidos.size(),
                        Duration.ofNanos(System.nanoTime() - reloj).toSeconds());
                System.out.println(avance);
                Files.writeString(salida.resolve(etiqueta + "_avance.txt"), avance + System.lineSeparator(),
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            }
            int[] fila = porDia.computeIfAbsent(dia, d -> new int[5]);
            fila[0] += nuevos.size();
            fila[1] = noAtendidos.size();
            fila[2]++;
            fila[3] += (int) ms;
            fila[4] = Math.max(fila[4], noAtendidos.size());
            previo = t;
            if (primerColapso != null && !seguir) {
                break;
            }
        }

        List<String> lineas = new ArrayList<>();
        lineas.add("dia,llegados,sin_atender_cierre,max_sin_atender,ciclos,ms_promedio");
        porDia.forEach((d, f) -> lineas.add(d + "," + f[0] + "," + f[1] + "," + f[4] + "," + f[2] + ","
                + (f[2] == 0 ? 0 : f[3] / f[2])));
        Files.write(salida.resolve(etiqueta + "_dias.csv"), lineas, StandardCharsets.UTF_8);
        Files.write(salida.resolve(etiqueta + "_fallas.txt"), detalleFallas, StandardCharsets.UTF_8);
        long minutos = Duration.ofNanos(System.nanoTime() - reloj).toMinutes();
        String resumen = String.format("[SIM %s] " + (porEventos || hibrido ? politica + "(paso " + paso + ")" : "fijo") + " Sa=%d min, iter=%d, averias/dia=%.1f, dias=%d, ciclos=%d, pedidos=%d,"
                        + " averias=%d, trasvases=%d, recargas_intermedio=%d, fallidos=%d, primer colapso=%s (%s, dia %s), ms prom=%d, ms max=%d, real=%d min",
                etiqueta, sa, iteraciones, averiasDia, dias, ciclos, conocidos.size(), averiasGeneradas, trasvases.size(),
                recargasIntermedio.size(),
                fallidos.size(), primerColapso == null ? "ninguno" : DiagnosticoPlanificadorTest.hora(primerColapso),
                pedidoColapso, primerColapso == null ? "-"
                        : String.valueOf(Duration.between(inicio, primerColapso).toHours() / 24.0),
                ciclos == 0 ? 0 : msTotal / ciclos, msMax, minutos);
        System.out.println(resumen);
        Files.writeString(salida.resolve(etiqueta + "_resumen.txt"), resumen + System.lineSeparator(),
                StandardCharsets.UTF_8);
        assertTrue(ciclos > 0);
    }

    /**
     * Averías entre dos ciclos: el número sigue una Poisson de media {@code averiasDia} por día; cada una cae en un
     * instante uniforme del intervalo sobre un vehículo que en ese instante está en ruta. Tipos 60/30/10 %.
     */
    static List<BreakdownEvent> generarAverias(List<RutaVigente> rutas, Instant desde, Instant hasta,
                                               double averiasDia, Random azar, Set<String> yaAveriados) {
        List<BreakdownEvent> averias = new ArrayList<>();
        if (averiasDia <= 0) {
            return averias;
        }
        double media = averiasDia * Duration.between(desde, hasta).toMinutes() / 1440.0;
        int cantidad = poisson(media, azar);
        Set<String> averiados = new HashSet<>(yaAveriados);
        for (int i = 0; i < cantidad; i++) {
            Instant t = desde.plusSeconds((long) (azar.nextDouble() * Duration.between(desde, hasta).getSeconds()));
            List<RutaVigente> enRuta = rutas.stream()
                    .filter(r -> r.programada() != null && r.ruta().departureAt().isBefore(t)
                            && r.programada().completedAt().isAfter(t) && !averiados.contains(r.vehiculoId())
                            && r.programada().scheduledStops().stream().anyMatch(p -> p.stop() instanceof DeliveryStop))
                    .sorted(Comparator.comparing(RutaVigente::vehiculoId))
                    .toList();
            if (enRuta.isEmpty()) {
                continue;
            }
            RutaVigente ruta = enRuta.get(azar.nextInt(enRuta.size()));
            double tipo = azar.nextDouble();
            BreakdownType clase = tipo < 0.6 ? BreakdownType.MINOR
                    : tipo < 0.9 ? BreakdownType.INTERMEDIATE : BreakdownType.MAJOR;
            averiados.add(ruta.vehiculoId());
            averias.add(new BreakdownEvent(ruta.vehiculoId(), clase, t,
                    ubicacionEn(ruta.programada().scheduledStops(), ruta.ruta().startLocation(), t)));
        }
        averias.sort(Comparator.comparing(BreakdownEvent::occurredAt));
        return averias;
    }

    private static int poisson(double media, Random azar) {
        double limite = Math.exp(-media);
        double producto = azar.nextDouble();
        int k = 0;
        while (producto > limite) {
            producto *= azar.nextDouble();
            k++;
        }
        return k;
    }

}
