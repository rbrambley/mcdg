param(
    [string]$ReleaseId = (Get-ChildItem releases/test-packs | Where-Object { $_.PSIsContainer } | Sort-Object Name | Select-Object -Last 1).Name,
    [string]$Repo = "rbrambley/mcdg"
)

$ErrorActionPreference = 'Stop'
$packDir = "releases/test-packs/$ReleaseId"
$tag = "tester-$ReleaseId"

# Verify artifacts
$required = @(
    "$packDir/${ReleaseId}-client.zip",
    "$packDir/${ReleaseId}-server.zip",
    "$packDir/${ReleaseId}-all-in-one.zip",
    "$packDir/MCDG-Test-Resources.zip",
    "$packDir/README-RELEASE.txt"
)
foreach ($f in $required) {
    if (-not (Test-Path $f)) { throw "Missing artifact: $f" }
}

# Create/push tag if needed
$localTag = git tag -l $tag
$remoteTag = git ls-remote --tags origin refs/tags/$tag
if (-not $localTag -and -not $remoteTag) {
    git tag -a $tag -m "Test pack $ReleaseId"
    git push origin $tag
    Write-Host "Created and pushed tag $tag"
} else {
    Write-Host "Tag $tag already exists"
}

# Create release (ignore if already exists)
try {
    gh release create $tag `
        --title "MCDG Test Pack $ReleaseId" `
        --notes-file "$packDir/README-RELEASE.txt" `
        --repo $Repo
    Write-Host "Created release $tag"
} catch {
    Write-Host "Release may already exist: $_"
}

# Upload assets
gh release upload $tag `
    "$packDir/${ReleaseId}-client.zip" `
    "$packDir/${ReleaseId}-server.zip" `
    "$packDir/${ReleaseId}-all-in-one.zip" `
    "$packDir/MCDG-Test-Resources.zip" `
    --repo $Repo --clobber

Write-Host "Uploaded assets to https://github.com/$Repo/releases/tag/$tag"
