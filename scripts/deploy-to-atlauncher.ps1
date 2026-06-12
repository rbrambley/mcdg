# Deploy the latest MCDG mod build to the ATLauncher test instance.
# Skips the copy if the destination jar is already up to date.

param(
    [string]$ModsDir = $env:ATLAUNCHER_TEST_MODS_DIR
)

# Fallback to known default path
if (-not $ModsDir) {
    $ModsDir = 'C:\Users\rich\AppData\Roaming\ATLauncher\instances\Minecraft1206withFabric\mods'
}

# Find the latest remapped Fabric jar (exclude sources and dev variants)
$builtJar = Get-ChildItem -Path "$PSScriptRoot\..\build\libs\*.jar" -ErrorAction SilentlyContinue `
    | Where-Object { $_.Name -notmatch '-sources\.jar$' -and $_.Name -notmatch '-dev\.jar$' } `
    | Sort-Object LastWriteTime -Descending `
    | Select-Object -First 1

if (-not $builtJar) {
    Write-Error "No built jar found in build/libs. Run .\gradlew build first."
    exit 1
}

# Check existing deployed jar (also exclude sources/dev variants)
$existingJar = Get-ChildItem -Path "$ModsDir\mcdg-*.jar" -ErrorAction SilentlyContinue `
    | Where-Object { $_.Name -notmatch '-sources\.jar$' -and $_.Name -notmatch '-dev\.jar$' } `
    | Select-Object -First 1

if (-not $existingJar -or $builtJar.LastWriteTime -gt $existingJar.LastWriteTime) {
    Copy-Item -Path $builtJar.FullName -Destination $ModsDir -Force
    Write-Host "Deployed $($builtJar.Name) to ATLauncher instance ($ModsDir)"
} else {
    Write-Host "ATLauncher instance already up to date ($($existingJar.Name))"
}
