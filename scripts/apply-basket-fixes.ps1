
$file = "D:\VS Code projects\MCDG\src\main\java\com\mcdg\world\CoursePlacementService.java"
$enc = New-Object System.Text.UTF8Encoding($false)
$raw = [System.IO.File]::ReadAllText($file)
$lf = $raw -replace "`r`n","`n"

# ── 1. Widen relocation radii ──────────────────────────────────────────────
$lf = $lf -replace "private static final int TEE_RELOCATION_RADIUS = 12;",
                   "private static final int TEE_RELOCATION_RADIUS = 18;"
$lf = $lf -replace "private static final int BASKET_RELOCATION_RADIUS = 16;",
                   "private static final int BASKET_RELOCATION_RADIUS = 24;"

# ── 2. Phase 3: add clearBasketCanopyZone call after clearHeadroom basket ──
$old3 = @'
            clearHeadroom(world, basketSurface, 2, 6, originalBlocks, null);
            clearTeeLaunchLane(world, teeSurface, basketSurface, originalBlocks, protectedPositions);
'@
$new3 = @'
            clearHeadroom(world, basketSurface, 2, 6, originalBlocks, null);
            clearBasketCanopyZone(world, basketSurface, originalBlocks, protectedPositions);
            clearTeeLaunchLane(world, teeSurface, basketSurface, originalBlocks, protectedPositions);
'@
$lf = $lf.Replace($old3, $new3)

# ── 3. Phase 3: add tee headspace guarantee after signature accents ─────────
$oldTee = @'
            if (hole.isSignature()) {
                placeSignatureBasketAccents(world, basketSurface, originalBlocks, protectedPositions);
            }

            progressCallback.accept(hole.index());
'@
$newTee = @'
            if (hole.isSignature()) {
                placeSignatureBasketAccents(world, basketSurface, originalBlocks, protectedPositions);
            }

            // Final headspace guarantee: ensure tee center column (y+1 to y+3) is air
            // after all Phase 3 structure placement to prevent tee_unsafe.
            for (int yOff = 1; yOff <= 3; yOff++) {
                BlockPos aboveTee = teeSurface.up(yOff);
                BlockState aboveState = world.getBlockState(aboveTee);
                if (!aboveState.isAir() && aboveState.getFluidState().isEmpty()) {
                    setTrackedBlock(world, aboveTee, Blocks.AIR.getDefaultState(), originalBlocks);
                }
            }

            progressCallback.accept(hole.index());
'@
$lf = $lf.Replace($oldTee, $newTee)

# ── 4. Replace isPlayableBasketSurface + inject new methods after it ────────
$oldPBS = @'
    private static boolean isPlayableBasketSurface(ServerWorld world, BlockPos pos) {
        if (!isWalkableGround(world, pos)) {
            return false;
        }
        if (isLikelyPitSurface(world, pos)) {
            return false;
        }
        return !hasExcessiveTeeEnclosure(world, pos);
    }

    private static boolean isPlayableTeeSurface(ServerWorld world, BlockPos pos) {
'@
$newPBS = @'
    private static boolean isPlayableBasketSurface(ServerWorld world, BlockPos pos) {
        if (!isWalkableGround(world, pos)) {
            return false;
        }
        if (isLikelyPitSurface(world, pos)) {
            return false;
        }
        if (hasExcessiveTeeEnclosure(world, pos)) {
            return false;
        }
        return !isBasketRawEnclosed(world, pos);
    }

    /** Mirrors CoursePlacementValidator.isBasketDeeplyEnclosed using raw MOTION_BLOCKING_NO_LEAVES topY. */
    private static boolean isBasketRawEnclosed(ServerWorld world, BlockPos pos) {
        int centerSurfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
        int centerDepth = centerSurfaceY - pos.getY();
        if (centerDepth >= 8) {
            return true;
        }
        int highWallSamples = 0;
        int totalSamples = 0;
        int scanRadius = 6;
        for (int dx = -scanRadius; dx <= scanRadius; dx += 2) {
            for (int dz = -scanRadius; dz <= scanRadius; dz += 2) {
                if (dx == 0 && dz == 0) continue;
                int distSq = dx * dx + dz * dz;
                if (distSq > (scanRadius * scanRadius)) continue;
                int sampleSurfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        pos.getX() + dx, pos.getZ() + dz) - 1;
                if ((sampleSurfaceY - pos.getY()) >= 6) highWallSamples++;
                totalSamples++;
            }
        }
        return totalSamples > 0 && highWallSamples >= Math.max(10, (int) Math.ceil(totalSamples * 0.65));
    }

    /** Clears canopy and grades terrain walls in the validator basket-enclosure scan radius (6 blocks). */
    private static void clearBasketCanopyZone(ServerWorld world, BlockPos basketBase,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        int scanRadius = 6;
        // Step 1: clear vegetation/canopy
        Set<BlockPos> clearedTreeNodes = new HashSet<>();
        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                if ((dx * dx) + (dz * dz) > scanRadius * scanRadius + 1) continue;
                clearFairwayColumnVegetation(world, basketBase.getX() + dx, basketBase.getZ() + dz,
                        basketBase.getY(), originalBlocks, protectedPositions, clearedTreeNodes);
            }
        }
        // Step 2: grade terrain walls — carve any surrounding column that is 6+ blocks above
        // the basket base down to at most 5 blocks above, fixing basket_deeply_enclosed in bowls.
        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                if (dx == 0 && dz == 0) continue;
                if ((dx * dx) + (dz * dz) > scanRadius * scanRadius + 1) continue;
                int cx = basketBase.getX() + dx;
                int cz = basketBase.getZ() + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
                if (topY - basketBase.getY() >= 6) {
                    int targetCeiling = basketBase.getY() + 5;
                    for (int y = topY; y > targetCeiling; y--) {
                        BlockPos target = new BlockPos(cx, y, cz);
                        if (isProtected(protectedPositions, target)) continue;
                        BlockState state = world.getBlockState(target);
                        if (!state.isAir() && state.getFluidState().isEmpty()) {
                            setTrackedBlock(world, target, Blocks.AIR.getDefaultState(), originalBlocks);
                        }
                    }
                }
            }
        }
    }

    private static boolean isPlayableTeeSurface(ServerWorld world, BlockPos pos) {
'@

$found = $lf.IndexOf($oldPBS)
if ($found -lt 0) {
    Write-Error "isPlayableBasketSurface old block NOT FOUND — aborting"
    exit 1
}
$lf = $lf.Replace($oldPBS, $newPBS)

# ── 5. Verify Phase 3 replacements landed ──────────────────────────────────
$checks = @(
    "clearBasketCanopyZone(world, basketSurface",
    "Final headspace guarantee",
    "return !isBasketRawEnclosed(world, pos);",
    "private static boolean isBasketRawEnclosed",
    "private static void clearBasketCanopyZone"
)
$allOk = $true
foreach ($chk in $checks) {
    if ($lf.IndexOf($chk) -lt 0) {
        Write-Error ('MISSING: ' + $chk)
        $allOk = $false
    }
}
if (-not $allOk) { exit 1 }

# ── 6. Write with LF, no BOM ───────────────────────────────────────────────
[System.IO.File]::WriteAllText($file, $lf, $enc)
Write-Host "All fixes applied successfully."
