param(
    [switch]$FromATLauncher,
    [string]$LogsRoot = "",
    [string]$OutputDir = "",
    [int]$MaxLinesPerFile = 120000,
    [int]$MaxEventDetails = 300
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Resolve-LogsRoot {
    param(
        [bool]$UseAtLauncher,
        [string]$RequestedLogsRoot
    )

    if (-not [string]::IsNullOrWhiteSpace($RequestedLogsRoot)) {
        if (-not (Test-Path -LiteralPath $RequestedLogsRoot -PathType Container)) {
            throw "LogsRoot does not exist: $RequestedLogsRoot"
        }
        return (Resolve-Path $RequestedLogsRoot).Path
    }

    if ($UseAtLauncher) {
        $modsDir = $env:ATLAUNCHER_TEST_MODS_DIR
        if ([string]::IsNullOrWhiteSpace($modsDir)) {
            throw "ATLAUNCHER_TEST_MODS_DIR is not set. Set it or pass -LogsRoot."
        }
        if (-not (Test-Path -LiteralPath $modsDir -PathType Container)) {
            throw "ATLAUNCHER_TEST_MODS_DIR does not exist: $modsDir"
        }

        $instanceRoot = Split-Path -Parent $modsDir
        $atLauncherLogs = Join-Path $instanceRoot "logs"
        if (-not (Test-Path -LiteralPath $atLauncherLogs -PathType Container)) {
            throw "ATLauncher logs folder not found: $atLauncherLogs"
        }
        return (Resolve-Path $atLauncherLogs).Path
    }

    $defaultLogs = Join-Path $repoRoot "run\logs"
    if (-not (Test-Path -LiteralPath $defaultLogs -PathType Container)) {
        throw "Default logs folder not found: $defaultLogs"
    }
    return (Resolve-Path $defaultLogs).Path
}

function New-OutputRoot {
    param(
        [string]$RequestedOutputDir,
        [string]$BaseLogsRoot
    )

    if (-not [string]::IsNullOrWhiteSpace($RequestedOutputDir)) {
        New-Item -ItemType Directory -Path $RequestedOutputDir -Force | Out-Null
        return (Resolve-Path $RequestedOutputDir).Path
    }

    $timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
    $defaultOut = Join-Path $BaseLogsRoot "strict-analysis-$timestamp"
    New-Item -ItemType Directory -Path $defaultOut -Force | Out-Null
    return (Resolve-Path $defaultOut).Path
}

function Read-MatchingEvents {
    param(
        [string]$FilePath,
        [string[]]$Patterns,
        [int]$TailLines
    )

    $results = @()
    if (-not (Test-Path -LiteralPath $FilePath)) {
        return $results
    }

    $lineNumber = 0
    $content = if ($TailLines -gt 0) {
        Get-Content -LiteralPath $FilePath -Tail $TailLines
    } else {
        Get-Content -LiteralPath $FilePath
    }

    $content | ForEach-Object {
        $lineNumber++
        $line = $_
        foreach ($pattern in $Patterns) {
            if ($line -match $pattern) {
                $results += [pscustomobject]@{
                    file = $FilePath
                    line = $lineNumber
                    pattern = $pattern
                    text = $line
                }
                break
            }
        }
    }

    return $results
}

function Count-Events {
    param([object[]]$Events)

    $counts = [ordered]@{
        strictPending = 0
        strictBlocked = 0
        strictResolved = 0
        strictLandingWait = 0
        strictLandingClassified = 0
        forcedLandingResolution = 0
    }

    foreach ($event in $Events) {
        $text = $event.text
        if ($text -match 'Strict throw gate pending resolution') { $counts.strictPending++ }
        if ($text -match 'Strict throw gate blocked') { $counts.strictBlocked++ }
        if ($text -match 'Strict landing resolved') { $counts.strictResolved++ }
        if ($text -match 'Strict landing wait') { $counts.strictLandingWait++ }
        if ($text -match 'Strict landing classified') { $counts.strictLandingClassified++ }
        if ($text -match 'Forcing throw landing resolution after') { $counts.forcedLandingResolution++ }
    }

    return $counts
}

function Extract-Snapshot {
    param([string]$Line)

    if ($Line -notmatch 'snapshot=(.+)$') {
        return $null
    }

    $snapshotText = $Matches[1]
    $pairs = @{}
    foreach ($token in ($snapshotText -split '\s+')) {
        if ($token -match '=') {
            $parts = $token -split '=', 2
            $pairs[$parts[0]] = $parts[1]
        }
    }

    return [pscustomobject]@{
        raw = $snapshotText
        fields = $pairs
    }
}

$resolvedLogsRoot = Resolve-LogsRoot -UseAtLauncher:$FromATLauncher.IsPresent -RequestedLogsRoot $LogsRoot
$outputRoot = New-OutputRoot -RequestedOutputDir $OutputDir -BaseLogsRoot $resolvedLogsRoot

$latestLog = Join-Path $resolvedLogsRoot "latest.log"
$debugLog = Join-Path $resolvedLogsRoot "debug.log"

$targetFiles = @($latestLog, $debugLog) | Where-Object { Test-Path -LiteralPath $_ }
if ($targetFiles.Count -eq 0) {
    throw "No readable log files found under $resolvedLogsRoot (expected latest.log and/or debug.log)."
}

$strictPatterns = @(
    'Strict throw gate pending resolution',
    'Strict throw gate blocked',
    'Strict landing wait',
    'Strict landing classified',
    'Strict landing resolved',
    'Forcing throw landing resolution after'
)

$events = @()
foreach ($target in $targetFiles) {
    $events += Read-MatchingEvents -FilePath $target -Patterns $strictPatterns -TailLines $MaxLinesPerFile
}

$counts = Count-Events -Events $events
$blockedEvents = $events | Where-Object { $_.text -match 'Strict throw gate blocked|Strict throw gate pending resolution' }
$blockedWithSnapshot = @()
foreach ($entry in $blockedEvents) {
    $snapshot = Extract-Snapshot -Line $entry.text
    $blockedWithSnapshot += [pscustomobject]@{
        file = $entry.file
        line = $entry.line
        text = $entry.text
        snapshot = $snapshot
    }
}

$recentEvents = $events | Sort-Object file, line | Select-Object -Last $MaxEventDetails
$recentBlockedWithSnapshot = $blockedWithSnapshot | Select-Object -Last $MaxEventDetails

$assessment = "NO_STRICT_BLOCK_DETECTED"
if ($counts.strictBlocked -gt 0) {
    $assessment = "STRICT_BLOCK_DETECTED"
} elseif ($counts.strictPending -gt 0) {
    $assessment = "STRICT_PENDING_DETECTED"
}

$summary = [pscustomobject]@{
    createdAt = (Get-Date).ToString("o")
    sourceLogsRoot = $resolvedLogsRoot
    outputRoot = $outputRoot
    filesScanned = $targetFiles
    maxLinesPerFile = $MaxLinesPerFile
    maxEventDetails = $MaxEventDetails
    scannedEventCount = $events.Count
    assessment = $assessment
    eventCounts = $counts
    blockedOrPendingEvents = $recentBlockedWithSnapshot
}

$jsonPath = Join-Path $outputRoot "summary.json"
$timelinePath = Join-Path $outputRoot "strict-events.log"
$reportPath = Join-Path $outputRoot "report.txt"

$summary | ConvertTo-Json -Depth 8 | Set-Content -Path $jsonPath -Encoding ASCII

$recentEvents |
    Sort-Object file, line |
    ForEach-Object { "[{0}:{1}] {2}" -f (Split-Path -Leaf $_.file), $_.line, $_.text } |
    Set-Content -Path $timelinePath -Encoding ASCII

$reportLines = @(
    "MCDG Strict Throw Session Analysis",
    "Created: $((Get-Date).ToString('o'))",
    "Source logs: $resolvedLogsRoot",
    "Assessment: $assessment",
    "",
    "Event counts:",
    " - Strict pending-resolution blocks: $($counts.strictPending)",
    " - Strict distance/lie blocks: $($counts.strictBlocked)",
    " - Strict landing waits: $($counts.strictLandingWait)",
    " - Strict landing classified: $($counts.strictLandingClassified)",
    " - Strict landing resolved: $($counts.strictResolved)",
    " - Forced landing resolution: $($counts.forcedLandingResolution)",
    "",
    "Key blocked/pending events (with snapshot when available):"
)

if ($blockedWithSnapshot.Count -eq 0) {
    $reportLines += " - none"
} else {
    foreach ($entry in $recentBlockedWithSnapshot) {
        $reportLines += " - [$([System.IO.Path]::GetFileName($entry.file)):$($entry.line)] $($entry.text)"
    }
    if ($blockedWithSnapshot.Count -gt $recentBlockedWithSnapshot.Count) {
        $reportLines += " - ... truncated to latest $MaxEventDetails blocked/pending events"
    }
}

$reportLines += ""
$reportLines += "Artifacts:"
$reportLines += " - $reportPath"
$reportLines += " - $timelinePath"
$reportLines += " - $jsonPath"

Set-Content -Path $reportPath -Value $reportLines -Encoding ASCII

Write-Host "Strict session analysis complete."
Write-Host " - Source logs: $resolvedLogsRoot"
Write-Host " - Report:      $reportPath"
Write-Host " - Timeline:    $timelinePath"
Write-Host " - JSON:        $jsonPath"
Write-Host " - Assessment:  $assessment"
