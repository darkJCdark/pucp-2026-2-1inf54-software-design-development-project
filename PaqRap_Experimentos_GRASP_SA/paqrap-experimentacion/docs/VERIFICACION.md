# Verificación del entregable

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
