$ErrorActionPreference = 'Stop'
docker compose -f "$PSScriptRoot\..\compose.yml" down
