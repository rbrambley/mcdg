content = open('src/main/java/com/mcdg/game/TrajectoryCalculator.java', 'r').read()

# Increase UPWARD_IMPULSE from 0.065 to 0.085
old_impulse = 'private static final double UPWARD_IMPULSE = 0.065;'
new_impulse = 'private static final double UPWARD_IMPULSE = 0.085;'

if old_impulse in content:
    content = content.replace(old_impulse, new_impulse)
    print('Increased UPWARD_IMPULSE from 0.065 to 0.085')
else:
    # Check current value
    import re
    match = re.search(r'UPWARD_IMPULSE = ([0-9.]+)', content)
    if match:
        current = match.group(1)
        print(f'Current UPWARD_IMPULSE is {current}, changing to 0.085')
        content = content.replace(f'UPWARD_IMPULSE = {current}', 'UPWARD_IMPULSE = 0.085')
    else:
        print('Could not find UPWARD_IMPULSE')

open('src/main/java/com/mcdg/game/TrajectoryCalculator.java', 'w').write(content)
print('Done!')
