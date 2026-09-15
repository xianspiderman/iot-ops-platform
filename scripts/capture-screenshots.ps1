$ErrorActionPreference = 'Stop'

$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
if (-not (Test-Path -LiteralPath $edge)) { throw 'Microsoft Edge was not found.' }

$projectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$outputDir = Join-Path $projectRoot 'docs\screenshots'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$profileDir = Join-Path ([System.IO.Path]::GetTempPath()) 'iot-ops-platform-edge-profile'

$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:18080/api/auth/login' `
    -ContentType 'application/json' -Body '{"username":"admin","password":"Admin@123"}'
$token = $login.data.tokenValue

$edgeProcess = Start-Process -FilePath $edge -PassThru -WindowStyle Hidden -ArgumentList @(
    '--headless=new', '--disable-gpu', '--hide-scrollbars', '--window-size=1440,900',
    '--remote-debugging-port=9223', "--user-data-dir=$profileDir", 'http://localhost:8080/login'
)

try {
    $target = $null
    for ($attempt = 0; $attempt -lt 30 -and $null -eq $target; $attempt++) {
        Start-Sleep -Milliseconds 300
        try {
            $target = (Invoke-RestMethod 'http://127.0.0.1:9223/json').Where({ $_.type -eq 'page' })[0]
        } catch { }
    }
    if ($null -eq $target) { throw 'Edge DevTools target did not become ready.' }

    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    $socket.ConnectAsync([Uri]$target.webSocketDebuggerUrl,
        [Threading.CancellationToken]::None).GetAwaiter().GetResult() | Out-Null
    $script:commandId = 0

    function Invoke-Cdp([string]$method, [hashtable]$params = @{}) {
        $script:commandId++
        $id = $script:commandId
        $json = @{ id = $id; method = $method; params = $params } | ConvertTo-Json -Compress -Depth 10
        $bytes = [Text.Encoding]::UTF8.GetBytes($json)
        $socket.SendAsync([ArraySegment[byte]]::new($bytes),
            [System.Net.WebSockets.WebSocketMessageType]::Text, $true,
            [Threading.CancellationToken]::None).GetAwaiter().GetResult() | Out-Null
        while ($true) {
            $stream = [IO.MemoryStream]::new()
            do {
                $buffer = [byte[]]::new(1048576)
                $result = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer),
                    [Threading.CancellationToken]::None).GetAwaiter().GetResult()
                $stream.Write($buffer, 0, $result.Count)
            } while (-not $result.EndOfMessage)
            $response = [Text.Encoding]::UTF8.GetString($stream.ToArray()) | ConvertFrom-Json
            if ($response.id -eq $id) { return $response }
        }
    }

    Invoke-Cdp 'Page.enable' | Out-Null
    Start-Sleep -Seconds 2
    $loginShot = Invoke-Cdp 'Page.captureScreenshot' @{ format = 'png'; fromSurface = $true }
    [IO.File]::WriteAllBytes((Join-Path $outputDir 'login.png'),
        [Convert]::FromBase64String($loginShot.result.data))

    $tokenLiteral = $token | ConvertTo-Json -Compress
    Invoke-Cdp 'Runtime.evaluate' @{ expression = "localStorage.setItem('iot-ops-token', $tokenLiteral)" } | Out-Null
    Invoke-Cdp 'Page.navigate' @{ url = 'http://localhost:8080/' } | Out-Null
    Start-Sleep -Seconds 4
    $dashboardShot = Invoke-Cdp 'Page.captureScreenshot' @{ format = 'png'; fromSurface = $true }
    [IO.File]::WriteAllBytes((Join-Path $outputDir 'dashboard.png'),
        [Convert]::FromBase64String($dashboardShot.result.data))
    $socket.Dispose()
} finally {
    if (-not $edgeProcess.HasExited) { $edgeProcess.Kill($true) }
    if (Test-Path -LiteralPath $profileDir) {
        $resolvedProfile = (Resolve-Path -LiteralPath $profileDir).Path
        $resolvedTemp = (Resolve-Path -LiteralPath ([System.IO.Path]::GetTempPath())).Path
        if ($resolvedProfile.StartsWith($resolvedTemp, [StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedProfile -Recurse -Force
        }
    }
}

Write-Host "Captured login.png and dashboard.png in $outputDir"
