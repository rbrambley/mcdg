param(
    [long]$Seed = 123456789,
    [switch]$SkipPresentation,
    [switch]$NoDebugStrictFlow,
    [switch]$NoDebugHudScoring
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

Remove-Item Env:\MCDG_THROW_AUTOTEST_COUNT -ErrorAction SilentlyContinue
Remove-Item Env:\MCDG_THROW_AUTOTEST_PLAYER -ErrorAction SilentlyContinue
Remove-Item Env:\MCDG_THROW_AUTOTEST_SHUTDOWN -ErrorAction SilentlyContinue
Remove-Item Env:\MCDG_AUTO_STRICT_SETUP -ErrorAction SilentlyContinue

if ($SkipPresentation.IsPresent) {
    $env:MCDG_SKIP_ROUND_PRESENTATION = "true"
} else {
    Remove-Item Env:\MCDG_SKIP_ROUND_PRESENTATION -ErrorAction SilentlyContinue
}

if ($NoDebugStrictFlow.IsPresent) {
    Remove-Item Env:\MCDG_DEBUG_STRICT_FLOW -ErrorAction SilentlyContinue
} else {
    $env:MCDG_DEBUG_STRICT_FLOW = "true"
}

if ($NoDebugHudScoring.IsPresent) {
    Remove-Item Env:\MCDG_DEBUG_HUD_SCORING -ErrorAction SilentlyContinue
} else {
    $env:MCDG_DEBUG_HUD_SCORING = "true"
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

Write-Host "Strict manual debug server mode enabled."
Write-Host " - Seed: $Seed"
Write-Host " - Skip presentation: $($SkipPresentation.IsPresent)"
Write-Host " - Strict-flow debug: $($NoDebugStrictFlow.IsPresent -eq $false)"
Write-Host " - HUD scoring debug: $($NoDebugHudScoring.IsPresent -eq $false)"
Write-Host " - Throw autotest: disabled"
Write-Host ""
Write-Host "Server behavior: start dedicated server only (world auto-creates on first launch)."
Write-Host "Gameplay setup is manual so throw issues can be reproduced deterministically."
Write-Host ""

try {
    gradle runServer
} finally {
    Remove-Item Env:\MCDG_SKIP_ROUND_PRESENTATION -ErrorAction SilentlyContinue
    Remove-Item Env:\MCDG_DEBUG_STRICT_FLOW -ErrorAction SilentlyContinue
    Remove-Item Env:\MCDG_DEBUG_HUD_SCORING -ErrorAction SilentlyContinue
}
