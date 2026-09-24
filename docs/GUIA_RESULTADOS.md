# Leer los resultados · v3

## Archivos

`runs.csv`: una fila por corrida, incluidos errores. `paired.csv`: parejas de la misma instancia, semilla y presupuesto. `report.html`: visor local sin servidor. `metadata.json`: entorno, parámetros y huellas. `instances/*.json`: flota, demanda y bloqueos exactos. `jobs/*.plan.json`: decisiones y cronología auditada. `jobs/*.trace.csv`: avances observados de la búsqueda. `jobs/*.result.properties`: resultado persistido usado al reanudar. `analysis/`: análisis descriptivo opcional.

## Campos importantes

| Campo | Lectura |
|---|---|
| `final_state` | COMPLETE, INCOMPLETE, NO_SOLUTION, TIMEOUT o ERROR |
| `status` | Etiqueta técnica conservada: OK, PARTIAL, NO_SOLUTION, TIME_LIMIT_NO_PLAN, INVALID, ERROR, WORKER_ERROR o HARD_TIMEOUT |
| `full_feasible` | Rutas válidas que cubren toda la demanda original |
| `route_constraints_valid` | Validez de las rutas propuestas, incluso si falta demanda |
| `orders_fully_served` / `orders_missing` | Pedidos completos a tiempo / pedidos todavía no completos |
| `packages_covered_on_time` | Unidades de pedidos cubiertas a tiempo; distingue entregas parciales |
| `coverage_orders_pct` | Pedidos completos / todos los pedidos de la instancia |
| `cost_complete_feasible` | Costo utilizable en la comparación principal; **vacío en planes incompletos o inválidos** |
| `cost_raw_do_not_rank_incomplete` | Costo de lo planificado, solo diagnóstico si no se completó |
| `distance_km` | Distancia real de los caminos propuestos, con retornos |
| `elapsed_ms` | Tiempo de búsqueda e inicialización; no incluye IO, arranque, warmup ni auditoría |
| `time_first_complete_ms` | Tiempo de la primera solución completa encontrada; vacío si no hubo ninguna |
| `initialization_ms` | Duración de inicialización de SA, ya incluida en elapsed_ms |
| `budget_exhausted` / `termination` | Si actuó el límite de tiempo / razón de terminación |
| `reheats` | Recalentamientos de SA en TIME |
| `violations` | Cantidad total de observaciones del evaluador; incluye demanda pendiente en un plan parcial |
| `input_sha256` / `plan_sha256` | Identidad de entrada / identidad de rutas y su cronología |
| `config_sha256` / `algorithm_version` | Configuración y versión; no mezclar campañas diferentes |

Los contadores `iterations`, `neighbor_attempts`, `plan_evaluations` y `path_queries` son diagnósticos. No se interpretan como unidades idénticas de trabajo entre algoritmos. La caché temporal reduce programación repetida de rutas inmutables, pero la factibilidad global y el inventario se vuelven a comprobar.

## Estados sin ambigüedad

Una corrida que encuentra un plan completo y después agota su tiempo es `final_state=COMPLETE`, `termination=TIME_LIMIT`. No es un fracaso por timeout. Una corrida que conserva algunas entregas válidas es `INCOMPLETE`, incluso si terminó por tiempo. TIMEOUT se reserva como estado final cuando no hay plan útil y el tiempo se agotó; también puede aparecer con el guardián externo de proceso.

Un plan parcial con 9 pedidos pendientes puede tener 9 observaciones `PARTIAL_DELIVERY_MISMATCH` y `route_constraints_valid=true`: no significa que se estén tolerando rutas que violan plazo o capacidad. Una propuesta que incumple esas restricciones es inválida y no entra a la búsqueda como solución factible.

Las métricas `*_unruled_out_*` excluyen solo pedidos descartados por una cota optimista. Son secundarias. Un valor de 100 % no sustituye `full_feasible=true`, y puede ser vacuo si no queda ningún pedido después de esa exclusión.

## Costos pareados

Comparar costos únicamente en la pareja que tiene ambos `full_feasible=true`. Si GRASP atiende 8 de 10 pedidos por S/300 y SA 10 de 10 por S/500, el costo de GRASP no establece superioridad. Reportar antes tasas de éxito y cobertura sobre **todas** las corridas, incluidos errores.

Las trazas guardan costo y número de pedidos pendientes. Una mejora en unidades parciales puede producir otro punto con el mismo número de pedidos pendientes y mayor costo; no es una mejora puramente monetaria. Inspeccionar plan y cobertura por unidades en los resultados finales.
