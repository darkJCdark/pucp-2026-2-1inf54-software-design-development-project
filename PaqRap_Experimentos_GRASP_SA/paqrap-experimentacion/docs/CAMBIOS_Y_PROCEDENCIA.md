# Cambios y procedencia

## Evidencia formal v5 (24 de septiembre de 2026)

No se modificaron dominio, GRASP, SA, parámetros, configuración ni scripts estadísticos para esta ejecución. Sobre el commit `79760a7129723d3d21e5c3113a8d45c5417a0e31` se ejecutó una campaña formal de 40 instancias × 5 semillas × 2 algoritmos × límite máximo de 5 s. Las versiones registradas fueron `GRASP-v2 2026-09-23` y `SA-operational-v1.2 + shared-domain-v2 (semilla incremental; 2026-09-23)`.

La campaña produjo 400/400 `OK`, 200 parejas completas y cobertura total para ambos algoritmos. Incluye bloqueos, pero excluye mantenimiento preventivo y averías: los manifiestos registran respectivamente 582, 0 y 0. SA termina por su calendario térmico antes del límite; el diseño preservado compara el mismo tiempo máximo, no tiempo usado idéntico. `evidencia/v5/` conserva los insumos, resultados compactos, hashes y análisis ya calculado; no se copiaron los 400 directorios `jobs/`.

## Versión 4 (23 de septiembre de 2026): semilla SA incremental y alcance experimental aclarado

### SA v1.2

La etiqueta pasa a `SA-operational-v1.2 + shared-domain-v2 (semilla incremental; 2026-09-23)`. `SeedPlan` expone el `OperationalPlan` construido y listas inmutables de pedidos `attended` y `unattended`. `InitialPlanBuilder` mantiene orden por deadline, añade desempate por identificador y conserva round-robin como preferencia; si la rotación preferida no produce un plan factible, prueba las demás en orden determinista y acepta la primera factible. No elige por costo. Un pedido dividido se incorpora con todas sus partes o con ninguna.

`SaAdapter` ejecuta SA solo sobre `attended`; la auditoría común sigue recibiendo todos los pedidos originales y clasifica correctamente los faltantes como `PARTIAL`. Cuando `attended` está vacío se conserva el contrato `NO_INITIAL_PLAN`. La instrumentación de SA recibe el número fijo de no atendidos para no registrar falsamente una primera solución completa.

Este cambio es una **mejora heurística y de semántica de salida**, no una corrección de Metropolis. Permanecen intactos la fórmula de aceptación, los siete operadores de vecindad, el calendario de enfriamiento, los límites configurados, GRASP, `RoadNetwork` y `OperationalPlanEvaluator`. Los resultados v1.1 no deben mezclarse con v1.2.

### Alcance del diseño experimental

Una nueva aclaración docente establece para el diseño de experimentos: bloqueos incluidos; mantenimiento preventivo y averías excluidos. `InstanceFactory` crea calendarios/eventos vacíos y lo declara en cada manifiesto. No se eliminó el archivo ni el cargador de mantenimiento, `MaintenanceCalendar`, `BreakdownEvent` ni sus pruebas del dominio. La familia `REDUCED`, cuya perturbación era mantenimiento, se retiró de `pilot.csv` y `formal.csv`; sus filas conservan identificadores y semillas como casos `NORMAL`.

Las averías se introducirán manualmente solo en futuros escenarios día a día/5D. Trasvase y la decisión giro en U/continuidad de ruta no pertenecen a esta experimentación y no se implementaron.

### Verificación

Se añadieron seis JUnit causales: fallback desde bicicleta a la primera alternativa factible; S03 96/96; S04 144/144; S07 38/41 con tres no atendidos exactos; S08 49/53 con cuatro no atendidos exactos y bloqueos respetados; y split atómico. La prueba real ahora demuestra que un archivo de mantenimiento presente no altera disponibilidad experimental, que no hay averías y que los bloqueos siguen activos. Las pruebas de producto que excluyen vehículos por mantenimiento permanecen.

Resultado ejecutado: 38 JUnit correctos, self-test 22/22, smoke 16/16 `OK` y mini campaña 24/24 sin `ERROR`, `INVALID`, `NO_INITIAL_PLAN` ni timeout sin plan. En la mini campaña S01–S04 fueron completos para ambos; S07 fue 38/41 y S08 49/53 para ambos. Evidencia en `evidencia/v4/`. No se ejecutaron piloto, formal ni la campaña completa de escalabilidad.

## Versión 3 (23 de septiembre de 2026): Simulated Annealing validado e instrumentado

### Base y alcance

Esta revisión parte de GRASP v2 y `shared-domain-v2`, commit `c96b49c289a54e7495f4f619f81a9613d5ef1e64`, y de la familia SA experimental ya incluida allí. `origin/test/validacion-datos-profesor-sa` (`a02988ab59fc5165d1732195531fd138d8db9eb4`) se leyó como referencia de comportamiento y pruebas; no se copió su implementación. La etiqueta de las corridas SA pasa a `SA-operational-v1.1 + shared-domain-v2 (metricas; 2026-09-23)`. El sufijo 1.1 identifica corrección de métricas y validación, no una nueva heurística.

### Cambios SA

- `OperationalAnnealingResult` expone `initialCost`, `iterations`, `evaluatedNeighbors`, `acceptedNeighbors` y `finalTemperature`.
- `OperationalSimulatedAnnealingPlanner` cuenta un vecino como evaluado solo cuando el evaluador devuelve su resultado, incluso si es inviable. Antes llenaba `evaluatedNeighbors` con el número de iteraciones. Una propuesta vacía o una evaluación interrumpida no se contabilizan como vecino evaluado.
- `SaAdapter` identifica la versión SA y expone esas métricas adicionales en `detail`. Las columnas compartidas y los contadores de `SearchControl` conservan su significado.

### Pruebas añadidas

Se añadieron diez métodos JUnit: seis de búsqueda SA (Metropolis, enfriamiento, límites, factibilidad, contadores, semilla fija y plazo cooperativo), tres de reglas operativas (pedido de 30 unidades, capacidades 24/8/4 y mantenimiento TA/TM/TB) y uno de integración con los archivos reales de ventas, bloqueos y mantenimiento del 1-sep-2026 a las 02:00 en `America/Lima`. La prueba real usa los cargadores existentes; TA01 queda sin ruta, se verifican cargas, plazos y bloqueo de tramos.

### Cambios que no se hicieron e impacto

Permanecen intactos `InitialPlanBuilder`, la fórmula de Metropolis, el enfriamiento, los siete operadores, los parámetros, `SearchControl`, GRASP, `RoadNetwork` y el evaluador común. No se migró código Spring ni infraestructura backend. La corrección cambia telemetría y `algorithm_version`, no la selección de planes: en el smoke v3, las 16 filas conservaron estado, costo y `plan_sha256` de v2; dos ejecuciones `FIXED` de SA conservaron también sus contadores deterministas. No mezclar resultados de versiones sin considerar `algorithm_version`, aunque los planes de esta comprobación coincidan.

Sigue siendo posible `NO_INITIAL_PLAN` por la heurística de la semilla. El laboratorio continúa siendo planificador batch, no simulador online de cinco días. También permanecen las limitaciones del dominio documentadas en `LIMITACIONES.md` y la diferencia entre presupuesto máximo y tiempo realmente usado. La evidencia ejecutada de esta revisión está en `evidencia/v3/`; el detalle de verificación se encuentra en `VERIFICACION.md`. En esta fase no se ejecutaron piloto, formal ni escalabilidad.

## Versión 2 (23 de septiembre de 2026): GRASP optimizado y corregido

Esta versión cambia **deliberadamente y de forma visible** GRASP y la infraestructura compartida. Todas las corridas exportan `algorithm_version` (`GRASP-v2 2026-09-23`); no mezclar resultados de la versión 1 con los de esta versión. La diferencia exacta respecto del ZIP recibido está en `provenance/v2/`.

### 1. `RoadNetwork` (dominio compartido): mismo resultado, mucho más rápido

El cálculo de caminos dominaba el tiempo de ambos algoritmos (medido: ~0,4 s por consulta con los bloqueos reales de un día). Se reescribió su implementación **sin cambiar su semántica**:

- Índice de bloqueos por nodo: un bloqueo solo afecta un tramo si alguno de sus extremos es un nodo bloqueado (los extremos de todo segmento bloqueado también son nodos bloqueados). Antes se revisaban todos los bloqueos en cada tramo.
- Dijkstra sobre arreglos, con el mismo orden de extracción `(llegada, x, y)` y el mismo orden de vecinos, sin crear objetos por nodo.
- Plantillas sin bloqueos: si ningún bloqueo se cruza en tiempo y espacio con la exploración que haría Dijkstra, la ejecución real sería idéntica a la exploración sin bloqueos desplazada en el tiempo; se devuelve esa plantilla. Si algún bloqueo pudiera influir, se ejecuta el Dijkstra completo (y se memoriza).

**Verificación de exactitud:** `RoadNetworkDifferentialTest` compara tramo por tramo contra una copia congelada de la versión anterior (`ReferenceRoadNetwork`, solo en pruebas): 1.800 consultas aleatorias con 0–24 bloqueos, sus repeticiones desde caché y 1.800 `traverse`. Además, con el perfil determinista, **SA produce planes idénticos bit a bit** (mismo `plan_sha256`, que incluye cada tramo, y las mismas consultas de camino) que la evidencia de la versión 1. Como ambos algoritmos usan este mismo código, la mejora beneficia a los dos por igual.

### 2. GRASP v2 (`GraspPlanificador`): correcciones y optimización

El esqueleto GRASP no cambia (multiarranque, RCL con `alpha`, búsqueda local de primera mejora con inserción de pendientes, 2-opt, reubicación e intercambio). Cambios:

| # | Cambio | Motivo |
|---|---|---|
| 1 | **Entregas divididas**: un pedido mayor que la mayor capacidad disponible se reparte en partes; todas o ninguna | P&R 13 del curso admite entregas parciales. Antes esos pedidos (familia SPLIT) nunca se atendían |
| 2 | **Costo exacto del candidato**: diferencia real de km de la ruta completa (incluido el regreso) a las horas reales | La aproximación anterior podía dar costo infinito → umbral `NaN` → RCL vacía → `IllegalArgumentException` que terminaba la corrida como `ERROR` (reproducido con 38 pedidos reales) |
| 3 | **Consolidación en el primer viaje**: apilar en el primer tramo reconstruye la ruta con mayor carga inicial | Antes cada pedido adicional obligaba a volver a un almacén (la carga inicial se trataba como inmutable) |
| 4 | **Factibilidad de construcción = evaluador**: regreso final al central, mantenimiento/avería durante toda la ruta, descuento de inventario al apilar en una recarga | Evita construir planes que el evaluador rechaza |
| 5 | **Evaluación incremental**: cada vehículo guarda su línea temporal; un candidato recorre solo lo que cambia | Rendimiento |
| 6 | **Búsqueda local**: memoria de rutas ya evaluadas, sin recálculos repetidos en bucles, stock de almacenes intermedios, la ruta de origen deja de recoger lo que ya no entrega | Rendimiento y consistencia de carga/inventario |
| 7 | La caché privada de caminos de GRASP se eliminó: ahora la resuelve `RoadNetwork` para ambos | `path_queries` cuenta ahora todas las consultas de GRASP (antes su caché privada ocultaba las repetidas); no comparar ese contador con la versión 1 |

Pruebas nuevas en `GraspPlanificadorTest`: división de un pedido de 30 paquetes, plazo imposible (sin partes sueltas), ruta que terminaría dentro del mantenimiento del vehículo, consolidación en el primer viaje, y factibilidad en 5 semillas con bloqueos, mantenimiento, cruce de medianoche y pedidos divididos. La prueba antigua que esperaba que un pedido de 30 quedara sin atender se reemplazó, porque su premisa contradecía el enunciado.

### 3. Laboratorio experimental

- `algorithm_version` en cada fila y `algorithm_versions` en `metadata.json`.
- Auditoría común: `orders_provably_unservable`, `servable_orders_fully_served`, `coverage_servable_pct`, `full_servable_feasible` (métricas **secundarias**; ver `PROTOCOLO.md` §7).
- Lotes reales: se excluyen, y se registran en el manifiesto, pedidos cuyo plazo ya venció al planificar. Solo ocurre con ventanas largas; las instancias de 1 h conservan su huella de entrada.
- Perfil nuevo `config/escalabilidad.*` (24–144 pedidos sintéticos y ventanas reales de 1, 2, 4 y 8 h).
- `analyze.py` resume las métricas secundarias sin cambiar las pruebas inferenciales principales.
- `--self-test`: 22 comprobaciones (3 nuevas).

SA no se modificó: ni su constructor inicial ni su vecindario ni sus parámetros.

## Base utilizada (versión 1)

Se partió de los dos ZIP proporcionados por el usuario, identificados en `provenance/archives.sha256`. No se utiliza como evidencia la afirmación anterior del chat de que habían sido probados: la carpeta `evidencia/` corresponde a verificaciones efectivamente realizadas sobre este entregable.

| Destino | Origen |
|---|---|
| `dominio/.../domain` y `dominio/.../route` | Módulo `dominio` del último ZIP GRASP |
| `grasp/.../GraspPlanificador` | Implementación GRASP del ZIP |
| `grasp/.../ResultadoPlanificacion` | Resultado nativo GRASP del ZIP |
| `sa/...` | Familia Operational de SA: optimizador, generador de vecinos, interfaz, configuración y resultado |
| `experimentos/.../modelo` | Cargadores y auxiliares de entrada del ZIP GRASP |
| `data/` | Archivos originales de entrada del ZIP GRASP |
| `experimentos/.../experiment` | Nuevo ejecutor experimental y adaptadores |
| `dominio/.../search` | Nueva instrumentación opcional por hilo |

No se copió el segundo `domain/route` que venía con SA. Su código se compila contra el dominio actualizado compartido. Se conservaron algunos auxiliares heredados en el dominio para compatibilidad y pruebas; la campaña invoca `OperationalPlan`, `OperationalPlanEvaluator` y la familia Operational de SA, no el optimizador SA antiguo sobre `RoutePlan`.

## Cambios instrumentales sobre los algoritmos y dominio

1. **Tiempo y contadores opcionales.** Se agregó `SearchControl`, instalado exclusivamente durante una corrida. Comprueba plazo en ciclos, evaluador, scheduler y red; cuenta evaluaciones, caminos y vecinos. Fuera del laboratorio las APIs pueden usarse sin instalarlo.
2. **Mejor resultado ante interrupción.** GRASP conserva la construcción ya evaluada antes de comenzar búsqueda local, para no perderla si vence el plazo durante esa fase. SA conserva el mejor plan inicial/vecino aceptado cuando se interrumpe la búsqueda. Una excepción técnica de detención no se confunde con una violación de reglas.
3. **Orden determinista.** Los mapas compartidos se construyen con orden estable. La cola de caminos añade desempates por coordenadas. Esto puede cambiar elecciones entre empates respecto del repositorio original, por lo que se explicita y aplica a ambos.
4. **Validación inicial GRASP.** Se rechazan iteraciones no positivas y alpha fuera de rango/no finito. No se convierten configuraciones erróneas en éxitos de costo cero.
5. **Trazas de mejora.** Se registra primera solución completa y mejoras costo/cobertura. No se utiliza información del resultado del otro algoritmo.
6. **Compilación unificada.** Los cuatro módulos usan release Java 21. No se introduce Spring ni persistencia.

Los parches exactos sobre fuentes previas están en `provenance/cambios.patch`. El manifiesto `origen_archivos.json` distingue archivos originales conservados, modificados y añadidos.

## Adaptadores: una entrada/salida común, dos implementaciones reales

```text
InstanceFactory -> ProblemInstance
                       |
              UnifiedPlanner.solve(...)
                 /                   \
       GraspAdapter                 SaAdapter
            |                          |
   GraspPlanificador          InitialPlanBuilder propio de SA
            |                          |
 ResultadoPlanificacion       OperationalPlan inicial
                                       |
                          OperationalSimulatedAnnealingPlanner
                                       |
                            OperationalAnnealingResult
                 \                   /
                     AlgorithmOutput
                           |
                   CommonAudit (TODOS los pedidos)
                           |
                    CSV / JSON / HTML
```

GRASP mantiene `planificar(...)`. SA mantiene `optimize(...)`. Los adaptadores normalizan salida y protocolo sin exigir que sus firmas originales sean idénticas ni afirmar que ya implementaban una interfaz Strategy común.

El constructor inicial de SA no depende de GRASP ni recibe gratuitamente un plan elaborado por este; su costo de construcción se mide. Desde v1.2 su política incremental/fallback está versionada explícitamente.

## Cambios que NO se hicieron

No se añadieron microservicios, backend HTTP, frontend, penalizaciones de incumplimiento, un solucionador exacto ni reparaciones automáticas de carga a SA. No se alteraron refrigerio, bloqueos, capacidades, Metropolis ni vecindarios para forzar factibilidad. La exclusión de mantenimiento y averías está declarada y limitada a `InstanceFactory`; el dominio general conserva esas reglas.

## Lectura sugerida del código

`ExperimentMain` organiza parejas y procesos. `InstanceFactory` materializa el escenario. `TrialMain` delimita medición y auditoría. `GraspAdapter` y `SaAdapter` llaman a los algoritmos existentes. `CommonAudit` decide cobertura y factibilidad comparable. `HtmlReport` y `analyze.py` preparan los resultados.

## Para integrar después al backend

El backend podrá depender de `paqrap-dominio`, `paqrap-grasp` y `paqrap-sa`. `experimentos` es un consumidor de esos módulos y no una dependencia que el motor necesite. Las adaptaciones que correspondan al negocio final deben hacerse y probarse en el dominio común, no por duplicado en los algoritmos.
