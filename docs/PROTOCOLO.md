# Protocolo experimental · v3

## Unidad de comparación

Una instancia contiene pedidos, flota fija, inventario, almacenes, velocidades, costos, hora de planificación y bloqueos planificados. No incluye mantenimiento preventivo ni averías automáticas. La familia NORMAL es un control sintético sin bloqueos; BLOCKED y REAL sí los incorporan. REDUCED modifica la flota antes de empezar, no durante la corrida.

GRASP y SA reciben la misma instancia inmutable y el mismo conjunto completo de pedidos. La semilla de generación sintética es distinta de la semilla de búsqueda. Las repeticiones de una misma instancia no se consideran instancias independientes.

## Regla de éxito y objetivo

Una solución completa exige rutas válidas y entrega de toda la cantidad de cada pedido entre su registro y su plazo. Llegar exactamente al plazo está permitido. La hora de acondicionamiento posterior ocupa al vehículo pero no se suma al plazo de entrega. Cada entrega parcial efectiva consume esa hora; dos fragmentos contiguos de la misma visita son una sola entrega efectiva.

Durante la búsqueda se prioriza: validez operativa → menos pedidos pendientes → menos unidades pendientes → costo → distancia. No se penalizan los incumplimientos mediante dinero. Una reducción de costo no permite aceptar peores cantidades cubiertas. SA aplica Metropolis únicamente a propuestas de la misma cobertura; GRASP usa RCL y búsqueda local. La comparación estadística primaria del costo se limita a parejas de soluciones completas.

La auditoría final usa una instancia nueva del mismo `OperationalPlanEvaluator`, fuera del límite de búsqueda. No se confía en una etiqueta de éxito del algoritmo sin volver a comprobar sus rutas.

## Reloj operativo y supuestos

Retícula de 70 × 50 km, extremos incluidos, desplazamientos horizontales/verticales bidireccionales. Se calcula el tiempo por calle con precisión de nanosegundos redondeada desde la velocidad; no se usa una distancia recta diagonal.

Central `(27,14)` con stock permanente; Nor-Oeste `(12,38)` y Este `(57,27)` con 1000 unidades por defecto. Recargas sin duración extra, conservando la convención del código recibido. Inventario compartido por todas las rutas, eventos ordenados cronológicamente y reposición diaria a las 23:59:59. Volver a un almacén sin recoger unidades no consume stock. Todas las unidades parten del Central en las instancias experimentales.

Turnos 07–15, 15–23 y 23–07. Se fija una hora de comida por turno con `meal.startOffsetMinutes=180`; franjas 10–11, 18–19 y 02–03. Esta es una decisión de modelado experimental: el contexto maestro no precisa la hora exacta. Los márgenes de una hora se conservan del código previo, no de una aclaración nueva. Viaje y servicio no invaden la franja; el servicio se modela como una hora ininterrumpida. El relevo no agrega tiempo.

`routing.maxLegKm=0` significa sin tope artificial de distancia por tramo. `80` permite reproducir esa interpretación heredada como sensibilidad explícita, pero no es la línea base del contexto maestro.

## Pedidos y bloqueos reales

Se toman pedidos recibidos en `[from_hour,to_hour)`; se planifica al terminar esa ventana, por lo que no se conocen pedidos futuros. Esto difiere de despachar continuamente durante la ventana. Un lote largo puede contener pedidos que una operación online habría atendido antes. Por defecto no se borran: `orders.expiredPolicy=KEEP`. La política EXCLUDE registra identificadores y cambia la población; debe informarse y no mezclarse con KEEP.

Se cargan y fingerprintan los archivos mensuales de bloqueos relevantes para el horizonte de los plazos más dos días de margen operativo. Si falta un mes requerido, la instancia falla explícitamente. La factibilidad se refiere a los bloqueos suministrados; no se simulan incidencias desconocidas fuera de los archivos. Las rutas conservan todos sus nodos y horarios para inspección. No se equipara este horizonte a una prueba general de operación ilimitada o colapso.

Los bloqueos se consideran activos en `[inicio,fin)`: no se ingresa a un nodo bloqueado exactamente al inicio; tras el fin puede reabrirse. Si la espera de comida cambia los horarios, se vuelve a consultar la red. El plan evita bloqueos conocidos; el método operativo `traverse` conserva la respuesta de giro en U cuando se encuentra un nodo bloqueado. No hay bloqueos por averías en estos experimentos.

## Presupuesto y semillas

Modo principal `TIME`: mismo máximo de milisegundos por algoritmo, incluyendo inicialización de SA y construcción de GRASP. Se permite un pequeño exceso por comprobaciones cooperativas, planificación del sistema operativo y salida de funciones; se informa `elapsed_ms` real y `budget_exhausted`. No se presenta el límite como un reloj duro exacto a nivel de instrucción.

Cada corrida utiliza una JVM nueva; el controlador ejecuta secuencialmente y alterna el orden GRASP/SA. Carga de archivos, inicio de JVM, calentamiento de ambos algoritmos y auditoría final quedan fuera del tiempo de búsqueda. Se registra tiempo de inicialización y auditoría. Parámetros de JVM iguales dentro de una campaña.

GRASP se instancia de nuevo con su semilla. SA utiliza una semilla de búsqueda explícita e inicialización determinista. En TIME recalienta a la temperatura inicial cuando cae por debajo de Tmin, conservando su mejor plan. También existen topes de iteraciones y estancamiento que pueden acabar antes del tiempo; siempre revisar `termination`.

FIXED es un protocolo para comprobar repetibilidad con un número fijo de pasos. Sus “iteraciones” no son trabajo equivalente entre algoritmos y no deben utilizarse para una conclusión temporal justa. En FIXED, `budget_ms=0` significa sin límite cooperativo, no cero tiempo consumido. El guardián de proceso sigue activo.

Una semilla fija no garantiza idéntico plan al cortar por tiempo real en distintas máquinas. Sí hace reproducible la secuencia pseudoaleatoria de la búsqueda bajo el mismo protocolo determinista.

## Interpretación y campaña

`orders_provably_unservable` es una cota necesaria conservadora: si incluso el viaje Manhattan optimista desde cualquier vehículo disponible llega tarde, ese pedido no se puede cumplir en la instancia estática. Se ignoran bloqueos, inventario, comidas, servicio, retornos y otros pedidos para no exagerar la imposibilidad. **Que un pedido no sea descartado no demuestra que pueda atenderse.** `*_unruled_out_*` son indicadores secundarios, nunca sustitutos del éxito primario.

Primero ejecutar smoke/readiness; luego calibrar tiempo y parámetros en instancias de desarrollo. Congelar configuración, semillas, familias, cantidades, políticas y compilación antes de la campaña formal. Reportar todos los fallos y resultados incompletos. No aumentar selectivamente el tiempo solo al algoritmo que perdió ni reintentar únicamente semillas fallidas.

El análisis descriptivo está listo en `scripts/analyze.py`. La inferencia opcional trabaja a nivel de instancia, no de cada semilla como observación independiente, y sigue siendo condicional al diseño escogido. No se ejecutó una campaña formal ni se declara un ganador con esta entrega.
