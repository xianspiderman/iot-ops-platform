#!/usr/bin/env sh
set -eu
docker compose -f "$(dirname "$0")/../compose.yml" down --volumes --remove-orphans
echo 'IoT Ops Platform containers and named data volumes were removed.'
