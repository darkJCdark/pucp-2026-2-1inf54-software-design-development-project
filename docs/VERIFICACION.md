# PaqRap v3 · informe de correcciones y verificación

**Entrega: 24 de septiembre de 2026.** Se modificó el módulo autónomo `PaqRap_Experimentos_GRASP_SA/paqrap-experimentacion`. No se modificó el frontend ni el backend REST.

## Resultado de la entrega

El módulo queda ejecutable para pilotos y campañas de planificación estática por lotes, con reglas compartidas, presupuesto temporal, semillas y resultados auditables. Incluye fuente, JAR Java 21, datos originales, configuración, scripts y evidencia. Esto no significa que todas las instancias tengan solución completa ni que ya se haya realizado la campaña formal.

## Cambios principales

Se excluyó mantenimiento preventivo y averías automáticas de las instancias numéricas, conservando bloqueos planificados. La flota reducida ahora es una flota menor, no mantenimiento simulado. Se corrigió la separación entre llegada física y hora de acondicionamiento; las entregas parciales, recargas y vecinos conservan cantidades. La factibilidad y el inventario cronológico se comprueban en un evaluador común sobre todos los pedidos originales.

GRASP conserva construcción RCL y búsqueda local; se retiró su lógica privada duplicada de factibilidad. SA tiene inicialización de mejor esfuerzo y reparación de vecinos: un pedido imposible ya no elimina las entregas atendibles. Ambos conservan el mejor plan validado al agotarse el tiempo. En TIME, la inicialización de SA consume su presupuesto y SA recalienta al llegar a Tmin. Se retiró la equivalencia incorrecta entre pedido no asignado y colapso.

Los resultados distinguen completitud, validez de rutas, cobertura, costo, distancia y terminación. El costo principal queda vacío si el plan no es completo y factible. Se verifican identidades de datos/configuración/compilación antes de reanudar y se conservan fallos.

## Verificación realmente ejecutada

Entorno: Linux x86_64, OpenJDK 21.0.11. Compilación con `javac --release 21`, sin Maven. Se recompilaron los **82 archivos fuente principales** y todos los `.class` coincidieron byte a byte con el JAR probado.

| Verificación | Resultado |
|---|---|
| Batería autónoma `--self-test` | **76 comprobaciones aprobadas** |
| Red vial contra referencia lenta corregida | **500 escenarios aprobados**, 1000 comparaciones de caminos con caché y 500 de recorrido operativo |
| Smoke: 4 instancias × 2 semillas × 2 algoritmos | **16/16 planes completos y factibles** |
| Readiness: 5, 10, 15 y 20 pedidos con bloqueos | **8/8 planes completos y factibles**, una semilla por algoritmo e instancia |
| Readiness: lote real de 41 pedidos | 2 planes parciales válidos; no contados como éxito completo |
| Repetibilidad FIXED en JVM separadas | **4/4 parejas de planes idénticas**; se comparan dos ejecuciones de 4 corridas cada una |
| Flota vacía | 2 resultados NO_SOLUTION, sin costo de éxito |
| Presupuesto de 1 ms | 2 resultados TIMEOUT, sin falso éxito |
| Reanudación sin cambios | Retiene las 16 filas sin alterar resultados |
| Reanudación con cambio de configuración o archivo fuente | Rechazada antes de modificar la campaña |
| Inspección independiente de JSON/CSV con Python | **38 corridas / 19 parejas**: cargas, cantidades, bloqueos, plazos, costos, distancias, hashes y completitud coherentes |

Las comprobaciones autónomas cubren, entre otros, capacidad, falta de carga, sobreentrega, stock compartido entre rutas, reposición a las 23:59:59, bloqueos activados exactamente al llegar, comidas, plazo exacto, pedidos imposibles mezclados con atendibles, reparación de 150 propuestas de vecinos y retención de mejor plan ante interrupción.

### Piloto: lectura sin ocultar resultados parciales

Presupuesto: **1500 ms por algoritmo**, incluyendo construcción/inicialización. Las 26 corridas TIME de smoke y readiness consumieron aproximadamente 1500–1510 ms; son cortes cooperativos, no un límite exacto a nivel de instrucción.

| Instancia readiness | Pedidos | GRASP completos | SA completos | Estado |
|---|---:|---:|---:|---|
| READY05 · bloqueada | 5 | 5 | 5 | Ambos completos/factibles |
| READY10 · bloqueada | 10 | 10 | 10 | Ambos completos/factibles |
| READY15 · bloqueada | 15 | 15 | 15 | Ambos completos/factibles |
| READY20 · bloqueada | 20 | 20 | 20 | Ambos completos/factibles |
| READYREAL · real 23/09, recepción 07–11 | 41 | 32 | 38 | Ambos parciales con rutas válidas |

En READYREAL, la cota optimista Manhattan descarta 3 pedidos como individualmente imposibles desde el estado de planificación del lote. Eso no demuestra que todos los demás sean atendibles por cualquier algoritmo. SA cubrió los 38 no descartados en esta corrida; GRASP cubrió 32 de 41 dentro del mismo presupuesto. **No se comparó el costo de estos planes como si ambos fueran completos.** Un tiempo mayor o una operación online constituyen experimentos distintos.

No se ejecutaron completas las campañas `pilot` (72 corridas), `escalabilidad` (96) ni `formal` (400) con esta versión. Los resultados anteriores de v1/v2 no se reutilizaron como evidencia de v3. Esta muestra no establece un ganador general.

## Decisiones del modelo que deben mantenerse explícitas

**Refrigerio:** una hora por turno con franjas 10–11, 18–19 y 02–03, configurables mediante `meal.startOffsetMinutes`. La hora exacta no está fijada en el contexto maestro. Los márgenes de una hora se conservaron como convención heredada, no como una nueva regla atribuida al profesor.

**Distancia:** el tope heredado de 80 km por tramo no aparece en el contexto maestro; la línea base usa `routing.maxLegKm=0`. La opción `80` está disponible para una sensibilidad declarada.

**Lotes reales:** se planifica al final de la ventana y se conservan vencidos por defecto. No equivale a despachar continuamente. La alternativa EXCLUDE registra exclusiones y cambia la población.

## Ejecución inicial

Abrir una terminal dentro de `paqrap-experimentacion`:

```powershell
java -version
java -Xmx512m -jar dist/paqrap-experimentos.jar --self-test
java -jar dist/paqrap-experimentos.jar --config config/readiness.properties --output results/mi-piloto-v3
```

Abrir `results/mi-piloto-v3/report.html` y revisar `runs.csv`. Para una segunda campaña usar otra carpeta. Para recompilar en Windows: `powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1`. En Linux/macOS: `bash scripts/build.sh`.

## Evidencia y límites

Los logs y resultados actuales están en `evidencia/v3/`. El archivo `environment.json` y `source-binary-verification.json` identifican el JAR. La suite Maven/JUnit **no se ejecutó** porque Maven no estaba instalado; no se presentan logs históricos como prueba nueva. Los scripts PowerShell no se probaron en Windows en esta sesión. No se verificaron frontend, REST, persistencia ni una simulación productiva de cinco días.

El siguiente paso experimental es calibrar presupuesto y parámetros en el equipo de ejecución, congelar el protocolo y correr la campaña formal. No hace falta integrar frontend o backend para ello.
