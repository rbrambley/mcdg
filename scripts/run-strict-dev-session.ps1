param(
    [long]$Seed = 123456789,
    [int]$AutoThrows = 25,
    [string]$AutoThrowPlayer = "",
    [switch]$ShutdownWhenDone,
    [switch]$SkipPresentation,
    [switch]$DebugStrictFlow
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Set-Or-AddProperty {
    param(
        [string]$Text,
        [string]$Key,
        [string]$Value
    )

    $pattern = "(?m)^$([regex]::Escape($Key))=.*$"
    if ([regex]::IsMatch($Text, $pattern)) {
        return [regex]::Replace($Text, $pattern, "$Key=$Value")
    }

    if ($Text.Length -gt 0 -and -not $Text.EndsWith("`n")) {
        $Text += "`r`n"
    }

    return "$Text$Key=$Value`r`n"
}

function Set-LocalDevServerProperties {
    param([string]$RootPath)

    $serverPropertiesPath = Join-Path $RootPath "run\server.properties"
    if (-not (Test-Path $serverPropertiesPath)) {
        return
    }

    $serverProperties = Get-Content $serverPropertiesPath -Raw
    $serverProperties = Set-Or-AddProperty -Text $serverProperties -Key 'online-mode' -Value 'false'
    $serverProperties = Set-Or-AddProperty -Text $serverProperties -Key 'enforce-secure-profile' -Value 'false'
    $serverProperties = Set-Or-AddProperty -Text $serverProperties -Key 'server-ip' -Value '127.0.0.1'
    Set-Content -Path $serverPropertiesPath -Value $serverProperties -Encoding ASCII

    Write-Host " - Dev auth mode: offline (online-mode=false, enforce-secure-profile=false)"
    Write-Host " - Dev bind: server-ip=127.0.0.1"
}

function Start-BackgroundCommand {
    param(
        [string]$WorkingDirectory,
        [string]$Command,
        [string]$StdOutPath,
        [string]$StdErrPath,
        [hashtable]$EnvironmentVariables = @{}
    )

    $envPrefixParts = @()
    foreach ($entry in $EnvironmentVariables.GetEnumerator()) {
        if ($null -eq $entry.Value) {
            $envPrefixParts += "set $($entry.Key)="
        } else {
            $envPrefixParts += "set $($entry.Key)=$($entry.Value)"
        }
    }

    $fullCommand = if ($envPrefixParts.Count -gt 0) {
        ($envPrefixParts -join ' && ') + ' && ' + $Command
    } else {
        $Command
    }

    return Start-Process cmd.exe -ArgumentList @('/d', '/c', $fullCommand) -WorkingDirectory $WorkingDirectory -RedirectStandardOutput $StdOutPath -RedirectStandardError $StdErrPath -PassThru -WindowStyle Hidden
}

function Wait-ForTcpPort {
    param(
        [string]$HostName = "127.0.0.1",
        [int]$Port = 25565,
        [int]$TimeoutSeconds = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $client = [System.Net.Sockets.TcpClient]::new()
            try {
                $async = $client.BeginConnect($HostName, $Port, $null, $null)
                if ($async.AsyncWaitHandle.WaitOne(500)) {
                    $client.EndConnect($async)
                    return
                }
            } finally {
                $client.Close()
            }
        } catch {
        }

        Start-Sleep -Milliseconds 500
    }

    throw "Timed out waiting for $HostName`:$Port"
}

function Wait-ForLogPattern {
    param(
        [string]$LogPath,
        [string]$Pattern,
        [int]$TimeoutSeconds = 180
    )

    if (-not (Test-Path $LogPath)) {
        throw "Log file not found: $LogPath"
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $logText = Get-Content $LogPath -Raw -ErrorAction SilentlyContinue
        if ($logText -match $Pattern) {
            return
        }

        Start-Sleep -Milliseconds 250
    }

    throw "Timed out waiting for '$Pattern' in $LogPath"
}

$serverEnv = @{
    MCDG_AUTO_STRICT_SETUP = "$Seed"
    MCDG_THROW_AUTOTEST_COUNT = "$AutoThrows"
    MCDG_THROW_AUTOTEST_PLAYER = $(if ([string]::IsNullOrWhiteSpace($AutoThrowPlayer)) { $null } else { $AutoThrowPlayer })
    MCDG_THROW_AUTOTEST_SHUTDOWN = $(if ($ShutdownWhenDone.IsPresent) { 'true' } else { 'false' })
    MCDG_SKIP_ROUND_PRESENTATION = $(if ($SkipPresentation.IsPresent) { 'true' } else { $null })
    MCDG_DEBUG_STRICT_FLOW = $(if ($DebugStrictFlow.IsPresent) { 'true' } else { $null })
}

Write-Host "Starting strict dev session."
Write-Host " - Server script: scripts/run-strict-dev-server.ps1"
Write-Host " - Client: gradle runClient"
Write-Host " - Seed: $Seed"
Write-Host " - Auto throws: $AutoThrows"
if (-not [string]::IsNullOrWhiteSpace($AutoThrowPlayer)) {
    Write-Host " - Auto throw player: $AutoThrowPlayer"
} else {
    Write-Host " - Auto throw player: first online player"
}
Write-Host " - Shutdown when done: $($ShutdownWhenDone.IsPresent)"
Write-Host " - Skip presentation: $($SkipPresentation.IsPresent)"
Write-Host " - Strict-flow debug: $($DebugStrictFlow.IsPresent)"
Write-Host ""
Write-Host "Waiting for the server to open 127.0.0.1:25565 before starting the client..."

$serverStdOut = Join-Path $repoRoot "run\logs\strict-dev-session-server.out.log"
$serverStdErr = Join-Path $repoRoot "run\logs\strict-dev-session-server.err.log"
Remove-Item $serverStdOut, $serverStdErr -ErrorAction SilentlyContinue

Set-LocalDevServerProperties -RootPath $repoRoot
$serverProcess = Start-BackgroundCommand -WorkingDirectory $repoRoot -Command 'gradle runServer' -StdOutPath $serverStdOut -StdErrPath $serverStdErr -EnvironmentVariables $serverEnv
Start-Sleep -Seconds 2

if ($serverProcess.HasExited) {
    $exitCode = $serverProcess.ExitCode
    $stdoutTail = if (Test-Path $serverStdOut) { (Get-Content $serverStdOut -Tail 60 -ErrorAction SilentlyContinue) -join "`n" } else { "" }
    $stderrTail = if (Test-Path $serverStdErr) { (Get-Content $serverStdErr -Tail 60 -ErrorAction SilentlyContinue) -join "`n" } else { "" }
    throw "Strict dev server process exited early (exit code $exitCode).`n--- STDOUT ---`n$stdoutTail`n--- STDERR ---`n$stderrTail"
}

Wait-ForTcpPort -HostName "127.0.0.1" -Port 25565
Wait-ForLogPattern -LogPath $serverStdOut -Pattern 'Done \(' -TimeoutSeconds 240

$clientStdOut = Join-Path $repoRoot 'run\logs\strict-dev-session-client.out.log'
$clientStdErr = Join-Path $repoRoot 'run\logs\strict-dev-session-client.err.log'
Remove-Item $clientStdOut, $clientStdErr -ErrorAction SilentlyContinue

Write-Host "Launching client with auto-connect (127.0.0.1:25565)..."
$clientProcess = Start-BackgroundCommand -WorkingDirectory $repoRoot -Command 'gradle runClient' -StdOutPath $clientStdOut -StdErrPath $clientStdErr -EnvironmentVariables @{ MCDG_AUTOCONNECT_SERVER = '127.0.0.1:25565' }
Start-Sleep -Seconds 2

if ($clientProcess.HasExited) {
    $exitCode = $clientProcess.ExitCode
    $stdoutTail = if (Test-Path $clientStdOut) { (Get-Content $clientStdOut -Tail 80 -ErrorAction SilentlyContinue) -join "`n" } else { "" }
    $stderrTail = if (Test-Path $clientStdErr) { (Get-Content $clientStdErr -Tail 80 -ErrorAction SilentlyContinue) -join "`n" } else { "" }
    throw "Strict dev client exited early (exit code $exitCode).`n--- CLIENT STDOUT ---`n$stdoutTail`n--- CLIENT STDERR ---`n$stderrTail"
}

Write-Host "Client process started (pid=$($clientProcess.Id)). Waiting for auto strict setup..."

Wait-ForLogPattern -LogPath $serverStdOut -Pattern 'Auto strict setup complete:' -TimeoutSeconds 240

Write-Host "Server is fully ready and the client has been launched to auto-connect."