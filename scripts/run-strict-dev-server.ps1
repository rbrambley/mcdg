param(
    [long]$Seed = 123456789,
    [int]$AutoThrows = 25,
    [string]$AutoThrowPlayer = "",
    [switch]$ShutdownWhenDone,
    [switch]$SkipPresentation,
    [switch]$DebugStrictFlow
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$env:MCDG_AUTO_STRICT_SETUP = "$Seed"
$env:MCDG_THROW_AUTOTEST_COUNT = "$AutoThrows"
if ([string]::IsNullOrWhiteSpace($AutoThrowPlayer)) {
    Remove-Item Env:\MCDG_THROW_AUTOTEST_PLAYER -ErrorAction SilentlyContinue
} else {
    $env:MCDG_THROW_AUTOTEST_PLAYER = $AutoThrowPlayer
}
$env:MCDG_THROW_AUTOTEST_SHUTDOWN = $(if ($ShutdownWhenDone.IsPresent) { "true" } else { "false" })
if ($SkipPresentation.IsPresent) {
    $env:MCDG_SKIP_ROUND_PRESENTATION = "true"
} else {
    Remove-Item Env:\MCDG_SKIP_ROUND_PRESENTATION -ErrorAction SilentlyContinue
}

if ($DebugStrictFlow.IsPresent) {
    $env:MCDG_DEBUG_STRICT_FLOW = "true"
} else {
    Remove-Item Env:\MCDG_DEBUG_STRICT_FLOW -ErrorAction SilentlyContinue
}

$serverPropertiesPath = Join-Path $repoRoot "run\server.properties"
if (Test-Path $serverPropertiesPath) {
    $serverProperties = Get-Content $serverPropertiesPath -Raw

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

    $serverProperties = Set-Or-AddProperty -Text $serverProperties -Key 'online-mode' -Value 'false'
    $serverProperties = Set-Or-AddProperty -Text $serverProperties -Key 'enforce-secure-profile' -Value 'false'
    $serverProperties = Set-Or-AddProperty -Text $serverProperties -Key 'server-ip' -Value '127.0.0.1'
    Set-Content -Path $serverPropertiesPath -Value $serverProperties -Encoding ASCII
    Write-Host " - Dev auth mode: offline (online-mode=false, enforce-secure-profile=false)"
    Write-Host " - Dev bind: server-ip=127.0.0.1"
}

Write-Host "Strict dev-server auto mode enabled."
Write-Host " - Seed: $Seed"
Write-Host " - Auto throws: $AutoThrows"
if (-not [string]::IsNullOrWhiteSpace($AutoThrowPlayer)) {
    Write-Host " - Auto throw player: $AutoThrowPlayer"
} else {
    Write-Host " - Auto throw player: first online player"
}
Write-Host " - Shutdown when done: $($ShutdownWhenDone.IsPresent)"
Write-Host " - Skip presentation: $($SkipPresentation.IsPresent)"
Write-Host " - Strict-flow debug: $($DebugStrictFlow.IsPresent)"
Write-Host ""
Write-Host "The server will auto-create the course, enable strict mode, start the round, and run the throw test."
Write-Host "Join the world once the server is up; the throw test begins automatically when the target player is online."
Write-Host ""

try {
    gradle runServer
} finally {
    Remove-Item Env:\MCDG_AUTO_STRICT_SETUP -ErrorAction SilentlyContinue
    Remove-Item Env:\MCDG_THROW_AUTOTEST_COUNT -ErrorAction SilentlyContinue
    Remove-Item Env:\MCDG_THROW_AUTOTEST_PLAYER -ErrorAction SilentlyContinue
    Remove-Item Env:\MCDG_THROW_AUTOTEST_SHUTDOWN -ErrorAction SilentlyContinue
    Remove-Item Env:\MCDG_SKIP_ROUND_PRESENTATION -ErrorAction SilentlyContinue
    Remove-Item Env:\MCDG_DEBUG_STRICT_FLOW -ErrorAction SilentlyContinue
}