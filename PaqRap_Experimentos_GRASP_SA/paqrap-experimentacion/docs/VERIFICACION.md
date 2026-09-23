# Verificación del entregable

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
