$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\..\mvnw.cmd" -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
docker compose -f "$PSScriptRoot\..\compose.yml" up -d --build
Write-Host 'IoT Ops Platform is starting. UI: http://localhost:8080'
