# Evidencia v5 — campaña formal GRASP-v2 vs. SA v1.2

Evidencia compacta y versionada de la campaña formal ya ejecutada el 24 de septiembre de 2026. No contiene los 400 directorios `jobs/` ni los planes individuales, que son voluminosos y redundantes para reproducir los agregados y auditar las filas. La campaña original permanece localmente en `results/formal-sa-v12-grasp-v2-20260924/`.

## Identidad y entorno

- Commit: `79760a7129723d3d21e5c3113a8d45c5417a0e31` (`feature/experimentos-sa-v2`).
- JAR: `dist/paqrap-experimentos.jar`, SHA-256 `ed9dab5b9c6347b06805cb65220c1c6f7a40dfc8e255b2f7c72e0c4b7cb3d5e6`.
- Configuración original: SHA-256 `881ed98c93e4b99268c71dae168f3e35c11d36fe6ed89206227f220dcdec350d`.
- Entorno de corrida: Oracle Java 22, Windows 10, 512 MiB y 2 procesadores activos por JVM hija.
- Ejecución: `java -jar dist/paqrap-experimentos.jar --config config/formal.properties --output results/formal-sa-v12-grasp-v2-20260924`.

## Diseño congelado

40 instancias × 5 semillas × 2 algoritmos × límite máximo de 5 s = 400 corridas. Las familias son NORMAL (16), BLOCKED (8), SPLIT (8) y REAL (8). Los lotes REAL corresponden a 13–20 de septiembre.

El diseño excluye mantenimiento preventivo y averías; incluye bloqueos. Los manifiestos de las 40 instancias registran 0 mantenimientos, 0 averías y 582 bloqueos (24 en BLOCKED y 558 en REAL).

## Resultado registrado, sin reinterpretación

| Algoritmo | Corridas OK | Costo medio | Distancia media | Tiempo medio | Primer plan completo medio |
|---|---:|---:|---:|---:|---:|
| GRASP-v2 | 200/200 | 2649.97 | 457.67 km | 5001.05 ms | 78.20 ms |
| SA-operational-v1.2 | 200/200 | 5904.95 | 1053.23 km | 1713.01 ms | 41.17 ms |

Hubo 200/200 parejas completas: GRASP tuvo menor costo en 199, SA en 1 y no hubo empates. Ambas coberturas fueron totales. SA terminó antes por su calendario térmico; el protocolo compara límite máximo común de 5 s, no tiempo usado idéntico.

El análisis agregado primero por instancia usa n=40: el éxito completo fue empate (p=1.0); las 40 medias de costo por instancia favorecen GRASP; razón geométrica GRASP/SA=0.4583; prueba bilateral de signos p=1.819e-12, Holm=3.638e-12, bootstrap 95% de la diferencia logarítmica [-0.8267, -0.7309] y Wilcoxon bilateral exploratorio p=1.819e-12. No es una certificación de optimalidad ni de superioridad universal.

## Contenido

- `config/`: `formal.properties` y `formal.csv` usados.
- `metadata.json`, `runs.csv`, `paired.csv` y `report.html`: salida compacta primaria.
- `analysis/`: resultado ya generado por `scripts/analyze.py`, incluido el resumen por familia, por instancia y por pareja.
- `SHA256SUMS.txt`: huellas de los insumos, JAR y archivos versionados de esta evidencia.
