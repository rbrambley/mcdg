param(
    [int]$Throws = 25,
    [string]$PlayerName = "",
    [switch]$ShutdownWhenDone
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$env:MCDG_THROW_AUTOTEST_COUNT = "$Throws"
if ([string]::IsNullOrWhiteSpace($PlayerName)) {
    Remove-Item Env:\MCDG_THROW_AUTOTEST_PLAYER -ErrorAction SilentlyContinue
} else {
    $env:MCDG_THROW_AUTOTEST_PLAYER = $PlayerName
}
$env:MCDG_THROW_AUTOTEST_SHUTDOWN = $(if ($ShutdownWhenDone) { "true" } else { "false" })

Write-Host "Throw autotest auto mode enabled."
Write-Host " - Throws: $Throws"
if (-not [string]::IsNullOrWhiteSpace($PlayerName)) {
    Write-Host " - Player: $PlayerName"
} else {
    Write-Host " - Player: first online player"
}
Write-Host " - Shutdown when done: $($ShutdownWhenDone.IsPresent)"
Write-Host ""
Write-Host "Next steps:"
Write-Host "1) Join the world as the target player."
Write-Host "2) Start or resume a round (/mcdg startround or /mcdg resumecourse)."
Write-Host "3) The throw test will start automatically and write run/logs/mcdg-throw-autotest-latest.txt"
Write-Host ""

try {
    gradle runServer
} finally {
    Remove-Item Env:\MCDG_THROW_AUTOTEST_COUNT -ErrorAction SilentlyContinue
    Remove-Item Env:\MCDG_THROW_AUTOTEST_PLAYER -ErrorAction SilentlyContinue
    Remove-Item Env:\MCDG_THROW_AUTOTEST_SHUTDOWN -ErrorAction SilentlyContinue

    $latestReport = Join-Path $repoRoot "run\logs\mcdg-throw-autotest-latest.txt"
    if (Test-Path $latestReport) {
        Write-Host "\n=== MCDG THROW AUTOTEST REPORT ==="
        Get-Content $latestReport
    } else {
        Write-Warning "No throw autotest report was generated at $latestReport"
    }
}
