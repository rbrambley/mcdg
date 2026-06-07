param(
    [string]$RepoRoot = (Get-Location).Path,
    [string]$OutputPath = (Join-Path (Get-Location).Path 'releases\test-packs\MANIFEST.json')
)

$ErrorActionPreference = 'Stop'

$commit = (& git -C $RepoRoot rev-parse HEAD).Trim()
$statusLines = & git -C $RepoRoot status --porcelain
$dirty = @($statusLines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count -gt 0

$manifest = @{
    Git = @{
        Commit = $commit
        Dirty = [bool]$dirty
    }
}

$json = $manifest | ConvertTo-Json -Depth 3
Set-Content -LiteralPath $OutputPath -Value $json -Encoding UTF8
Write-Host "Generated tester manifest: $OutputPath (commit=$commit, dirty=$dirty)"
