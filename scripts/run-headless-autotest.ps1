param(
    [int]$Runs = 8,
    [int]$Holes = 9,
    [switch]$EnforceCleanRuntime
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

function Stop-ConflictingRunProcesses {
    param([string]$RootPath)

    $stoppedProcessIds = New-Object System.Collections.Generic.List[int]
    $rootPattern = [regex]::Escape($RootPath)

    $candidates = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -match '^(java|javaw|gradle|cmd)\.exe$' -and
            ($_.CommandLine -match $rootPattern -or $_.CommandLine -match 'runServer')
        }

    foreach ($candidate in $candidates) {
        $procId = [int]$candidate.ProcessId
        if ($procId -eq $PID) {
            continue
        }

        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            [void]$stoppedProcessIds.Add($procId)
        } catch {
        }
    }

    $listeners = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique

    foreach ($listenerProcId in $listeners) {
        if ($listenerProcId -eq $PID) {
            continue
        }

        try {
            Stop-Process -Id $listenerProcId -Force -ErrorAction Stop
            if (-not $stoppedProcessIds.Contains([int]$listenerProcId)) {
                [void]$stoppedProcessIds.Add([int]$listenerProcId)
            }
        } catch {
        }
    }

    if ($stoppedProcessIds.Count -gt 0) {
        Write-Host "[Preflight] Cleared lock-holding processes:" (($stoppedProcessIds | Sort-Object -Unique) -join ', ')
    }
}

function Assert-DevServerPortFree {
    $listeners = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique
    if ($listeners) {
        throw "Runtime-confidence preflight failed: port 25565 still in use by process(es): $($listeners -join ', ')."
    }
}

$serverProps = Join-Path $repoRoot "run\server.properties"
if (-not (Test-Path $serverProps)) {
    throw "Missing server.properties at $serverProps. Run the server once to generate it."
}

$propsContent = Get-Content $serverProps -Raw
$originalTick = "max-tick-time=60000"
$updatedProps = [regex]::Replace($propsContent, "(?m)^max-tick-time=.*$", "max-tick-time=-1")
if ($updatedProps -ne $propsContent) {
    Set-Content -Path $serverProps -Value $updatedProps -Encoding ASCII
}

$env:MCDG_AUTOTEST = "$Runs,$Holes"
$env:MCDG_AUTOTEST_SHUTDOWN = "true"

if ($EnforceCleanRuntime.IsPresent) {
    Write-Host "Running runtime-confidence preflight (clear lock holders)..."
    Stop-ConflictingRunProcesses -RootPath $repoRoot
    Assert-DevServerPortFree
}

try {
    ./gradlew runServer --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "runServer exited with code $LASTEXITCODE"
    }
} finally {
    Remove-Item Env:\MCDG_AUTOTEST -ErrorAction SilentlyContinue
    Remove-Item Env:\MCDG_AUTOTEST_SHUTDOWN -ErrorAction SilentlyContinue

    $latestReport = Join-Path $repoRoot "run\logs\mcdg-autotest-latest.txt"
    if (Test-Path $latestReport) {
        Write-Host "\n=== MCDG AUTOTEST REPORT ==="
        Get-Content $latestReport

        $reportText = Get-Content $latestReport -Raw
        if ($reportText -notmatch "Status:\s*Autotest complete") {
            throw "Lifecycle smoke report did not complete successfully."
        }
        if ($reportText -notmatch "Fail runs:\s*0") {
            throw "Lifecycle smoke report contains failed runs (expected Fail runs: 0)."
        }
        if ($reportText -notmatch "Total issues:\s*0") {
            throw "Lifecycle smoke report contains issues (expected Total issues: 0)."
        }
    } else {
        Write-Warning "No autotest report was generated at $latestReport"
        throw "Lifecycle smoke report missing: $latestReport"
    }
}
