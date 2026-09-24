$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
  & (Join-Path $PSScriptRoot "build.ps1")
  & java -Xmx512m -jar dist/paqrap-experimentos.jar --self-test
  if ($LASTEXITCODE -ne 0) { throw "Fallaron las comprobaciones del experimento." }
  New-Item -ItemType Directory -Force build/offline-tests | Out-Null
  & javac --release 21 -encoding UTF-8 -cp dist/paqrap-experimentos.jar -d build/offline-tests dominio/src/test/java/pe/edu/pucp/paqrap/planner/domain/ReferenceRoadNetwork.java dominio/src/test/java/pe/edu/pucp/paqrap/planner/domain/RoadNetworkOfflineVerification.java
  if ($LASTEXITCODE -ne 0) { throw "Fallo la compilacion de la prueba diferencial." }
  & java -Xmx512m -cp "dist/paqrap-experimentos.jar;build/offline-tests" pe.edu.pucp.paqrap.planner.domain.RoadNetworkOfflineVerification
  if ($LASTEXITCODE -ne 0) { throw "Fallo la prueba diferencial." }
} finally { Pop-Location }
