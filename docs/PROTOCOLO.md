# Protocolo de experimentación

## Alcance y controles

Planificación estática por lotes. No requiere ni demuestra funcionamiento de Día a Día, 5D,
colapso continuo, frontend o backend. Las entradas se materializan antes de buscar.
En datos reales se planifica al cerrar la ventana de recepción y se conservan los deadlines
originales. `n_orders=0` en una fila REAL significa cargar todos los pedidos de esa ventana,
no una instancia vacía. La política base KEEP conserva pedidos vencidos o muy difíciles.

Las ventanas grandes pueden dejar pedidos imposibles antes de empezar: aumentar tiempo de
cómputo no cambia el instante operativo del snapshot ni recupera un plazo ya agotado. Debe
informarse este efecto y separar escalabilidad de envejecimiento de pedidos. No descartar
silenciosamente esos pedidos para mejorar cobertura. Un pedido que la cota Manhattan no descarta
no queda demostrado atendible: la cota es solo una condición necesaria optimista.

Se considera la flota 10/15/12 y sus capacidades 24/8/4, velocidades 40/25/12 y costos 8/6/3 por km,
Central (27,14), NW (12,38), Este (57,27), inventarios, reposiciones, plazos y entregas divididas.
Se incluyen bloqueos en las familias sintéticas y archivos reales. Mantenimiento y averías automáticas
se excluyen y su activación por configuración se rechaza. No hay límite base de 80 km por tramo.

## Unidad y tiempos

Una corrida = algoritmo + instancia + semilla de búsqueda + presupuesto. Una pareja usa la misma
instancia, semilla y presupuesto para GRASP y SA. La semilla de construcción sintética no es la
semilla de búsqueda. Cada instancia tiene manifiesto y hash de sus datos materializados.

La campaña ejecuta una JVM hija nueva por corrida y alterna el orden entre combinaciones de
instancia y repetición. GRASP y SA nunca se ejecutan simultáneamente dentro del ejecutor.
`worker.processors=2` informa a la JVM cuántos procesadores utilizar; no reserva núcleos ni aísla
al proceso de otros programas. Cierra cargas pesadas y usa el mismo equipo durante toda la campaña.

TIME limita tiempo real de búsqueda, no horas de circulación ni solo CPU consumida. Incluye
la inicialización de SA, inserciones, programación del descanso y evaluaciones dentro de la búsqueda.
Lectura, arranque JVM, calentamiento y auditoría final se excluyen; se registra `final_audit_ms`.
El corte temporal es cooperativo: puede existir un pequeño exceso al llegar al siguiente checkpoint.
Hay además un límite de seguridad del proceso hijo (al menos 90 s con los perfiles incluidos).
`TIME_LIMIT` con un plan completo es normal: se encontró un plan y se siguió mejorando hasta agotar
el presupuesto. No significa automáticamente error. El mejor plan ya validado se conserva.

En la prevalidación de FIXED la suma mostrada corresponde a valores nominales del perfil, no a un tiempo estimado.
FIXED sirve para pruebas de repetibilidad con pasos acotados; su `budget_ms=0` significa que no usa
un límite temporal de búsqueda. No debe presentarse como igualdad de tiempo GRASP/SA.
Repetir TIME con la misma semilla puede producir planes diferentes porque el sistema operativo
permite completar distinto número de pasos antes del corte.

## Criterio de evaluación

Primero validez de rutas; después demanda completamente atendida y cobertura de pedidos/unidades.
El costo principal existe únicamente para planes completos factibles frente a TODA la demanda
original. Para comparar costos directamente se usan parejas donde ambos completan. Se preservan
costos brutos de planes parciales solo para inspección, nunca como ranking experimental.

Las métricas `unruled_out` son diagnósticas y no reemplazan el denominador original. Un plan que
atiende todos los pedidos no descartados por la cota puede seguir siendo incompleto frente al lote.
Las salidas fallidas, no soluciones y timeouts forman parte de los resultados; no se repiten o borran
selectivamente. Tampoco se declara un colapso porque una metaheurística no encuentre un plan.

## Etapas

1. `--self-test`, prueba diferencial, smoke y readiness: verificación técnica.
2. Piloto: 12 instancias, 3 semillas, 3/5/10/20 s, 288 corridas. Ajustar parámetros aquí, no sobre formal.
3. Revisar resultados y justificar presupuesto; mantener igual programador y reglas para ambos.
4. Congelar código, datos, configuración y protocolo; plantilla formal de 400 corridas en instancias distintas.
5. Ejecutar formal, auditar y producir resultados descriptivos y análisis previamente definido.

El minipiloto de 8 corridas incluido en evidencia solo prueba el funcionamiento del recorrido completo
a 3 y 10 s. No sustituye el piloto de 288 corridas. La congelación de ejemplo incluida en evidencia
es una PRUEBA del script, no aprobación de 10 s para la entrega final.

## Análisis

`analyze.py` comprueba pares y hashes de configuración, distingue éxitos y agrupa las semillas por
instancia. La sección estadística opcional usa diferencias agregadas por instancia; Shapiro se aplica
sobre diferencias pareadas, no sirve por sí solo para validar todos los supuestos de Wilcoxon.
Los contrastes son bilaterales exploratorios; no se elige una cola después de ver quién obtuvo
menor costo. El costo inferencial opcional se limita a instancias con éxito en todas las repeticiones
de ambos algoritmos y su interpretación es condicional a ese subconjunto.

`pilot_summary.py` exporta cobertura y éxito por presupuesto y algoritmo. Los costos se comparan
sobre las MISMAS parejas que completaron en TODOS los presupuestos: evita una falsa mejora por
cambiar el conjunto de casos que entran a la media. Si no hay ese subconjunto, el costo queda vacío.
No se consideran 200 semillas-instancias como 200 problemas independientes ni se garantiza potencia
estadística solo por llegar a 400 corridas. Los días reales pueden estar relacionados entre sí.

## Resultados y reanudación

Conservar `runs.csv`, `paired.csv`, `metadata.json`, `experiment.properties`, `instances.csv`,
manifiestos JSON y `jobs/*.plan.json`, `*.trace.csv`, `*.result.properties` y logs. Las trazas registran
mejoras encontradas, no necesariamente una muestra equiespaciada del tiempo. En los JSON los
instantes se exportan en UTC; para leer hora Lima restar cinco horas.

Una nueva ejecución usa una carpeta nueva. `--resume true` retiene corridas terminadas, incluidas
fallidas, y rechaza cambios de binario, entradas, configuración o ciertos datos del entorno Java/SO.
No detecta el modelo físico de procesador ni todas las fuentes de carga: el equipo debe controlar
manualmente esas condiciones. No lanzar dos procesos sobre la misma carpeta de salida.
