#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/offline-tests
javac --release 21 -encoding UTF-8 -cp dist/paqrap-experimentos.jar -d build/offline-tests \
 dominio/src/test/java/pe/edu/pucp/paqrap/planner/domain/ReferenceRoadNetwork.java \
 dominio/src/test/java/pe/edu/pucp/paqrap/planner/domain/RoadNetworkOfflineVerification.java
java -Xmx512m -cp dist/paqrap-experimentos.jar:build/offline-tests pe.edu.pucp.paqrap.planner.domain.RoadNetworkOfflineVerification
