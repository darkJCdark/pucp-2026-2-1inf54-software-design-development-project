# Prueba de estrés – Semana 12

## 1. Objetivo

Validar la precarga de datos históricos y el comportamiento de una ejecución FIVE_DAY, considerando tiempos de carga, tiempo de planificación y consumo de memoria.

La prueba se realizó localmente sobre una base de datos independiente denominada `paqrap_sem12_r3`.

## 2. Precarga de datos

Se cargaron los archivos correspondientes al periodo enero de 2026 a diciembre de 2028.

- Archivos de ventas procesados: 36
- Archivos de bloqueos procesados: 36
- Total de archivos procesados: 72
- Cargas exitosas: 72
- Cargas fallidas: 0

Los resultados detallados se encuentran en `R3_precarga_local.csv`.

### 2.1. Tiempos de carga

| Tipo | Archivos | Promedio | Mínimo | Máximo |
|---|---:|---:|---:|---:|
| Ventas | 36 | 3291.56 ms | 1757 ms | 4309 ms |
| Bloqueos | 36 | 670.22 ms | 389 ms | 1068 ms |

Estos valores corresponden al tiempo extremo a extremo de los POST de carga y no al tiempo de ejecución del algoritmo de planificación.

## 3. Validación de recarga

Se volvió a cargar el archivo `ventas.202601.txt`.

Resultado:

- Leídos: 641
- Registrados: 0
- Omitidos: 641

Con ello se verificó que una segunda carga del mismo archivo de ventas no duplica pedidos previamente registrados.

También se volvió a cargar `bloqueo.2601.txt`.

Resultado:

- Reemplazados: 669
- Registrados: 669

Con ello se verificó que la recarga de bloqueos reemplaza los registros correspondientes al periodo y vuelve a registrar el contenido del archivo.

## 4. Ejecución FIVE_DAY

Se creó una ejecución FIVE_DAY con inicio simulado el 28/02/2026 a las 00:00, con el objetivo de validar un escenario que cruce de febrero a marzo.

Datos de ejecución:

- Tipo: FIVE_DAY
- Estado final: COMPLETED
- Inicio simulado: 28/02/2026 00:00
- Fin simulado: 05/03/2026 00:00
- Factor de aceleración: 160
- Duración real aproximada: 45 minutos
- Total de pedidos: 215
- Pedidos entregados: 213
- Pedidos entregados a tiempo: 213
- Pedidos no entregados: 2
- Bloqueos considerados: 89
- Replanificaciones reportadas por backend: 375
- Tiempo acumulado de cómputo: 315431 ms
- Colapso: no se produjo

El resultado completo se encuentra en `R3_resultado_fiveday_local.json`.

## 5. Consumo de memoria

Durante la ejecución FIVE_DAY se realizó un muestreo periódico del proceso Java correspondiente al backend.

Resultados:

- Memoria promedio: 365.04 MB
- Memoria mínima: 354.81 MB
- Memoria máxima: 392.07 MB

Las muestras se encuentran registradas en `R3_memoria_fiveday_local.csv`.

No se observó un crecimiento progresivo de memoria que impidiera completar la simulación.

## 6. Tiempo de planificación

Durante la ejecución se capturaron 295 planes distintos mediante el campo `duracionMs`.

Resultados:

- Tiempo promedio observado: 738.60 ms
- Tiempo mínimo observado: 0 ms
- Tiempo máximo observado: 1557 ms

Distribución:

| Rango | Planes |
|---|---:|
| 0 ms | 150 |
| 1–1000 ms | 1 |
| 1001–1400 ms | 0 |
| 1401–1500 ms | 9 |
| Mayor a 1500 ms | 135 |
| Total observado | 295 |

El backend reportó 375 replanificaciones totales. Debido a que la consulta del último plan se realizó mediante polling, no fue posible capturar individualmente todos los ciclos, por lo que los 295 registros corresponden a los planes observados durante el monitoreo.

Los datos obtenidos se encuentran en `R3_planes_fiveday_local.csv`.

## 7. Resultado

La precarga de los 36 meses de ventas y 36 meses de bloqueos se completó correctamente y se verificó el comportamiento esperado ante recargas.

La ejecución FIVE_DAY también finalizó correctamente, cruzó de febrero a marzo y mantuvo un consumo de memoria estable.

Sin embargo, el presupuesto máximo de 1500 ms por ciclo de planificación no se cumple de manera estricta. De los 295 planes observados, 135 presentaron un `duracionMs` superior a 1500 ms. El mayor valor registrado fue 1557 ms, es decir, 57 ms por encima del límite establecido.

Por este motivo, el criterio de rendimiento del planificador queda registrado como hallazgo y debe ser revisado en la tarea de optimización correspondiente.