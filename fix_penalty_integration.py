content = open('src/main/java/com/mcdg/game/ThrowResolver.java', 'r').read()

# Fix the variable declaration and the penalty application logic
old_declaration = '        BlockPos calcPenaltyType = null;  // Will store the penalty type for calculated throws'
new_declaration = '        StrictPenaltyType calcPenaltyType = null;  // Will store the penalty type for calculated throws'

if old_declaration in content:
    content = content.replace(old_declaration, new_declaration)
    print('1. Fixed variable type from BlockPos to StrictPenaltyType')
else:
    print('1. Could not find variable declaration')

# Fix the OB assignment
old_ob = '''                targetFeet = crossing.safeLie();
                calcPenaltyType = calcFeetPos;  // Remember for penalty application below'''
new_ob = '''                targetFeet = crossing.safeLie();
                calcPenaltyType = StrictPenaltyType.OB;  // Remember for penalty application below'''

if old_ob in content:
    content = content.replace(old_ob, new_ob)
    print('2. Fixed OB assignment to use StrictPenaltyType.OB')
else:
    print('2. Could not find OB assignment')

# Fix the Hazard assignment
old_hazard = '''                targetFeet = SafePositionFinder.findNearestStandableFeet(world, calcFeetPos);
                calcPenaltyType = calcFeetPos;  // Remember for penalty application below'''
new_hazard = '''                targetFeet = SafePositionFinder.findNearestStandableFeet(world, calcFeetPos);
                calcPenaltyType = StrictPenaltyType.HAZARD;  // Remember for penalty application below'''

if old_hazard in content:
    content = content.replace(old_hazard, new_hazard)
    print('3. Fixed Hazard assignment to use StrictPenaltyType.HAZARD')
else:
    print('3. Could not find Hazard assignment')

# Fix the penalty application to use calcPenaltyType if available
old_penalty_start = '''        if (madeShot) {
            // Successful basket shot - no penalties apply, lie is set to basket
            resultingLie = basket.up();
            player.teleport(resultingLie.getX() + 0.5, resultingLie.getY() + 1.0, resultingLie.getZ() + 0.5);
            state = roundStateManager.markLastThrowPenalty(player.getUuid(), false).orElse(state);
        } else if (ENABLE_STRICT_LANDING_PENALTIES && rulesetManager.isStrict()) {
            StrictPenaltyType currentFeetPenalty = OutOfBoundsClassifier.classifyOutType(world, currentFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            StrictPenaltyType standableFeetPenalty = OutOfBoundsClassifier.classifyOutType(world, landingFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            landingPenalty = combinePenalty(currentFeetPenalty, standableFeetPenalty);'''

new_penalty_start = '''        if (madeShot) {
            // Successful basket shot - no penalties apply, lie is set to basket
            resultingLie = basket.up();
            player.teleport(resultingLie.getX() + 0.5, resultingLie.getY() + 1.0, resultingLie.getZ() + 0.5);
            state = roundStateManager.markLastThrowPenalty(player.getUuid(), false).orElse(state);
        } else if (ENABLE_STRICT_LANDING_PENALTIES && rulesetManager.isStrict()) {
            // For calculated throws, use the pre-determined penalty type
            // (classification was already done at the calculated position, not the safe position)
            StrictPenaltyType currentFeetPenalty;
            StrictPenaltyType standableFeetPenalty;
            if (calcPenaltyType != null) {
                // Calculated throw: use pre-determined penalty
                currentFeetPenalty = calcPenaltyType;
                standableFeetPenalty = StrictPenaltyType.NONE;
            } else {
                // Pearl-based throw: classify current position
                currentFeetPenalty = OutOfBoundsClassifier.classifyOutType(world, currentFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
                standableFeetPenalty = OutOfBoundsClassifier.classifyOutType(world, landingFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            }
            landingPenalty = combinePenalty(currentFeetPenalty, standableFeetPenalty);'''

if old_penalty_start in content:
    content = content.replace(old_penalty_start, new_penalty_start)
    print('4. Fixed penalty application to use calcPenaltyType')
else:
    print('4. Could not find penalty application section')

# Fix the OB handling in penalty application to not re-calculate for calculated throws
old_ob_handling = '''                if (landingPenalty == StrictPenaltyType.OB) {
                    CrossingResolution crossing = findLastSolidBeforeOutCrossing(
                            world,
                            throwLie,
                            currentFeet,
                            currentHole,
                            tee,
                            basket,
                                alternateAnchor,
                            rulesetManager
                    );
                    resultingLie = crossing.safeLie();
                    firstOutCrossing = crossing.firstOutCrossing();
                } else {'''

new_ob_handling = '''                if (landingPenalty == StrictPenaltyType.OB) {
                    if (calcPenaltyType != null) {
                        // Calculated throw: already handled above, use current position
                        resultingLie = currentFeet.toImmutable();
                    } else {
                        // Pearl-based throw: find last in-bounds position
                        CrossingResolution crossing = findLastSolidBeforeOutCrossing(
                                world,
                                throwLie,
                                currentFeet,
                                currentHole,
                                tee,
                                basket,
                                    alternateAnchor,
                                rulesetManager
                        );
                        resultingLie = crossing.safeLie();
                        firstOutCrossing = crossing.firstOutCrossing();
                    }
                } else {'''

if old_ob_handling in content:
    content = content.replace(old_ob_handling, new_ob_handling)
    print('5. Fixed OB handling to skip re-calculation for calculated throws')
else:
    print('5. Could not find OB handling section')

open('src/main/java/com/mcdg/game/ThrowResolver.java', 'w').write(content)
print('Done!')
