$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
  if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw "Instala un JDK 21 o superior y configura PATH/JAVA_HOME." }
  if (Test-Path build/classes) { Remove-Item build/classes -Recurse -Force }
  New-Item -ItemType Directory -Force build/classes, dist | Out-Null
  $sources = Get-ChildItem dominio,grasp,sa,experimentos -Recurse -Filter *.java |
    Where-Object { $_.FullName -match '[\\/]src[\\/]main[\\/]java[\\/]' } |
    Sort-Object FullName | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }
  [IO.File]::WriteAllLines((Join-Path $root "build/sources.txt"), [string[]]$sources, (New-Object System.Text.UTF8Encoding($false)))
  & javac --release 21 -encoding UTF-8 -d build/classes '@build/sources.txt'
  if ($LASTEXITCODE -ne 0) { throw "Falló javac. Revisa el error anterior." }
  & jar --create --file dist/paqrap-experimentos.jar --main-class pe.edu.pucp.paqrap.experiment.ExperimentMain -C build/classes .
  if ($LASTEXITCODE -ne 0) { throw "Falló la creación del JAR." }
  Write-Host "Listo: dist/paqrap-experimentos.jar"
} finally { Pop-Location }
