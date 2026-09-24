#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/build.sh
java -Xmx512m -jar dist/paqrap-experimentos.jar --self-test
bash scripts/test-road-network.sh
