param(
    [string]$InstanceModsDir = $env:ATLAUNCHER_TEST_MODS_DIR,
    [switch]$SkipBuild
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
        gradle quickRegression smokeRegression build
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
