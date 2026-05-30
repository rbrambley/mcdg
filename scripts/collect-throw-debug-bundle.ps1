param(
    [string]$SessionDir = "",
    [switch]$IncludeArchivedLogs,
    [switch]$IncludeCrashReports
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$logsRoot = Join-Path $repoRoot "run\logs"
$bundleTimestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$bundleRoot = Join-Path $logsRoot "throw-debug-bundles"
$stagingDir = Join-Path $bundleRoot "mcdg-throw-debug-$bundleTimestamp"
$zipPath = "$stagingDir.zip"

function Copy-IfExists {
    param(
        [string]$SourcePath,
        [string]$DestinationPath
    )

    if (-not (Test-Path $SourcePath)) {
        return $false
    }

    $parent = Split-Path -Parent $DestinationPath
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }

    Copy-Item -Path $SourcePath -Destination $DestinationPath -Recurse -Force
    return $true
}

function Resolve-SessionDirectory {
    param([string]$RequestedPath)

    if (-not [string]::IsNullOrWhiteSpace($RequestedPath)) {
        if (-not (Test-Path $RequestedPath -PathType Container)) {
            throw "Requested session directory not found: $RequestedPath"
        }

        return (Resolve-Path $RequestedPath).Path
    }

    if (-not (Test-Path $logsRoot)) {
        return $null
    }

    $latestSession = Get-ChildItem -Path $logsRoot -Directory |
        Where-Object { $_.Name -like 'strict-manual-debug-*' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $latestSession) {
        return $null
    }

    return $latestSession.FullName
}

New-Item -ItemType Directory -Path $bundleRoot -Force | Out-Null
Remove-Item $stagingDir, $zipPath -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $stagingDir -Force | Out-Null

$selectedSessionDir = Resolve-SessionDirectory -RequestedPath $SessionDir
$includedItems = [System.Collections.Generic.List[string]]::new()

if ($selectedSessionDir) {
    $sessionName = Split-Path -Leaf $selectedSessionDir
    if (Copy-IfExists -SourcePath $selectedSessionDir -DestinationPath (Join-Path $stagingDir $sessionName)) {
        $includedItems.Add("session:$sessionName")
    }
}

$fileMap = @(
    @{ Source = (Join-Path $logsRoot "latest.log"); Destination = (Join-Path $stagingDir "logs\latest.log"); Label = "logs/latest.log" },
    @{ Source = (Join-Path $logsRoot "debug.log"); Destination = (Join-Path $stagingDir "logs\debug.log"); Label = "logs/debug.log" },
    @{ Source = (Join-Path $logsRoot "mcdg-throw-autotest-latest.txt"); Destination = (Join-Path $stagingDir "logs\mcdg-throw-autotest-latest.txt"); Label = "logs/mcdg-throw-autotest-latest.txt" },
    @{ Source = (Join-Path $logsRoot "mcdg-autotest-latest.txt"); Destination = (Join-Path $stagingDir "logs\mcdg-autotest-latest.txt"); Label = "logs/mcdg-autotest-latest.txt" },
    @{ Source = (Join-Path $repoRoot "run\server.properties"); Destination = (Join-Path $stagingDir "run\server.properties"); Label = "run/server.properties" }
)

foreach ($entry in $fileMap) {
    if (Copy-IfExists -SourcePath $entry.Source -DestinationPath $entry.Destination) {
        $includedItems.Add($entry.Label)
    }
}

if ($IncludeArchivedLogs.IsPresent -and (Test-Path $logsRoot)) {
    $archivedLogs = Get-ChildItem -Path $logsRoot -File |
        Where-Object { $_.Name -match '^(debug-\d+\.log\.gz|\d{4}-\d{2}-\d{2}-\d+\.log\.gz)$' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 6

    foreach ($archivedLog in $archivedLogs) {
        if (Copy-IfExists -SourcePath $archivedLog.FullName -DestinationPath (Join-Path $stagingDir "logs\archived\$($archivedLog.Name)")) {
            $includedItems.Add("logs/archived/$($archivedLog.Name)")
        }
    }
}

if ($IncludeCrashReports.IsPresent) {
    $crashReportsDir = Join-Path $repoRoot "run\crash-reports"
    if (Copy-IfExists -SourcePath $crashReportsDir -DestinationPath (Join-Path $stagingDir "crash-reports")) {
        $includedItems.Add("crash-reports/")
    }
}

$manifestLines = @(
    "MCDG Throw Debug Bundle",
    "Created: $(Get-Date -Format o)",
    "Repo root: $repoRoot",
    "Selected session directory: $(if ($selectedSessionDir) { $selectedSessionDir } else { '<none found>' })",
    "Include archived logs: $($IncludeArchivedLogs.IsPresent)",
    "Include crash reports: $($IncludeCrashReports.IsPresent)",
    "",
    "Included items:"
)
$manifestLines += ($includedItems | ForEach-Object { "- $_" })

Set-Content -Path (Join-Path $stagingDir "manifest.txt") -Value $manifestLines -Encoding ASCII

Compress-Archive -Path (Join-Path $stagingDir "*") -DestinationPath $zipPath -Force
Remove-Item $stagingDir -Recurse -Force

Write-Host "Throw debug bundle created:"
Write-Host " - Zip: $zipPath"
if ($selectedSessionDir) {
    Write-Host " - Session: $selectedSessionDir"
} else {
    Write-Host " - Session: none found (bundle contains shared logs only)"
}
Write-Host " - Included items: $($includedItems.Count)"
