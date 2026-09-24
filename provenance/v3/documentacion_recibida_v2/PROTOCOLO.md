# Protocolo experimental propuesto

## 1. Preguntas y alcance

El objetivo es comparar **estas dos implementaciones** de GRASP y SA, con las mismas instancias del modelo operativo PaqRap. No se pretende establecer que una metaheurística sea universalmente superior, demostrar optimalidad o certificar toda la aplicación final.

Se estudia planificación estática por lotes. Cada corrida recibe un snapshot, un conjunto completo de pedidos conocidos y bloqueos del horizonte. No comparte stock, memoria de búsqueda ni decisiones con la corrida siguiente.

El documento de referencia `22.dis.experim.v01.docx` aporta la organización: objetivo, especificación del problema, condiciones, unidad experimental, datos, comparación y anexos. **No se trasladan** sus capacidades de vuelos, penalizaciones, AG/BT, resultados ni conclusiones a este experimento.

## 2. Unidad experimental, emparejamiento y aleatoriedad

Una corrida se identifica por `(instancia, algoritmo, semilla de búsqueda, presupuesto, configuración)`. Una pareja contiene GRASP y SA sobre la misma instancia, semilla y presupuesto.

Una instancia es una combinación concreta de pedidos, flota, inventarios, tiempo, mantenimiento y bloqueos. Sus datos se guardan en JSON y se verifican mediante SHA-256. Hay dos semillas distintas:

- `instance_seed`: genera datos sintéticos; no cambia entre repeticiones de una instancia.
- `search_seed`: controla las decisiones pseudoaleatorias del algoritmo; cambia entre repeticiones.

Usar el mismo número de semilla en GRASP y SA **no hace que sus secuencias de decisiones sean equivalentes**, porque consumen la aleatoriedad de forma diferente. El emparejamiento relevante procede principalmente de los mismos datos, restricciones y recursos.

## 3. Condiciones comparables

| Factor | Control implementado |
|---|---|
| Dominio | Una única copia del módulo actualizado de GRASP; SA depende de ella |
| Factibilidad | Mismo `OperationalPlanEvaluator` y auditoría posterior con TODOS los pedidos |
| Datos | Mismo generador/cargador, fechas, inventarios, flota, bloqueos y huella de entrada |
| Presupuesto | Mismo máximo de tiempo en modo TIME, incluido el plan inicial de SA |
| Proceso | Una JVM nueva para cada corrida, ejecutadas secuencialmente |
| Memoria | Mismos `-Xms64m` y `-Xmx512m` por defecto |
| Procesadores | Mismo `-XX:ActiveProcessorCount=2`; no implica afinidad ni reserva exclusiva de dos CPU |
| Calentamiento | Ambos algoritmos ejecutan el mismo caso diminuto de calentamiento en cada JVM |
| Orden | Alternancia GRASP→SA y SA→GRASP según instancia/repetición |
| Repetibilidad | Contenedores de orden estable; desempates de red deterministas; semillas registradas |
| Fallos | Se exportan, no se borran ni se convierten en costo cero exitoso |

La memoria, JIT, recolector, hardware y carga del sistema pueden influir en el tiempo medido. El calentamiento reduce diferencias iniciales, pero una ronda no demuestra estado estacionario de la JVM. Para la campaña final, mantener todos esos parámetros constantes y describir la máquina utilizada.

## 4. Qué tiempo se mide

El cronómetro monotónico (`System.nanoTime`) empieza antes de invocar el adaptador. Incluye preparación interna del algoritmo, construcción inicial, construcción GRASP, búsqueda local, vecinos y evaluaciones dentro de la búsqueda.

Se excluyen: arranque de JVM, lectura/generación de datos, calentamiento, petición de GC previa, escritura de archivos y auditoría posterior. La auditoría se mide aparte en `final_audit_ms`.

La interrupción es cooperativa y se comprueba también dentro del cálculo de caminos. Por eso `elapsed_ms` puede superar ligeramente el presupuesto. Hay además un límite externo del proceso para que una corrida colgada no paralice indefinidamente la campaña; ese evento queda como `HARD_TIMEOUT`.

Ambos disponen del mismo **máximo**, no se obliga a consumir exactamente todos los milisegundos. SA puede terminar por temperatura o por su criterio nativo antes del límite; queda registrado en `termination`. No se reinicia SA artificialmente para llenar el tiempo.

El modo `FIXED` usa límites nativos de iteraciones y presupuesto temporal desactivado; se reserva para regresión/repetibilidad. Nunca comparar “50 iteraciones vs. 50 iteraciones” como igualdad de esfuerzo.

## 5. Familias de escenarios

| Familia | Definición | Finalidad |
|---|---|---|
| NORMAL | Pedidos sintéticos de 1–8 paquetes; plazos 4, 8, 12, 18 o 36 h | Comparación base y volumen |
| BLOCKED | Misma clase de demanda sintética, con tres bloqueos temporales generados | Efecto de restricciones viales |
| REDUCED | Mantenimiento de aproximadamente la mitad de vehículos de cada tipo | Disponibilidad de flota |
| SPLIT | Algunos pedidos tienen 25–32 paquetes | Comprobar efectividad frente a demanda que exige dividir entregas |
| REAL | Lotes horarios de archivos originales, con bloqueos y mantenimientos originales | Contraste con datos proporcionados |

Estas cinco familias no equivalen a los tres modos finales de operación del sistema. No se simulan llegada continua, averías dinámicas, ejecución de rutas ni colapso cronológico.

Para los sintéticos todas las demandas se conocen desde el instante de planificación. Los destinos se generan dentro de la red y las semillas quedan publicadas. Para `REAL`, se recogen pedidos en `[07:00,08:00)` y se planifica a las `08:00`: así no se suministran al algoritmo pedidos que todavía no habían llegado a las 07:00. Los plazos conservan su fecha de registro original. Los bloqueos se cargan desde la planificación hasta la fecha límite más lejana más dos días, no solamente los activos durante la hora de recogida.

La campaña formal propone 8 instancias por familia: 32 sintéticas (6/12/18/24 pedidos y semillas publicadas), más 8 lotes reales de días distintos. Las fechas y parámetros están en el CSV y en los manifiestos, no ocultos en la interfaz.

**Ventanas reales largas.** Con ventanas de varias horas, el lote se planifica al final de la ventana y algunos pedidos registrados al inicio ya vencieron: ningún plan de ese lote puede cubrirlos. Desde la versión 2 se excluyen al crear la instancia y se listan en `orders_expired_before_planning_excluded` del manifiesto. Con ventanas de 1 h no ocurre (el plazo mínimo es 4 h) y esas instancias conservan su huella. Estas ventanas modelan una acumulación de pedidos pendientes planificada de una vez: sirven para estudiar **volumen**, no reproducen la operación online.

## 6. Etapas y parámetros

`smoke`: 4 instancias × 2 semillas × 2 algoritmos = 16 corridas. Verifica conectividad, métricas, fallos y formato de resultados, no superioridad.

`pilot`: 12 × 3 × 2 = 72. Sirve para revisar el presupuesto y rangos de parámetros. Los sintéticos usan otras semillas y los reales 9–12 de septiembre.

`formal`: 40 × 5 × 2 = 400. Es una propuesta práctica, no un cálculo de potencia ni un tamaño exigido por el curso. Los reales son 13–20 de septiembre. No se ajustarán parámetros mirando sus resultados y luego se presentarán como evaluación independiente.

`escalabilidad` (versión 2): 8 × 3 × 2 algoritmos × 2 presupuestos (5 y 20 s) = 96. Estudia el comportamiento con más pedidos: 24, 48, 96 y 144 sintéticos, y ventanas reales de 1, 2, 4 y 8 h de los días 21–24 de septiembre, que no se usan en piloto ni en la campaña formal. Los dos presupuestos se analizan por separado.

Los valores de `alpha=0.3`, temperatura 1000, enfriamiento 0.95 y 50 vecinos por nivel son puntos de partida conservados del desarrollo, **no configuraciones óptimas demostradas**. Si se calibra, dedicar esfuerzo comparable a los dos algoritmos y registrar el procedimiento, sin seleccionar únicamente su mejor corrida aislada. Con la red de caminos de la versión 2, ese esquema de SA termina en 1–2 s: en el piloto conviene decidir si se compara a igual tiempo máximo (diseño actual) o a igual tiempo usado (requiere calibrar el esquema de SA), y dejarlo escrito antes de la campaña formal.

## 7. Métricas e interpretación

**Primaria:** tasa de éxito completo = corridas con `full_feasible=true` / todas las corridas previstas y terminadas (incluidos fallos). Una campaña interrumpida con parejas incompletas debe reanudarse antes de analizar.

**Cobertura:** pedidos íntegramente cubiertos dentro de plazo / pedidos requeridos. Además se exportan paquetes cubiertos, para diferenciar pedidos grandes de pequeños. Son cantidades planificadas.

**Costo:** suma calculada por el evaluador. Se utiliza para comparación directa únicamente cuando ambos algoritmos de una pareja cubren toda la demanda con un plan factible. `cost_raw_do_not_rank_incomplete` es diagnóstico, no indicador para seleccionar ganador entre plan parcial y completo.

**Eficiencia:** tiempo hasta la primera solución completa, calidad obtenida al consumir el presupuesto, evaluaciones y consultas de caminos. El tiempo total es poco informativo para seleccionar velocidad cuando ambos alcanzan el mismo tope. Los casos sin primer éxito son fallos/censurados, no tiempos cero. `path_queries` cuenta solicitudes a la red (desde la versión 2, igual para ambos: GRASP ya no tiene caché privada); una solicitud repetida puede resolverse desde la caché exacta de la red.

**Secundarias: pedidos atendibles (versión 2).** Un pedido es *demostrablemente no atendible* si, para cada vehículo disponible al planificar, la ruta directa central → pedido → central que sale en ese instante incumple el plazo u otra regla dura por ruta (camino, 80 km, refrigerio, mantenimiento), evaluada con el mismo scheduler común. Esa ruta da la llegada más temprana posible: cualquier parada previa o salida posterior solo la retrasa. `full_servable_feasible` exige rutas válidas que entreguen a tiempo todos los demás pedidos; `coverage_servable_pct` mide la cobertura sobre ellos. Si ambos algoritmos de una pareja cumplen `full_servable_feasible`, entregan el mismo conjunto y su costo es comparable. Estas métricas son **descriptivas**: la métrica primaria y las pruebas inferenciales no cambian. Se calculan en la auditoría, fuera del tiempo medido.

**Memoria:** `heap_sampled_peak_mib` es el máximo de muestras de heap utilizado durante la búsqueda. No es pico exacto, memoria incremental atribuible al algoritmo, RSS del proceso ni memoria reservada. Se incluyen objetos preexistentes del proceso. No usarlo como una medición exhaustiva del consumo.

No se calcula gap contra el óptimo porque no hay una solución óptima certificada disponible. La diferencia/razón de costo entre dos heurísticas no es un gap de optimalidad.

## 8. Análisis estadístico incluido

El análisis descriptivo es el predeterminado. `--statistics` activa pruebas exploratorias, no conclusiones automáticas.

1. Se verifican parejas e igualdad de huellas. Se rechazan filas duplicadas o configuraciones mezcladas.
2. Las semillas se agregan dentro de cada instancia. No se tratan las cinco repeticiones de la misma instancia como cinco observaciones independientes del problema.
3. Para éxito se usa la diferencia de tasas de éxito por instancia. Para costo, se resume `log(costo_GRASP/costo_SA)` solo en instancias con éxito de ambos en todas sus repeticiones; se informa el tamaño del subconjunto. El alcance de esta inferencia es condicional, no incluye escenarios que alguno no resuelve.
4. Se ofrece prueba bilateral de signos sobre esos resúmenes e intervalo bootstrap del promedio, remuestreando instancias, no filas individuales. Los p-valores de signos se ajustan con Holm entre las métricas y presupuestos reportados.
5. Con SciPy se puede obtener Shapiro sobre las **diferencias emparejadas por instancia**, y opcionalmente Wilcoxon bilateral. No se decide Wilcoxon únicamente porque cada muestra original no sea normal. Wilcoxon exige considerar la simetría pertinente de las diferencias; Shapiro no certifica ese supuesto.
6. No se busca una prueba unilateral favorable después de ver los datos. Los p-valores de Wilcoxon son exploratorios y no reciben el ajuste Holm aplicado a los contrastes principales de signos; no tratarlos como múltiples confirmaciones independientes.

Las instancias sintéticas de familias distintas no son idénticamente distribuidas; el resumen global describe la mezcla de escenarios que se eligió. Los lotes reales de días contiguos podrían ser dependientes. Por ello la inferencia es orientativa para este conjunto y exige justificar muestreo/independencia antes de extrapolar. Preferir tablas y análisis por familia y volumen. El script no ofrece una selección universal de algoritmo.

## 9. Qué guardar para el informe posterior

Conservar la carpeta completa de resultados, el código/JAR empleado, `.properties`, CSV de instancias, fecha, hardware real, versión exacta del JDK, decisiones de calibración y las limitaciones del modelo. No reportar resultados de la maqueta frontend ni del informe del otro equipo.

## Fuentes técnicas de apoyo (no sustituyen los archivos del caso)

- Java SE 21, `System.nanoTime`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html
- Apache Maven, reactor multimódulo: https://maven.apache.org/guides/mini/guide-multiple-modules.html
- SciPy, Wilcoxon y sus supuestos: https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.wilcoxon.html
- Java SE 21, orden de colecciones no modificables: https://docs.oracle.com/en/java/javase/21/core/creating-immutable-lists-sets-and-maps.html
