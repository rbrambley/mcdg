content = open('src/main/java/com/mcdg/game/ThrowResolver.java', 'r').read()

# Replace the calculated throw landing section to classify first, then find safe position
old_section = '''        // Check for calculated throw landing position (trajectory-based system)
        Vec3d calcLanding = getCalculatedLandingPosition(world, player.getUuid());
        if (calcLanding != null) {
            // Find safe standable position at calculated X,Z coordinates
            // The calculated Y may not match actual terrain (water, hills, etc.)
            BlockPos targetFeet = new BlockPos((int) Math.round(calcLanding.x), (int) Math.round(calcLanding.y), (int) Math.round(calcLanding.z));
            BlockPos safeFeet = SafePositionFinder.findNearestStandableFeet(world, targetFeet);

            // Teleport player to safe landing position
            player.teleport(safeFeet.getX() + 0.5, safeFeet.getY(), safeFeet.getZ() + 0.5);
            currentFeet = safeFeet;
            McdgMod.LOGGER.info(
                    "Player teleported to calculated landing | player={} calc={},{},{} safe={},{},{} dist={}ft",
                    player.getGameProfile().getName(),
                    String.format("%.1f", calcLanding.x),
                    String.format("%.1f", calcLanding.y),
                    String.format("%.1f", calcLanding.z),
                    safeFeet.getX(), safeFeet.getY(), safeFeet.getZ(),
                    String.format("%.1f", calcLanding.distanceTo(new Vec3d(safeFeet.getX(), safeFeet.getY(), safeFeet.getZ())) * 3.0)
            );
        }'''

new_section = '''        // Check for calculated throw landing position (trajectory-based system)
        Vec3d calcLanding = getCalculatedLandingPosition(world, player.getUuid());
        BlockPos calcPenaltyType = null;  // Will store the penalty type for calculated throws
        if (calcLanding != null) {
            // First, classify the CALCULATED landing position (before finding safe spot)
            // This ensures water/hazard detection works correctly
            BlockPos calcFeetPos = new BlockPos((int) Math.round(calcLanding.x), (int) Math.round(calcLanding.y), (int) Math.round(calcLanding.z));
            StrictPenaltyType calcPenalty = OutOfBoundsClassifier.classifyOutType(world, calcFeetPos, currentHole, tee, basket, alternateAnchor, rulesetManager);

            BlockPos targetFeet;
            if (calcPenalty == StrictPenaltyType.OB) {
                // OB (water): Find last in-bounds solid block before the calculated position
                CrossingResolution crossing = findLastSolidBeforeOutCrossing(
                        world,
                        throwLie,
                        calcFeetPos,
                        currentHole,
                        tee,
                        basket,
                        alternateAnchor,
                        rulesetManager
                );
                targetFeet = crossing.safeLie();
                calcPenaltyType = calcFeetPos;  // Remember for penalty application below
                McdgMod.LOGGER.info(
                        "Calculated throw landed in OB (water) | player={} calc={},{},{} safe={},{},{} dist={}ft",
                        player.getGameProfile().getName(),
                        String.format("%.1f", calcLanding.x),
                        String.format("%.1f", calcLanding.y),
                        String.format("%.1f", calcLanding.z),
                        targetFeet.getX(), targetFeet.getY(), targetFeet.getZ(),
                        String.format("%.1f", calcLanding.distanceTo(new Vec3d(targetFeet.getX(), targetFeet.getY(), targetFeet.getZ())) * 3.0)
                );
            } else if (calcPenalty == StrictPenaltyType.HAZARD) {
                // Hazard: Find nearest standable position at calculated X,Z (within hazard area)
                targetFeet = SafePositionFinder.findNearestStandableFeet(world, calcFeetPos);
                calcPenaltyType = calcFeetPos;  // Remember for penalty application below
                McdgMod.LOGGER.info(
                        "Calculated throw landed in Hazard | player={} calc={},{},{} safe={},{},{} dist={}ft",
                        player.getGameProfile().getName(),
                        String.format("%.1f", calcLanding.x),
                        String.format("%.1f", calcLanding.y),
                        String.format("%.1f", calcLanding.z),
                        targetFeet.getX(), targetFeet.getY(), targetFeet.getZ(),
                        String.format("%.1f", calcLanding.distanceTo(new Vec3d(targetFeet.getX(), targetFeet.getY(), targetFeet.getZ())) * 3.0)
                );
            } else {
                // Normal in-bounds: Find nearest standable position
                targetFeet = SafePositionFinder.findNearestStandableFeet(world, calcFeetPos);
                McdgMod.LOGGER.info(
                        "Player teleported to calculated landing | player={} calc={},{},{} safe={},{},{} dist={}ft",
                        player.getGameProfile().getName(),
                        String.format("%.1f", calcLanding.x),
                        String.format("%.1f", calcLanding.y),
                        String.format("%.1f", calcLanding.z),
                        targetFeet.getX(), targetFeet.getY(), targetFeet.getZ(),
                        String.format("%.1f", calcLanding.distanceTo(new Vec3d(targetFeet.getX(), targetFeet.getY(), targetFeet.getZ())) * 3.0)
                );
            }

            // Teleport player to the determined landing position
            player.teleport(targetFeet.getX() + 0.5, targetFeet.getY(), targetFeet.getZ() + 0.5);
            currentFeet = targetFeet;
        }'''

if old_section in content:
    content = content.replace(old_section, new_section)
    open('src/main/java/com/mcdg/game/ThrowResolver.java', 'w').write(content)
    print('Fixed OB/Hazard detection to classify calculated position first')
else:
    print('Could not find the section to replace')
