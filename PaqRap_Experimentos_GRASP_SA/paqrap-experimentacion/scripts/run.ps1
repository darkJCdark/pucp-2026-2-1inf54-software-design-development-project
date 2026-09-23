param([ValidateSet("smoke","pilot","formal","deterministic")][string]$Profile="smoke")
$ErrorActionPreference="Stop"
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
  if (-not (Test-Path dist/paqrap-experimentos.jar)) { & "$PSScriptRoot/build.ps1" }
  & java -jar dist/paqrap-experimentos.jar --config "config/$Profile.properties"
  if ($LASTEXITCODE -ne 0) { throw "La ejecución falló. Revisa el mensaje anterior." }
} finally { Pop-Location }
