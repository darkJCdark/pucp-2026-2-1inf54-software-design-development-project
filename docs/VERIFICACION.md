# Verificación ejecutada — flex1

Entorno: Linux amd64, OpenJDK 21.0.11, Python 3. Herramientas ejecutadas dentro del entorno de
esta entrega, no en la computadora del estudiante. Los timestamps UTC son los registrados por el
entorno de ejecución; no se usan para atribuir horas de trabajo a integrantes del equipo.

## Pruebas

- Compilación de 87 archivos Java principales. Las 111 clases recompiladas coinciden byte a byte con las del JAR probado.
- 118 comprobaciones autónomas (`--self-test`), incluyendo 60 rutas aleatorias con auditoría de
  descanso, mutaciones inválidas, cambio de turno, medianoche, bloqueos y caché de horarios.
- 500 escenarios diferenciales de red: 1000 comparaciones de caminos (con repetición de caché)
  y 500 comparaciones de recorrido.
- 21 cuerpos de métodos de pruebas Java compilados/ejecutados con adaptador offline. **No es
  una ejecución de Maven/JUnit/Surefire**. Incluye la suite autónoma; no sumar sus 118 checks dos veces.
- 14 pruebas Python: pares, duplicados, cuadrícula incompleta, mezclas, costo de parciales,
  resumen del piloto, congelación y detección de errores en horarios exportados.
- 8 verificaciones del ejecutor: reanudación idéntica, no sobrescritura, cambio de configuración,
  guardia formal, rechazo de horario fijo, exclusión de mantenimiento, flota vacía y presupuesto 1 ms.
- Repetibilidad FIXED: 4 de 4 pares conservan hash de plan y métricas entre procesos separados.

Los logs y resultados detallados se conservan en `evidencia/flex1/`.

## Campañas de verificación nuevas

| Campaña | Corridas | Completas factibles | Rutas válidas |
|---|---:|---:|---:|
| smoke | 16 | 16 | 16 |
| readiness | 10 | 8 | 10 |
| deterministic-a | 4 | 4 | 4 |
| deterministic-b | 4 | 4 | 4 |
| mini-pilot | 8 | 4 | 8 |
| no-fleet | 2 | 0 | 2 |
| one-ms | 2 | 0 | 2 |

En total se ejecutaron 46 corridas: se conservan también los casos NO_SOLUTION y TIMEOUT.
La auditoría Python revisó 42 planes no fallidos y sus 343 descansos de rutas; para flota vacía
la solución está vacía y para 1 ms se conservan los timeouts. No se presentan esos casos como éxitos.
La evidencia no equivale a 46 instancias independientes: contiene repeticiones y pruebas técnicas.

Readiness completa las 8 corridas sintéticas de 5, 10, 15 y 20 pedidos. En el lote real de 41 pedidos,
a 1,5 s GRASP atendió 21 y SA 22; ambos son planes parciales con rutas válidas. El nuevo programador
consume más trabajo que una pausa fija: el presupuesto antiguo no se mantiene sin recalibración.

## Minipiloto técnico a 3 y 10 segundos

| Instancia | Presupuesto (s) | Algoritmo | Pedidos completos | Costo solo completo (S/) |
|---|---:|---|---:|---:|
| MINI20 | 3 | GRASP | 20/20 | 4212.000000 |
| MINI20 | 3 | SA | 20/20 | 3873.000000 |
| MINI20 | 10 | GRASP | 20/20 | 3744.000000 |
| MINI20 | 10 | SA | 20/20 | 3873.000000 |
| MINIREAL | 3 | SA | 33/41 | — |
| MINIREAL | 3 | GRASP | 33/41 | — |
| MINIREAL | 10 | SA | 38/41 | — |
| MINIREAL | 10 | GRASP | 38/41 | — |

En esta corrida real ambos llegaron a 38/41 con 10 s. Tres pedidos fueron descartados por la cota
optimista de llegada desde el snapshot: aumentar el tiempo de búsqueda no cambia sus deadlines.
Atender los 38 no convierte el plan en completo frente a los 41 originales ni prueba optimalidad
de costo. Es una observación de esta instancia/semilla, no un ganador general.

El minipiloto comprueba ejecución y análisis con varios presupuestos. No se ejecutaron el piloto
completo de 288 corridas ni la campaña formal de 400. La configuración de congelación de ejemplo
se validó para 400 trabajos, pero esos trabajos **no se lanzaron**.

## Identidad del binario

SHA-256 del JAR usado por las campañas anteriores:

```
8df53b32c9606470925c3a9d3428a716cc61c02007ae16ae399dfac1794cc2e8
```

No reutilizar estos resultados después de cambiar el código/configuración/descanso. Los hashes y
el modelo `snapshot-batch-flex1` aparecen también en los metadatos y manifiestos de cada campaña.
