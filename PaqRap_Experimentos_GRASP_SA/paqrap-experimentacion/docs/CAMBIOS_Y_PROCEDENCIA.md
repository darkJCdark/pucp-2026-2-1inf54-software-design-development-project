# Cambios y procedencia

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
   GraspPlanificador          InitialPlanBuilder original
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

El constructor inicial de SA se toma del núcleo compartido entregado. No depende de GRASP, no recibe gratuitamente un plan elaborado por este y su costo de construcción se mide.

## Cambios que NO se hicieron

No se añadieron microservicios, backend HTTP, frontend, algoritmos nuevos, penalizaciones de incumplimiento, un solucionador exacto, operadores de división a GRASP o reparaciones automáticas de carga a SA. No se alteraron ocultamente refrigerio, bloqueos, capacidades o mantenimiento para forzar factibilidad.

## Lectura sugerida del código

`ExperimentMain` organiza parejas y procesos. `InstanceFactory` materializa el escenario. `TrialMain` delimita medición y auditoría. `GraspAdapter` y `SaAdapter` llaman a los algoritmos existentes. `CommonAudit` decide cobertura y factibilidad comparable. `HtmlReport` y `analyze.py` preparan los resultados.

## Para integrar después al backend

El backend podrá depender de `paqrap-dominio`, `paqrap-grasp` y `paqrap-sa`. `experimentos` es un consumidor de esos módulos y no una dependencia que el motor necesite. Las adaptaciones que correspondan al negocio final deben hacerse y probarse en el dominio común, no por duplicado en los algoritmos.
