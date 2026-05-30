param(
    [int]$Runs = 8,
    [int]$Holes = 9
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

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

try {
    gradle runServer
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
