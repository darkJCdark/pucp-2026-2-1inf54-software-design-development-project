# Cambios y procedencia · v3

## Alcance de la entrega

Base: ZIP `pucp-2026-2-1inf54-software-design-development-project-feature-experimentos-grasp-v2(1).zip` y contexto maestro `Markdown(20260924-055915).md pegado`, aportados por el usuario. Se modificó el módulo autónomo `PaqRap_Experimentos_GRASP_SA/paqrap-experimentacion`. El frontend y el backend REST quedan sin modificaciones.

La documentación anterior se conserva en `provenance/v3/documentacion_recibida_v2/`; su contenido describe otra versión. Los logs antiguos de JUnit o benchmarks no son evidencia de esta entrega. El parche y la relación de archivos se encuentran en `provenance/v3/`.

## Correcciones funcionales

| Área | Problema detectado | Corrección |
|---|---|---|
| `InstanceFactory` | Los lotes REAL cargaban mantenimiento; REDUCED lo utilizaba para inhabilitar vehículos | Calendarios de mantenimiento y averías automáticas vacíos; reducción física de flota antes del experimento |
| `OperationalPlanEvaluator` | Cobertura, factibilidad y costos podían interpretarse por separado; faltaban verificaciones de consistencia | Evaluación contra todos los pedidos originales; validación de cantidades, registro, vehículo, origen, almacenes, capacidad y stock |
| `PlanEvaluation` / `PlanQuality` | Un plan barato incompleto podía confundirse con uno completo | Factibilidad de rutas separada de completitud; orden lexicográfico por cobertura y luego costo/distancia |
| `RouteScheduler` | Refrigerio sumado arbitrariamente a la llegada; reloj de viaje/servicio no compartido de forma suficiente | Llegada física independiente del acondicionamiento; comidas en franjas fijas configurables, pausa en nodos y recálculo de caminos si cambia el horario |
| `RoadNetwork` | Entrada permitida justo al activarse un bloqueo | No se admite llegada al nodo bloqueado en el instante inicial; el fin del bloqueo sigue siendo exclusivo |
| `RoadPath` | Una espera en parada de distancia cero podía desaparecer del reloj | Hora final explícita, sin perder el tiempo de espera |
| `InventoryTimeline` | Regreso de cero unidades a un almacén vacío rechazado sin sustento en el contexto maestro | Se permite retorno sin retiro; toda recarga positiva exige stock cronológico y considera 23:59:59 |
| `RouteLoadRepair` | Vecinos cambiaban entregas sin ajustar cargas | Reconstrucción de pickups, recargas y divisiones; conservación de carga; partes contiguas de una misma visita se fusionan |
| `FeasibleInsertionService` | Factibilidad duplicada, inventario estático y poca capacidad para entregas divididas | Generación mecánica común de inserciones, recargas y cantidades; decisión de aceptación siempre del evaluador común |
| GRASP | Reglas privadas divergentes, pérdida de progreso por timeout | Construcción RCL + búsqueda local sobre servicios comunes; conservación de mejor candidato validado |
| SA | Inicializador todo-o-nada y vecinos frecuentemente inválidos | Inicializador determinista de mejor esfuerzo; se busca también sobre planes parciales válidos; reparación de vecinos; Metropolis solo dentro del mismo nivel de cobertura |
| SA / presupuesto | Llegaba a Tmin antes del tiempo disponible; intentos vacíos no contaban bien | Recalentamiento en TIME, no en FIXED; control del máximo sin mejora en todo intento |
| `SearchControl` | Corte dentro de construcción/búsqueda podía perder soluciones | Mejor plan inmutable por hilo; tiempo e instrumentación; inicialización incluida |
| `CommonAudit` | Ruta directa con retorno usada como supuesta prueba de imposibilidad | Cota optimista Manhattan desde estados físicos, ignorando otras restricciones; pasarla no prueba factibilidad |
| `ExperimentMain` / `TrialMain` | Riesgos al reanudar con datos/código diferentes | Huellas de configuración, fuentes, instancias y compilación; rechazo previo a modificar la campaña; resultados de corrida escritos atómicamente |
| Reportes | Etiquetas secundarias sugerían “atendibilidad” demostrada | Campos `*_unruled_out_*`, estado final explícito y costo de éxito vacío en resultados incompletos |

## Qué no debe afirmarse sobre v3

No es “SA original sin cambios”, ni una optimización exclusivamente de velocidad. Cambiaron mecanismos de construcción, reparación, factibilidad y búsqueda de ambos algoritmos. Comparaciones publicadas de v1/v2 necesitan repetirse, no incorporarse a una tabla v3 como si fueran equivalentes.

No se agregó un algoritmo exacto, un simulador completo de cinco días ni detección general certificada de colapso. `ResultadoPlanificacion.esColapso()` se retiró del módulo: utilizar `esCompleta()` y `requiereReplanificacion()`. Los consumidores del backend no se integraron ni se probaron en esta entrega.

## Supuestos declarados, no reglas inventadas

Política fija de refrigerio; márgenes de una hora heredados; límite opcional de 80 km desactivado; lote estático con vencidos conservados por defecto. Véase `PROTOCOLO.md`. La fuente prioritaria es el contexto maestro; cualquier nueva aclaración del profesor debe convertirse en configuración o regla común antes de una nueva campaña.
