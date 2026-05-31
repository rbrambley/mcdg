param(
    [string]$InstanceModsDir = $env:ATLAUNCHER_TEST_MODS_DIR,
    [switch]$SkipBuild,
    [switch]$QuickOnly,
    [switch]$EnforceCleanLifecycleSmoke
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($InstanceModsDir)) {
    throw 'Set ATLAUNCHER_TEST_MODS_DIR to your dedicated test instance mods folder, or pass -InstanceModsDir.'
}

if (-not (Test-Path -LiteralPath $InstanceModsDir)) {
    throw "Instance mods folder does not exist: $InstanceModsDir"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot

try {
    if (-not $SkipBuild) {
        if ($QuickOnly) {
            Write-Host "Running quick deploy gate: quickRegression + smokeRegression + build"
            gradle quickRegression smokeRegression build
            if ($LASTEXITCODE -ne 0) { throw "Quick regression gate failed (exit $LASTEXITCODE)." }
        } else {
            Write-Host "Running full deploy gate: lifecycle smoke + quickRegression + smokeRegression + build"

            Write-Host ""
            Write-Host "--- Step 1/3: Lifecycle smoke ---"
            $lifecycleArgs = @(
                '-NoProfile',
                '-ExecutionPolicy', 'Bypass',
                '-File', "$repoRoot\scripts\run-headless-autotest.ps1",
                '-Runs', '3',
                '-Holes', '9'
            )
            if ($EnforceCleanLifecycleSmoke.IsPresent) {
                Write-Host "Lifecycle smoke strict mode enabled: enforcing clean runtime preflight."
                $lifecycleArgs += '-EnforceCleanRuntime'
            }
            powershell @lifecycleArgs
            if ($LASTEXITCODE -ne 0) {
                Write-Host ""
                Write-Host "DEPLOY BLOCKED: Lifecycle smoke failed. Fix the issues above before deploying." -ForegroundColor Red
                $latestReport = Join-Path $repoRoot "run\logs\mcdg-autotest-latest.txt"
                if (Test-Path -LiteralPath $latestReport) {
                    Write-Host ""
                    Write-Host "--- Latest autotest report (key lines) ---" -ForegroundColor Yellow
                    Get-Content -LiteralPath $latestReport | Select-String -Pattern '^Status:|^Summary:|^Fail runs:|^Total issues:|^Warning landing gaps:|^Max landing gap:' | ForEach-Object { $_.Line }
                }
                Write-Host ""
                Write-Host "Tip: run powershell -NoProfile -ExecutionPolicy Bypass -File '.\scripts\run-headless-autotest.ps1' manually for full details." -ForegroundColor Yellow
                exit 1
            }

            Write-Host ""
            Write-Host "--- Step 2/3: Quick + smoke regression ---"
            gradle quickRegression smokeRegression
            if ($LASTEXITCODE -ne 0) { throw "Regression checks failed (exit $LASTEXITCODE)." }

            Write-Host ""
            Write-Host "--- Step 3/3: Build ---"
            gradle build
            if ($LASTEXITCODE -ne 0) { throw "Build failed (exit $LASTEXITCODE)." }
        }
    }
    else {
        Write-Host "Fast path deploy active (-SkipBuild): verification gates were intentionally skipped." -ForegroundColor Yellow
    }

    $libsDir = Join-Path $repoRoot 'build\libs'
    if (-not (Test-Path -LiteralPath $libsDir)) {
        throw "Build output not found: $libsDir"
    }

    $jar = Get-ChildItem -Path $libsDir -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '(?i)(sources|javadoc)' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $jar) {
        throw 'No deployable jar found in build/libs.'
    }

    $modPrefix = ($jar.BaseName -replace '-\d+.*$','')
    Get-ChildItem -Path $InstanceModsDir -Filter "$modPrefix*.jar" -ErrorAction SilentlyContinue |
        Remove-Item -Force

    $dest = Join-Path $InstanceModsDir $jar.Name
    Copy-Item -LiteralPath $jar.FullName -Destination $dest -Force

    Write-Host "Deployed: $($jar.Name)"
    Write-Host "To:       $InstanceModsDir"
}
finally {
    Pop-Location
}
