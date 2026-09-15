$ErrorActionPreference = 'Stop'

$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
if (-not (Test-Path -LiteralPath $edge)) { throw 'Microsoft Edge was not found.' }

$profileDir = Join-Path ([System.IO.Path]::GetTempPath()) 'iot-ops-platform-ui-audit'
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:18080/api/auth/login' `
    -ContentType 'application/json' -Body '{"username":"admin","password":"Admin@123"}'
$tokenLiteral = $login.data.tokenValue | ConvertTo-Json -Compress
$edgeProcess = Start-Process -FilePath $edge -PassThru -WindowStyle Hidden -ArgumentList @(
    '--headless=new', '--disable-gpu', '--remote-debugging-port=9224',
    "--user-data-dir=$profileDir", 'http://localhost:8080/login'
)

try {
    $target = $null
    for ($attempt = 0; $attempt -lt 30 -and $null -eq $target; $attempt++) {
        Start-Sleep -Milliseconds 300
        try { $target = (Invoke-RestMethod 'http://127.0.0.1:9224/json').Where({ $_.type -eq 'page' })[0] } catch { }
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
    Start-Sleep -Seconds 1
    Invoke-Cdp 'Runtime.evaluate' @{
        expression = "localStorage.setItem('iot-ops-token', $tokenLiteral)"
    } | Out-Null

    $pages = [ordered]@{
        '/' = '平台总览'
        '/devices' = '设备台账'
        '/device-imports' = '设备导入'
        '/taxonomy' = '分组与标签'
        '/work-orders' = '工单管理'
        '/reliability' = '可靠性中心'
        '/access' = '权限管理'
        '/audit' = '业务审计'
    }
    foreach ($entry in $pages.GetEnumerator()) {
        Invoke-Cdp 'Page.navigate' @{ url = "http://localhost:8080$($entry.Key)" } | Out-Null
        Start-Sleep -Seconds 1
        $evaluation = Invoke-Cdp 'Runtime.evaluate' @{
            expression = 'document.body.innerText'
            returnByValue = $true
        }
        $body = [string]$evaluation.result.result.value
        if (-not $body.Contains($entry.Value)) {
            throw "Page $($entry.Key) did not render heading $($entry.Value)."
        }
        Write-Host "Page passed: $($entry.Key) -> $($entry.Value)"
    }

    Invoke-Cdp 'Page.navigate' @{ url = 'http://localhost:8080/work-orders' } | Out-Null
    Start-Sleep -Seconds 1
    $click = Invoke-Cdp 'Runtime.evaluate' @{
        expression = "(() => { const row = [...document.querySelectorAll('.el-table__row')].find(value => value.innerText.includes('三入口串联演示工单')); const button = row?.querySelector('button'); if (button) { button.click(); return true; } return false; })()"
        returnByValue = $true
    }
    if (-not $click.result.result.value) { throw 'The linked demo work order was not found.' }
    Start-Sleep -Seconds 1
    $drawer = Invoke-Cdp 'Runtime.evaluate' @{
        expression = 'document.body.innerText'
        returnByValue = $true
    }
    $drawerText = [string]$drawer.result.result.value
    if (-not ($drawerText.Contains('工单详情与操作轨迹') -and
            $drawerText.Contains('系统') -and $drawerText.Contains('标记超时'))) {
        throw 'The localized SYSTEM timeout track was not visible in the work-order drawer.'
    }
    Write-Host 'Work-order drawer passed: 系统 / 标记超时'

    Invoke-Cdp 'Page.navigate' @{ url = 'http://localhost:8080/reliability' } | Out-Null
    Start-Sleep -Seconds 1
    $failureTab = Invoke-Cdp 'Runtime.evaluate' @{
        expression = "(() => { const tab = [...document.querySelectorAll('.el-tabs__item')].find(value => value.innerText.includes('巡检失败记录')); if (tab) { tab.click(); return true; } return false; })()"
        returnByValue = $true
    }
    if (-not $failureTab.result.result.value) { throw 'The timeout failure tab was not found.' }
    Start-Sleep -Seconds 2
    $failurePage = Invoke-Cdp 'Runtime.evaluate' @{
        expression = 'document.body.innerText'
        returnByValue = $true
    }
    $failureText = [string]$failurePage.result.result.value
    if (-not ($failureText.Contains('已解决') -and
            $failureText.Contains('演示用操作轨迹写入失败'))) {
        throw 'The localized resolved timeout failure was not visible in the reliability center.'
    }
    Write-Host 'Reliability recovery passed: 已解决 / 演示用操作轨迹写入失败'
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
