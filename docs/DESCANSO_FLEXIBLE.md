# Descanso obligatorio con ubicación temporal flexible

## Regla implementada

Se asigna un descanso continuo de 60 minutos por conductor/turno. El conductor cambia a las
07:00, 15:00 y 23:00; el vehículo mantiene su ruta y cada nuevo turno tiene su propia obligación.
No se impone una pausa simultánea 10–11, 18–19 o 02–03 a todos los vehículos.

Se interpreta el margen del caso como una hora completa respecto de ambos cambios de turno:

| Turno | Ventana que debe contener todo el descanso | Inicio permitido del descanso |
|---|---|---|
| 07:00–15:00 | 08:00–14:00 | 08:00–13:00 |
| 15:00–23:00 | 16:00–22:00 | 16:00–21:00 |
| 23:00–07:00 | 00:00–06:00 del día siguiente | 00:00–05:00 del día siguiente |

La flexibilidad consiste en escoger el instante de inicio, no en acortar, omitir o fraccionar la hora.
La formulación textual del margen del caso no especifica por separado extremos de inicio y fin;
esta interpretación está declarada para que el equipo la confirme frente a la aclaración docente.

## Cómo se decide

`RouteScheduler` evalúa varias programaciones para una misma secuencia de paradas.
Cada alternativa mantiene el reloj del vehículo, si el descanso del turno ya se tomó y las pausas
programadas. Explora descansos antes de viajar, al llegar antes del acondicionamiento, después del
acondicionamiento (antes del siguiente viaje) y al terminar el recorrido. Aprovecha esperas por
bloqueo para descansar y obliga a hacerlo antes del último inicio legal si continuar trabajando
haría perder esa oportunidad. La pausa se realiza estando detenido en un nodo, no en una arista.

Si una pausa cambia la hora de paso por la red, se vuelve a consultar el camino con bloqueos
temporales. Las alternativas se comparan buscando conservar plazos, reducir costo y evitar
demoras innecesarias. GRASP y SA usan exactamente el mismo programador y las mismas reglas.

Se retiene un número acotado de alternativas (`meal.beamWidth=8`, permitido 2..32), incluyendo
estados de descanso pendiente y realizado. Es una **heurística determinista de programación**:
no explora todos los instantes continuos posibles ni certifica que el horario elegido sea el mejor
matemáticamente. Puede no encontrar un horario factible aunque exista; nunca se convierte ese
fallo en prueba de imposibilidad. Un ancho mayor puede mejorar exploración y también consumir
más presupuesto de cómputo. Debe mantenerse igual para ambos algoritmos y fijarse tras el piloto.

## Acondicionamiento y plazos

La llegada física debe ser anterior o igual al deadline. Después hay 60 minutos de acondicionamiento
por entrega efectiva. Son dos actividades diferentes: el descanso no se cuenta como acondicionamiento.
Por ejemplo, el vehículo puede llegar a las 10:02, acondicionar hasta las 11:02 y descansar después;
no tiene que esperar a las 11:00 por una franja de descanso impuesta artificialmente.

En el JSON se distinguen `arrival`, `service_start`, `completion`, `returned_at` y `duty_completed_at`.
Si se programa la pausa entre llegada y acondicionamiento, `service_start` es posterior a `arrival`.
La llegada sigue determinando el plazo. Al completar una ruta temprana se puede descansar después
del regreso; esa pausa queda registrada y prolonga `duty_completed_at`, no la distancia de conducción.
Un regreso exacto al cambio de turno no crea artificialmente una nueva jornada activa.

## Estado inicial en este laboratorio por lotes

Este módulo no recibe un historial real de conductores de una simulación 5D. Por eso el manifiesto
declara una convención común: antes del snapshot los vehículos están ociosos en Central. Cuando
cabe una hora legal completa antes de salir, se registra explícitamente un descanso `BEFORE_ROUTE`.
Ejemplo: un lote cerrado a las 11:00 puede registrar 10:00–11:00 en Central antes de la salida.
No significa que el descanso vuelva a ser fijo: para otra hora de salida o turno será diferente.

No se regala ese historial a rutas de replanificación desde posiciones actuales en la calle.
Si no existe historial y ya pasó el último inicio legal, la ruta se rechaza. La integración futura
con 5D debe pasar estado real de descansos y trabajo ejecutado: no debe reutilizar esta convención
estática como si fueran hechos observados.

Cada ruta incluye sus descansos de todos los turnos alcanzados por su horizonte. Los vehículos
sin ruta tienen un descanso ocioso explícito para el primer turno del snapshot (`idle_vehicle_meals`),
sin kilómetros ni costo ficticio. No se genera una plantilla de cinco días para vehículos inactivos:
este laboratorio no simula una operación continua de cinco días.

## Validación y trazabilidad

`ScheduledMealBreak` registra turno, inicio, fin, ubicación y posición relativa al recorrido.
`MealBreakAudit` comprueba de forma separada del programador: duración exacta, márgenes,
una pausa por turno, ubicación alcanzable y ausencia de solapamiento con conducción o servicio.
`CommonAudit` repite la evaluación final sobre toda la demanda original y exporta los detalles.
`scripts/verify_results.py` revisa las exportaciones sin llamar al planificador Java.

Las columnas `mandatory_meals_valid` y `scheduled_route_meals` se añaden a `runs.csv` y al reporte.
El rechazo de una ruta por el programador no es un certificado de que ninguna otra ruta pueda servir
la demanda. Solo la cota optimista separada puede descartar ciertos pedidos individualmente.

La antigua clave `meal.startOffsetMinutes` se rechaza al cargar la configuración. No existe una
configuración aceptada que desactive el descanso ni que cambie sus 60 minutos.
