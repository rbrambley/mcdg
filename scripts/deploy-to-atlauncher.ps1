param(
    [string]$InstanceModsDir = $env:ATLAUNCHER_TEST_MODS_DIR,
    [switch]$SkipBuild,
    [switch]$QuickOnly,
    [switch]$EnforceCleanLifecycleSmoke,
    [string]$TesterPackManifestPath = $env:MCDG_TESTER_PACK_MANIFEST,
    [switch]$SkipTesterPackParity,
    [switch]$AllowDirtyTree
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($InstanceModsDir)) {
    throw 'Set ATLAUNCHER_TEST_MODS_DIR to your dedicated test instance mods folder, or pass -InstanceModsDir.'
}

if (-not (Test-Path -LiteralPath $InstanceModsDir)) {
    throw "Instance mods folder does not exist: $InstanceModsDir"
}

$repoRoot = Split-Path -Parent $PSScriptRoot

function Get-GitState {
    param([string]$RepoRootPath)

    $commit = (& git -C $RepoRootPath rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($commit)) {
        throw "Unable to resolve git commit for repo: $RepoRootPath"
    }

    $statusLines = & git -C $RepoRootPath status --porcelain
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to determine git working-tree state for repo: $RepoRootPath"
    }

    $dirty = @($statusLines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count -gt 0
    return [pscustomobject]@{
        Commit = $commit
        Dirty = $dirty
        StatusLines = @($statusLines)
    }
}

function Assert-CleanGitTree {
    param(
        [pscustomobject]$GitState,
        [switch]$AllowDirty
    )

    if ($GitState.Dirty -and -not $AllowDirty.IsPresent) {
        throw "Working tree has uncommitted changes. Commit or stash changes before deploy, or pass -AllowDirtyTree to override."
    }
}

function Resolve-TesterPackManifestPath {
    param(
        [string]$ExplicitPath,
        [string]$RepoRootPath
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        return $ExplicitPath
    }

    $candidates = @()
    $releasesRoot = Join-Path $RepoRootPath 'releases\test-packs'
    $buildRoot = Join-Path $RepoRootPath 'build\test-packs'

    if (Test-Path -LiteralPath $releasesRoot) {
        $candidates += Get-ChildItem -Path $releasesRoot -Recurse -File -Filter 'MANIFEST.json' -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $buildRoot) {
        $candidates += Get-ChildItem -Path $buildRoot -Recurse -File -Filter 'MANIFEST.json' -ErrorAction SilentlyContinue
    }

    $latest = $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $latest) {
        return $null
    }

    return $latest.FullName
}

function Assert-TesterPackCommitParity {
    param(
        [string]$ManifestPath,
        [pscustomobject]$GitState
    )

    if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
        throw "Tester-pack manifest was not found. Generate a tester pack manifest first or set -TesterPackManifestPath."
    }
    if (-not (Test-Path -LiteralPath $ManifestPath)) {
        throw "Tester-pack manifest does not exist: $ManifestPath"
    }

    $manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
    $manifestCommit = $manifest.Git.Commit
    if ([string]::IsNullOrWhiteSpace($manifestCommit)) {
        throw "Tester-pack manifest missing Git.Commit: $ManifestPath"
    }
    if ($manifestCommit -ne $GitState.Commit) {
        throw "Tester-pack parity failed. Manifest commit $manifestCommit does not match current HEAD $($GitState.Commit)."
    }

    $manifestDirty = [bool]$manifest.Git.Dirty
    if ($manifestDirty) {
        throw "Tester-pack manifest indicates dirty source tree (Git.Dirty=true). Refuse deploy."
    }

    Write-Host "Tester-pack commit parity OK: $manifestCommit"
}

Push-Location $repoRoot

try {
    $gitState = Get-GitState -RepoRootPath $repoRoot
    Assert-CleanGitTree -GitState $gitState -AllowDirty:$AllowDirtyTree.IsPresent

    if (-not $SkipTesterPackParity.IsPresent) {
        $resolvedManifestPath = Resolve-TesterPackManifestPath -ExplicitPath $TesterPackManifestPath -RepoRootPath $repoRoot
        Assert-TesterPackCommitParity -ManifestPath $resolvedManifestPath -GitState $gitState
    } else {
        Write-Host "Skipping tester-pack parity check (-SkipTesterPackParity)." -ForegroundColor Yellow
    }

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
