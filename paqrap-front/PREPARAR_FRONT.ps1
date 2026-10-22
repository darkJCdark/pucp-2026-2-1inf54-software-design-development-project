$ErrorActionPreference = "Stop"

if (Test-Path "package-lock.json") {
  Write-Host "Eliminando package-lock anterior para incorporar Konva..."
  Remove-Item "package-lock.json" -Force
}

Write-Host "Instalando dependencias del frontend..."
npm install

Write-Host "Validando TypeScript y build de producción..."
npm run build

Write-Host "Frontend preparado. Versiona package.json y el nuevo package-lock.json."
