# Guion de exposición (10 min) – Backend de Jorge Chamorro

Proyecto PaqRap, Grupo 4D (1INF54). Texto para leer tal cual. Cada bloque indica primero el archivo que vas a abrir y su ruta; lo que sigue en cita es lo que se dice.

**Carpeta del repositorio:** `D:\Claude-DP1\pucp-2026-2-1inf54-software-design-development-project`

**Prefijo de las rutas de código:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/`
**Prefijo de las rutas de pruebas:** `paqrap-back/src/test/java/com/pucp/paqrap/modulos/`

## Orden de exposición

| # | Archivo | Estado | Ruta completa desde la carpeta del repositorio |
| --- | --- | --- | --- |
| 1 | `ParametrosFlotaActualizados.java` | Nuevo | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/flota/entity/ParametrosFlotaActualizados.java` |
| 2 | `FlotaService.java` | Modificado | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/flota/service/FlotaService.java` |
| 3 | `MotorEscenarios.java` | Modificado | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/escenarios/service/MotorEscenarios.java` |
| 4 | `DetalleController.java` | Nuevo | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/monitoreo/controller/DetalleController.java` |
| 5 | `VehiculoDetalleResponse.java` | Nuevo | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/monitoreo/dto/VehiculoDetalleResponse.java` |
| 6 | `AlmacenDetalleResponse.java` | Nuevo | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/monitoreo/dto/AlmacenDetalleResponse.java` |
| 7 | `DetalleOperacionService.java` | Nuevo | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/monitoreo/service/DetalleOperacionService.java` |
| 8 | `PedidoDetalleResponse.java` | Modificado | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/pedidos/dto/PedidoDetalleResponse.java` |
| 9 | `PedidoController.java` | Modificado | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/pedidos/controller/PedidoController.java` |
| 10 | `PedidoService.java` | Modificado | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/pedidos/service/PedidoService.java` |
| 11 | `AltaVehiculoRequest.java` | Nuevo | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/flota/dto/AltaVehiculoRequest.java` |
| 12 | `FlotaCantidadController.java` | Nuevo | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/flota/controller/FlotaCantidadController.java` |
| 13 | `FlotaCantidadService.java` | Nuevo | `paqrap-back/src/main/java/com/pucp/paqrap/modulos/flota/service/FlotaCantidadService.java` |

Después de los 13 archivos de código se muestran las 6 clases de prueba (todas nuevas), en la tabla de la Parte 5.

---

## Apertura (0:00 – 1:00)

**Mostrar:** la tabla de arriba.

> Buenas. Voy a presentar mi parte del backend de PaqRap, del Grupo 4D. Trabajé en cuatro frentes. Primero, que cambiar los parámetros de la flota haga replanificar la simulación. Segundo, el detalle de unidades y de almacenes para los paneles del mapa. Tercero, mejoras en los pedidos: su detalle y una regla de coherencia al registrarlos. Y cuarto, la gestión de la cantidad de flota, con alta y baja de vehículos.
>
> En total son trece archivos de código, ocho nuevos y cinco modificados, más seis clases de prueba nuevas. Todo está en el backend: no toqué la base de datos, el despliegue ni la configuración. Los presento en ese orden, empezando por la flota.

---

## Parte 1: cambiar la flota replanifica (1:00 – 3:00)

### Archivo 1 de 13: `ParametrosFlotaActualizados.java` (NUEVO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/flota/entity/ParametrosFlotaActualizados.java`

> El primer archivo es nuevo: ParametrosFlotaActualizados, en el paquete entity del módulo flota. Es un evento de dominio, un record con un solo dato: el tipo de vehículo que cambió. No acepta valor nulo.
>
> Lo creé porque el planificador lee velocidad, capacidad y costo de la base de datos en cada ciclo, pero nadie avisaba al motor de escenarios cuando esos valores cambiaban.

### Archivo 2 de 13: `FlotaService.java` (MODIFICADO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/flota/service/FlotaService.java`
**Dónde mirar:** el constructor y el método `actualizarTipo`.

> El segundo archivo es FlotaService, que modifiqué. Agregué al constructor el publicador de eventos de Spring. Y en el método actualizarTipo, antes de guardar, comparo la capacidad, la velocidad y el costo por kilómetro nuevos contra los actuales.
>
> Si alguno cambió, publico el evento ParametrosFlotaActualizados. Si guardan exactamente los mismos valores, no publico nada y no se replanifica sin motivo.

### Archivo 3 de 13: `MotorEscenarios.java` (MODIFICADO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/escenarios/service/MotorEscenarios.java`
**Dónde mirar:** el método `alActualizarParametrosFlota`, el método `avanzar` y el método `planificar`.

> El tercer archivo es MotorEscenarios, el motor de la simulación, también modificado. Agregué el método alActualizarParametrosFlota, que escucha ese evento y anota el tipo cambiado en una lista, tiposFlotaCambiados, de cada escenario activo.
>
> En el método avanzar, si esa lista no está vacía, el motor planifica en el siguiente tick con motivo PERIODICO, sin esperar al intervalo normal. El método planificar envía la lista en la solicitud del ciclo y después la limpia. Según el comentario del método, las rutas en marcha del tipo que cambió se vuelven a programar con el perfil vigente, y las de los demás tipos se conservan. Si no hay un escenario activo, el cambio solo se guarda.

---

## Parte 2: detalle de unidad y de almacén para el mapa (3:00 – 5:30)

### Archivo 4 de 13: `DetalleController.java` (NUEVO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/monitoreo/controller/DetalleController.java`

> Pasamos al módulo de monitoreo. El cuarto archivo es nuevo: DetalleController. Expone dos rutas de lectura bajo /api. La primera, GET /api/flota/vehiculos/{id}/detalle, para el panel de una unidad. La segunda, GET /api/almacenes/{id}/detalle, para el panel de un almacén.
>
> Las dos exigen el parámetro ejecucionId, porque el detalle siempre se calcula en el instante simulado de una ejecución. La de almacén acepta además el parámetro horas, con 24 por defecto.

### Archivo 5 de 13: `VehiculoDetalleResponse.java` (NUEVO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/monitoreo/dto/VehiculoDetalleResponse.java`

> El quinto es VehiculoDetalleResponse, lo que devuelve el detalle de una unidad. Trae su posición, su estado, su carga actual y su capacidad. Trae el turno vigente, con inicio y fin, y la ventana de alimentación, que es el rango en el que el turno permite el refrigerio, no una hora exacta.
>
> Si la unidad está detenida por refrigerio, también informa el motivo de detención y hasta cuándo. Y trae el identificador de su ruta y la lista ordenada de sus próximas entregas, cada una con pedido, posición, paquetes y llegada prevista.

### Archivo 6 de 13: `AlmacenDetalleResponse.java` (NUEVO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/monitoreo/dto/AlmacenDetalleResponse.java`

> El sexto es AlmacenDetalleResponse, para el panel del almacén. Devuelve el stock, la capacidad y el porcentaje de ocupación; el consumo de la ventana de horas; la última y la próxima recarga; y un historial de stock punto por hora.
>
> Incluye también un nivel de alerta, que puede ser verde, ámbar o rojo, un indicador de bajo stock, y la lista de movimientos de inventario de la ventana. En el almacén central el stock es ilimitado, así que esos campos van nulos o vacíos, pero los movimientos sí se conservan.

### Archivo 7 de 13: `DetalleOperacionService.java` (NUEVO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/monitoreo/service/DetalleOperacionService.java`
**Dónde mirar:** los métodos `vehiculo`, `almacen`, `nivelAlerta` y `proximaRecarga`.

> El séptimo es DetalleOperacionService, donde está la lógica. Solo lee, no modifica nada. El instante de consulta es el del reloj simulado si el escenario está activo; si no, el final del escenario; y si aún no empezó, su inicio.
>
> En el método vehiculo calculo el turno y la ventana de alimentación con el calendario de turnos, la capacidad con el tipo de vehículo, y las próximas entregas con las paradas de entrega del último plan cuya llegada es posterior al instante, ordenadas por llegada.
>
> En el método almacen valido que las horas estén entre 1 y 72. El stock sale del kardex de inventario: parte de la capacidad si hubo una recarga, o del stock inicial si no, y resta lo despachado. El historial tiene un punto por hora, sin pasar antes del inicio del escenario. La próxima recarga es siempre a las 23:59:59, hora de Lima.
>
> El nivel de alerta se calcula con la disponibilidad, que es el stock entre la capacidad: hasta 33 por ciento es rojo, hasta 66 por ciento es ámbar, y por encima es verde.

---

## Parte 3: pedidos, detalle y regla de coherencia (5:30 – 7:30)

### Archivo 8 de 13: `PedidoDetalleResponse.java` (MODIFICADO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/pedidos/dto/PedidoDetalleResponse.java`

> Ahora el módulo de pedidos. El octavo archivo es PedidoDetalleResponse, que ya devolvía el pedido con su historial de estados. Le agregué tres campos: unidadAsignadaId, incumplido y minutosRestantes.
>
> La unidad asignada es la última unidad registrada en el historial. Un pedido está incumplido si se entregó después de su plazo o, si aún no se entrega, si el instante consultado ya superó el vencimiento. Los minutos restantes salen negativos si ya venció, y nulos si el pedido ya se entregó o no se indicó el instante.

### Archivo 9 de 13: `PedidoController.java` (MODIFICADO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/pedidos/controller/PedidoController.java`
**Dónde mirar:** el método `obtener`, al final.

> El noveno es PedidoController. En el método obtener, que atiende GET /api/pedidos/{id}, agregué un parámetro opcional llamado instante, en formato fecha y hora ISO. Sin ese parámetro el endpoint se comporta como antes. Usé un instante y no un identificador de ejecución para que pedidos no dependa del módulo de escenarios.

### Archivo 10 de 13: `PedidoService.java` (MODIFICADO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/pedidos/service/PedidoService.java`
**Dónde mirar:** los métodos `registrar`, `validarContraEscenarioActivo` y `obtener`.

> El décimo es PedidoService, con dos cambios. El primero está en el método obtener, que ahora tiene una versión con instante y calcula la unidad asignada, el incumplimiento y los minutos restantes que acabo de describir.
>
> El segundo es la regla de coherencia en el método registrar, a través de validarContraEscenarioActivo. Si hay un escenario en ejecución o en pausa y el pedido manual trae una fecha anterior al inicio de ese escenario, el sistema responde 409 con un mensaje claro y no guarda el pedido. Un pedido registrado exactamente en el inicio, o después, se acepta. Sin escenario activo, o con el escenario detenido, una fecha antigua también se acepta. Para esto agregué al servicio el repositorio de ejecuciones y la constante de estados activos, que son RUNNING y PAUSED.

---

## Parte 4: cantidad de flota, alta y baja de vehículos (7:30 – 8:45)

### Archivo 11 de 13: `AltaVehiculoRequest.java` (NUEVO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/flota/dto/AltaVehiculoRequest.java`

> Último frente: la cantidad de flota. El archivo once es AltaVehiculoRequest, un record con un solo campo obligatorio: el tipo de vehículo. El identificador no se envía, lo genera el sistema.

### Archivo 12 de 13: `FlotaCantidadController.java` (NUEVO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/flota/controller/FlotaCantidadController.java`

> El doce es FlotaCantidadController. Lo hice como controlador aparte para no tocar el controlador de flota existente. Expone dos operaciones sobre /api/flota/vehiculos: un POST para dar de alta, que responde 201, y un DELETE por identificador para dar de baja, que responde 204.

### Archivo 13 de 13: `FlotaCantidadService.java` (NUEVO)

**Ruta:** `paqrap-back/src/main/java/com/pucp/paqrap/modulos/flota/service/FlotaCantidadService.java`
**Dónde mirar:** los métodos `alta` y `baja`.

> El trece es FlotaCantidadService. En el alta, verifica que el tipo tenga parámetros; si no, responde 404. Luego busca el mayor número de ese tipo y crea el siguiente: si existe hasta TA03, crea TA04. La unidad nace disponible.
>
> La baja tiene tres rechazos con error 409. Primero, si la unidad va en ruta. Segundo, si hay un escenario activo. Tercero, si la unidad ya tiene historial de rutas, averías, mantenimientos o pedidos: la base de datos lo impide con sus llaves foráneas, y yo convierto ese error en un mensaje claro que sugiere marcar la unidad como UNAVAILABLE. Si el vehículo no existe, responde 404. El alta, en cambio, siempre se permite.

---

## Parte 5: pruebas automatizadas (8:45 – 9:30)

**Mostrar:** esta tabla, con las rutas desde `paqrap-back/src/test/java/com/pucp/paqrap/modulos/`.

| Archivo (todos nuevos) | Pruebas | Ruta completa desde la carpeta del repositorio |
| --- | --- | --- |
| `ParametrosFlotaReplanificacionApiTest.java` | 6 | `paqrap-back/src/test/java/com/pucp/paqrap/modulos/escenarios/controller/ParametrosFlotaReplanificacionApiTest.java` |
| `DetalleApiTest.java` | 20 | `paqrap-back/src/test/java/com/pucp/paqrap/modulos/monitoreo/controller/DetalleApiTest.java` |
| `PedidoCoherenciaApiTest.java` | 6 | `paqrap-back/src/test/java/com/pucp/paqrap/modulos/pedidos/controller/PedidoCoherenciaApiTest.java` |
| `PedidoCasosPruebaApiTest.java` | 9 | `paqrap-back/src/test/java/com/pucp/paqrap/modulos/pedidos/controller/PedidoCasosPruebaApiTest.java` |
| `PedidoRegistroFeedbackApiTest.java` | 11 | `paqrap-back/src/test/java/com/pucp/paqrap/modulos/pedidos/controller/PedidoRegistroFeedbackApiTest.java` |
| `FlotaCantidadApiTest.java` | 6 | `paqrap-back/src/test/java/com/pucp/paqrap/modulos/flota/controller/FlotaCantidadApiTest.java` |

> Cada frente tiene su clase de prueba, en total 58 pruebas de integración sobre la API, con base de datos en memoria.
>
> ParametrosFlotaReplanificacionApiTest comprueba que cambiar velocidad, capacidad o costo replanifica, que guardar los mismos valores no, que sin escenario activo el cambio solo se guarda, y que una velocidad inválida se rechaza. DetalleApiTest cubre unidad con y sin ruta, refrigerio, almacén con historial, recarga diaria, almacén central, los niveles verde, ámbar y rojo en sus límites, validaciones, y el detalle de pedido con incumplimiento.
>
> PedidoCoherenciaApiTest cubre la regla del 409. PedidoCasosPruebaApiTest agrupa los casos CP-61 a CP-69 del registro de pedidos. PedidoRegistroFeedbackApiTest cubre fecha y hora con zona de Lima, plazo que cruza la medianoche, cantidad, tipo de entrega y dirección, con casos buenos y malos. Y FlotaCantidadApiTest cubre alta y baja de unidades.

---

## Cierre (9:30 – 10:00)

**Mostrar:** la tabla de apertura.

> Para cerrar, cuatro resultados. Cambiar los parámetros de la flota ahora replanifica la simulación. El mapa dispone de detalle de unidad y de almacén, con nivel de alerta. Un pedido anterior al inicio del escenario activo se rechaza con un mensaje claro. Y la flota puede crecer o reducirse con reglas de seguridad.
>
> Quiero ser explícito con los límites. El panel de pedido no muestra peso ni volumen porque el modelo actual no los tiene. Hoy no existe un tope máximo de paquetes por pedido: basta que la cantidad sea un entero mayor a cero. La ventana de alimentación del detalle de unidad es el rango que permite el turno, no una hora exacta de refrigerio. Y quedan pendientes los casos de prueba del catálogo de aceptación que aún no están mapeados contra las pruebas existentes. Gracias. Quedo atento a sus preguntas.
