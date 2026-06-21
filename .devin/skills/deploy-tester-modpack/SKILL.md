# deploy-tester-modpack

Publish the latest MCDG tester modpack to GitHub Releases at https://github.com/rbrambley/mcdg/releases.

## When to invoke

- After creating a new test pack in `releases/test-packs/` when the user wants to publish it.
- When the user asks to "deploy to GitHub", "publish release", "upload modpack", or similar.

## Preconditions

- `gh` CLI is installed and authenticated (`gh auth status` shows logged in).
- The repository remote points to `rbrambley/mcdg` (or the current fork).
- A test pack directory exists under `releases/test-packs/` with the expected zip files.

## Procedure

### 1. Determine the release to publish

By default, use the lexicographically latest directory under `releases/test-packs/` (e.g. `2026-06-21-r1`). Accept an optional `$ReleaseId` parameter to override.

### 2. Verify artifacts exist

Check that these files exist in the release directory:

- `{ReleaseId}-client.zip`
- `{ReleaseId}-server.zip`
- `{ReleaseId}-all-in-one.zip`
- `MCDG-Test-Resources.zip`
- `README-RELEASE.txt`
- `MANIFEST.json`

If any are missing, report which ones and stop.

### 3. Choose or create a Git tag

Derive a tag name from the release ID, e.g. `tester-2026-06-21-r1`.

Check if the tag already exists locally or remotely:

```powershell
$tag = "tester-$ReleaseId"
git tag -l $tag      # local
git ls-remote --tags origin refs/tags/$tag   # remote
```

If the tag does not exist, create it at the current HEAD and push it:

```powershell
git tag -a $tag -m "Test pack $ReleaseId"
git push origin $tag
```

If the tag already exists, use it as-is (assume it points to the correct commit).

### 4. Create the GitHub release

Use the `gh release create` command. Set:

- `--title "MCDG Test Pack $ReleaseId"`
- `--notes-file` pointing to the `README-RELEASE.txt` from the pack
- The tag name as the release target

Example:

```powershell
$notesFile = "releases/test-packs/$ReleaseId/README-RELEASE.txt"
gh release create $tag `
  --title "MCDG Test Pack $ReleaseId" `
  --notes-file "$notesFile" `
  --repo rbrambley/mcdg
```

If the release already exists, proceed to upload assets instead of failing.

### 5. Upload release assets

Upload the four main artifacts to the release:

```powershell
gh release upload $tag `
  "releases/test-packs/$ReleaseId/${ReleaseId}-client.zip" `
  "releases/test-packs/$ReleaseId/${ReleaseId}-server.zip" `
  "releases/test-packs/$ReleaseId/${ReleaseId}-all-in-one.zip" `
  "releases/test-packs/$ReleaseId/MCDG-Test-Resources.zip" `
  --repo rbrambley/mcdg
```

If assets with the same filename already exist, use `--clobber` to replace them.

### 6. Verify and report

Open the release page in the browser (or print the URL) and confirm the assets are listed:

```powershell
gh release view $tag --repo rbrambley/mcdg --web
```

Report the release URL to the user, e.g.:
`https://github.com/rbrambley/mcdg/releases/tag/tester-2026-06-21-r1`

## Example PowerShell script

```powershell
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
```

## Notes

- The GitHub MCP server (`github-mcp-server`) does **not** currently expose release creation or asset upload tools, so this skill relies on the `gh` CLI.
- If `gh` is not authenticated, run `gh auth login` first.
- The release notes come directly from the pack's `README-RELEASE.txt`, so ensure that file is up to date before publishing.
