content = open('src/main/java/com/mcdg/game/ThrowResolver.java', 'r').read()

old_section = '''        // Check for calculated throw landing position (trajectory-based system)
        Vec3d calcLanding = getCalculatedLandingPosition(world, player.getUuid());
        if (calcLanding != null) {
            // Teleport player to calculated landing position
            player.teleport(calcLanding.x, calcLanding.y, calcLanding.z);
            currentFeet = player.getBlockPos();
            McdgMod.LOGGER.info(
                    "Player teleported to calculated landing | player={} pos={},{},{} dist={}ft",
                    player.getGameProfile().getName(),
                    String.format("%.1f", calcLanding.x),
                    String.format("%.1f", calcLanding.y),
                    String.format("%.1f", calcLanding.z),
                    String.format("%.1f", calcLanding.distanceTo(new Vec3d(currentFeet.getX(), currentFeet.getY(), currentFeet.getZ())) * 3.0)
            );
        }'''

new_section = '''        // Check for calculated throw landing position (trajectory-based system)
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

if old_section in content:
    content = content.replace(old_section, new_section)
    open('src/main/java/com/mcdg/game/ThrowResolver.java', 'w').write(content)
    print('Fixed landing position to use safe standable feet')
else:
    print('Could not find the section to replace')
