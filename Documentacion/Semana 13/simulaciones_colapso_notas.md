# Simulaciones de colapso — notas de trabajo (Semana 13, Tenipuclla)

Simulador: `SimulacionColapsoTest` (opt-in `-Dpaqrap.sim=true`). Datos del curso desde 2026-01-01, flota y almacenes del
mock (37 unidades, central + NOROESTE + ESTE), bloqueos de los archivos, SA con tope de 300 iteraciones, semilla de
averías fija (20261111). "Falla" = pedido que ya no se puede entregar a tiempo. Ta = ms por ciclo (ojo: las corridas
en paralelo se hicieron más lentas por contención de CPU/memoria, el Ta absoluto de las corridas largas no es fiable).

## 1. Sin averías (solo bloqueos), política fija

| Versión | Sa | Primer colapso | Fallas en 150 días | Causa |
|---|---|---|---|---|
| base (código de develop) | 60 | ninguno al 2-may (detenida) | 0 | — (Ta ~3 s/ciclo) |
| v1 | 30 | día 18 | 1 | error de splitDelivery (misma unidad, mismo punto) |
| v1 | 60 | día 121 | 2 | error de splitDelivery + pedido grande de 4 h |
| v1 | 120 | día 81 | 2 | pedidos de 4 h esperando el ciclo |
| v1 | 240 | colapso masivo en mayo | 227 | Sa demasiado grande |
| v2 (splitDelivery corregido) | 60 | ninguno al 20-may (detenida) | 0 | — |

## 2. Con 3 averías/día + bloqueos (v2 = con todas las mejoras, sin trasvase)

| Versión | Política | Fallas (fecha del último avance) |
|---|---|---|
| v2 | eventos, paso 15 min | **0 al 6-may** |
| v2 | eventos, paso 5 min | 2 al 13-abr |
| v2 | híbrido (fijo 30 + averías al instante) | 0 al 30-mar |
| v2 | híbrido (fijo 60 + averías al instante) | 1 al 6-abr |
| v2 | fijo 30 | 4 al 27-abr |
| v2 | fijo 60 | 6 al 20-abr |
| v2 | fijo 90 | 11 al 16-abr |
| v2 | fijo 120 | 14 al 3-abr |
| base | fijo 60 | 3 al 4-mar |
| base | eventos 15 | 1 al 11-mar |
| diag v2 | fijo 60, 60 días | 4 fallas, primer colapso día 26.7 |

Causa de las fallas con averías (diagnóstico): averías poco antes del plazo. Ej.: MINOR TB08 16:25 con plazo 17:24
(el ciclo fijo de las 17:00 ya no llega); INTERMEDIATE TB04 03:50 con plazo 04:51 a 53 km del central (solo un
trasvase o un intermedio cercano lo salva).

## 3. Con trasvase (3 averías/día) — corridas detenidas temprano por falta de memoria

| Política | Fallas (fecha) |
|---|---|
| eventos 15 + trasvase (margen 60 min) | 0 al 4-mar |
| eventos 15 + trasvase siempre | 0 al 19-feb |
| híbrido 30 + trasvase | 0 al 22-feb |
| fijo 60 + trasvase | 4 al 7-mar (sin trasvase: ya 4 al 26-feb) |

## Conclusiones provisionales

- Sa grande (≥ 90 min) colapsa pronto: los pedidos de 4 h vencen esperando el ciclo.
- Con averías, replanificar al instante ante la avería es lo que más importa; "eventos 15" e "híbrido 30" son las mejores.
- La v2 es ~6x más rápida por ciclo que la base, clave para la app real (1 s real = 24 min simulados en colapso).

## 4. Demanda alta desde 2026-09-01 (código anterior a refrigerio postergable / corte en intermedios)

Eventos paso 15, 3 averías/día, ~165-200 pedidos/día. Detenidas el 9-oct (ya habían respondido la pregunta).

| Corrida | Primer colapso | Fallas al 9-oct | Ta prom. |
|---|---|---|---|
| con trasvase | 2-oct (día 31) | 1 | 1.63 s |
| sin trasvase | 2-oct (día 31) | 1 | 1.59 s |

Falla: 202610-00260, 8 paq., registrado 13:36, plazo 17:36, destino (55,22), **sin avería**: 4 h sin unidad
disponible. Con demanda alta el colapso viene de la saturación de la flota, no de las averías; el trasvase no cambia nada.

## 5. Costo de cómputo de las mejoras (2 días desde 2026-09-01)

| Versión | Ta prom. | Ta máx. |
|---|---|---|
| corte en intermedios en cada ciclo | 5.8 s | 14.1 s |
| sin refrigerio postergable ni corte en intermedios | 1.6 s | 7.5 s |
| final (refrigerio postergable + corte en intermedios solo si la ruta incumple) | 1.1 s | 3.8 s |

Versión final (trasvase activo) desde 2026-09-01: 0 fallas al 23-sep, Ta prom. ~1-2.3 s; detenida para probar oct-2027.

## 6. Oct-2027 (~500 pedidos/día, los 5 000 del archivo caen en 10 días)

Versión final, eventos 15, 3 averías/día. Detenida el 2-oct: colapso el mismo 1-oct (sin avería), 45 fallas al
2-oct y Ta prom. ~16 s. Con y sin trasvase, igual. La demanda supera la capacidad física de 37 unidades.

Nota de datos: cada archivo trae como máximo 5 000 pedidos; desde sep-2026 se concentran en menos días
(ene-2027: 20 días, ~250/día; oct-2027: 10 días, ~500/día; dic-2028: 6 días, ~833/día) y el resto del mes viene vacío.

## 7. Ene-2027 (~250 pedidos/día, 20 días con pedidos) — versión final sin inserción

Eventos 15, 3 averías/día. Detenidas el 7-ene: primer colapso el 3-ene (día 3, 202701-00666, 9 paq., plazo 4 h, a 51 km,
sin avería). 6 fallas al 7-ene, Ta prom. ~3.2 s. Con y sin trasvase, igual.

## 8. Inserción en rutas en marcha y trasvase ampliado (ene-2027, 5 días, 3 averías/día)

| Versión | Fallas | Trasvases | Recargas en intermedio | Ta prom. / máx. |
|---|---|---|---|---|
| sin inserción | 6 al 7-ene (primera el 3-ene, saturación) | 0 | — | ~3.2 s |
| con inserción | 1 (3-ene, MINOR TM03 a 75 min del plazo) | 0 | 1 622 | 2.3 s / 8.9 s |
| con inserción + trasvase a mitad de ruta | **0** | 2 | 1 572 | 2.3 s / 11.3 s |

## 9. Oct-nov 2026 (~185-250 pedidos/día), 3 averías/día — con y sin inserción (trasvase sin ampliar)

Detenidas el 12/13-nov para probar con la regla del bloqueo del profesor.

| Versión | Fallas | Primer colapso |
|---|---|---|
| sin inserción | 4 al 12-nov | 2-oct (saturación) |
| con inserción | 2 al 13-nov | en noviembre (todo octubre sin fallas) |

## 10. Ene-2027, 5 días, versión completa (inserción + trasvase ampliado + regla de bloqueo del profesor)

1 332 pedidos, 18 averías: **0 fallas**, 0 trasvases, 1 779 recargas en intermedio, nunca un pedido esperando.
Ta prom. 1.7 s / máx. 7.3 s (sin otras simulaciones en paralelo).
