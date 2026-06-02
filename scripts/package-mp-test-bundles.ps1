param(
    [string]$ReleaseId = (Get-Date -Format "yyyy-MM-dd") + "-r1",
    [string]$InstancePath = "D:\ATLauncher\instances\TestInstanceMinecraft1206withFabric",
    [string]$OutputRoot = (Join-Path $PSScriptRoot "..\build\test-packs")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Info {
    param([string]$Message)
    Write-Host "[pack] $Message"
}

function New-EmptyDirectory {
    param([string]$Path)
    if (Test-Path $Path) {
        Remove-Item -Path $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
}

function Copy-IfExists {
    param(
        [string]$Source,
        [string]$Destination
    )

    if (Test-Path $Source) {
        Copy-Item -Path $Source -Destination $Destination -Recurse -Force
        return $true
    }

    return $false
}

function Render-TemplateFile {
    param(
        [string]$TemplatePath,
        [string]$TargetPath,
        [hashtable]$Tokens
    )

    $content = Get-Content -Path $TemplatePath -Raw
    foreach ($key in $Tokens.Keys) {
        $content = $content.Replace("{{${key}}}", [string]$Tokens[$key])
    }
    Set-Content -Path $TargetPath -Value $content -Encoding UTF8
}

function Write-DirectoryChecksums {
    param([string]$RootPath)

    $checksumPath = Join-Path $RootPath "SHA256SUMS.txt"
    $files = Get-ChildItem -Path $RootPath -Recurse -File |
        Where-Object { $_.FullName -ne $checksumPath } |
        Sort-Object FullName

    $lines = @()
    foreach ($file in $files) {
        $hash = (Get-FileHash -Path $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $relative = $file.FullName.Substring($RootPath.Length).TrimStart('\\') -replace '\\', '/'
        $lines += "$hash *$relative"
    }

    Set-Content -Path $checksumPath -Value $lines -Encoding UTF8
}

if (-not (Test-Path $InstancePath)) {
    throw "InstancePath not found: $InstancePath"
}

$templateRoot = Join-Path $PSScriptRoot "templates\test-pack"
if (-not (Test-Path $templateRoot)) {
    throw "Template directory not found: $templateRoot"
}

New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null

$stageRoot = Join-Path $OutputRoot $ReleaseId
$clientRoot = Join-Path $stageRoot "client"
$serverRoot = Join-Path $stageRoot "server"

Write-Info "Preparing staging folders for release '$ReleaseId'"
New-EmptyDirectory -Path $stageRoot
New-Item -ItemType Directory -Path $clientRoot -Force | Out-Null
New-Item -ItemType Directory -Path $serverRoot -Force | Out-Null

$clientDirs = @("mods", "config", "defaultconfigs", "resourcepacks")
$serverDirs = @("mods", "config", "defaultconfigs")
$serverFiles = @("server.properties", "whitelist.json", "ops.json")

Write-Info "Copying client directories from instance"
foreach ($dir in $clientDirs) {
    $source = Join-Path $InstancePath $dir
    $copied = Copy-IfExists -Source $source -Destination $clientRoot
    if (-not $copied) {
        Write-Info "Skipping missing client path: $dir"
    }
}

Write-Info "Copying server directories/files from instance"
foreach ($dir in $serverDirs) {
    $source = Join-Path $InstancePath $dir
    $copied = Copy-IfExists -Source $source -Destination $serverRoot
    if (-not $copied) {
        Write-Info "Skipping missing server path: $dir"
    }
}
foreach ($file in $serverFiles) {
    $source = Join-Path $InstancePath $file
    if (Test-Path $source) {
        Copy-Item -Path $source -Destination $serverRoot -Force
    }
}

$generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
$tokenMap = @{
    RELEASE_ID = $ReleaseId
    GENERATED_AT = $generatedAt
    INSTANCE_PATH = $InstancePath
}

Write-Info "Rendering README templates"
Render-TemplateFile -TemplatePath (Join-Path $templateRoot "README-CLIENT.txt") -TargetPath (Join-Path $clientRoot "README-CLIENT.txt") -Tokens $tokenMap
Render-TemplateFile -TemplatePath (Join-Path $templateRoot "README-SERVER.txt") -TargetPath (Join-Path $serverRoot "README-SERVER.txt") -Tokens $tokenMap

$bugTemplateSource = Join-Path $templateRoot "BUG-REPORT-TEMPLATE.txt"
Copy-Item -Path $bugTemplateSource -Destination (Join-Path $stageRoot "BUG-REPORT-TEMPLATE.txt") -Force
Copy-Item -Path $bugTemplateSource -Destination (Join-Path $clientRoot "BUG-REPORT-TEMPLATE.txt") -Force
Copy-Item -Path $bugTemplateSource -Destination (Join-Path $serverRoot "BUG-REPORT-TEMPLATE.txt") -Force

$clientModList = @()
$clientModsPath = Join-Path $clientRoot "mods"
if (Test-Path $clientModsPath) {
    $clientModList = Get-ChildItem -Path $clientModsPath -File | Sort-Object Name | Select-Object -ExpandProperty Name
}

$serverModList = @()
$serverModsPath = Join-Path $serverRoot "mods"
if (Test-Path $serverModsPath) {
    $serverModList = Get-ChildItem -Path $serverModsPath -File | Sort-Object Name | Select-Object -ExpandProperty Name
}

$clientVersionText = @(
    "Release ID: $ReleaseId",
    "Generated At: $generatedAt",
    "Minecraft: 1.20.6",
    "Fabric Loader: 0.16.10",
    "Java: 21",
    "",
    "Client mod snapshot:"
)
$clientVersionText += if ($clientModList.Count -gt 0) { $clientModList } else { "(no mods found)" }
Set-Content -Path (Join-Path $clientRoot "VERSION.txt") -Value $clientVersionText -Encoding UTF8

$serverVersionText = @(
    "Release ID: $ReleaseId",
    "Generated At: $generatedAt",
    "Minecraft: 1.20.6",
    "Fabric Loader: 0.16.10",
    "Java: 21",
    "",
    "Server mod snapshot:"
)
$serverVersionText += if ($serverModList.Count -gt 0) { $serverModList } else { "(no mods found)" }
Set-Content -Path (Join-Path $serverRoot "VERSION.txt") -Value $serverVersionText -Encoding UTF8

Write-Info "Generating internal checksums"
Write-DirectoryChecksums -RootPath $clientRoot
Write-DirectoryChecksums -RootPath $serverRoot

$clientZip = Join-Path $OutputRoot ("$ReleaseId-client.zip")
$serverZip = Join-Path $OutputRoot ("$ReleaseId-server.zip")
if (Test-Path $clientZip) { Remove-Item -Path $clientZip -Force }
if (Test-Path $serverZip) { Remove-Item -Path $serverZip -Force }

Write-Info "Creating client/server zip archives"
Compress-Archive -Path (Join-Path $clientRoot "*") -DestinationPath $clientZip -CompressionLevel Optimal -Force
Compress-Archive -Path (Join-Path $serverRoot "*") -DestinationPath $serverZip -CompressionLevel Optimal -Force

$bundleChecksums = @()
$clientZipHash = (Get-FileHash -Path $clientZip -Algorithm SHA256).Hash.ToLowerInvariant()
$serverZipHash = (Get-FileHash -Path $serverZip -Algorithm SHA256).Hash.ToLowerInvariant()
$bundleChecksums += "$clientZipHash *$(Split-Path -Leaf $clientZip)"
$bundleChecksums += "$serverZipHash *$(Split-Path -Leaf $serverZip)"
Set-Content -Path (Join-Path $stageRoot "SHA256SUMS.txt") -Value $bundleChecksums -Encoding UTF8

Write-Info "Done"
Write-Host ""
Write-Host "Release staging folder: $stageRoot"
Write-Host "Client zip: $clientZip"
Write-Host "Server zip: $serverZip"
Write-Host "Top-level checksums: $(Join-Path $stageRoot 'SHA256SUMS.txt')"
