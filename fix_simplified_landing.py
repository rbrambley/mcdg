content = open('src/main/java/com/mcdg/game/ThrowResolver.java', 'r').read()

# Replace the entire calculated throw section with simplified version
old_section = '''        // Check for calculated throw landing position (trajectory-based system)
        Vec3d calcLanding = getCalculatedLandingPosition(world, player.getUuid());
        StrictPenaltyType calcPenaltyType = null;  // Will store the penalty type for calculated throws
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
                calcPenaltyType = StrictPenaltyType.OB;  // Remember for penalty application below
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
                calcPenaltyType = StrictPenaltyType.HAZARD;  // Remember for penalty application below
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

new_section = '''        // Check for calculated throw landing position (trajectory-based system)
        // Just like pearls, teleport to exact calculated position and let penalty system handle OB/hazard
        Vec3d calcLanding = getCalculatedLandingPosition(world, player.getUuid());
        if (calcLanding != null) {
            BlockPos calcFeetPos = new BlockPos((int) Math.round(calcLanding.x), (int) Math.round(calcLanding.y), (int) Math.round(calcLanding.z));

            // Teleport to exact calculated position (even if water/OB - penalty system will handle it)
            player.teleport(calcFeetPos.getX() + 0.5, calcFeetPos.getY(), calcFeetPos.getZ() + 0.5);
            currentFeet = calcFeetPos;

            McdgMod.LOGGER.info(
                    "Player teleported to calculated landing | player={} pos={},{},{} distFromThrow={}ft",
                    player.getGameProfile().getName(),
                    calcFeetPos.getX(), calcFeetPos.getY(), calcFeetPos.getZ(),
                    String.format("%.1f", DistanceUtils.distanceFeet(throwLie, calcFeetPos))
            );
        }'''

if old_section in content:
    content = content.replace(old_section, new_section)
    open('src/main/java/com/mcdg/game/ThrowResolver.java', 'w').write(content)
    print('Simplified calculated throw landing to match pearl behavior')
else:
    print('Could not find the section to replace')
