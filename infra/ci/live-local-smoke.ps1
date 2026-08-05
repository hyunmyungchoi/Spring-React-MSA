param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")),
    [string]$EnvironmentFile = (Join-Path $Root "infra\vm\.env.local"),
    [switch]$KafkaEnabled
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

$variables = @{}
Get-Content -LiteralPath $EnvironmentFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and !$line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line -split "=", 2
        $name = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"')
        $variables[$name] = $value
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

foreach ($required in @("ADMIN_BOOTSTRAP_LOGIN_ID", "ADMIN_BOOTSTRAP_PASSWORD")) {
    if (!$variables[$required]) {
        throw "Missing required environment variable: $required"
    }
}

foreach ($targetPort in $(if ($KafkaEnabled) { 8079, 8081, 8083, 8084 } else { 8082, 18083, 18084 })) {
    $portProbe = [Net.Sockets.TcpClient]::new()
    try {
        $portProbe.Connect("127.0.0.1", $targetPort)
        throw "Port $targetPort is already occupied"
    } catch [Net.Sockets.SocketException] {
        # Expected: the service under test is started on this port below.
    } finally {
        $portProbe.Dispose()
    }
}

$env:JAVA_HOME = Join-Path $Root "JDK"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:SPRING_PROFILES_ACTIVE = "local"
$env:APP_KAFKA_ENABLED = if ($KafkaEnabled) { "true" } else { "false" }

$logDirectory = Join-Path $env:TEMP "springmsa-codex-live"
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null

$processes = @()
$handler = $null
$client = $null
$webSocket = $null
$communityId = $null
$stockId = $null

function Start-ServiceProcess {
    param([string]$Name, [string]$Directory, [int]$Port)

    $env:SERVER_PORT = [string]$Port
    $process = Start-Process `
        -FilePath (Join-Path $Root "$Directory\gradlew.bat") `
        -ArgumentList "bootRun", "--no-daemon" `
        -WorkingDirectory (Join-Path $Root $Directory) `
        -RedirectStandardOutput (Join-Path $logDirectory "$Name.out.log") `
        -RedirectStandardError (Join-Path $logDirectory "$Name.err.log") `
        -WindowStyle Hidden `
        -PassThru

    $script:processes += [pscustomobject]@{
        Name = $Name
        Port = $Port
        Process = $process
    }
}

function Send-Json {
    param(
        [string]$Method,
        [Uri]$Uri,
        $Body = $null,
        [string]$CsrfToken = ""
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method),
        $Uri
    )

    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Compress
        $request.Content = [System.Net.Http.StringContent]::new(
            $json,
            [Text.Encoding]::UTF8,
            "application/json"
        )
    }

    if ($CsrfToken) {
        $request.Headers.Add("X-MEMBER-XSRF-TOKEN", $CsrfToken)
    }

    return $script:client.SendAsync($request).GetAwaiter().GetResult()
}

function Read-ResponseJson {
    param([System.Net.Http.HttpResponseMessage]$Response)

    return $Response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
}

function Find-EntityId {
    param($Data)

    $property = $Data.PSObject.Properties |
        Where-Object { $_.Name -match "(^id$|Id$)" } |
        Select-Object -First 1

    return $property.Value
}

try {
    if ($KafkaEnabled) {
        Start-ServiceProcess "user" "BackEnd\spring-user-service" 8081
    }
    Start-ServiceProcess "community" "BackEnd\spring-member-community-service" $(if ($KafkaEnabled) { 8083 } else { 18083 })
    Start-ServiceProcess "stock" "BackEnd\spring-member-stock-service" $(if ($KafkaEnabled) { 8084 } else { 18084 })

    if (!$KafkaEnabled) {
        $env:BFF_API_COMMUNITY_API_BASE_URL = "http://127.0.0.1:18083"
        $env:BFF_API_STOCK_API_BASE_URL = "http://127.0.0.1:18084"
    }
    Start-ServiceProcess "memberBff" "BackEnd\spring-member-bff-service" $(if ($KafkaEnabled) { 8079 } else { 8082 })

    $deadline = (Get-Date).AddMinutes(4)
    $ready = @{}
    $expectedServiceCount = if ($KafkaEnabled) { 4 } else { 3 }
    while ((Get-Date) -lt $deadline -and $ready.Count -lt $expectedServiceCount) {
        foreach ($entry in $processes) {
            if ($ready.ContainsKey($entry.Name)) {
                continue
            }
            if ($entry.Process.HasExited) {
                throw "$($entry.Name) exited with $($entry.Process.ExitCode)"
            }
            try {
                $health = Invoke-RestMethod `
                    -Uri "http://127.0.0.1:$($entry.Port)/actuator/health" `
                    -TimeoutSec 2
                if ($health.status -eq "UP") {
                    $ready[$entry.Name] = $true
                    Write-Output "START_$($entry.Name)=UP"
                }
            } catch {
                # Continue polling while Gradle and Spring Boot initialize.
            }
        }
        Start-Sleep -Seconds 2
    }

    if ($ready.Count -lt $expectedServiceCount) {
        throw "Service startup timeout"
    }

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.UseCookies = $true
    $handler.CookieContainer = [System.Net.CookieContainer]::new()
    $client = [System.Net.Http.HttpClient]::new($handler)
    $script:client = $client
    $gateway = [Uri]"http://localhost:8080"

    $login = Send-Json "POST" ([Uri]::new($gateway, "/login/password")) @{
        loginId = $variables["ADMIN_BOOTSTRAP_LOGIN_ID"]
        password = $variables["ADMIN_BOOTSTRAP_PASSWORD"]
    }
    Write-Output "MEMBER_PASSWORD_LOGIN=$([int]$login.StatusCode)"
    if (!$login.IsSuccessStatusCode) {
        throw "Member password login failed"
    }

    $current = [Uri]::new($gateway, "/bff/oauth2/authorization/member-bff")
    $oauthComplete = $false
    for ($step = 1; $step -le 24; $step++) {
        $response = Send-Json "GET" $current
        $status = [int]$response.StatusCode
        if ($status -ge 300 -and $status -lt 400) {
            $location = $response.Headers.Location
            if (!$location.IsAbsoluteUri) {
                $location = [Uri]::new($current, $location)
            }
            if ($location.Port -eq 5173) {
                if ($location.AbsolutePath -eq "/" -or $location.AbsolutePath.StartsWith("/auth")) {
                    $oauthComplete = $true
                    break
                }
                $location = [Uri]::new($gateway, $location.PathAndQuery)
            }
            $current = $location
            continue
        }
        if (!$response.IsSuccessStatusCode) {
            throw "Member OAuth failed: HTTP $status"
        }
        break
    }

    if (!$oauthComplete) {
        throw "Member OAuth did not complete"
    }
    Write-Output "MEMBER_OAUTH=OK"

    $me = Send-Json "GET" ([Uri]::new($gateway, "/bff/auth/me"))
    $meBody = Read-ResponseJson $me
    if (!$meBody.data.authenticated) {
        throw "Member session not authenticated"
    }
    Write-Output "MEMBER_ME=AUTHENTICATED"

    $csrfCookie = $handler.CookieContainer.GetCookies($gateway)["MEMBER-XSRF-TOKEN"]
    if (!$csrfCookie) {
        throw "Member CSRF cookie missing"
    }
    $csrf = [Uri]::UnescapeDataString($csrfCookie.Value)
    $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

    if ($KafkaEnabled) {
        $testLoginId = "codex-kafka-$stamp"
        $registration = Send-Json "POST" ([Uri]::new($gateway, "/bff/registration/member")) @{
            loginId = $testLoginId
            email = "$testLoginId@springmsa.local"
            password = "CodexKafka-$stamp"
            username = "Kafka E2E"
            phoneNumber = $null
            whatsappNumber = $null
        } $csrf
        Write-Output "USER_REGISTER=$([int]$registration.StatusCode)"
        Write-Output "KAFKA_TEST_USER_LOGIN=$testLoginId"
        if (!$registration.IsSuccessStatusCode) {
            Write-Output "USER_REGISTER_BODY=$($registration.Content.ReadAsStringAsync().GetAwaiter().GetResult())"
            throw "User registration failed"
        }

        $webSocket = [Net.WebSockets.ClientWebSocket]::new()
        $webSocket.Options.Cookies = $handler.CookieContainer
        $webSocket.Options.SetRequestHeader("Origin", "http://localhost:5173")
        $webSocket.ConnectAsync(
            [Uri]"ws://localhost:8080/bff/chat/ws?roomId=codex-kafka-e2e",
            [Threading.CancellationToken]::None
        ).GetAwaiter().GetResult()
        $chatPayload = @{ type = "CHAT_MESSAGE"; content = "Codex Kafka $stamp" } |
            ConvertTo-Json -Compress
        $chatBytes = [Text.Encoding]::UTF8.GetBytes($chatPayload)
        $webSocket.SendAsync(
            [ArraySegment[byte]]::new($chatBytes),
            [Net.WebSockets.WebSocketMessageType]::Text,
            $true,
            [Threading.CancellationToken]::None
        ).GetAwaiter().GetResult()
        Write-Output "CHAT_WEBSOCKET_SEND=OK"
        Start-Sleep -Seconds 1
        $webSocket.Abort()
        $webSocket.Dispose()
        $webSocket = $null
    }

    $create = Send-Json "POST" ([Uri]::new($gateway, "/bff/community/posts")) @{
        title = "Codex live $stamp"
        content = "integration create"
    } $csrf
    Write-Output "COMMUNITY_CREATE=$([int]$create.StatusCode)"
    if (!$create.IsSuccessStatusCode) {
        throw "Community create failed"
    }
    $communityId = Find-EntityId (Read-ResponseJson $create).data
    if (!$communityId) {
        throw "Community ID missing"
    }

    $read = Send-Json "GET" ([Uri]::new($gateway, "/bff/community/posts"))
    Write-Output "COMMUNITY_READ=$([int]$read.StatusCode)"
    if (!$read.IsSuccessStatusCode) {
        throw "Community read failed"
    }

    $update = Send-Json "PUT" ([Uri]::new($gateway, "/bff/community/posts/$communityId")) @{
        title = "Codex live updated $stamp"
        content = "integration update"
    } $csrf
    Write-Output "COMMUNITY_UPDATE=$([int]$update.StatusCode)"
    if (!$update.IsSuccessStatusCode) {
        throw "Community update failed"
    }

    $delete = Send-Json "DELETE" ([Uri]::new($gateway, "/bff/community/posts/$communityId")) $null $csrf
    Write-Output "COMMUNITY_DELETE=$([int]$delete.StatusCode)"
    if (!$delete.IsSuccessStatusCode) {
        throw "Community delete failed"
    }
    $communityId = $null

    $existing = Send-Json "GET" ([Uri]::new($gateway, "/bff/stock/watch-items"))
    if (!$existing.IsSuccessStatusCode) {
        throw "Stock watch read failed"
    }
    $usedSymbols = @((Read-ResponseJson $existing).data | ForEach-Object { $_.symbol })
    $symbol = @("MSFT", "NVDA", "TSM", "AMZN", "GOOGL") |
        Where-Object { $_ -notin $usedSymbols } |
        Select-Object -First 1
    if (!$symbol) {
        throw "No free stock test symbol"
    }

    $stockCreate = Send-Json "POST" ([Uri]::new($gateway, "/bff/stock/watch-items")) @{
        symbol = $symbol
        memo = "Codex live $stamp"
    } $csrf
    Write-Output "STOCK_CREATE=$([int]$stockCreate.StatusCode)"
    if (!$stockCreate.IsSuccessStatusCode) {
        throw "Stock create failed"
    }
    $stockId = Find-EntityId (Read-ResponseJson $stockCreate).data
    if (!$stockId) {
        throw "Stock ID missing"
    }

    $stockUpdate = Send-Json "PUT" ([Uri]::new($gateway, "/bff/stock/watch-items/$stockId")) @{
        symbol = $symbol
        memo = "Codex updated $stamp"
    } $csrf
    Write-Output "STOCK_UPDATE=$([int]$stockUpdate.StatusCode)"
    if (!$stockUpdate.IsSuccessStatusCode) {
        throw "Stock update failed"
    }

    $stockDelete = Send-Json "DELETE" ([Uri]::new($gateway, "/bff/stock/watch-items/$stockId")) $null $csrf
    Write-Output "STOCK_DELETE=$([int]$stockDelete.StatusCode)"
    if (!$stockDelete.IsSuccessStatusCode) {
        throw "Stock delete failed"
    }
    $stockId = $null

    $marketUri = [Uri]::new($gateway, "/bff/stock/market/workspace?symbols=005930,AAPL")
    $market = Send-Json "GET" $marketUri
    $marketText = $market.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $tokenUnavailable = $marketText.Contains("TOSS_TOKEN_UNAVAILABLE")
    Write-Output "TOSS_MARKET_STATUS=$([int]$market.StatusCode)"
    Write-Output "TOSS_TOKEN_UNAVAILABLE_PRESENT=$tokenUnavailable"
    if (!$market.IsSuccessStatusCode -or $tokenUnavailable) {
        throw "Toss market integration failed"
    }

    if ($KafkaEnabled) {
        Start-Sleep -Seconds 8
    }

    $logout = Send-Json "POST" ([Uri]::new($gateway, "/bff/auth/logout")) $null $csrf
    Write-Output "MEMBER_LOGOUT=$([int]$logout.StatusCode)"
    if (!$logout.IsSuccessStatusCode) {
        throw "Member logout failed"
    }

    $afterLogout = Send-Json "GET" ([Uri]::new($gateway, "/bff/auth/me"))
    $afterLogoutBody = Read-ResponseJson $afterLogout
    Write-Output "MEMBER_AFTER_LOGOUT_AUTHENTICATED=$($afterLogoutBody.data.authenticated)"
    if ($afterLogoutBody.data.authenticated) {
        throw "Member logout did not clear BFF session"
    }
} finally {
    if ($webSocket) {
        $webSocket.Abort()
        $webSocket.Dispose()
    }
    if ($client -and $handler) {
        try {
            $cookie = $handler.CookieContainer.GetCookies([Uri]"http://localhost:8080")["MEMBER-XSRF-TOKEN"]
            $cleanupCsrf = if ($cookie) { [Uri]::UnescapeDataString($cookie.Value) } else { "" }
            if ($communityId) {
                $null = Send-Json "DELETE" ([Uri]"http://localhost:8080/bff/community/posts/$communityId") $null $cleanupCsrf
            }
            if ($stockId) {
                $null = Send-Json "DELETE" ([Uri]"http://localhost:8080/bff/stock/watch-items/$stockId") $null $cleanupCsrf
            }
        } catch {
            Write-Warning "Test data cleanup request failed"
        }
        $client.Dispose()
        $handler.Dispose()
    }

    foreach ($entry in $processes) {
        if (!$entry.Process.HasExited) {
            taskkill.exe /PID $entry.Process.Id /T /F 2>$null | Out-Null
        }
    }
}
