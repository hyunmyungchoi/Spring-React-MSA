param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")),
    [string]$EnvironmentFile = (Join-Path $Root "infra\vm\.env.local")
)

$ErrorActionPreference = "Stop"

Get-Content -LiteralPath $EnvironmentFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and !$line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line -split "=", 2
        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim().Trim('"'), "Process")
    }
}

$env:JAVA_HOME = Join-Path $Root "JDK"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:SPRING_PROFILES_ACTIVE = "local"
$env:APP_KAFKA_ENABLED = "true"
$env:SERVER_PORT = "8079"

$probe = [Net.Sockets.TcpClient]::new()
try {
    $probe.Connect("127.0.0.1", 8079)
    throw "Port 8079 is already occupied"
} catch [Net.Sockets.SocketException] {
    # Expected: this script starts the member BFF below.
} finally {
    $probe.Dispose()
}

$logDirectory = Join-Path $env:TEMP "springmsa-codex-dlt"
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$stdout = Join-Path $logDirectory "member-bff.out.log"
$stderr = Join-Path $logDirectory "member-bff.err.log"
$process = $null

try {
    $process = Start-Process `
        -FilePath (Join-Path $Root "BackEnd\spring-member-bff-service\gradlew.bat") `
        -ArgumentList "bootRun", "--no-daemon" `
        -WorkingDirectory (Join-Path $Root "BackEnd\spring-member-bff-service") `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru

    $deadline = (Get-Date).AddMinutes(4)
    do {
        if ($process.HasExited) {
            throw "Member BFF exited with $($process.ExitCode)"
        }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:8079/actuator/health" -TimeoutSec 2
            if ($health.status -eq "UP") {
                break
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    if ((Get-Date) -ge $deadline) {
        throw "Member BFF startup timeout"
    }

    $eventId = [guid]::NewGuid().ToString()
    $env:KAFKA_DLT_TEST_EVENT_ID = $eventId
    $python = "C:\Users\chyun\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
    $env:PYTHONPATH = "$env:TEMP\springmsa-e2e-py"

    $dltReceived = @'
import json
import os
import time
from datetime import datetime, timezone
from kafka import KafkaConsumer, KafkaProducer

bootstrap = os.environ['SPRING_KAFKA_BOOTSTRAP_SERVERS']
event_id = os.environ['KAFKA_DLT_TEST_EVENT_ID']
source_topic = 'springmsa.community.post-created.v1'
dlt_topic = source_topic + '.DLT'

consumer = KafkaConsumer(
    dlt_topic,
    bootstrap_servers=bootstrap,
    group_id='springmsa-dlt-smoke-' + event_id,
    auto_offset_reset='latest',
    enable_auto_commit=False,
)
consumer.poll(timeout_ms=1000)

payload = {
    'eventId': event_id,
    'eventType': 'community.post-created',
    'eventVersion': 1,
    'producer': 'springmsa-kafka-dlt-smoke',
    'occurredAt': datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
    'payload': {'postId': 999999, 'author': 'codex', 'title': 'intentional failure'}
}
producer = KafkaProducer(bootstrap_servers=bootstrap)
producer.send(source_topic, key=event_id.encode(), value=json.dumps(payload).encode()).get(timeout=10)
producer.flush()

found = False
deadline = time.time() + 25
while time.time() < deadline and not found:
    for records in consumer.poll(timeout_ms=1000).values():
        for record in records:
            if event_id.encode() in record.value:
                found = True
                break
producer.close()
consumer.close()
print(str(found))
'@ | & $python -

    if ($dltReceived.Trim() -ne "True") {
        throw "Test event was not observed in the DLT"
    }

    Start-Sleep -Seconds 2
    $retryLines = Select-String -LiteralPath $stdout -Pattern "key=$eventId, deliveryAttempt="
    $attempts = @($retryLines | ForEach-Object {
        if ($_.Line -match 'deliveryAttempt=(\d+)') { [int]$Matches[1] }
    } | Sort-Object -Unique)
    $dltLogged = [bool](Select-String -LiteralPath $stdout -Pattern "Domain event moved to DLT.*$eventId")

    Write-Output "DLT_EVENT_ID=$eventId"
    Write-Output "RETRY_DELIVERY_ATTEMPTS=$($attempts -join ',')"
    Write-Output "DLT_TOPIC_RECEIVED=$dltReceived"
    Write-Output "DLT_MONITOR_LOGGED=$dltLogged"

    if (($attempts -join ',') -ne '1,2,3,4') {
        throw "Expected delivery attempts 1,2,3,4"
    }
    if (!$dltLogged) {
        throw "Member BFF DLT consumer log was not observed"
    }
} finally {
    if ($process -and !$process.HasExited) {
        taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
    }
}
