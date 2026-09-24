#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v javac >/dev/null || { echo "Se necesita un JDK 21 o superior (javac)."; exit 1; }
rm -rf build/classes
mkdir -p build/classes dist
find dominio grasp sa experimentos -path '*/src/main/java/*' -name '*.java' | sort | sed 's/.*/"&"/' > build/sources.txt
javac --release 21 -encoding UTF-8 -d build/classes @build/sources.txt
jar --create --file dist/paqrap-experimentos.jar --main-class pe.edu.pucp.paqrap.experiment.ExperimentMain -C build/classes .
echo "Listo: dist/paqrap-experimentos.jar"
