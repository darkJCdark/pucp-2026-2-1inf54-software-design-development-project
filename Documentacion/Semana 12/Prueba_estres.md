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

## 8. Validación en despliegue

La prueba fue repetida sobre el despliegue del proyecto disponible en `http://1inf54-983-4d.inf.pucp.edu.pe`.

### 8.1. Precarga desplegada

Se procesaron nuevamente los 36 archivos de ventas y los 36 archivos de bloqueos correspondientes al periodo enero de 2026 a diciembre de 2028.

| Tipo | Archivos | Promedio | Mínimo | Máximo |
|---|---:|---:|---:|---:|
| Ventas | 36 | 7316.64 ms | 143 ms | 10314 ms |
| Bloqueos | 36 | 1514.58 ms | 1068 ms | 3691 ms |

La base de datos desplegada se encontraba parcialmente precargada. Algunos meses de ventas devolvieron `registrados = 0`, mientras que otros incorporaron los registros faltantes. Luego de la ejecución se completó la carga de los 36 meses considerados.

Estos tiempos corresponden únicamente a las solicitudes de carga de archivos y no se comparan con el presupuesto de 1500 ms establecido para cada ciclo del planificador.

Los resultados se encuentran en `R3_precarga_desplegado.csv`.

### 8.2. Ejecución FIVE_DAY desplegada

Se ejecutó el escenario FIVE_DAY número 23 con inicio simulado el 28/02/2026 a las 00:00.

Resultados:

- Estado final: COMPLETED
- Fin simulado: 05/03/2026 a las 00:00
- Duración real aproximada: 45 minutos
- Pedidos considerados: 215
- Pedidos entregados: 212
- Pedidos entregados a tiempo: 212
- Pedidos no entregados: 3
- Bloqueos considerados: 89
- Replanificaciones reportadas: 367
- Tiempo acumulado de cómputo: 312488 ms
- Colapso: no se produjo

La ejecución completó correctamente los cinco días simulados y cruzó de febrero a marzo utilizando la información previamente cargada.

### 8.3. Tiempo de planificación en despliegue

Durante la ejecución se observaron 238 planes distintos mediante consultas periódicas al campo `duracionMs`.

- Promedio observado: 780.19 ms
- Mínimo observado: 0 ms
- Máximo observado: 1535 ms
- Planes observados con `duracionMs > 1500`: 117

El backend reportó 367 replanificaciones totales. Debido a que el monitoreo se realizó mediante polling, no todos los ciclos fueron capturados individualmente.

El resultado confirma el hallazgo obtenido en la prueba local: el presupuesto máximo de 1500 ms por ciclo no se cumple de manera estricta. El máximo observado en el despliegue fue de 1535 ms y 117 de los 238 planes observados superaron el límite.

Los datos se encuentran en `R3_planes_fiveday_desplegado.csv` y `R3_resultado_fiveday_desplegado.json`.

### 8.4. Conclusión de la validación desplegada

La precarga de datos y la ejecución FIVE_DAY funcionan correctamente en el entorno desplegado. La simulación completó el periodo establecido, procesó información de dos meses consecutivos y no presentó colapso.

Sin embargo, se reproduce el incumplimiento del presupuesto de 1500 ms por ciclo de planificación observado localmente. Por ello, el rendimiento del planificador queda registrado como hallazgo para revisión y optimización.