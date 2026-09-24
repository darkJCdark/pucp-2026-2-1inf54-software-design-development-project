# Verificación del entregable

## Versión 4 (23 de septiembre de 2026): SA v1.2 y alcance experimental

Entorno: Windows 10, Java/Javac 22 compilando con `--release 21`, Maven 3.9.9. El comando literal `mvn clean verify` falló antes de compilar porque el entorno intentó crear `C:\.m2\repository`. Se repitió con un settings temporal que apunta al repositorio local existente y modo offline: `mvn -s results/maven-settings.xml -o clean verify`; terminó `BUILD SUCCESS` en 27,980 s. `scripts/build.ps1` generó el JAR de `dist/`, SHA-256 `ed9dab5b9c6347b06805cb65220c1c6f7a40dfc8e255b2f7c72e0c4b7cb3d5e6`.

| Comprobación ejecutada | Resultado observado |
|---|---|
| JUnit | 38 métodos: 12 dominio, 8 GRASP, 10 SA, 8 experimentos; 0 fallos, 0 errores, 0 omitidos |
| Semilla S03 / S04 | 96/96 y 144/144 pedidos atendidos; plan evaluado factible |
| Semilla S07 | 38/41; no atendidos exactos: `c3274-2026-09-3799`, `c4638-2026-09-3800`, `c9729-2026-09-3806` |
| Semilla S08 | 49/53; no atendidos exactos: `c8715-2026-09-3985`, `c0901-2026-09-4013`, `c1413-2026-09-4015`, `c3076-2026-09-3994`; sin cruce de bloqueos |
| Fallback | Bicicleta preferida inviable; se usa la primera alternativa factible y el pedido queda atendido |
| Split atómico | Pedido inviable: 0 partes incorporadas; pedido factible de 30: varias partes que suman 30 |
| Regla experimental | El mantenimiento presente en `data/` no altera disponibilidad; manifiestos con mantenimiento y averías vacíos; bloqueos reales presentes |
| Soporte de producto | Los JUnit existentes siguen comprobando exclusión TA/TM/TB cuando un snapshot general sí contiene mantenimiento |
| Self-test | 22/22 comprobaciones correctas |

### Smoke v4

`config/smoke.properties` sin cambios: 16/16 `OK`, sin parciales, fallos ni timeouts sin plan.

| Instancia | GRASP semilla 42 / 73 | SA semilla 42 / 73 |
|---|---:|---:|
| SMOKE01 | S/ 624 / 624 | S/ 1.296 / 1.136 |
| SMOKE02 | S/ 864 / 864 | S/ 1.320 / 1.352 |
| SMOKE03 | S/ 896 / 896 | S/ 1.112 / 1.464 |
| SMOKE04 | S/ 2.456 / 2.456 | S/ 4.960 / 4.638 |

### Mini campaña previa al piloto

Configuración temporal: S01, S02, S03, S04, S07 y S08 × semillas 42 y 73 × GRASP y SA × 5.000 ms = 24 corridas. Las 24 terminaron sin `ERROR`, `INVALID`, `NO_INITIAL_PLAN` ni `TIME_LIMIT_NO_PLAN`.

| Instancia | GRASP (42 / 73) | SA v1.2 (42 / 73) |
|---|---|---|
| S01 | OK 24/24 · S/ 3.660 / 3.630 | OK 24/24 · S/ 9.085 / 9.676 |
| S02 | OK 48/48 · S/ 7.956 / 8.118 | OK 48/48 · S/ 18.614 / 18.158 |
| S03 | OK 96/96 · S/ 12.804 / 13.048 | OK 96/96 · S/ 27.684 / 26.856 |
| S04 | OK 144/144 · S/ 20.486 / 20.390 | OK 144/144 · S/ 41.759 / 41.290 |
| S07 | PARTIAL 38/41 · costo diagnóstico S/ 7.186 / 6.878 | PARTIAL 38/41 · costo diagnóstico S/ 17.614 / 17.753 |
| S08 | PARTIAL 49/53 · costo diagnóstico S/ 10.034 / 11.314 | PARTIAL 49/53 · costo diagnóstico S/ 19.269 / 18.783 |

Los costos parciales se conservan solo como diagnóstico y no se comparan contra planes completos. Frente a la campaña histórica v2, SA cambia de `NO_INITIAL_PLAN` a `OK` en S03/S04 y a `PARTIAL` válido en S07/S08. Esta mini campaña usa dos semillas y un solo presupuesto; valida la corrección antes del piloto, no sustenta superioridad. La evidencia exacta está en `evidencia/v4/`.

No se ejecutaron `pilot`, `formal` ni la campaña completa de `escalabilidad` en esta versión.

## Versión 3 (23 de septiembre de 2026): validación SA

Entorno de esta verificación: Windows, Java/Javac 22 compilando con `--release 21`, Maven 3.9.9. El primer `mvn clean verify` dentro del sandbox no pudo crear `C:\.m2\repository`; el mismo comando ejecutado con acceso al repositorio local terminó correctamente. Después de la última aserción de servicio por parada se repitió `mvn -q clean verify` con salida 0. `scripts/build.ps1` compiló el JAR de `dist/` desde el código actualizado; SHA-256: `58651887e58efe4300d33301bdad16bc815e7093ae91a6702bf170e569ad026d`.

| Comprobación ejecutada | Resultado observado |
|---|---|
| JUnit, `mvn clean verify` | 32 métodos: 12 dominio, 8 GRASP, 10 SA, 2 experimentos; 0 fallos, 0 errores. Base v2: 22; nuevos: 10 |
| Pedido de 30 unidades | Construcción y evaluación factibles; múltiples `DeliveryStop` suman 30; una hora de servicio por parada; cargas dentro de capacidad y nunca negativas |
| Mantenimiento TA/TM/TB | Los tres vehículos programados se excluyen; las alternativas del mismo tipo reciben rutas factibles |
| Datos reales, 1-sep-2026 02:00 Lima | Se cargaron `ventas.202609.txt`, `bloqueo.2609.txt` y `mant.preventivo.09.10.txt`; pedidos y bloqueos activos presentes; TA01 sin ruta; plan completo factible, sin carga negativa, exceso de capacidad, vencimiento ni cruce de bloqueos vigentes |
| `--self-test` del JAR actualizado | 22/22 comprobaciones correctas; el contador no se aumentó por los nuevos JUnit |
| Smoke con `config/smoke.properties` intacto | 16/16 `OK`: GRASP 8/8, SA 8/8; las 16 filas tienen el mismo estado, costo y `plan_sha256` que `evidencia/v2/smoke/runs.csv` |
| Dos ejecuciones `FIXED` con `config/deterministic.properties` intacto | Las dos filas SA repiten entrada, costo, huella de plan, iteraciones, intentos, inválidos, aceptados y evaluaciones; planes y costos coinciden también con `evidencia/v2/determinismo-a/runs.csv` |

Evidencia de corrida: `evidencia/v3/smoke/runs.csv`, `evidencia/v3/smoke/metadata.json`, `evidencia/v3/determinismo-a/runs.csv` y `evidencia/v3/determinismo-b/runs.csv`. Los directorios completos generados localmente están bajo `results/` (ignorado por Git). La nueva telemetría `initialCost`, `evaluatedNeighbors` y `finalTemperature` aparece en `detail` de cada corrida SA terminada; no se cambió el esquema común de CSV. El nuevo JUnit demuestra `iterations=2`, `neighbor_attempts=2`, `evaluatedNeighbors=1` y `acceptedNeighbors=1` para una propuesta vacía seguida de una evaluada.

En esta fase no se ejecutaron `pilot`, `formal` ni `escalabilidad`. La evidencia de escalabilidad de la versión 2 queda como resultado histórico de esa versión.

## Versión 2 (23 de septiembre de 2026)

Entorno: Windows 11 Home (10.0.26200), JDK 21, Maven 3, Git Bash; Python 3.14 para el análisis. Mismo JAR de `dist/` para todas las corridas de esta sección.

| Comprobación | Resultado observado |
|---|---|
| `mvn clean verify` | 22 pruebas JUnit, 0 fallos (12 dominio, 8 GRASP, 1 SA, 1 experimento) |
| Exactitud de `RoadNetwork` (`RoadNetworkDifferentialTest`) | 1.800 consultas aleatorias con 0–24 bloqueos + repeticiones desde caché + 1.800 `traverse`: idénticas tramo por tramo a la versión anterior |
| Exactitud de punta a punta | Perfil FIXED: SA (sin cambios) produce el mismo `plan_sha256` y las mismas `path_queries` que la evidencia v1 en las 2 instancias; huellas de entrada idénticas |
| `scripts/build.sh` + `--self-test` | 22 comprobaciones correctas (19 anteriores + 3 nuevas) |
| Repetibilidad FIXED v2 | Dos ejecuciones de 4 corridas: mismas huellas de plan y costos |
| Smoke v2 | 16/16 corridas `OK` (v1: GRASP 5 OK, 2 PARTIAL en SPLIT, 1 sin plan en REAL) |
| Escalabilidad v2 | 96 corridas; ver tabla abajo |

### GRASP antes y después, a igual presupuesto (semilla 42)

`evidencia/v2/benchmark-versiones.txt` contiene las filas completas. Resumen:

| Caso | Presupuesto | v1: iteraciones · 1.ª solución completa · costo | v2: iteraciones · 1.ª solución completa · costo |
|---|---:|---|---|
| Real 07–08 (8 pedidos) | 5 s | 12 · 953 ms · S/ 3.274 | 8.007 · 72 ms · S/ 2.242 |
| Real 07–11 (38 pedidos) | 20 s | 1 · — · 35/38 cubiertos | 49 · — · 35/38 (máximo posible: 3 no atendibles) |
| Real 07–15 (75 pedidos) | 30 s | 1 · — · 67/75, S/ 17.744 | 14 · — · 67/75 (máximo posible), S/ 11.070 |
| Sintético 24 | 5 s | 2 · 623 ms · S/ 5.834 | 283 · 61 ms · S/ 4.248 |
| Sintético 60 | 20 s | 1 · 1.947 ms · S/ 13.778 | 231 · 156 ms · S/ 8.640 |
| Sintético 120 | 30 s | 1 · 7.833 ms · S/ 24.810 | 61 · 363 ms · S/ 15.634 |
| SPLIT 24 | 5 s | 10 · — · 16/24 cubiertos | 370 · 92 ms · 24/24, S/ 5.474 |

Con solo la red optimizada (GRASP v1 intacto), la ventana real 07–11 terminó con `IllegalArgumentException: bound must be positive` desde `GraspPlanificador.construirGreedyAleatorizada`: el costo marginal infinito dejaba la RCL vacía. Con más iteraciones el defecto se vuelve alcanzable; en una campaña habría producido `ERROR` y descartado el mejor plan ya encontrado. Se corrigió en v2 (costo exacto por tramo y RCL robusta).

Los 3 pedidos no cubiertos de la ventana 07–11 del 13-sep se comprobaron uno por uno: ni una ruta directa en auto desde el central los entrega a tiempo con el evaluador común, por la regla heredada del refrigerio (ver `LIMITACIONES.md`).

### Perfil `escalabilidad` (96 corridas, `evidencia/v2/escalabilidad/`)

Promedios de 3 semillas por celda. "Atendibles cubiertos" = pedidos que no son demostrablemente no atendibles y el plan entrega completos a tiempo. Todas las corridas de GRASP terminaron por tiempo (`TIME_LIMIT`), como corresponde a un multiarranque.

| Instancia | Pedidos (vencidos excluidos / no atendibles) | GRASP 5 s: atendibles cubiertos · costo · iteraciones | GRASP 20 s: costo · iteraciones | SA (ambos presupuestos) |
|---|---|---|---|---|
| S01 sintético | 24 (0 / 0) | 24/24 · S/ 3.654 · 337 | S/ 3.552 · 1.776 | OK, S/ 9.375, termina en ~1,7–2,0 s |
| S02 sintético | 48 (0 / 0) | 48/48 · S/ 7.926 · 100 | S/ 7.886 · 452 | OK, S/ 18.305, ~2,3–2,6 s |
| S03 sintético | 96 (0 / 0) | 96/96 · S/ 12.991 · 17 | S/ 12.685 · 105 | `NO_INITIAL_PLAN` |
| S04 sintético | 144 (0 / 0) | 144/144 · S/ 19.919 · 5 | S/ 19.350 · 32 | `NO_INITIAL_PLAN` |
| S05 real 21-sep 07–08 | 7 (0 / 0) | 7/7 · S/ 2.576 · 8.578 | S/ 2.576 · 45.091 | OK, S/ 3.963, ~1,4 s |
| S06 real 22-sep 07–09 | 13 (0 / 0) | 13/13 · S/ 2.607 · 2.299 | S/ 2.593 · 16.521 | OK, S/ 6.606, ~2,2 s |
| S07 real 23-sep 07–11 | 41 (0 / 3) | 38/38 · S/ 6.895 · 8 | S/ 6.819 · 31 | `NO_INITIAL_PLAN` |
| S08 real 24-sep 07–15 | 53 (3 / 4) | 49/49 · S/ 9.916 · 5 | S/ 9.627 · 20 | `NO_INITIAL_PLAN` |

Lectura: GRASP v2 cubre todo lo atendible en las 48 corridas y su primera solución completa aparece en menos de 0,4 s aun con 144 pedidos; el presupuesto adicional (5 → 20 s) reduce el costo entre 0 % y 3 %. SA termina su esquema de enfriamiento en 1–3 s y no aprovecha el presupuesto (ver `LIMITACIONES.md`); su constructor inicial falla con 96 y 144 pedidos y en cuanto existe un pedido no atendible. **Es una prueba de escalabilidad de estas implementaciones, no la campaña formal**: 8 instancias y 3 semillas no permiten conclusiones inferenciales generales, y los costos solo son comparables donde ambos completan (S01, S02, S05, S06).

### Evidencia v2 incluida

`evidencia/v2/`: `smoke/`, `determinismo-a/`, `determinismo-b/`, `escalabilidad/` (con `consola.txt` y `analysis/` generado por `scripts/analyze.py`), `self-test.log`, `junit.log`, `exactitud-red.txt`, `benchmark-versiones.txt`. Las rutas absolutas de los `.job.properties` corresponden a `D:\DP1\paqrap-experimentacion`.

SHA-256 del JAR entregado en la versión 2:

```text
c4c2c8574cac36b1c3fe3fd6dd8e4d7a83d7bb5a09d809b6898f1424da797fdf
```

## Versión 1 (histórica)

Fecha: 20 de septiembre de 2026 (UTC). Entorno de ejecución: Linux, OpenJDK 21.0.11, `javac` 21.0.11. Python 3.13.5 para análisis opcional.

## Comprobaciones ejecutadas

| Comprobación | Resultado observado |
|---|---|
| Compilación de los cuatro módulos con `javac --release 21` | Correcta |
| Creación y ejecución del JAR | Correcta |
| Pruebas JUnit | 16 ejecutadas, 16 correctas, 0 fallos |
| Suite de protocolo dentro de las pruebas | 19 comprobaciones correctas |
| Carga de los cuatro perfiles | 4, 12, 40 y 2 instancias respectivamente; todas cargaron |
| Campaña smoke | 16 corridas completas registradas |
| Repetibilidad FIXED | Dos ejecuciones de 4 corridas; mismas huellas de entrada/plan y costos en las 4 parejas repetidas |
| Reanudación | Conservó las 16 filas de smoke; `runs.csv` idéntico byte a byte |
| Análisis descriptivo y gráficos | Ejecutados sobre smoke |
| Ayudantes estadísticos | 5 comprobaciones (signos, empates, Holm y bootstrap) |

Las 16 pruebas JUnit corresponden a 15 pruebas conservadas de los fuentes y una prueba de regresión experimental que ejecuta las 19 comprobaciones del protocolo. No son 35 pruebas JUnit independientes.

## Prueba smoke observada (diagnóstico, no campaña formal)

Presupuesto de búsqueda: 1500 ms; 4 instancias y 2 semillas por algoritmo.

| Algoritmo | OK | PARTIAL | TIME_LIMIT_NO_PLAN | Total |
|---|---:|---:|---:|---:|
| GRASP | 5 | 2 | 1 | 8 |
| SA | 8 | 0 | 0 | 8 |

Los dos resultados parciales de GRASP corresponden al caso SPLIT. El timeout sin plan ocurrió en una repetición del lote real con presupuesto pequeño. Estos resultados verifican que el ejecutor conserva fallos y no premia planes incompletos; **no establecen superioridad general de SA ni de GRASP**. Los costos de las parejas completas están disponibles, no se extrapolan al resto.

La campaña piloto de 72 corridas y la formal de 400 **no se ejecutaron**. Solo se verificó que sus configuraciones e instancias pudieran cargarse (piloto: 99 pedidos acumulados en 12 instancias; formal: 539 en 40).

## Evidencia incluida

`evidencia/junit.log`, `self-test.log`, `configuracion.log`, `reanudacion.log`, `smoke.log`, `determinismo.log`; carpetas `smoke-verificado`, `determinismo-a` y `determinismo-b`; comparación de huellas en `determinismo_comparacion.json`.

No se verificó el comando Maven, pues no estaba instalado en el entorno. Las pruebas JUnit se compilaron y lanzaron con el lanzador oficial usando las dependencias presentes en el ZIP SA; esas bibliotecas externas no se redistribuyen aquí. Los scripts PowerShell no se ejecutaron en Windows.

SHA-256 del JAR entregado:

```text
44108c3058f1cf137caf93eb3f876c8999836df7576c65070053e6db0e8e22fb
```

Las rutas absolutas dentro de los archivos `.job.properties` de evidencia describen el entorno donde se obtuvieron esas mediciones. Para ejecutar en otro equipo usa los perfiles en `config/`: se generarán nuevos jobs con las rutas correctas. No ejecutes directamente un job histórico de `evidencia/`.
