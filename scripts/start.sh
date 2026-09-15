#!/usr/bin/env sh
set -eu
project_dir="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$project_dir"
chmod +x mvnw
./mvnw -q -DskipTests package
docker compose -f compose.yml up -d --build
printf '%s\n' 'IoT Ops Platform is starting. UI: http://localhost:8080'
