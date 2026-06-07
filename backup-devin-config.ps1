#!/usr/bin/env powershell
# Back up Devin CLI and Windsurf configuration before reinstalling Windows
# Usage: powershell -ExecutionPolicy Bypass -File backup-devin-config.ps1 -Destination "E:\Backups\devin-backup"

param(
    [string]$Destination = (Join-Path $env:USERPROFILE "Desktop\devin-backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"),
    [string]$ProjectRoot = "C:\VS Code projects\MCDG"
)

$ErrorActionPreference = "Stop"

function Copy-Tree($Source, $Dest) {
    if (-not (Test-Path $Source)) {
        Write-Warning "  SKIP: Source not found: $Source"
        return
    }
    $parent = Split-Path $Dest -Parent
    if (-not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    if (Test-Path $Source -PathType Container) {
        Copy-Item -Path $Source -Destination $Dest -Recurse -Force
    } else {
        Copy-Item -Path $Source -Destination $Dest -Force
    }
    Write-Host "  OK: $Source -> $Dest"
}

Write-Host "========================================"
Write-Host "Devin/Windsurf Configuration Backup"
Write-Host "Destination: $Destination"
Write-Host "========================================"
New-Item -ItemType Directory -Path $Destination -Force | Out-Null

# ---- 1. Check git repo status ----
Write-Host ""
Write-Host "--- Step 1: Git Repository Check ---"
$repoPushed = $true
try {
    # Only check master and playtest/latest, ignore backup branches
    $unpushedMaster = & git -C $ProjectRoot log master --not --remotes --oneline 2>$null
    $unpushedPlaytest = & git -C $ProjectRoot log playtest/latest --not --remotes --oneline 2>$null
    if ($unpushedMaster -or $unpushedPlaytest) {
        Write-Host "WARNING: The following commits are NOT pushed to origin:"
        if ($unpushedMaster) { $unpushedMaster | ForEach-Object { Write-Host "  [master] $_" } }
        if ($unpushedPlaytest) { $unpushedPlaytest | ForEach-Object { Write-Host "  [playtest/latest] $_" } }
        Write-Host "Run: git push origin master"
        Write-Host "Run: git push origin playtest/latest"
        $repoPushed = $false
    } else {
        Write-Host "OK: master and playtest/latest are pushed to origin."
        if (& git -C $ProjectRoot branch --format='%(refname:short)' | Where-Object { $_ -match '^backup/' }) {
            Write-Host "NOTE: Backup branches exist locally (these are expected):"
            & git -C $ProjectRoot branch --format='%(refname:short)' | Where-Object { $_ -match '^backup/' } | ForEach-Object { Write-Host "  $_" }
        }
    }
} catch {
    Write-Warning "Could not check git status: $_"
}

# ---- 2. Devin User Config ----
Write-Host ""
Write-Host "--- Step 2: Devin User Config ---"
$devinConfigSource = Join-Path $env:APPDATA "devin"
$devinConfigDest = Join-Path $Destination "devin-user-config"
if (Test-Path $devinConfigSource) {
    # Copy only the important parts
    Copy-Tree (Join-Path $devinConfigSource "config.json") (Join-Path $devinConfigDest "config.json")
    Copy-Tree (Join-Path $devinConfigSource "User\settings.json") (Join-Path $devinConfigDest "User\settings.json")
    Copy-Tree (Join-Path $devinConfigSource "User\History") (Join-Path $devinConfigDest "User\History")
    Copy-Tree (Join-Path $devinConfigSource "cli\summaries") (Join-Path $devinConfigDest "cli\summaries")
} else {
    Write-Warning "Devin config directory not found: $devinConfigSource"
}

# ---- 3. Windsurf Global Config ----
Write-Host ""
Write-Host "--- Step 3: Windsurf Global Config ---"
$windsurfConfigSource = Join-Path $env:USERPROFILE ".codeium\windsurf"
$windsurfConfigDest = Join-Path $Destination "windsurf-global-config"
if (Test-Path $windsurfConfigSource) {
    # Copy key subdirectories
    $subdirs = @("memories", "windsurf", "skills", "workflows")
    foreach ($sub in $subdirs) {
        $src = Join-Path $windsurfConfigSource $sub
        $dst = Join-Path $windsurfConfigDest $sub
        if (Test-Path $src) {
            Copy-Tree $src $dst
        }
    }
    # Also copy any .md files at root
    Get-ChildItem -Path $windsurfConfigSource -Filter "*.md" -ErrorAction SilentlyContinue | ForEach-Object {
        Copy-Tree $_.FullName (Join-Path $windsurfConfigDest $_.Name)
    }
} else {
    Write-Warning "Windsurf config directory not found: $windsurfConfigSource"
}

# ---- 4. Project Local Config (gitignored) ----
Write-Host ""
Write-Host "--- Step 4: Project Local Config (.devin, .windsurf) ---"
$projectLocalItems = @(
    (Join-Path $ProjectRoot ".devin"),
    (Join-Path $ProjectRoot ".windsurf")
)
$projectLocalDest = Join-Path $Destination "MCDG-project-local"
foreach ($item in $projectLocalItems) {
    if (Test-Path $item) {
        $name = Split-Path $item -Leaf
        Copy-Tree $item (Join-Path $projectLocalDest $name)
    }
}

# ---- 5. VS Code Settings (if they exist) ----
Write-Host ""
Write-Host "--- Step 5: VS Code Settings ---"
$vscodeSettings = Join-Path $env:APPDATA "Code\User\settings.json"
$vscodeDest = Join-Path $Destination "vscode"
if (Test-Path $vscodeSettings) {
    Copy-Tree $vscodeSettings (Join-Path $vscodeDest "settings.json")
} else {
    Write-Host "  SKIP: VS Code settings not found"
}

# ---- 6. ATLauncher deploy path reminder ----
Write-Host ""
Write-Host "--- Step 6: ATLauncher Deploy Path ---"
$deployPath = "C:\Users\rich\AppData\Roaming\ATLauncher\instances\Minecraft1206withFabric\mods"
if (Test-Path $deployPath) {
    Write-Host "  ATLauncher mods folder exists: $deployPath"
    Write-Host "  (Reinstall ATLauncher and restore this path manually)"
} else {
    Write-Host "  ATLauncher mods folder not found (may need reinstall)"
}

# ---- 7. Write manifest ----
Write-Host ""
Write-Host "--- Step 7: Writing manifest ---"
$manifest = @{
    backup_date = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    computer = $env:COMPUTERNAME
    user = $env:USERNAME
    devin_version = "unknown"
    git_repo_pushed = $repoPushed
    items_backed_up = @(
        "Devin user config: $devinConfigSource"
        "Windsurf global config: $windsurfConfigSource"
        "MCDG project local config: $ProjectRoot"
        "VS Code settings"
    )
    restore_instructions = @(
        "1. Install Windsurf/VS Code"
        "2. Install Devin CLI (through Windsurf or standalone)"
        "3. Clone repo: git clone https://github.com/rbrambley/mcdg.git"
        "4. Copy 'devin-user-config' to %APPDATA%\devin\"
        "5. Copy 'windsurf-global-config' to %USERPROFILE%\.codeium\windsurf\"
        "6. Copy 'MCDG-project-local' contents into your cloned repo"
        "7. Copy 'vscode/settings.json' to %APPDATA%\Code\User\"
        "8. Run: git checkout master && git pull"
        "9. Run: git checkout playtest/latest && git pull"
        "10. Generate tester manifest: powershell -File generate-tester-manifest.ps1"
    )
}
$manifestJson = $manifest | ConvertTo-Json -Depth 5
Set-Content -Path (Join-Path $Destination "manifest.json") -Value $manifestJson
Write-Host "  OK: manifest.json written"

# ---- 8. Write restore script ----
Write-Host ""
Write-Host "--- Step 8: Writing restore script ---"
$restoreScript = @"
# Restore Devin/Windsurf configuration after reinstalling Windows
# Run this from the backup directory

param([string]`$RepoPath = "C:\VS Code projects\MCDG")

Write-Host "Restoring Devin/Windsurf configuration..."

# 1. Devin user config
`$devinDest = Join-Path `$env:APPDATA "devin"
if (Test-Path "devin-user-config") {
    if (-not (Test-Path `$devinDest)) { New-Item -ItemType Directory -Path `$devinDest -Force | Out-Null }
    Copy-Item -Path "devin-user-config\*" -Destination `$devinDest -Recurse -Force
    Write-Host "OK: Devin config restored to `$devinDest"
}

# 2. Windsurf global config
`$windsurfDest = Join-Path `$env:USERPROFILE ".codeium\windsurf"
if (Test-Path "windsurf-global-config") {
    if (-not (Test-Path `$windsurfDest)) { New-Item -ItemType Directory -Path `$windsurfDest -Force | Out-Null }
    Copy-Item -Path "windsurf-global-config\*" -Destination `$windsurfDest -Recurse -Force
    Write-Host "OK: Windsurf config restored to `$windsurfDest"
}

# 3. Project local config
if (Test-Path "MCDG-project-local") {
    if (-not (Test-Path `$RepoPath)) {
        Write-Warning "Repo path not found: `$RepoPath"
        Write-Host "Clone it first: git clone https://github.com/rbrambley/mcdg.git '`$RepoPath'"
    } else {
        Copy-Item -Path "MCDG-project-local\*" -Destination `$RepoPath -Recurse -Force
        Write-Host "OK: Project local config restored to `$RepoPath"
    }
}

# 4. VS Code settings
`$vscodeDest = Join-Path `$env:APPDATA "Code\User\settings.json"
if (Test-Path "vscode\settings.json") {
    `$parent = Split-Path `$vscodeDest -Parent
    if (-not (Test-Path `$parent)) { New-Item -ItemType Directory -Path `$parent -Force | Out-Null }
    Copy-Item -Path "vscode\settings.json" -Destination `$vscodeDest -Force
    Write-Host "OK: VS Code settings restored"
}

Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. git clone https://github.com/rbrambley/mcdg.git '`$RepoPath'"
Write-Host "  2. cd '`$RepoPath'"
Write-Host "  3. git checkout master && git pull"
Write-Host "  4. git checkout playtest/latest && git pull"
Write-Host "  5. powershell -File generate-tester-manifest.ps1"
Write-Host "  6. Build and deploy: powershell -ExecutionPolicy Bypass -File scripts\deploy-to-atlauncher.ps1 ..."
"@
Set-Content -Path (Join-Path $Destination "restore.ps1") -Value $restoreScript
Write-Host "  OK: restore.ps1 written"

# ---- Summary ----
Write-Host ""
Write-Host "========================================"
Write-Host "BACKUP COMPLETE"
Write-Host "========================================"
Write-Host "Location: $Destination"
Write-Host ""
Write-Host "Contents:"
Get-ChildItem -Path $Destination -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($Destination.Length + 1)
    Write-Host "  $rel"
}
Write-Host ""
Write-Host "To restore on new machine:"
Write-Host "  powershell -ExecutionPolicy Bypass -File `"$Destination\restore.ps1`""
Write-Host ""
if (-not $repoPushed) {
    Write-Host "WARNING: You have unpushed commits. Push them BEFORE reinstalling:"
    Write-Host "  cd '$ProjectRoot'"
    Write-Host "  git push origin master"
    Write-Host "  git push origin playtest/latest"
}
Write-Host ""
Write-Host "Next: Copy the backup folder to a USB drive or cloud storage."
