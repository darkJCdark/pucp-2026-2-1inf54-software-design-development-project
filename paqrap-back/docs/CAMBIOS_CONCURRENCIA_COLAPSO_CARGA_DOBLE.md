# PaqRap — SA, ejecuciones independientes, cierre por colapso y carga de dos meses

Base exacta: `PaqRap(20261114-193858).zip`. Entrega: solo `paqrap-back`.

## Qué se mantiene

No se modificaron el motor de optimización SA, las vecindades, los parámetros de SA, `RouteScheduler`, `OperationalPlanEvaluator` ni `ReplanificacionSaService`. Tampoco se cambió la regla existente de cambio de velocidad desde la siguiente parada. El esquema SQL, los puertos, la conexión local y los scripts Docker conservan sus valores de la base.

No se añadió ALNS. No se requiere una migración de base de datos por esta entrega y no se debe borrar el volumen de MySQL para instalarla.

## 1. Día a Día simultáneo con una simulación

El backend admite simultáneamente:

- un `DAY_TO_DAY`; y
- un `FIVE_DAY` **o** un `COLLAPSE`.

No admite dos Día a Día ni dos simulaciones. Una ejecución pausada sigue ocupando su grupo. Se pueden iniciar en cualquier orden y pausar, reanudar o detener una sin cerrar la otra.

No bastaba con retirar la comprobación de “ya existe una ejecución”: los pedidos y vehículos se modificaban globalmente. Se añadió `EstadoOperativoEscenarios`, que mantiene por simulación copias independientes de estado de pedidos, entregas, historial, posición y carga de vehículos. La ejecución virtual no reinicia ni escribe sobre el estado operativo de Día a Día. Inventario, averías, planes y resultados mantienen su aislamiento existente por `executionId`.

Se comparten el catálogo mensual de entrada, los parámetros de tipos de vehículo, los almacenes, los mantenimientos y los bloqueos. Cambiar parámetros globales sigue afectando a las ejecuciones activas según el comportamiento ya existente. No se duplicó el optimizador ni se alteró su sincronización: hay coexistencia de ejecuciones y relojes, no garantía de cómputo SA en paralelo ni de igual rendimiento al ejecutar dos a la vez.

### Pedidos y averías: elegir la ejecución

Con las dos ejecuciones activas, enviar el identificador en las consultas evita ambigüedad:

```text
GET /api/pedidos?ejecucionId=ID&page=0&size=50
GET /api/pedidos/PEDIDO?ejecucionId=ID&instante=2026-11-29T05:00:00Z
POST /api/pedidos?ejecucionId=ID
GET /api/monitoreo/ID
```

Los endpoints existentes de detalle de vehículos y almacenes conservan su parámetro `ejecucionId`. Para registrar una avería con varias ejecuciones activas, indicar `ejecucionId` en el cuerpo de la solicitud, como ya permite la API.

Sin identificador explícito, pedidos/listados prefieren Día a Día si está activo; si solo existe una simulación activa conservan el uso anterior. Los pedidos manuales virtuales usan `SIM-<executionId>-...`, de modo que no entran a Día a Día ni a otra simulación. Los `M-...` reales no se incorporan a las copias virtuales.

### Persistencia y reinicio

El detalle mutable de las simulaciones vive en memoria, igual que sus relojes y planes activos. No se implementó reanudación de simulaciones tras reiniciar el backend. Los planes, resultados consolidados y kardex siguen persistidos. Después de un reinicio, consultar el resultado consolidado mediante `/api/escenarios/ID/resultado`; el detalle operativo en memoria no se reconstruye. Una consulta explícita de pedidos a una simulación sin copia disponible no reutiliza silenciosamente el estado real.

## 2. FIVE_DAY termina cuando colapsa

Cuando el `DetectorColapso` existente informa `PLAZO_VENCIDO` o `PLAZO_INALCANZABLE`, FIVE_DAY ahora:

1. termina con estado `COLLAPSED`;
2. conserva el instante de colapso;
3. persiste el resultado consolidado;
4. sale del conjunto activo y no solicita más planes.

COLLAPSE mantiene este comportamiento. DAY_TO_DAY continúa funcionando y conserva la alerta. El cierre de una simulación no detiene Día a Día.

Se conservó el criterio del detector: no se redefinió la factibilidad física ni la cota que utiliza. Que SA deje un pedido sin asignar en un ciclo NO basta por sí solo para terminar. La revisión ocurre en los puntos de avance del motor; no se añadió un reloj continuo independiente del tick.

## 3. Dos archivos mensuales

Se conservan sin cambios de formato los endpoints de un archivo (`archivo`). Se añaden endpoints de lote, con uno o dos campos multipart repetidos llamados `archivos`:

```text
POST /api/pedidos/carga-multiple
POST /api/red-vial/bloqueos/carga-multiple
```

Los nombres deben seguir los formatos existentes:

```text
Pedidos:  ventas.202611.txt + ventas.202612.txt
Bloqueos: bloqueo.2611.txt + bloqueo.2612.txt
```

Se valida el lote antes de guardar/reemplazar y se usa una transacción para todo el lote. Un segundo archivo inválido no deja el primero importado parcialmente. Se rechazan meses repetidos y más de dos archivos. Repetir pedidos importados conserva la deduplicación por identificador; los bloqueos se reemplazan únicamente dentro del mes de cada archivo.

El límite por archivo sigue en 5 MB y el límite total multipart pasa a 11 MB para dos archivos más los encabezados.

La validación ya existente al crear FIVE_DAY exige los meses del intervalo, incluyendo el mes siguiente cuando corresponda. Los archivos se conservan en el catálogo; el planificador solo recibe pedidos cuando llega su `registeredAt`. También se respeta el cruce diciembre–enero y la interpretación de horas de Lima.

**Cargar un archivo mensual no crea pedidos para sus días vacíos.** Verificar que los archivos del curso contienen datos en las fechas exactas elegidas.

### Ejemplo en PowerShell (servidor local en 8080)

Reemplazar las rutas de los archivos:

```powershell
curl.exe --fail-with-body -X POST "http://localhost:8080/api/pedidos/carga-multiple" `
  -F "archivos=@C:\datos\ventas.202611.txt" `
  -F "archivos=@C:\datos\ventas.202612.txt"

curl.exe --fail-with-body -X POST "http://localhost:8080/api/red-vial/bloqueos/carga-multiple" `
  -F "archivos=@C:\datos\bloqueo.2611.txt" `
  -F "archivos=@C:\datos\bloqueo.2612.txt"
```

También sigue siendo válido importar cada archivo por separado con los controles de carga actuales. La importación del mes siguiente no borra los pedidos del anterior.

### Probar la coexistencia por API

Después de cargar ambos meses, y sin otra ejecución del mismo grupo activa:

```powershell
$api = "http://localhost:8080/api"

$dia = Invoke-RestMethod -Method Post -Uri "$api/escenarios" `
  -ContentType "application/json" -Body '{"tipo":"DAY_TO_DAY"}'

$sim = Invoke-RestMethod -Method Post -Uri "$api/escenarios" `
  -ContentType "application/json" `
  -Body '{"tipo":"FIVE_DAY","inicioSimulado":"2026-11-29T05:00:00Z"}'

Invoke-RestMethod -Method Post -Uri "$api/escenarios/$($dia.id)/iniciar"
Invoke-RestMethod -Method Post -Uri "$api/escenarios/$($sim.id)/iniciar"

Invoke-RestMethod -Uri "$api/monitoreo/$($dia.id)"
Invoke-RestMethod -Uri "$api/monitoreo/$($sim.id)"
```

Si ese escenario colapsa por sus propios datos, dejará de aparecer como RUNNING; eso es el cierre solicitado, no un rechazo de concurrencia.

## Integración con el frontend: pendiente y explícita

Esta entrega NO modifica el frontend. En el frontend del ZIP base:

- `src/features/configuracion/ConfiguracionPage.tsx` aún rechaza una segunda ejecución con una comprobación global. Debe distinguir los dos grupos igual que el backend.
- Los formularios de carga envían un solo archivo. Para seleccionar ambos a la vez deben usar los endpoints `carga-multiple` y añadir dos entradas `archivos` a `FormData`.
- El listado/detalle de pedidos de cada ejecución debe enviar su `ejecucionId` mientras ambas están activas; de lo contrario, el valor por defecto será Día a Día.
- La vista de ejecución debe tratar `COLLAPSED` como terminal también en FIVE_DAY, usando `finSimulado`/`colapsoEn` y el resultado consolidado.

Por ello, sustituir solamente el backend no añade automáticamente el selector múltiple ni elimina el bloqueo visual del frontend. Los ejemplos API permiten comprobar el soporte nuevo sin modificar esta entrega.

## Instalación y validación

Detener el backend, conservar una copia del actual y reemplazar `paqrap-back` por la carpeta de este ZIP. Conservar cualquier configuración local propia. No copiar `target`, no ejecutar `down -v` y no aplicar migraciones antiguas.

Desde `paqrap-back`, con Java 21 y Maven:

```powershell
.\VALIDAR_CONCURRENCIA_COLAPSO_CARGA.ps1
```

El script ejecuta pruebas focalizadas y luego `mvn test` completo. Se detiene ante cualquier fallo. Alternativa directa:

```powershell
mvn clean test
```

Después, con la conexión a MySQL habitual configurada en la terminal:

```powershell
mvn spring-boot:run
```

`run-local.ps1` se conserva tal cual. No se modifica automáticamente el nombre del contenedor ni el puerto de tu entorno.

### Validación realmente realizada en esta entrega

- Compilación Java 21 de los 20 archivos de producción nuevos/modificados, usando clases base del ZIP y stubs de APIs externas para comprobación de compatibilidad. No fue un build Maven completo.
- 18 pruebas deterministas de lógica de motor/estado/importación, con repositorios dobles: pasaron. Incluyen una entrega ejecutada sin alterar los datos reales, relojes independientes, rechazo por grupo, colapso 5D aislado, ingreso manual por ejecución, cruce de meses/año, deduplicación y rechazo previo de lotes inválidos.
- Se incorporaron/actualizaron pruebas JUnit y MockMvc/H2 para concurrencia, importación y finalización. Las pruebas de integración Spring/H2 no se ejecutaron aquí.
- El algoritmo SA y los archivos de programación de rutas conservados se compararon byte a byte con la base.
- No se ejecutó FIVE_DAY completo ni se midió rendimiento concurrente en MySQL. No se afirma mejora de rendimiento ni ausencia absoluta de regresiones.

El entorno de preparación no tiene Maven ni sus dependencias disponibles. Los stubs y compilados de validación NO se incluyen ni se requieren para ejecutar el proyecto; el backend mantiene las dependencias originales de `pom.xml`.
