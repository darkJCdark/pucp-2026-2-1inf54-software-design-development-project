# Cómo leer las salidas

## Estados de corrida

| Valor | Significado |
|---|---|
| `OK` | Se obtuvo un plan que pasa la auditoría común y cubre TODA la demanda |
| `PARTIAL` | Las rutas construidas pueden ser válidas, pero falta demanda |
| `INVALID` | La auditoría detectó incumplimientos estructurales/operativos |
| `NO_INITIAL_PLAN` | El constructor inicial propio de SA no produjo un plan completo factible |
| `TIME_LIMIT_NO_PLAN` | Se agotó el presupuesto sin disponer de un plan utilizable completo/parcial no vacío |
| `ERROR` | Excepción técnica registrada por el ejecutor |
| `HARD_TIMEOUT` | La JVM no terminó dentro del límite externo y fue detenida |
| `WORKER_ERROR` | El proceso no produjo un resultado válido |

`termination=TIME_LIMIT` puede acompañar a `OK`: encontró un buen plan antes del límite y lo devuelve al terminar. No se debe descartar un éxito solo por alcanzar el presupuesto.

## Columnas principales

- `instance_id`, `family`, `source`: escenario concreto y origen de datos.
- `instance_seed`, `search_seed`, `repetition`: separan generación del problema y aleatoriedad de búsqueda.
- `input_sha256`: debe coincidir en una pareja. `config_sha256` identifica la configuración. `metadata.json` conserva la huella del JAR.
- `mode`, `budget_ms`: distinguir TIME y FIXED; el presupuesto efectivo FIXED es cero (sin límite temporal de búsqueda).
- `full_feasible`: criterio principal. No es solo el `esFactible()` parcial del resultado nativo GRASP.
- `route_constraints_valid`: validez de rutas separada de demanda no cubierta.
- `orders_total`, `orders_fully_served`, `orders_missing`: cobertura de pedidos íntegros dentro del plazo.
- `packages_total`, `packages_covered_on_time`: cantidades cubiertas; no son entregas ejecutadas realmente.
- `cost_complete_feasible`: costo comparable, vacío cuando no hay solución completa válida.
- `cost_raw_do_not_rank_incomplete`: diagnóstico del plan construido; NO ordenar algoritmos por este valor ignorando cobertura.
- `distance_km`, `routes`: magnitudes del plan que pudo cronogramarse.
- `elapsed_ms`: tiempo de búsqueda medido; incluye el constructor inicial de SA.
- `initialization_ms`: fase inicial de SA (no sumar otra vez a elapsed).
- `time_first_complete_ms`: primer plan completo; vacío si no se encontró.
- `final_audit_ms`: auditoría independiente fuera de búsqueda; no ocultar su costo en un informe de tiempo end-to-end.
- `iterations`, `neighbor_attempts`, `invalid_neighbors`, `accepted_neighbors`: contadores cuyo significado depende del algoritmo; no equivalen automáticamente a esfuerzo igual.
- `plan_evaluations`, `route_schedules`, `path_queries`: diagnósticos sobre el núcleo compartido.
- `heap_sampled_peak_mib`: máximo observado de heap, aproximado (no RSS ni pico exacto).
- `plan_sha256`: huella del detalle de rutas/horarios, usada para regresión con modo FIXED.
- `orders_provably_unservable` (v2): pedidos que ni una ruta directa desde el central alcanza a tiempo con ningún vehículo disponible; ningún algoritmo puede cubrirlos.
- `servable_orders_fully_served`, `coverage_servable_pct`, `full_servable_feasible` (v2): cobertura y éxito sobre los pedidos atendibles. Métricas secundarias; con cero no atendibles coinciden con las principales.
- `algorithm_version` (v2): versión de la implementación medida. No mezclar versiones en un mismo análisis.
- `detail`: motivo de fallo o información del adaptador.

En el manifiesto de una instancia real, `orders_expired_before_planning_excluded` (v2) lista los pedidos cuyo plazo ya había vencido al planificar y que por eso no se entregaron a los algoritmos.

## Trazas

`jobs/*.trace.csv` guarda mejoras por tiempo. `unserved_orders>0` indica una solución parcial. No presentar su `planned_cost` como convergencia de una solución factible completa. Los planes completos se identifican por cero pendientes y luego se verifican con la auditoría común.

## Análisis

`paired_runs.csv`: todas las parejas, con costos/diferencias únicamente donde corresponde.

`per_instance.csv`: agrega semillas de la misma instancia, con porcentaje de éxito por algoritmo y razón/diferencia de costo en éxitos conjuntos. `all_repetitions_jointly_feasible=1` identifica el subconjunto de inferencia condicional de costo.

`summary.csv`: descripción por familia, algoritmo, presupuesto y modo; desde la v2 incluye `servable_success_pct`, `mean_coverage_servable_pct` y `mean_coverage_orders_pct`. Una media de costos de los éxitos propios de GRASP y otra de los éxitos propios de SA pueden incluir distintas instancias. Por eso se etiqueta `mean_cost_on_success_not_directly_comparable`; no usarla sola para elegir ganador.

`analysis.json`: advertencias, tamaños de muestra y contrastes solicitados. Se deja vacío lo no estimable, en vez de inventar ceros. Los contrastes son exploratorios, no una conclusión automática de superioridad.
