content = open('src/main/java/com/mcdg/game/ThrowResolver.java', 'r').read()

# Remove the calcPenaltyType variable declaration
old_var = '        StrictPenaltyType calcPenaltyType = null;  // Will store the penalty type for calculated throws\n'
new_var = ''

if old_var in content:
    content = content.replace(old_var, new_var)
    print('1. Removed calcPenaltyType variable declaration')
else:
    print('1. Could not find variable declaration')

# Fix the penalty application section to remove calcPenaltyType logic
old_penalty = '''        } else if (ENABLE_STRICT_LANDING_PENALTIES && rulesetManager.isStrict()) {
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

new_penalty = '''        } else if (ENABLE_STRICT_LANDING_PENALTIES && rulesetManager.isStrict()) {
            // Classify current position (same for both calculated throws and pearls)
            StrictPenaltyType currentFeetPenalty = OutOfBoundsClassifier.classifyOutType(world, currentFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            StrictPenaltyType standableFeetPenalty = OutOfBoundsClassifier.classifyOutType(world, landingFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            landingPenalty = combinePenalty(currentFeetPenalty, standableFeetPenalty);'''

if old_penalty in content:
    content = content.replace(old_penalty, new_penalty)
    print('2. Simplified penalty application section')
else:
    print('2. Could not find penalty application section')

# Fix the OB handling to remove calcPenaltyType check
old_ob = '''                if (landingPenalty == StrictPenaltyType.OB) {
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

new_ob = '''                if (landingPenalty == StrictPenaltyType.OB) {
                    // Find last in-bounds position (same for both calculated throws and pearls)
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

if old_ob in content:
    content = content.replace(old_ob, new_ob)
    print('3. Simplified OB handling')
else:
    print('3. Could not find OB handling section')

open('src/main/java/com/mcdg/game/ThrowResolver.java', 'w').write(content)
print('Done!')
