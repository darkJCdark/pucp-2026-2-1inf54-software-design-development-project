#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
profile="${1:-smoke}"
case "$profile" in smoke|pilot|formal|deterministic|readiness|escalabilidad) ;; *) echo "Perfil: smoke, readiness, pilot, escalabilidad, formal o deterministic"; exit 1;; esac
[ -f dist/paqrap-experimentos.jar ] || bash scripts/build.sh
java -jar dist/paqrap-experimentos.jar --config "config/$profile.properties"
