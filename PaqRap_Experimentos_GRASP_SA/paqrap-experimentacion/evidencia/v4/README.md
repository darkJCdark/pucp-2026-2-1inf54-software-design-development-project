# Evidencia v4 — SA v1.2 y restricciones experimentales

Evidencia generada el 23-sep-2026 en Windows 10, Java/Javac 22 con `--release 21` y Maven 3.9.9.

- `tests/`: reportes Surefire de `mvn -s results/maven-settings.xml -o clean verify`; 38 métodos, 0 fallos, 0 errores, 0 omitidos.
- `self-test.txt`: salida completa de las 22 comprobaciones internas.
- `smoke/`: 16 corridas (8 GRASP + 8 SA), todas `OK`.
- `mini/`: 24 corridas de S01, S02, S03, S04, S07 y S08; no es pilot ni formal.
- `config/`: configuraciones exactas usadas para smoke y mini.
- `sha256.txt`: huellas del JAR, configuraciones y resultados principales.

No se ejecutaron `pilot`, `formal` ni la campaña completa de `escalabilidad`.
