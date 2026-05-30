param(
    [long]$Seed = 123456789,
    [switch]$SkipPresentation,
    [switch]$NoDebugStrictFlow,
    [switch]$NoDebugHudScoring,
    [switch]$NoAutoConnectClient
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$sessionDir = Join-Path $repoRoot "run\logs\strict-manual-debug-$timestamp"
New-Item -ItemType Directory -Path $sessionDir -Force | Out-Null

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
        [int]$TimeoutSeconds = 240
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $LogPath) {
            $logText = Get-Content $LogPath -Raw -ErrorAction SilentlyContinue
            if ($logText -match $Pattern) {
                return
            }
        }

        Start-Sleep -Milliseconds 250
    }

    throw "Timed out waiting for '$Pattern' in $LogPath"
}

$serverStdOut = Join-Path $sessionDir "server.out.log"
$serverStdErr = Join-Path $sessionDir "server.err.log"
$clientStdOut = Join-Path $sessionDir "client.out.log"
$clientStdErr = Join-Path $sessionDir "client.err.log"

$serverEnv = @{
    MCDG_SKIP_ROUND_PRESENTATION = $(if ($SkipPresentation.IsPresent) { 'true' } else { $null })
    MCDG_DEBUG_STRICT_FLOW = $(if ($NoDebugStrictFlow.IsPresent) { $null } else { 'true' })
    MCDG_DEBUG_HUD_SCORING = $(if ($NoDebugHudScoring.IsPresent) { $null } else { 'true' })
    MCDG_THROW_AUTOTEST_COUNT = $null
    MCDG_THROW_AUTOTEST_PLAYER = $null
    MCDG_THROW_AUTOTEST_SHUTDOWN = $null
    MCDG_AUTO_STRICT_SETUP = $null
}

Write-Host "Starting strict manual debug session."
Write-Host " - Seed: $Seed"
Write-Host " - Skip presentation: $($SkipPresentation.IsPresent)"
Write-Host " - Strict-flow debug: $($NoDebugStrictFlow.IsPresent -eq $false)"
Write-Host " - HUD scoring debug: $($NoDebugHudScoring.IsPresent -eq $false)"
Write-Host " - Auto-connect client: $($NoAutoConnectClient.IsPresent -eq $false)"
Write-Host " - Session logs: $sessionDir"
Write-Host ""
Write-Host "Important: this workflow targets the dedicated server started by this script."
Write-Host "Do not restart into a separate single-player world or switch to an Open-to-LAN flow if you want /mcdg command behavior and logs to match this debug session."
Write-Host ""
Write-Host "Bringing server online and waiting for world/server readiness..."

Set-LocalDevServerProperties -RootPath $repoRoot
$serverProcess = Start-BackgroundCommand -WorkingDirectory $repoRoot -Command 'gradle runServer' -StdOutPath $serverStdOut -StdErrPath $serverStdErr -EnvironmentVariables $serverEnv
Start-Sleep -Seconds 2
if ($serverProcess.HasExited) {
    $exitCode = $serverProcess.ExitCode
    $stdoutTail = if (Test-Path $serverStdOut) { (Get-Content $serverStdOut -Tail 80 -ErrorAction SilentlyContinue) -join "`n" } else { "" }
    $stderrTail = if (Test-Path $serverStdErr) { (Get-Content $serverStdErr -Tail 80 -ErrorAction SilentlyContinue) -join "`n" } else { "" }
    throw "Manual debug server exited early (exit code $exitCode).`n--- STDOUT ---`n$stdoutTail`n--- STDERR ---`n$stderrTail"
}

Wait-ForTcpPort -HostName "127.0.0.1" -Port 25565
Wait-ForLogPattern -LogPath $serverStdOut -Pattern 'Done \('
Wait-ForLogPattern -LogPath $serverStdOut -Pattern 'For help, type "help"'

if (-not $NoAutoConnectClient.IsPresent) {
    $clientEnv = @{ MCDG_AUTOCONNECT_SERVER = '127.0.0.1:25565' }
    Write-Host "Launching client into the dedicated-server debug path at 127.0.0.1:25565..."
    [void](Start-BackgroundCommand -WorkingDirectory $repoRoot -Command 'gradle runClient' -StdOutPath $clientStdOut -StdErrPath $clientStdErr -EnvironmentVariables $clientEnv)

    Write-Host "Client launched with auto-connect to 127.0.0.1:25565."
} else {
    Write-Host "Client auto-connect skipped; start your client manually when ready."
}

Write-Host ""
Write-Host "Manual strict gameplay debug is ready."
Write-Host "Recommended manual flow:"
Write-Host "  1) In game, run: /mcdg ruleset strict"
Write-Host "  2) In game, run: /mcdg createcourse $Seed"
Write-Host "  3) In game, run: /mcdg startround"
Write-Host "  4) Reproduce the throw issue manually (second throw after strict penalty teleport)."
Write-Host "  5) Then run: /mcdg autotestthrows 25"
Write-Host "  6) Optional one-command baseline: /mcdg quickthrowtest $Seed 25"
Write-Host "  7) When finished, archive these logs for review:"
Write-Host "     - $serverStdOut"
Write-Host "     - $serverStdErr"
Write-Host "     - $clientStdOut"
Write-Host "     - $clientStdErr"
Write-Host "     - run/logs/latest.log"
Write-Host "     - run/logs/debug.log"
Write-Host "     - run/logs/mcdg-throw-autotest-latest.txt"
Write-Host ""
Write-Host "Note: server/client are left running for manual play."
