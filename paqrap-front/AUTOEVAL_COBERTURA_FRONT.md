# Cobertura de AutoEVAL — frontend semana 12

## Cubierto en código

- **B04:** configuración de cantidad y velocidad de unidades de transporte mediante los contratos existentes.
- **B06:** rangos configurables de semáforo para stock de almacenes y carga de vehículos.
- **B13 y B16:** carga independiente de ventas/bloqueos y reconocimiento de periodos de ventas ya precargados.
- **C12–C15:** momento simulado, transcurrido simulado, momento real y transcurrido real.
- **C21–C22:** semáforo, stock y porcentaje de almacenes en el mapa.
- **C27–C28:** semáforo y ocupación/carga de unidades en mapa y panel.
- **C29–C31 y C34:** movimiento interpolado, indicador de dirección y diferenciación de ruta realizada, tramo actual y ruta pendiente.
- **D01–D04:** visualización de bloqueos vigentes y coexistencia con rutas.
- **D05:** filtros por tipo de vehículo.
- **D06–D07:** filtros por semáforo de carga y stock.
- **D10–D13:** averías visibles y diferenciadas por tipo, con detalle temporal.
- **E01:** panel de listas inicialmente plegado.
- **E02–E05:** lista de unidades con carga, estado, ruta y acceso al detalle.
- **E13–E15:** orden por ocupación, salida y llegada (ascendente y descendente).
- **E18–E22:** almacenes con stock, semáforo y actividad temporal derivada del plan.
- **E31:** pedidos con destino, cantidad, unidad, ruta, salida y llegada.
- **F01:** selección vehículo lista ↔ mapa.
- **F02–F03:** selección pedido ↔ mapa y resaltado de rutas.
- **F05:** selección almacén lista ↔ mapa.
- **G01–G04:** indicadores globales de ocupación de flota y almacenes con semáforo.
- **G08–G09:** se conservan consulta de resultado y descarga CSV existentes.

## Requiere prueba manual

- Movimiento y actualización visual durante una corrida 5D completa.
- Aparición/desaparición temporal de bloqueos.
- Avería MINOR durante dos horas y recuperación posterior.
- Cambio de velocidad durante ejecución.
- Refrigerio y mantenimiento.
- Selección bidireccional con muchos elementos.
- **G06:** dos navegadores/dispositivos sobre la misma ejecución.
- Reportes finales de 5D y Día a Día contra datos reales.

## No se afirmó como cubierto

- **E06–E07:** búsqueda por minutos al destino/origen; el monitoreo actual no expone ambos tiempos calculados y no se inventó una aproximación.

- Centrado automático del mapa al seleccionar: opcional según el plan de tareas.
- Persistencia de los umbrales visuales de semáforo: el frontend usa la configuración de sesión; no se inventó un endpoint.
- Valores oficiales 20/40/14: no se modificaron porque la documentación los marca pendientes de confirmación.
