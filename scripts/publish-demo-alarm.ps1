$ErrorActionPreference = 'Stop'
$eventId = "demo-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
$payload = "{`"eventId`":`"$eventId`",`"deviceSn`":`"DEMO-SN-001`",`"eventType`":`"DEVICE_OFFLINE`",`"severity`":`"HIGH`",`"occurredAt`":`"$((Get-Date).ToString('yyyy-MM-ddTHH:mm:ss'))`",`"data`":{`"source`":`"compose-demo`"}}"
docker exec iot-ops-platform-broker-1 sh mqadmin sendMessage -n namesrv:9876 -t iot-device-alarm -p $payload
Write-Host "Published alarm event $eventId"
