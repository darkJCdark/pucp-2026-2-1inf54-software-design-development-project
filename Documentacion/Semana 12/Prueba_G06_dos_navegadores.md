# Prueba G06 – Dos navegadores

## 1. Objetivo

Verificar que una misma ejecución FIVE_DAY pueda ser visualizada simultáneamente desde dos dispositivos distintos y que las interacciones propias de cada navegador no modifiquen el estado visual del otro usuario.

## 2. Entorno de prueba

- Entorno: despliegue del proyecto
- URL: `http://1inf54-983-4d.inf.pucp.edu.pe`
- Ejecución utilizada: #23
- Tipo: FIVE_DAY
- Estado durante la prueba: RUNNING
- Factor de aceleración: 160x
- Inicio simulado: 28/02/2026 00:00
- Fin simulado esperado: 05/03/2026 00:00

Se utilizó una única ejecución activa en el backend y se accedió a ella simultáneamente desde dos dispositivos distintos.

## 3. Procedimiento

En ambos dispositivos se abrió la ejecución #23 mientras la simulación FIVE_DAY se encontraba en curso.

En el primer dispositivo se modificaron la selección de elementos, el nivel de zoom del mapa y los filtros disponibles.

En el segundo dispositivo se realizaron selecciones, zoom y filtros diferentes a los utilizados en el primer dispositivo.

Posteriormente se verificó nuevamente el estado de ambos navegadores.

## 4. Resultados

Se comprobó que ambos dispositivos mostraron simultáneamente la misma ejecución #23 y recibieron la actualización del mismo estado de simulación almacenado en el backend.

Las interacciones realizadas en cada navegador permanecieron independientes:

- La selección realizada en un dispositivo no modificó la selección del otro.
- El nivel de zoom utilizado en un dispositivo no alteró el mapa mostrado en el otro.
- Los filtros aplicados en un navegador no modificaron los filtros del segundo navegador.
- Ambos dispositivos continuaron visualizando el avance de la misma ejecución FIVE_DAY.

Durante la prueba se mantuvo una única ejecución activa en el servidor.

## 5. Evidencia

Se conservó una captura del navegador principal durante la ejecución #23, en la cual se observa la simulación FIVE_DAY en ejecución, el mapa operativo, los indicadores y los paneles de seguimiento.

La visualización simultánea desde el segundo dispositivo y la independencia de sus interacciones fueron verificadas manualmente durante la misma ejecución.

## 6. Conclusión

La prueba G06 se considera APROBADA.

Se verificó que dos dispositivos distintos pueden visualizar simultáneamente una misma ejecución FIVE_DAY y que las acciones de selección, zoom y filtrado pertenecen al estado local de cada navegador, sin modificar la visualización del otro usuario.

El estado de la simulación permanece centralizado en el backend, mientras cada visualizador mantiene de forma independiente su estado de interacción.
