param(
    [int]$Runs = 10,
    [int]$Throws = 25,
    [long]$Seed = 123456789,
    [int]$SeedStep = 97,
    [int]$ServerReadyTimeoutSeconds = 240,
    [int]$ReportTimeoutSeconds = 420,
    [int]$ServerShutdownTimeoutSeconds = 180,
    [string]$AutoThrowPlayer = "",
    [switch]$AutoLaunchClient,
    [switch]$SkipPresentation,
    [switch]$NoStrictFlowDebug,
    [int]$MaxSuspectUnchangedLieEvents = 0
)

$ErrorActionPreference = "Stop"

if ($Runs -lt 1) {
    throw "Runs must be >= 1"
}
if ($Throws -lt 1) {
    throw "Throws must be >= 1"
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$latestReportPath = Join-Path $repoRoot "run\logs\mcdg-throw-autotest-latest.txt"
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$sessionRoot = Join-Path $repoRoot "run\logs\strict-stability-gate-$timestamp"
New-Item -ItemType Directory -Path $sessionRoot -Force | Out-Null

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
        [int]$TimeoutSeconds = 180,
        [System.Diagnostics.Process]$Process = $null
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($null -ne $Process -and $Process.HasExited) {
            throw "Server process exited while waiting for TCP port."
        }

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
        [int]$TimeoutSeconds = 180,
        [System.Diagnostics.Process]$Process = $null
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($null -ne $Process -and $Process.HasExited) {
            throw "Server process exited while waiting for log pattern '$Pattern'."
        }

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

function Wait-ForReportUpdate {
    param(
        [string]$ReportPath,
        [datetime]$NotBeforeUtc,
        [datetime]$RunStartUtc,
        [int]$ExpectedThrows,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).ToUniversalTime().AddSeconds($TimeoutSeconds)
    while ((Get-Date).ToUniversalTime() -lt $deadline) {
        $candidateReports = @()

        if (Test-Path $ReportPath) {
            $candidateReports += Get-Item $ReportPath
        }

        $reportDirectory = Split-Path -Path $ReportPath -Parent
        if (Test-Path $reportDirectory) {
            $candidateReports += Get-ChildItem -Path $reportDirectory -File -Filter 'mcdg-throw-autotest-*.txt' -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -ne 'mcdg-throw-autotest-latest.txt' }
        }

        $selected = $null
        foreach ($candidate in ($candidateReports | Sort-Object LastWriteTimeUtc -Descending)) {
            $lastWriteUtc = $candidate.LastWriteTimeUtc
            if ($lastWriteUtc -lt $RunStartUtc -or $lastWriteUtc -le $NotBeforeUtc) {
                continue
            }

            $raw = Get-Content $candidate.FullName -Raw -ErrorAction SilentlyContinue
            if (-not ($raw -match '(?m)^Status:\s*.+$' -and $raw -match '(?m)^Target throws:\s*(\d+)$' -and $raw -match '(?m)^Launched throws:\s*\d+$')) {
                continue
            }

            $targetThrows = [int]$Matches[1]
            if ($targetThrows -ne $ExpectedThrows) {
                continue
            }

            $selected = $candidate.FullName
            break
        }

        if ($null -ne $selected) {
            return $selected
        }

        Start-Sleep -Milliseconds 500
    }

    throw "Timed out waiting for throw report update at $ReportPath"
}

function Parse-ThrowReport {
    param(
        [string]$ReportPath,
        [int]$ExpectedThrows
    )

    if (-not (Test-Path $ReportPath)) {
        throw "Report file not found: $ReportPath"
    }

    $raw = Get-Content $ReportPath -Raw

    $statusMatch = [regex]::Match($raw, '(?m)^Status:\s*(.+)$')
    $expectedCompletionMatch = [regex]::Match($raw, '(?m)^Expected completion:\s*(.+)$')
    $targetMatch = [regex]::Match($raw, '(?m)^Target throws:\s*(\d+)$')
    $launchedMatch = [regex]::Match($raw, '(?m)^Launched throws:\s*(\d+)$')
    $resolvedMatch = [regex]::Match($raw, '(?m)^Resolved throws:\s*(\d+)$')
    $resolvedUnchangedMatch = [regex]::Match($raw, '(?m)^Resolved unchanged-lie throws:\s*(\d+)$')
    $suspectMatch = [regex]::Match($raw, '(?m)^Suspect unchanged-lie events:\s*(\d+)$')

    if (-not $statusMatch.Success -or -not $targetMatch.Success -or -not $launchedMatch.Success) {
        throw "Report is missing required fields."
    }

    $status = $statusMatch.Groups[1].Value.Trim()
    $expectedCompletion = if ($expectedCompletionMatch.Success) { $expectedCompletionMatch.Groups[1].Value.Trim().ToLowerInvariant() } else { "" }
    $targetThrows = [int]$targetMatch.Groups[1].Value
    $launchedThrows = [int]$launchedMatch.Groups[1].Value
    $resolvedThrows = if ($resolvedMatch.Success) { [int]$resolvedMatch.Groups[1].Value } else { -1 }
    $resolvedUnchangedThrows = if ($resolvedUnchangedMatch.Success) { [int]$resolvedUnchangedMatch.Groups[1].Value } else { -1 }
    $suspectEvents = if ($suspectMatch.Success) { [int]$suspectMatch.Groups[1].Value } else { 0 }

    $reportOk = $status -eq 'Throw autotest complete.' -and
        $expectedCompletion -eq 'true' -and
        $targetThrows -eq $ExpectedThrows -and
        $launchedThrows -eq $ExpectedThrows -and
        ($resolvedThrows -lt 0 -or $resolvedThrows -eq $ExpectedThrows)

    return [pscustomobject]@{
        Raw = $raw
        Status = $status
        ExpectedCompletion = $expectedCompletion
        TargetThrows = $targetThrows
        LaunchedThrows = $launchedThrows
        ResolvedThrows = $resolvedThrows
        ResolvedUnchangedLieThrows = $resolvedUnchangedThrows
        SuspectUnchangedLieEvents = $suspectEvents
        PassedCoreChecks = $reportOk
    }
}

function Parse-ServerStrictMetrics {
    param(
        [string]$ServerLogText
    )

    $launchCount = [regex]::Matches($ServerLogText, 'Throw autotest launch \|').Count
    $resolvedCount = [regex]::Matches($ServerLogText, 'Strict landing resolved \|').Count
    $classifiedCount = [regex]::Matches($ServerLogText, 'Strict landing classified \|').Count

    return [pscustomobject]@{
        LaunchCount = $launchCount
        ResolvedCount = $resolvedCount
        ClassifiedCount = $classifiedCount
    }
}

function Stop-ProcessIfRunning {
    param([System.Diagnostics.Process]$Process)

    if ($null -eq $Process) {
        return
    }

    try {
        if (-not $Process.HasExited) {
            Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        }
    } catch {
    }
}

Set-LocalDevServerProperties -RootPath $repoRoot

$runResults = New-Object System.Collections.Generic.List[object]

Write-Host "Strict stability gate started."
Write-Host " - Runs: $Runs"
Write-Host " - Throws per run: $Throws"
Write-Host " - Base seed: $Seed"
Write-Host " - Seed step: $SeedStep"
Write-Host " - Auto-launch client each run: $($AutoLaunchClient.IsPresent)"
Write-Host " - Session logs: $sessionRoot"
Write-Host ""

for ($runIndex = 1; $runIndex -le $Runs; $runIndex++) {
    $runSeed = $Seed + (($runIndex - 1) * $SeedStep)
    $runDir = Join-Path $sessionRoot ("run-" + $runIndex.ToString("00"))
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null

    $serverStdOut = Join-Path $runDir "server.out.log"
    $serverStdErr = Join-Path $runDir "server.err.log"
    $clientStdOut = Join-Path $runDir "client.out.log"
    $clientStdErr = Join-Path $runDir "client.err.log"
    $reportCopy = Join-Path $runDir "throw-report.txt"

    $reportNotBeforeUtc = if (Test-Path $latestReportPath) {
        (Get-Item $latestReportPath).LastWriteTimeUtc
    } else {
        [DateTime]::MinValue
    }

    $strictFlowDebug = -not $NoStrictFlowDebug.IsPresent
    $serverEnv = @{
        MCDG_AUTO_STRICT_SETUP = "$runSeed"
        MCDG_THROW_AUTOTEST_COUNT = "$Throws"
        MCDG_THROW_AUTOTEST_PLAYER = $(if ([string]::IsNullOrWhiteSpace($AutoThrowPlayer)) { $null } else { $AutoThrowPlayer })
        MCDG_THROW_AUTOTEST_SHUTDOWN = 'true'
        MCDG_SKIP_ROUND_PRESENTATION = $(if ($SkipPresentation.IsPresent) { 'true' } else { $null })
        MCDG_DEBUG_STRICT_FLOW = $(if ($strictFlowDebug) { 'true' } else { $null })
    }

    $serverProcess = $null
    $clientProcess = $null
    $runFailed = $false
    $runFailureReason = ""
    $report = $null
    $strictMetrics = $null

    Write-Host "[Run $runIndex/$Runs] Starting strict session (seed=$runSeed)..."

    try {
        $runStartUtc = (Get-Date).ToUniversalTime()
        $serverProcess = Start-BackgroundCommand -WorkingDirectory $repoRoot -Command 'gradle --no-daemon runServer' -StdOutPath $serverStdOut -StdErrPath $serverStdErr -EnvironmentVariables $serverEnv

        Wait-ForTcpPort -HostName "127.0.0.1" -Port 25565 -TimeoutSeconds $ServerReadyTimeoutSeconds -Process $serverProcess
        Wait-ForLogPattern -LogPath $serverStdOut -Pattern 'Done \(' -TimeoutSeconds $ServerReadyTimeoutSeconds -Process $serverProcess

        if ($AutoLaunchClient.IsPresent) {
            $clientProcess = Start-BackgroundCommand -WorkingDirectory $repoRoot -Command 'gradle --no-daemon runClient' -StdOutPath $clientStdOut -StdErrPath $clientStdErr -EnvironmentVariables @{ MCDG_AUTOCONNECT_SERVER = '127.0.0.1:25565' }
        } else {
            Write-Host "[Run $runIndex/$Runs] Waiting for a manually connected player (client auto-launch disabled)."
        }

        Wait-ForLogPattern -LogPath $serverStdOut -Pattern 'Auto strict setup complete:' -TimeoutSeconds $ReportTimeoutSeconds -Process $serverProcess

        $selectedReportPath = Wait-ForReportUpdate -ReportPath $latestReportPath -NotBeforeUtc $reportNotBeforeUtc -RunStartUtc $runStartUtc -ExpectedThrows $Throws -TimeoutSeconds $ReportTimeoutSeconds
        Copy-Item -Path $selectedReportPath -Destination $reportCopy -Force
        $report = Parse-ThrowReport -ReportPath $reportCopy -ExpectedThrows $Throws

        if ($serverProcess -ne $null -and -not $serverProcess.WaitForExit($ServerShutdownTimeoutSeconds * 1000)) {
            Stop-ProcessIfRunning -Process $serverProcess
        }

        if (-not $runFailed -and -not $report.PassedCoreChecks) {
            $runFailed = $true
            $runFailureReason = "Throw report core checks failed."
        }

        $serverLogText = if (Test-Path $serverStdOut) { Get-Content $serverStdOut -Raw -ErrorAction SilentlyContinue } else { "" }
        $strictMetrics = Parse-ServerStrictMetrics -ServerLogText $serverLogText

        if (-not $runFailed -and $strictMetrics.LaunchCount -ne $Throws) {
            $runFailed = $true
            $runFailureReason = "Strict launch count mismatch ($($strictMetrics.LaunchCount) vs expected $Throws)."
        }

        if (-not $runFailed -and $strictMetrics.ResolvedCount -ne $Throws) {
            $runFailed = $true
            $runFailureReason = "Strict resolved count mismatch ($($strictMetrics.ResolvedCount) vs expected $Throws)."
        }

        if (-not $runFailed -and $report.SuspectUnchangedLieEvents -gt $MaxSuspectUnchangedLieEvents) {
            $runFailed = $true
            $runFailureReason = "Suspect unchanged-lie events exceeded threshold ($($report.SuspectUnchangedLieEvents) > $MaxSuspectUnchangedLieEvents)."
        }

        $forbiddenPatterns = @(
            'Strict throw gate blocked',
            'Forcing throw landing resolution after',
            'Throw autotest did not start',
            'Throw autotest canceled',
            'stuck lie',
            'Timed out waiting for the throw test'
        )

        $forbiddenHits = @()
        foreach ($pattern in $forbiddenPatterns) {
            if ($serverLogText -match $pattern) {
                $forbiddenHits += $pattern
            }
        }

        if (-not $runFailed -and $forbiddenHits.Count -gt 0) {
            $runFailed = $true
            $runFailureReason = "Detected forbidden strict-flow patterns: $($forbiddenHits -join ', ')"
        }
    } catch {
        $runFailed = $true
        $runFailureReason = $_.Exception.Message
    } finally {
        Stop-ProcessIfRunning -Process $clientProcess
        Stop-ProcessIfRunning -Process $serverProcess
    }

    $result = [pscustomobject]@{
        Run = $runIndex
        Seed = $runSeed
        Passed = -not $runFailed
        Reason = $(if ($runFailed) { $runFailureReason } else { "PASS" })
        Status = $(if ($report -ne $null) { $report.Status } else { "" })
        ExpectedCompletion = $(if ($report -ne $null) { $report.ExpectedCompletion } else { "" })
        TargetThrows = $(if ($report -ne $null) { $report.TargetThrows } else { 0 })
        LaunchedThrows = $(if ($report -ne $null) { $report.LaunchedThrows } else { 0 })
        ResolvedThrows = $(if ($report -ne $null) { $report.ResolvedThrows } else { -1 })
        ResolvedUnchangedLieThrows = $(if ($report -ne $null) { $report.ResolvedUnchangedLieThrows } else { -1 })
        StrictLaunchCount = $(if ($strictMetrics -ne $null) { $strictMetrics.LaunchCount } else { 0 })
        StrictResolvedCount = $(if ($strictMetrics -ne $null) { $strictMetrics.ResolvedCount } else { 0 })
        StrictClassifiedCount = $(if ($strictMetrics -ne $null) { $strictMetrics.ClassifiedCount } else { 0 })
        SuspectUnchangedLieEvents = $(if ($report -ne $null) { $report.SuspectUnchangedLieEvents } else { -1 })
        ServerOutLog = $serverStdOut
        ServerErrLog = $serverStdErr
        ClientOutLog = $clientStdOut
        ClientErrLog = $clientStdErr
        ReportPath = $reportCopy
    }

    $runResults.Add($result) | Out-Null

    if ($result.Passed) {
        Write-Host "[Run $runIndex/$Runs] PASS"
    } else {
        Write-Warning "[Run $runIndex/$Runs] FAIL - $($result.Reason)"
    }
}

$summaryPath = Join-Path $sessionRoot "summary.json"
$runResults | ConvertTo-Json -Depth 5 | Set-Content -Path $summaryPath -Encoding ASCII

$passCount = ($runResults | Where-Object { $_.Passed }).Count
$failCount = $Runs - $passCount

Write-Host ""
Write-Host "Strict stability gate complete."
Write-Host " - Passed: $passCount/$Runs"
Write-Host " - Failed: $failCount/$Runs"
Write-Host " - Summary: $summaryPath"

$runResults | Select-Object Run, Seed, Passed, Reason, LaunchedThrows, ResolvedThrows, StrictLaunchCount, StrictResolvedCount, TargetThrows, SuspectUnchangedLieEvents | Format-Table -AutoSize

if ($failCount -gt 0) {
    throw "Strict stability gate FAILED ($failCount of $Runs runs)."
}

Write-Host "Strict stability gate PASSED."
