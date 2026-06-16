content = open('src/main/java/com/mcdg/game/ThrowResolver.java', 'r').read()

# Fix 1: Remove the + 1.0 from penalty teleport (line 239-243)
old_penalty_teleport = '''player.teleport(
                        resultingLie.getX() + 0.5,
                        resultingLie.getY() + 1.0,
                        resultingLie.getZ() + 0.5
                );'''

new_penalty_teleport = '''// Find safe position before teleporting to avoid suffocation
                BlockPos safePos = SafePositionFinder.findNearestStandableFeet(world, resultingLie);
                player.teleport(
                        safePos.getX() + 0.5,
                        safePos.getY(),
                        safePos.getZ() + 0.5
                );'''

if old_penalty_teleport in content:
    content = content.replace(old_penalty_teleport, new_penalty_teleport)
    print('1. Fixed penalty teleport to not suffocate player')
else:
    print('1. Could not find penalty teleport')

# Fix 2: Fix the basket make teleport (line 192)
old_basket_teleport = 'player.teleport(resultingLie.getX() + 0.5, resultingLie.getY() + 1.0, resultingLie.getZ() + 0.5);'
new_basket_teleport = 'player.teleport(resultingLie.getX() + 0.5, resultingLie.getY(), resultingLie.getZ() + 0.5);'

if old_basket_teleport in content:
    content = content.replace(old_basket_teleport, new_basket_teleport)
    print('2. Fixed basket make teleport')
else:
    print('2. Could not find basket teleport')

# Fix 3: Fix the basket bounce teleport (line 276)
old_bounce_teleport = 'player.teleport(resultingLie.getX() + 0.5, resultingLie.getY() + 1.0, resultingLie.getZ() + 0.5);'
new_bounce_teleport = 'player.teleport(resultingLie.getX() + 0.5, resultingLie.getY(), resultingLie.getZ() + 0.5);'

# Count occurrences and only replace the second one (basket bounce)
if content.count(old_bounce_teleport) >= 2:
    # Split and replace only the last occurrence
    parts = content.rsplit(old_bounce_teleport, 1)
    if len(parts) == 2:
        content = parts[0] + new_bounce_teleport + parts[1]
        print('3. Fixed basket bounce teleport')
else:
    print('3. Could not find basket bounce teleport')

# Fix 4: Ensure calculated throw doesn't put player in solid block
old_calc_teleport = '''// Teleport to exact calculated position (even if water/OB - penalty system will handle it)
            player.teleport(calcFeetPos.getX() + 0.5, calcFeetPos.getY(), calcFeetPos.getZ() + 0.5);
            currentFeet = calcFeetPos;'''

new_calc_teleport = '''// Teleport to calculated position (avoid solid blocks)
            BlockPos safeCalcPos = calcFeetPos;
            if (!SafePositionFinder.isStandableFeet(world, calcFeetPos)) {
                safeCalcPos = SafePositionFinder.findNearestStandableFeet(world, calcFeetPos);
            }
            player.teleport(safeCalcPos.getX() + 0.5, safeCalcPos.getY(), safeCalcPos.getZ() + 0.5);
            currentFeet = safeCalcPos;'''

if old_calc_teleport in content:
    content = content.replace(old_calc_teleport, new_calc_teleport)
    print('4. Fixed calculated throw teleport to avoid solid blocks')
else:
    print('4. Could not find calculated throw teleport')

open('src/main/java/com/mcdg/game/ThrowResolver.java', 'w').write(content)
print('Done!')
