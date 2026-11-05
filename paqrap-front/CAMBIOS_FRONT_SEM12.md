# PaqRap — cambios de frontend para semana 12

## Alcance

Esta versión modifica únicamente el frontend y consume los contratos reales presentes en `PaqRap(10)`. No contiene cambios de despliegue, Docker, Nginx ni VM.

## Configuración

- La configuración principal prioriza escenario, inicio, datos, flota, semáforos y resumen.
- Los parámetros técnicos del planificador quedaron al final en un bloque plegable **Configuración avanzada del planificador**.
- Simulated Annealing solo se menciona dentro de esa opción avanzada.
- Se consume `GET /api/pedidos/periodos-cargados`.
- Si los meses requeridos ya están en BD, el archivo de ventas es opcional.
- Si falta cobertura, se indica el mes y el nombre esperado `ventas.AAAAMM.txt`.
- Se mantienen las validaciones para archivos de ventas y bloqueos.

## Ejecución

- Se agregó un bloque con momento y tiempo transcurrido real y simulado.
- Se agregó un panel plegable con listas de vehículos, pedidos y almacenes.
- La selección es bidireccional entre listas, mapa y panel de detalle.
- Los pedidos seleccionados resaltan sus rutas relacionadas, incluyendo pedidos fraccionados.

## Vehículos

- Búsqueda por código.
- Orden por ocupación, salida, llegada o código.
- Carga actual, capacidad y porcentaje.
- Estado, motivo de detención y hora de término.
- Ruta vigente, entregas y paquetes asignados.
- Semáforo de carga configurable en mapa y filtro por nivel.

## Pedidos

- Búsqueda por id o cliente.
- Planificados, entregados o todos.
- Destino, cantidad, vencimiento, unidades y rutas asociadas.
- Representación de pedidos fraccionados entre varias rutas.
- Selección desde lista o mapa y resaltado de rutas.

## Almacenes

- Búsqueda por nombre.
- Stock, capacidad, porcentaje y semáforo.
- En tránsito, próximas cuatro horas y visitas de las últimas cuatro horas, derivados del plan vigente.
- Stock y semáforo visibles en el mapa.
- Filtro de almacenes por nivel de stock.

## Mapa

- Conserva movimiento interpolado, añade indicador de dirección y diferencia rutas realizadas/actuales/futuras, bloqueos, pedidos y estados de detención.
- Diferencia averías leves, intermedias y graves.
- Muestra carga/capacidad de unidades individuales y porcentaje agregado de unidades agrupadas.
- Añade filtros por semáforo de carga y stock.
- Mantiene zoom, desplazamiento, tooltips y filtros por tipo.

## Indicadores

- Indicador global de ocupación de carga de la flota con umbrales configurables de sesión.
- Indicador global de stock de almacenes intermedios.
- Ambos incluyen porcentaje y semáforo.

## Backend

No se modificó el backend. Los contratos utilizados existen en la versión adjunta:

- periodos cargados;
- monitoreo;
- plan y replanificación;
- detalle de vehículo, pedido y almacén;
- carga y consulta de bloqueos;
- resultados y CSV;
- actualización de velocidad.
