$file = "d:\VS Code projects\MCDG\src\main\java\com\mcdg\world\CoursePlacementService.java"
$lines = Get-Content $file
$n = $lines.Count
Write-Host "Total lines: $n"

# Fix 1: Call site (0-indexed 148-156, 9 lines) -> 6-line clean call
$newCall = @(
    '            placeTeeHoleBanner(',
    '                world, teeSurface, basketSurface,',
    '                hole.index(), hole.par(), hole.distanceFeet(),',
    '                hole.signatureType().displayName(),',
    '                originalBlocks',
    '            );'
)
$after1 = $lines[0..147] + $newCall + $lines[157..($n-1)]
Write-Host "After call fix: $($after1.Count) lines"

# Fix 2: Method definition
# Method starts at 0-indexed 744 in after1, runs 33 lines (to index 776 inclusive)
# Replace with 8-param version that has signatureName and noteToShow logic
$newMethod = @(
    '    private static void placeTeeHoleBanner(',
    '            ServerWorld world,',
    '            BlockPos teeCenter,',
    '            BlockPos basketSurface,',
    '            int holeNumber,',
    '            int par,',
    '            int distanceFeet,',
    '            String signatureName,',
    '            Map<BlockPos, BlockState> originalBlocks',
    '    ) {',
    '        int[] forward = teeForwardUnit(teeCenter, basketSurface);',
    '        int[] left = new int[] { -forward[1], forward[0] };',
    '        int[] right = new int[] { -left[0], -left[1] };',
    '',
    '        BlockPos signGround = teeCenter.add(forward[0] + left[0], 0, forward[1] + left[1]);',
    '        BlockPos bannerGround = teeCenter.add(forward[0] + right[0], 0, forward[1] + right[1]);',
    '',
    '        clearHeadroom(world, bannerGround, 1, 4, originalBlocks, null);',
    '        setTrackedBlock(world, bannerGround.up(1), Blocks.OAK_FENCE.getDefaultState(), originalBlocks);',
    '        BlockPos bannerPos = bannerGround.up(2);',
    '        setTrackedBlock(world, bannerPos, Blocks.WHITE_BANNER.getDefaultState(), originalBlocks);',
    '        String hazardNote = teeHazardNote(world, teeCenter, basketSurface);',
    '        String noteToShow = signatureName.isEmpty() ? hazardNote : "\u2605 " + signatureName;',
    '        placeTeeHoleSign(',
    '            world,',
    '            signGround,',
    '            -forward[0],',
    '            -forward[1],',
    '            holeNumber,',
    '            par,',
    '            distanceFeet,',
    '            noteToShow,',
    '            originalBlocks',
    '        );',
    '    }'
)
# after1[0..743] + newMethod + after1[777..]
$after2 = $after1[0..743] + $newMethod + $after1[777..($after1.Count-1)]
Write-Host "After method fix: $($after2.Count) lines"

# Write result
Set-Content $file $after2
Write-Host "Done. Verifying..."
Select-String -Path $file -Pattern "placeTeeHoleBanner" | Select-Object LineNumber, Line
