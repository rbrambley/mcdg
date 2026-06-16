content = open('src/main/java/com/mcdg/game/TrajectoryCalculator.java', 'r').read()

# Fix 1: Fix overhand ground collision - add minimum flight time before ground check
old_ground_check = '''            // Check for ground collision (only after glide phase completes)
            if (glideProgress >= 1.0f && velY < 0 && pos.y <= startPos.y + 1.0) {
                // Glide complete and disc has descended to ground level
                break;
            }'''

new_ground_check = '''            // Check for ground collision
            // For stances with glide: only after glide phase completes
            // For overhand: wait until disc has fallen significantly below throw height
            if (glideProgress >= 1.0f && velY < 0) {
                boolean shouldStop;
                if (hasGlide) {
                    // Glide stance: stop when back to throw height
                    shouldStop = pos.y <= startPos.y + 1.0;
                } else {
                    // Overhand: stop when fallen 3+ blocks below throw height
                    // This allows natural arc to complete
                    shouldStop = pos.y <= startPos.y - 2.0;
                }
                if (shouldStop) {
                    break;
                }
            }'''

if old_ground_check in content:
    content = content.replace(old_ground_check, new_ground_check)
    print('1. Fixed overhand ground collision check')
else:
    print('1. Could not find ground collision check')

open('src/main/java/com/mcdg/game/TrajectoryCalculator.java', 'w').write(content)

# Fix 2: Fix basket proximity detection
content2 = open('src/main/java/com/mcdg/game/ThrowResolver.java', 'r').read()

old_proximity = '''    private static boolean isCloseProximityMake(BlockPos throwLie, BlockPos landingFeet, BlockPos basket) {
        // Must land on the basket column (same X/Z as basket)
        if (landingFeet.getX() != basket.getX() || landingFeet.getZ() != basket.getZ()) {
            return false;
        }
        // Check if throw started within proximity radius (horizontal distance only)
        int dx = throwLie.getX() - basket.getX();
        int dz = throwLie.getZ() - basket.getZ();
        int horizontalDistSq = dx * dx + dz * dz;
        int radiusSq = PROXIMITY_MAKE_RADIUS_BLOCKS * PROXIMITY_MAKE_RADIUS_BLOCKS;
        return horizontalDistSq <= radiusSq;
    }'''

new_proximity = '''    private static boolean isCloseProximityMake(BlockPos throwLie, BlockPos landingFeet, BlockPos basket) {
        // Check if landing is within proximity radius of basket (horizontal distance only)
        int dx = landingFeet.getX() - basket.getX();
        int dz = landingFeet.getZ() - basket.getZ();
        int landingDistSq = dx * dx + dz * dz;

        // Check if throw started within proximity radius
        int throwDx = throwLie.getX() - basket.getX();
        int throwDz = throwLie.getZ() - basket.getZ();
        int throwDistSq = throwDx * throwDx + throwDz * throwDz;

        int radiusSq = PROXIMITY_MAKE_RADIUS_BLOCKS * PROXIMITY_MAKE_RADIUS_BLOCKS;
        return landingDistSq <= radiusSq && throwDistSq <= radiusSq;
    }'''

if old_proximity in content2:
    content2 = content2.replace(old_proximity, new_proximity)
    print('2. Fixed basket proximity detection')
else:
    print('2. Could not find proximity detection')

open('src/main/java/com/mcdg/game/ThrowResolver.java', 'w').write(content2)
print('Done!')
