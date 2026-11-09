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
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.pedidos.service.CargadorPedidos;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.OperationalSimulatedAnnealingPlanner;
import com.pucp.paqrap.modulos.planificacion.dto.ModoOperacion;
import com.pucp.paqrap.modulos.planificacion.dto.NoAtendidoResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.service.ClasificadorNoAtendidos.Causa;
import com.pucp.paqrap.modulos.planificacion.service.ClasificadorNoAtendidos.Resultado;
import com.pucp.paqrap.modulos.planificacion.service.ReplanificacionService.Eventos;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.CargadorBloqueos;

/**
 * Paso 8: diagnóstico de por qué ciertos pedidos quedan sin atender (caso 202812-00084 y caso "todos los vehículos
 * ocupados"). Emula el motor con el {@link ReplanificacionSaService} real, sin modificarlo: arranca en el inicio de
 * diciembre de 2028 y, durante la ventana pedida, replanifica cada {@code paso} minutos si llegaron pedidos nuevos y
 * cada hora en punto aunque no lleguen.
 *
 * <p>Se ejecuta solo con {@code -Dpaqrap.diag=true}. Propiedades opcionales: {@code paqrap.datos} (por defecto
 * {@code ../paqrap-data}), {@code paqrap.diag.horas} (ventana, 6), {@code paqrap.diag.paso} (minutos, 5),
 * {@code paqrap.diag.iteraciones} (tope fijo de iteraciones del SA, 300), {@code paqrap.diag.maxNuevos} (pedidos nuevos
 * por ciclo, 100, como {@code max-pedidos} del adaptador) y {@code paqrap.diag.pedido} (pedido a seguir,
 * {@code 202812-00084}). El SA corre sin límite de tiempo y con semilla fija, de modo que la corrida es reproducible.
 *
 * <p>Salidas en {@code target/diagnostico/}: {@code ciclos.csv}, {@code no_atendidos.csv} e
 * {@code informe_<pedido>.txt}.
 *
 * <p>Límites del diagnóstico: sin pausas, colapso, mantenimientos ni averías; los bloqueos del mes se entregan
 * desde el inicio (los futuros del horizonte no son un evento); la clasificación ver {@link ClasificadorNoAtendidos}.
 */
class DiagnosticoPlanificadorTest {

    static final ZoneId ZONA = ShiftSchedule.DEFAULT_ZONE;
    static final long EJECUCION = 1L;
    static final long SEMILLA = 42L;
    static final Duration HORIZONTE_BLOQUEOS = Duration.ofDays(5);

    static final String CABECERA_CICLOS = "ciclo,hora,motivo,nuevos_enviados,en_cola_sin_enviar,pedidos_conocidos,"
            + "pendientes_planificados,asignados_en_rutas,no_atendidos,rutas_conservadas,rutas_nuevas,"
            + "vehiculos_ocupados,vehiculos_libres,autos_libres,ms,presupuesto_agotado";
    static final String CABECERA_NO_ATENDIDOS = "pedido,paquetes,registrado,plazo,primer_ciclo_sin_atender,"
            + "hora_primer_ciclo,causa_primer_ciclo,detalle_primer_ciclo,ciclos_sin_atender,"
            + "primer_ciclo_asignado,estado_final,causa_final,detalle_final";

    // ---------------------------------------------------------------- utilidades verificadas por pruebas

    /** Instantes de ciclo posibles: desde {@code inicio} hasta {@code inicio + horas}, ambos incluidos. */
    static List<Instant> instantesDeCiclo(Instant inicio, int horas, int pasoMinutos) {
        if (horas <= 0 || pasoMinutos <= 0) {
            throw new IllegalArgumentException("horas y paso deben ser positivos");
        }
        Instant fin = inicio.plus(Duration.ofHours(horas));
        List<Instant> instantes = new ArrayList<>();
        for (Instant t = inicio; !t.isAfter(fin); t = t.plus(Duration.ofMinutes(pasoMinutos))) {
            instantes.add(t);
        }
        return instantes;
    }

    /** Hasta {@code maximo} pedidos de la cola (ordenada por registro) ya registrados a {@code ahora}. */
    static List<Order> tomarNuevos(List<Order> cola, Instant ahora, int maximo) {
        if (maximo <= 0) {
            throw new IllegalArgumentException("maximo debe ser positivo");
        }
        List<Order> nuevos = new ArrayList<>();
        for (Order pedido : cola) {
            if (pedido.registeredAt().isAfter(ahora) || nuevos.size() >= maximo) {
                break;
            }
            nuevos.add(pedido);
        }
        return nuevos;
    }

    /** Hay ciclo si llegaron pedidos nuevos o si es una hora en punto contada desde {@code inicio}. */
    static boolean correCiclo(Instant inicio, Instant ahora, boolean hayNuevos) {
        long minutos = Duration.between(inicio, ahora).toMinutes();
        return hayNuevos || (minutos > 0 && minutos % 60 == 0);
    }

    static List<String> leer(Path archivo) throws IOException {
        return Files.readString(archivo, StandardCharsets.UTF_8).replace("﻿", "").lines().toList();
    }

    static List<Order> pedidosDesde(List<String> lineas, YearMonth periodo) {
        List<Order> pedidos = new ArrayList<>();
        for (CargadorPedidos.PedidoArchivo leido : new CargadorPedidos().cargar(lineas, periodo, ZONA)) {
            pedidos.add(new Order(leido.id(), leido.destino(), leido.paquetes(), leido.registradoEn(),
                    leido.registradoEn().plus(Duration.ofHours(leido.horasPrometidas()))));
        }
        pedidos.sort(Comparator.comparing(Order::registeredAt));
        return pedidos;
    }

    static List<RoadBlock> bloqueosRelevantes(List<RoadBlock> bloqueos, Instant hora, Duration horizonte) {
        Instant limite = hora.plus(horizonte);
        return bloqueos.stream().filter(b -> b.endsAt().isAfter(hora) && b.startsAt().isBefore(limite)).toList();
    }

    static String hora(Instant instante) {
        return instante.atZone(ZONA).toLocalDateTime().toString();
    }

    static Set<String> pedidosDe(RutaVigente ruta) {
        Set<String> ids = new HashSet<>();
        ruta.ruta().stops().forEach(parada -> {
            if (parada instanceof DeliveryStop entrega) {
                ids.add(entrega.order().id());
            }
        });
        return ids;
    }

    // ---------------------------------------------------------------- pruebas de las utilidades

    @Test
    void instantesDeCiclo_cubrenLaVentanaCompletaConPasoFijo() {
        Instant inicio = LocalDate.of(2028, 12, 1).atStartOfDay(ZONA).toInstant();

        List<Instant> instantes = instantesDeCiclo(inicio, 6, 5);

        assertEquals(73, instantes.size());
        assertEquals(inicio, instantes.get(0));
        assertEquals(inicio.plus(Duration.ofHours(6)), instantes.get(72));
        assertEquals("2028-12-01T06:00", hora(instantes.get(72)));
        assertEquals(List.of(inicio, inicio.plus(Duration.ofHours(1))), instantesDeCiclo(inicio, 1, 60));
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> instantesDeCiclo(inicio, 0, 5)).getMessage().contains("positivos"));
    }

    @Test
    void tomarNuevos_respetaElTopeElOrdenYLaHoraDeRegistro() {
        Instant t0 = Instant.parse("2028-12-01T05:00:00Z");
        List<Order> cola = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            cola.add(new Order("P" + i, new Location(1, 1), 1, t0.plusSeconds(i), t0.plusSeconds(i + 3600)));
        }

        List<Order> primeros = tomarNuevos(cola, t0.plusSeconds(1000), 100);
        List<Order> aun = tomarNuevos(cola, t0.plusSeconds(49), 100);

        assertEquals(100, primeros.size());
        assertEquals("P0", primeros.get(0).id());
        assertEquals("P99", primeros.get(99).id());
        assertEquals(50, aun.size());
        assertEquals("P49", aun.get(49).id());
        assertTrue(tomarNuevos(cola, t0.minusSeconds(1), 100).isEmpty());
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> tomarNuevos(cola, t0, 0)).getMessage().contains("positivo"));
    }

    @Test
    void correCiclo_conNuevosOCadaHoraEnPunto() {
        Instant inicio = Instant.parse("2028-12-01T05:00:00Z");

        assertTrue(correCiclo(inicio, inicio.plus(Duration.ofMinutes(5)), true));
        assertFalse(correCiclo(inicio, inicio.plus(Duration.ofMinutes(5)), false));
        assertTrue(correCiclo(inicio, inicio.plus(Duration.ofMinutes(60)), false));
        assertTrue(correCiclo(inicio, inicio.plus(Duration.ofMinutes(180)), false));
        assertFalse(correCiclo(inicio, inicio.plus(Duration.ofMinutes(90)), false));
        assertFalse(correCiclo(inicio, inicio, false));
    }

    @Test
    void pedidosDesde_generaElIdYElPlazoDelArchivo() {
        List<Order> pedidos = pedidosDesde(List.of("01d01h30m:51,11,c0011,08,08", "01d01h32m:66,41,c0057,10,04"),
                YearMonth.of(2028, 12));

        assertEquals(2, pedidos.size());
        Order segundo = pedidos.get(1);
        assertEquals("202812-00002", segundo.id());
        assertEquals(10, segundo.packages());
        assertEquals(new Location(66, 41), segundo.destination());
        assertEquals(Duration.ofHours(4), Duration.between(segundo.registeredAt(), segundo.deadline()));
    }

    // ---------------------------------------------------------------- diagnóstico (opt-in)

    private record Hallazgo(int ciclo, Instant hora, Resultado resultado) {
    }

    @Test
    @EnabledIfSystemProperty(named = "paqrap.diag", matches = "true")
    void ejecutarDiagnostico() throws IOException {
        Path datos = Path.of(System.getProperty("paqrap.datos", "../paqrap-data"));
        int horas = Integer.getInteger("paqrap.diag.horas", 6);
        int paso = Integer.getInteger("paqrap.diag.paso", 5);
        int iteraciones = Integer.getInteger("paqrap.diag.iteraciones", 300);
        int maxNuevos = Integer.getInteger("paqrap.diag.maxNuevos", 100);
        String idObjetivo = System.getProperty("paqrap.diag.pedido", "202812-00084");
        Path salida = Path.of("target", "diagnostico");
        Files.createDirectories(salida);

        YearMonth periodo = YearMonth.of(2028, 12);
        FuenteDatosOperativos fuente = new FuenteDatosOperativosMock();
        Warehouse central = fuente.almacenes().stream().filter(Warehouse::isCentral).findFirst().orElseThrow();
        List<Vehicle> flota = fuente.flota();
        List<Order> todos = pedidosDesde(leer(datos.resolve("ventas").resolve("ventas.202812.txt")), periodo);
        List<RoadBlock> bloqueos = new CargadorBloqueos().cargar(
                leer(datos.resolve("bloqueos").resolve("bloqueo.2812.txt")), periodo, ZONA);

        Instant inicio = LocalDate.of(2028, 12, 1).atStartOfDay(ZONA).toInstant();
        Instant fin = inicio.plus(Duration.ofHours(horas));
        List<Order> cola = new ArrayList<>(todos.stream().filter(p -> !p.registeredAt().isAfter(fin)).toList());
        Order objetivo = todos.stream().filter(p -> p.id().equals(idObjetivo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe el pedido " + idObjetivo));
        assertTrue(!objetivo.registeredAt().isAfter(fin), "el pedido a seguir debe registrarse dentro de la ventana");

        // SA de producción en temperatura, con tope fijo de iteraciones y sin límite de tiempo.
        AnnealingConfig config = new AnnealingConfig(1000.0, 1.0, 0.95, 50, iteraciones, iteraciones);
        PlanificadorSaService planificador = new PlanificadorSaService(fuente, config);
        ReplanificacionSaService servicio = new ReplanificacionSaService(fuente, planificador,
                Mockito.mock(PlanPersistenceService.class));
        ClasificadorNoAtendidos clasificador = new ClasificadorNoAtendidos(planificador.evaluador());
        List<RoadBlock> bloqueosIniciales = bloqueosRelevantes(bloqueos, inicio,
                HORIZONTE_BLOQUEOS.plus(Duration.ofHours(horas)));

        Map<String, Order> conocidos = new LinkedHashMap<>();
        Map<String, Hallazgo> primeraVez = new LinkedHashMap<>();
        Map<String, Integer> ciclosSinAtender = new HashMap<>();
        Map<String, Integer> primerCicloAsignado = new HashMap<>();
        Map<String, Resultado> ultimaCausa = new HashMap<>();
        Map<String, NoAtendidoResponse> ultimosNoAtendidos = new HashMap<>();
        List<String> lineasCiclos = new ArrayList<>();
        lineasCiclos.add(CABECERA_CICLOS);
        List<String> traza = new ArrayList<>();
        int ciclo = 0;
        int cicloObjetivoAsignado = -1;
        long msTotal = 0;
        long msMaximo = 0;
        int maxPendientes = 0;
        int maxCola = 0;
        long sumaPendientes = 0;
        Instant ultimoInstante = inicio;

        List<Instant> instantes = instantesDeCiclo(inicio, horas, paso);
        for (int i = 0; i < instantes.size(); i++) {
            Instant t = instantes.get(i);
            List<Order> nuevos = tomarNuevos(cola, t, maxNuevos);
            if (ciclo > 0 && !correCiclo(inicio, t, !nuevos.isEmpty())) {
                continue;
            }
            cola.subList(0, nuevos.size()).clear();
            ReplanificacionResponse respuesta;
            if (ciclo == 0) {
                respuesta = servicio.iniciar(EJECUCION, new SolicitudPlanificacion(ModoOperacion.SIMULACION_5D, t,
                        nuevos, bloqueosIniciales, SEMILLA, OperationalSimulatedAnnealingPlanner.PRESUPUESTO_MAXIMO_MS,
                        config));
            } else {
                respuesta = servicio.replanificar(EJECUCION, new Eventos(nuevos, List.of(), List.of()), t);
            }
            nuevos.forEach(p -> conocidos.put(p.id(), p));
            ultimoInstante = t;

            List<RutaVigente> rutas = servicio.rutasVigentes(EJECUCION);
            Set<String> idsNuevas = Set.copyOf(respuesta.rutasNuevas());
            Set<String> asignadosNuevos = new HashSet<>();
            Set<String> asignadosTodos = new HashSet<>();
            Set<String> ocupados = new HashSet<>();
            for (RutaVigente ruta : rutas) {
                ocupados.add(ruta.vehiculoId());
                Set<String> ids = pedidosDe(ruta);
                asignadosTodos.addAll(ids);
                if (idsNuevas.contains(ruta.ruta().id())) {
                    asignadosNuevos.addAll(ids);
                }
            }
            for (String id : asignadosTodos) {
                primerCicloAsignado.putIfAbsent(id, ciclo);
            }
            List<NoAtendidoResponse> noAtendidos = respuesta.plan().noAtendidos();
            Set<String> pendientes = new HashSet<>(asignadosNuevos);
            noAtendidos.forEach(n -> pendientes.add(n.pedidoId()));
            long autosLibres = flota.stream().filter(v -> v.type() == VehicleType.CAR)
                    .filter(v -> !ocupados.contains(v.id())).count();

            long ms = respuesta.plan().duracionMs();
            msTotal += ms;
            msMaximo = Math.max(msMaximo, ms);
            maxPendientes = Math.max(maxPendientes, pendientes.size());
            int enCola = tomarNuevos(cola, t, Integer.MAX_VALUE).size();
            maxCola = Math.max(maxCola, enCola);
            sumaPendientes += pendientes.size();
            lineasCiclos.add(String.join(",", String.valueOf(ciclo), hora(t), respuesta.motivo().name(),
                    String.valueOf(nuevos.size()), String.valueOf(enCola),
                    String.valueOf(conocidos.size()), String.valueOf(pendientes.size()),
                    String.valueOf(asignadosTodos.size()), String.valueOf(noAtendidos.size()),
                    String.valueOf(respuesta.rutasConservadas().size()), String.valueOf(respuesta.rutasNuevas().size()),
                    String.valueOf(ocupados.size()), String.valueOf(flota.size() - ocupados.size()),
                    String.valueOf(autosLibres), String.valueOf(ms),
                    String.valueOf(respuesta.plan().presupuestoAgotado())));

            // Clasificación de los no atendidos: la primera vez que aparecen y, para el pedido a seguir, en cada ciclo.
            OperationalSnapshot base = null;
            for (NoAtendidoResponse no : noAtendidos) {
                ultimosNoAtendidos.put(no.pedidoId(), no);
                ciclosSinAtender.merge(no.pedidoId(), 1, Integer::sum);
                boolean esObjetivo = no.pedidoId().equals(idObjetivo);
                if (primeraVez.containsKey(no.pedidoId()) && !esObjetivo) {
                    continue;
                }
                if (base == null) {
                    base = ClasificadorNoAtendidos.snapshotEnCentral(fuente, central, t);
                }
                Order original = conocidos.get(no.pedidoId());
                Order pedido = new Order(original.id(), original.destination(), no.paquetes(),
                        original.registeredAt(), original.deadline());
                Resultado resultado = clasificador.clasificar(pedido, t, base, central, flota, rutas,
                        servicio.bloqueosVigentes(EJECUCION));
                primeraVez.putIfAbsent(no.pedidoId(), new Hallazgo(ciclo, t, resultado));
                ultimaCausa.put(no.pedidoId(), resultado);
                if (esObjetivo) {
                    traza.add("[" + hora(t) + "] SIN ATENDER · " + resultado.causa() + " · " + resultado.detalle()
                            + " · autos libres: " + autosLibres + " · vehículos ocupados: " + ocupados.size());
                }
            }
            Set<String> sinAtenderAhora = new HashSet<>();
            noAtendidos.forEach(n -> sinAtenderAhora.add(n.pedidoId()));
            if (conocidos.containsKey(idObjetivo) && !sinAtenderAhora.contains(idObjetivo)
                    && (cicloObjetivoAsignado < 0 || ciclo <= cicloObjetivoAsignado + 2)) {
                traza.add("[" + hora(t) + "] " + describirAsignacion(rutas, objetivo, ciclo));
                if (cicloObjetivoAsignado < 0 && asignadosTodos.contains(idObjetivo)) {
                    cicloObjetivoAsignado = ciclo;
                }
            }
            ciclo++;
        }

        // Clasificación final de lo que sigue sin atender en el último ciclo.
        List<RutaVigente> rutasFinales = servicio.rutasVigentes(EJECUCION);
        Set<String> sinAtenderFinal = new HashSet<>(servicio.planVigente(EJECUCION).orElseThrow().plan().noAtendidos()
                .stream().map(NoAtendidoResponse::pedidoId).toList());
        OperationalSnapshot baseFinal = ClasificadorNoAtendidos.snapshotEnCentral(fuente, central, ultimoInstante);
        Map<String, Resultado> causaFinal = new HashMap<>();
        for (String id : sinAtenderFinal) {
            NoAtendidoResponse no = ultimosNoAtendidos.get(id);
            Order original = conocidos.get(id);
            Order pedido = new Order(original.id(), original.destination(), no.paquetes(), original.registeredAt(),
                    original.deadline());
            causaFinal.put(id, clasificador.clasificar(pedido, ultimoInstante, baseFinal, central, flota, rutasFinales,
                    servicio.bloqueosVigentes(EJECUCION)));
        }

        List<String> filas = new ArrayList<>();
        filas.add(CABECERA_NO_ATENDIDOS);
        Map<Causa, Integer> porCausaPrimera = new TreeMap<>();
        Map<Causa, Integer> porCausaFinal = new TreeMap<>();
        int luegoAsignados = 0;
        for (Map.Entry<String, Hallazgo> entrada : primeraVez.entrySet()) {
            String id = entrada.getKey();
            Hallazgo h = entrada.getValue();
            Order pedido = conocidos.get(id);
            Integer asignado = primerCicloAsignado.get(id);
            boolean sigue = sinAtenderFinal.contains(id);
            Resultado fin2 = causaFinal.get(id);
            if (asignado != null) {
                luegoAsignados++;
            }
            porCausaPrimera.merge(h.resultado().causa(), 1, Integer::sum);
            if (fin2 != null) {
                porCausaFinal.merge(fin2.causa(), 1, Integer::sum);
            }
            filas.add(String.join(",", id, String.valueOf(ultimosNoAtendidos.get(id).paquetes()),
                    hora(pedido.registeredAt()), hora(pedido.deadline()), String.valueOf(h.ciclo()), hora(h.hora()),
                    h.resultado().causa().name(), csv(h.resultado().detalle()),
                    String.valueOf(ciclosSinAtender.get(id)), asignado == null ? "" : String.valueOf(asignado),
                    sigue ? "SIN_ATENDER_AL_FINAL" : asignado != null ? "ASIGNADO_LUEGO" : "FUERA_DEL_PLAN",
                    fin2 == null ? "" : fin2.causa().name(), fin2 == null ? "" : csv(fin2.detalle())));
        }
        Files.write(salida.resolve("ciclos.csv"), lineasCiclos, StandardCharsets.UTF_8);
        Files.write(salida.resolve("no_atendidos.csv"), filas, StandardCharsets.UTF_8);

        List<String> informe = informeDelObjetivo(objetivo, central, flota, fuente, clasificador, servicio, traza,
                primeraVez.get(idObjetivo), causaFinal.get(idObjetivo), primerCicloAsignado.get(idObjetivo));
        Files.write(salida.resolve("informe_" + idObjetivo + ".txt"), informe, StandardCharsets.UTF_8);

        System.out.println("=== DIAGNÓSTICO PASO 8 ===");
        System.out.println("ventana: " + hora(inicio) + " a " + hora(fin) + ", paso " + paso + " min, SA " + iteraciones
                + " iteraciones máx., semilla base " + SEMILLA + ", tope nuevos/ciclo " + maxNuevos);
        System.out.println("ciclos ejecutados: " + ciclo + ", pedidos conocidos: " + conocidos.size());
        System.out.println("pendientes planificados por ciclo: máx. " + maxPendientes + ", promedio "
                + String.format(java.util.Locale.ROOT, "%.1f", ciclo == 0 ? 0.0 : (double) sumaPendientes / ciclo));
        System.out.println("cola sin enviar por el tope (máx.): " + maxCola);
        System.out.println("ms por ciclo: promedio " + (ciclo == 0 ? 0 : msTotal / ciclo) + ", máx. " + msMaximo);
        System.out.println("pedidos que alguna vez quedaron sin atender: " + primeraVez.size()
                + " (luego asignados: " + luegoAsignados + ", sin atender al final: " + sinAtenderFinal.size() + ")");
        System.out.println("causa la primera vez sin atender: " + porCausaPrimera);
        System.out.println("causa al final (solo los que siguen sin atender): " + porCausaFinal);
        System.out.println("--- informe " + idObjetivo + " ---");
        informe.forEach(System.out::println);

        assertTrue(ciclo > 0);
        assertEquals(ciclo + 1, lineasCiclos.size());
        assertEquals(primeraVez.size() + 1, filas.size());
        assertTrue(Files.exists(salida.resolve("informe_" + idObjetivo + ".txt")));
    }

    private static String csv(String texto) {
        return "\"" + texto.replace("\"", "'") + "\"";
    }

    /** Dónde quedó el pedido: vehículo, llegada, fin de servicio y margen contra el plazo. */
    private static String describirAsignacion(List<RutaVigente> rutas, Order objetivo, int ciclo) {
        for (RutaVigente ruta : rutas) {
            if (!pedidosDe(ruta).contains(objetivo.id())) {
                continue;
            }
            ScheduledDeliveryRoute programada = ruta.programada();
            String tiempos = "sin programación";
            if (programada != null) {
                for (ScheduledRouteStop parada : programada.scheduledStops()) {
                    if (parada.stop() instanceof DeliveryStop e && e.order().id().equals(objetivo.id())) {
                        long margen = Duration.between(parada.completedAt(), objetivo.deadline()).toMinutes();
                        tiempos = "llega " + hora(parada.arrivedAt()) + ", termina " + hora(parada.completedAt())
                                + ", margen al plazo " + margen + " min";
                    }
                }
            }
            return "ASIGNADO a " + ruta.vehiculoId() + " (" + ruta.ruta().id() + ", " + ruta.ruta().stops().size()
                    + " paradas, salida " + hora(ruta.ruta().departureAt()) + "): " + tiempos;
        }
        return "no figura en ninguna ruta vigente (ya entregado o fuera del plan)";
    }

    private List<String> informeDelObjetivo(Order objetivo, Warehouse central, List<Vehicle> flota,
                                            FuenteDatosOperativos fuente, ClasificadorNoAtendidos clasificador,
                                            ReplanificacionSaService servicio, List<String> traza, Hallazgo primera,
                                            Resultado finalCausa, Integer cicloAsignado) {
        List<String> informe = new ArrayList<>();
        informe.add("Pedido " + objetivo.id());
        informe.add("  destino " + objetivo.destination() + ", " + objetivo.packages() + " paquetes, registrado "
                + hora(objetivo.registeredAt()) + ", plazo " + hora(objetivo.deadline()) + " ("
                + Duration.between(objetivo.registeredAt(), objetivo.deadline()).toHours() + " h)");
        int distancia = (int) central.location().manhattanDistanceTo(objetivo.destination());
        informe.add("  distancia al central: " + distancia + " km por calles ortogonales");
        informe.add("");
        informe.add("Si saliera del central en el instante de su registro (mejor caso, sin esperar ciclo):");
        OperationalSnapshot base = ClasificadorNoAtendidos.snapshotEnCentral(fuente, central, objetivo.registeredAt());
        List<RoadBlock> bloqueos = servicio.bloqueosVigentes(EJECUCION);
        Set<VehicleType> vistos = new HashSet<>();
        for (Vehicle vehiculo : flota.stream().sorted(Comparator.comparing(Vehicle::id)).toList()) {
            if (!vistos.add(vehiculo.type())) {
                continue;
            }
            if (vehiculo.type().capacity() < objetivo.packages()) {
                informe.add("  " + vehiculo.type() + " (" + vehiculo.id() + "): no aplica, capacidad "
                        + vehiculo.type().capacity() + " < " + objetivo.packages() + " paquetes");
                continue;
            }
            PlanEvaluation evaluacion = clasificador.evaluarSolo(vehiculo, objetivo, objetivo.registeredAt(), base,
                    central, bloqueos);
            String tiempos = "";
            for (ScheduledDeliveryRoute programada : evaluacion.schedulesByRouteId().values()) {
                for (ScheduledRouteStop parada : programada.scheduledStops()) {
                    if (parada.stop() instanceof DeliveryStop) {
                        tiempos = "llega " + hora(parada.arrivedAt()) + ", termina " + hora(parada.completedAt())
                                + ", margen " + Duration.between(parada.completedAt(), objetivo.deadline()).toMinutes()
                                + " min";
                    }
                }
            }
            informe.add("  " + vehiculo.type() + " (" + vehiculo.id() + ", " + vehiculo.type().speedKmPerHour()
                    + " km/h): " + tiempos + " · violaciones: "
                    + ClasificadorNoAtendidos.violaciones(evaluacion).stream().map(v -> v.type().name()).toList());
        }
        informe.add("");
        informe.add("Evolución por ciclo:");
        informe.addAll(traza.stream().map(l -> "  " + l).toList());
        informe.add("");
        informe.add("Primera vez sin atender: " + (primera == null ? "nunca quedó sin atender"
                : "ciclo " + primera.ciclo() + " (" + hora(primera.hora()) + "), causa " + primera.resultado().causa()
                + " · " + primera.resultado().detalle()));
        informe.add("Primer ciclo en que figura en una ruta: " + (cicloAsignado == null ? "nunca" : cicloAsignado));
        informe.add("Estado al final de la ventana: " + (finalCausa == null ? "no está entre los sin atender"
                : "sin atender, causa " + finalCausa.causa() + " · " + finalCausa.detalle()));
        return informe;
    }
}
