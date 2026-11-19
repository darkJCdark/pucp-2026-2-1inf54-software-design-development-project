# Cambios de la Semana 13: ejecutor, rescate crítico, alertas, bloqueos por ejecución y flota

Rama `Gimena`. Responsable: Tenipuclla. Las pruebas de simulación en las que se basa este documento están en
[simulaciones_colapso_notas.md](simulaciones_colapso_notas.md).

**Estado de la suite del backend:** 475 pruebas, 0 fallas y 3 omitidas (las simulaciones largas, que se activan con
`-Dpaqrap.sim=true`). El front compila sin errores (`tsc -p tsconfig.app.json`).

---

## 1. El ejecutor perdía entregas al reprogramar una ruta en marcha

**Síntoma.** La ejecución 46 (octubre de 2026, 5 días, flota de 18 autos, 20 motos y 18 bicicletas) colapsó el
día 2 por `PLAZO_VENCIDO` del pedido 202610-00263. Ese pedido estaba asignado y llegaba con más de 3 h de holgura.

**Causa.**
1. TA03 iba a entregar el pedido a las 19:57.
2. Un bloqueo obligó a reprogramar la ruta en marcha, y el camino nuevo adelantó la llegada a las 19:49.
3. Cuando llegó el plan nuevo, el ejecutor ya había avanzado más allá de las 19:49.
4. El ejecutor solo aplica eventos posteriores a `ejecutadoHasta`, así que descartó esa llegada. El pedido nunca se
   entregó.

El colapso no se debió a que faltara trasvase ni a que el planificador fallara.

**Solución** (`EjecutorPlanService`). Las rutas en curso avanzan **por índice de parada** (`alcanzadas`) y no por
hora:
- Una parada ya alcanzada no se repite.
- Una parada pendiente que la reprogramación dejó en el pasado se ejecuta al inicio del avance siguiente, respetando
  el orden (`aMasTardar`).
- Las entregas que entran a una ruta continuada pasan de REGISTERED a ASSIGNED.

**Pruebas** (`EjecutorPlanServiceTest`):
- `unaReprogramacionQueAdelantaUnaLlegadaPendienteNoPierdeLaEntrega` reproduce la ejecución 46. Falla con el código
  anterior y pasa con el nuevo.
- `unaReprogramacionQueAtrasaUnaLlegadaYaHechaNoLaRepite`.

## 2. Rescate crítico: trasvase solo cuando la situación lo exige

Lo pidió el equipo: si un pedido se acerca a su plazo y no va a llegar, otra unidad debe cumplirlo, y la unidad que
lo salvó recarga en el almacén más cercano.

**Solución** (`ReplanificacionSaService`). Son dos mecanismos y ninguno deja tarde a otro pedido.

1. **Liberar entregas tardías** (`liberarEntregasTardias`, antes del SA).
   - Aplica cuando, al reprogramar una ruta en marcha (por bloqueo o cambio de velocidad), una entrega pendiente
     llegaría después de su plazo.
   - La ruta suelta esa entrega. Los paquetes siguen a bordo como sobrante, y el pedido compite por cualquier
     unidad: libre, de regreso o mediante inserción.
2. **Préstamo de carga** (`rescatarCriticos` / `mejorPrestamo`, después del SA, la inserción y el trasvase por
   avería).
   - Aplica a un pedido que nadie atendió y que vence en `paqrap.rescate.horas` (2 h por defecto) o menos.
   - Una unidad en marcha lo entrega con lo que lleva a bordo.
   - Si eso le deja déficit para sus propias entregas, inserta una recarga en el almacén (central o intermedio con
     stock) que menos la desvía.
   - El préstamo se acepta solo si todos los pedidos de esa ruta siguen llegando a tiempo y no hay violaciones de
     capacidad, stock de intermedios (reservado entre rescates), refrigerio ni bloqueos.

**Configuración:** `-Dpaqrap.rescate=false` lo desactiva, y `-Dpaqrap.rescate.horas=N` cambia el umbral.

**Pruebas** (`ReplanificacionSaServiceTest`). Las dos pasan con el rescate y fallan con `-Dpaqrap.rescate=false`:
- `unPedidoCriticoSeAtiendeConPrestamoDeCargaYLaUnidadRecargaAntesDeSuEntrega`. La secuencia esperada empieza con
  `[P39, P41, CENTRAL:2, P40]`.
- `unaEntregaQueUnBloqueoAtrasariaLaAtiendeOtraUnidadATiempo`.

**Costo medido** (2 días de octubre de 2026):

| Versión | Fallas | Ta promedio | Ta máximo |
|---|---|---|---|
| Con rescate | 0 | 860 ms | 2191 ms |
| Sin rescate | 0 | 850 ms | 2136 ms |

**Limitación conocida:** el sobrante que vuelve al central no se registra como devolución en el kárdex.

## 3. "Plazo inalcanzable" pasa a ser una alerta y no un colapso

`PLAZO_INALCANZABLE` usa como cota la salida inmediata desde el central y no considera las recargas en
intermedios. Por eso producía colapsos falsos, como el de la ejecución 39.

**Solución:**
- `DetectorColapso` solo declara colapso por `PLAZO_VENCIDO`.
- El plazo inalcanzable se registra como alerta, una sola vez por pedido, y no detiene la simulación.
- `MonitoreoResponse.alertas` expone las alertas, y el front las muestra en "Alertas de simulación": el colapso en
  rojo y los avisos en ámbar ("Plazo en riesgo · pedido X").
- La cota del detector se dejó como estaba originalmente.

**Pruebas** (`DetectorColapsoTest`): se ajustaron las existentes y se agregó
`unaAlertaNoSeRepiteYElColapsoLlegaRecienAlVencerElPlazo`.

## 4. Bloqueos por ejecución: solo cuentan los archivos cargados para ella

**Síntoma.** En las simulaciones y en Día a Día aparecían bloqueos que no estaban en el archivo seleccionado.

**Causa.**
- La tabla `road_blocks` es global. Al cargar `bloqueo.AAMM.txt` solo se reemplaza ese mes, así que la BD acumulaba
  8 meses de cargas anteriores. Se verificó que cada bloqueo coincide uno a uno con `paqrap-data/bloqueos`.
- El mapa, el planificador (con un horizonte de 36 h) y los eventos de inicio y fin consultaban por fecha, sin
  distinguir de qué ejecución venía cada bloqueo.
- Si no se seleccionaba archivo, se usaban en silencio los de la BD.
- En Día a Día, la fecha real (15-nov-2026) cae dentro de `bloqueo.2611.txt`, así que la operación real veía
  bloqueos de simulación.

**Implicancias:** simulaciones no reproducibles (dependían de lo cargado antes), una planificación real que esquivaba
calles abiertas y un mapa confuso.

**Solución.** No hay cambio de esquema.
- **Regla:** si se carga un archivo, la ejecución usa esos bloqueos. Si no se carga, no existen para ella aunque
  estén en la BD.
- `AlcanceBloqueos` (en memoria) guarda los meses habilitados por ejecución. Un bloqueo cuenta si empieza, en hora de
  Lima, en uno de esos meses. Guardarlo en memoria basta, porque las ejecuciones activas se cierran como FAILED al
  reiniciar el backend.
- `CrearEscenarioRequest.periodosBloqueos` recibe los meses. Una lista vacía significa "sin bloqueos". Si se omite el
  campo, la ejecución ve todos los bloqueos guardados (el comportamiento anterior, que se mantiene para la API y las
  pruebas). El front siempre lo envía.
- El filtro se aplica en `EventosSimulacion`, en `PlanificadorSaAdapter` (horizonte) y en el monitoreo
  (`BloqueoService.vigentesPara`).
- **Día a Día:** la Configuración permite seleccionar bloqueos del mes actual y del siguiente.
- **Carga durante la operación:** `POST /api/red-vial/bloqueos/carga-multiple?ejecucionId=N` carga el archivo y lo
  habilita para esa ejecución. Desde el front se usa con `bloqueosService.cargarArchivos(archivos, ejecucionId)`.
  Falta el botón en la pantalla de ejecución.

**Pruebas** (`EventosYMonitoreoApiTest`):
- `unBloqueoDeUnMesNoCargadoParaLaEjecucionNoSeMuestraNiSePlanifica`.
- `conElMesDelArchivoCargadoElBloqueoSeMuestraYReplanifica`. También comprueba que se ignora otro mes guardado en la
  BD.

**A tener en cuenta:**
- Cargar un mes sigue reemplazando ese mes en la BD para todos. No genera conflictos porque solo hay una ejecución
  activa a la vez.
- Tras reiniciar el backend, al consultar una ejecución ya terminada el mapa muestra todos los bloqueos de la BD.

## 5. Flota: baja lógica de unidades con historial

**Síntoma.** Al reducir la cantidad de autos, la simulación no arrancaba: "El vehículo TA18 tiene historial…", con
`SQL Error 1451` (`fk_routes_vehicle`).

**Causa.** Las llaves foráneas RESTRICT desde `routes`, `breakdown_events` y `maintenance_days` impiden borrar una
unidad que ya simuló.

**Solución:**
- `FlotaCantidadService.baja` revisa primero el historial (`VehicleRepository.tieneHistorial`). Si la unidad lo
  tiene, la deja UNAVAILABLE (baja lógica), un estado que el planificador, el ejecutor y el detector ya excluyen. Si
  no lo tiene, la borra como antes.
- La conciliación de cantidades en Configuración cuenta solo las unidades operativas.
  - **Al bajar la cantidad:** da de baja las que sobran.
  - **Al subirla:** primero reactiva las dadas de baja lógica (por código ascendente) y después crea unidades nuevas.
  - Se agregó `flotaService.cambiarEstadoVehiculo`.

**Prueba** (`FlotaCantidadApiTest`): `unaUnidadConHistorialSeDaDeBajaLogicaQuedandoFueraDeServicio`.

## 6. Front de ejecución

- **Ordenar pedidos.** El panel de operaciones ofrece "Vence primero" (por defecto), "Vence último", "Registro más
  reciente" y "Código". En "Todos", al ordenar por vencimiento se ocultan los entregados, salvo que se busque un
  pedido por su id.
- **Centrar el mapa.** Al seleccionar un pedido, el mapa se centra en él, igual que con un vehículo.
- **Averías en pausa.** Se puede registrar una avería con la simulación pausada. Queda en el instante simulado y el
  motor replanifica al reanudar.

## 7. Pedidos en rojo al terminar una simulación y estado "Sin detalle"

**Síntoma.** Al terminar con éxito una 5D, todos los puntos de entrega se pintaban de rojo.

**Causa.** Las simulaciones son aisladas: los estados de sus pedidos viven en memoria y nunca se escriben en la BD,
donde los pedidos siguen REGISTERED. El mapa pedía la lista de pedidos sin `ejecucionId`. Mientras la simulación
corre, el backend usa la única ejecución activa; al terminar ya no hay ninguna activa y responde con los estados de la
BD, así que el mapa veía pedidos no entregados con el plazo vencido.

**Solución:**
- El front envía `ejecucionId` al listar los pedidos.
- Si el backend se reinició, el detalle de esa simulación ya no existe y el backend responde 409. En ese caso el front
  pide `GET /api/pedidos?catalogo=true`, que devuelve los pedidos de la BD sin el estado de ninguna ejecución, y los
  dibuja en gris con el estado **"Sin detalle"**.

**Prueba** (`PedidoApiTest`): `unaSimulacionSinDetalleEnMemoriaAvisaYElCatalogoDevuelveLosPedidos`.

## 8. Flota en el CSV de resultados

**Qué cambia.** El CSV agrega estas columnas al final: `autos;motos;bicicletas`, `velocidad{Autos,Motos,Bicicletas}KmH`
y `velocidadFinal{Autos,Motos,Bicicletas}KmH`.

**Cómo se calcula:**
- `FlotaEjecucion` captura las unidades operativas por tipo (sin contar las UNAVAILABLE) y las velocidades al iniciar
  la ejecución.
- Si las velocidades se cambian en caliente, también registra las finales.
- La captura vive en memoria. Tras un reinicio del backend esas columnas salen vacías; para conservarlas habría que
  agregar una columna en la BD.

**Prueba** (`ResultadoEscenarioApiTest`): `elCsvIncluyeLaFlotaYSusVelocidadesInicialesYFinales`.

---

## Archivos tocados

| Área | Archivos |
|---|---|
| Ejecutor | `escenarios/service/EjecutorPlanService.java`, `EjecutorPlanServiceTest.java` |
| Rescate | `planificacion/service/ReplanificacionSaService.java`, `ReplanificacionSaServiceTest.java` |
| Alertas | `escenarios/service/DetectorColapso.java`, `DetectorColapsoTest.java`, `monitoreo/dto/MonitoreoResponse.java`, `monitoreo/service/MonitoreoService.java`, `api/monitoreo.dto.ts` |
| Bloqueos | `redvial/service/AlcanceBloqueos.java` (nuevo), `redvial/service/BloqueoService.java`, `redvial/controller/BloqueoController.java`, `escenarios/dto/CrearEscenarioRequest.java`, `escenarios/service/EscenarioService.java`, `escenarios/service/EventosSimulacion.java`, `planificacion/service/PlanificadorSaAdapter.java`, `EventosYMonitoreoApiTest.java`, `api/escenarios.dto.ts`, `services/bloqueos.service.ts`, `features/configuracion/{ConfiguracionPage, OrigenDatos, periodos, ResumenConfiguracion, ConfirmarInicioModal}` |
| Flota | `flota/repository/VehicleRepository.java`, `flota/service/FlotaCantidadService.java`, `FlotaCantidadApiTest.java`, `services/flota.service.ts`, `ConfiguracionPage.tsx` (conciliación) |
| Pedidos sin detalle | `pedidos/controller/PedidoController.java`, `pedidos/service/PedidoService.java`, `PedidoApiTest.java`, `services/pedidos.service.ts` |
| CSV con flota | `escenarios/service/FlotaEjecucion.java` (nuevo), `escenarios/service/MotorEscenarios.java`, `escenarios/service/ResultadoEscenarioCsvExporter.java`, `escenarios/controller/EscenarioController.java`, `ResultadoEscenarioApiTest.java` |
| Front de ejecución | `features/ejecucion/{EjecucionPage, ListaPedidosEjecucion, MapaOperacion, CapaPedidos}.tsx`, `features/ejecucion/ejecucion.types.ts` |

## Cómo probarlo

1. Reinicia el backend y refresca el front.
2. Inicia octubre de 2026, 5 días, con 18 autos, 20 motos y 18 bicicletas:
   - Debe arrancar aunque antes hubiera más unidades; las sobrantes quedan fuera de servicio.
   - El día 2 no debe colapsar por pedidos perdidos.
   - En la consola del backend aparece "Rescate crítico: …" cada vez que se activa.
3. Inicia una simulación sin archivo de bloqueos: no debe aparecer ninguno. Con `bloqueo.2610.txt`, solo deben
   aparecer los de octubre.
4. En Día a Día, sin archivo, no debe haber bloqueos. Con `bloqueo.2611.txt`, aparecen los de hoy.
