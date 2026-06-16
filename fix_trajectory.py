content = open('src/main/java/com/mcdg/game/TrajectoryCalculator.java', 'r').read()

# Fix 1: Increase UPWARD_IMPULSE from 0.035 to 0.045
old_impulse = 'private static final double UPWARD_IMPULSE = 0.035;'
new_impulse = 'private static final double UPWARD_IMPULSE = 0.045;'

if old_impulse in content:
    content = content.replace(old_impulse, new_impulse)
    print('1. Increased UPWARD_IMPULSE to 0.045')
else:
    print('1. Could not find UPWARD_IMPULSE declaration')

# Fix 2: Change ground collision to only trigger after glide completes
old_collision = '''            // Check for ground collision
            if (velY < 0 && pos.y <= startPos.y - 0.5) {
                // Disc has descended back to approximate ground level
                // In real implementation, would check actual block collision
                break;
            }'''

new_collision = '''            // Check for ground collision (only after glide phase completes)
            if (glideProgress >= 1.0f && velY < 0 && pos.y <= startPos.y + 1.0) {
                // Glide complete and disc has descended to ground level
                break;
            }'''

if old_collision in content:
    content = content.replace(old_collision, new_collision)
    print('2. Fixed ground collision to wait for glide completion')
else:
    print('2. Could not find ground collision check')

open('src/main/java/com/mcdg/game/TrajectoryCalculator.java', 'w').write(content)
print('Done!')
