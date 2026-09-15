#!/usr/bin/env sh
set -eu
event_id="demo-$(date +%s)"
payload="{\"eventId\":\"$event_id\",\"deviceSn\":\"DEMO-SN-001\",\"eventType\":\"DEVICE_OFFLINE\",\"severity\":\"HIGH\",\"occurredAt\":\"$(date '+%Y-%m-%dT%H:%M:%S')\",\"data\":{\"source\":\"compose-demo\"}}"
docker exec iot-ops-platform-broker-1 sh mqadmin sendMessage -n namesrv:9876 -t iot-device-alarm -p "$payload"
printf 'Published alarm event %s\n' "$event_id"
