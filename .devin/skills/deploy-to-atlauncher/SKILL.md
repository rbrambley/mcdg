# deploy-to-atlauncher

Deploy the latest MCDG mod build to the ATLauncher test instance, skipping the copy if already up to date.

## When to invoke

- After a successful `./gradlew build` when the user wants to test in Minecraft.
- When the user asks to "deploy to ATLauncher" or "update the mod".

## Procedure

1. **Find the built jar**
   - Look in `build/libs/` for the remapped Fabric jar (e.g. `mcdg-1.x.x.jar`).
   - Exclude `*-sources.jar` and `*-dev.jar`.
   - Use `Get-Item` to get `LastWriteTime`.

2. **Resolve ATLauncher mods directory**
   - Use env var `ATLAUNCHER_TEST_MODS_DIR` if set.
   - Fallback: `C:\Users\rich\AppData\Roaming\ATLauncher\instances\Minecraft1206withFabric\mods`

3. **Check existing deployment**
   - List jars in the mods directory matching `mcdg-*.jar`.
   - If none exist, deploy immediately.
   - If one exists, compare `LastWriteTime`.
     - If built jar is newer, overwrite.
     - If same or older, skip and report "ATLauncher instance already up to date."

4. **Copy**
   - `Copy-Item -Path <builtJar> -Destination <modsDir> -Force`

5. **Report result**
   - Tell the user what was copied, or that no update was needed.

## Example

```powershell
# PowerShell equivalent
$built = Get-ChildItem build/libs/*.jar | Where-Object { $_.Name -notmatch '(sources|dev)' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$dest = $env:ATLAUNCHER_TEST_MODS_DIR
if (-not $dest) { $dest = 'C:\Users\rich\AppData\Roaming\ATLauncher\instances\Minecraft1206withFabric\mods' }
$existing = Get-ChildItem "$dest\mcdg-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $existing -or $built.LastWriteTime -gt $existing.LastWriteTime) {
    Copy-Item $built.FullName -Destination $dest -Force
    Write-Host "Deployed $($built.Name) to ATLauncher"
} else {
    Write-Host "ATLauncher instance already up to date"
}
```
