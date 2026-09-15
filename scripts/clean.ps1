$ErrorActionPreference = 'Stop'
docker compose -f "$PSScriptRoot\..\compose.yml" down --volumes --remove-orphans
Write-Host 'IoT Ops Platform containers and named data volumes were removed.'
