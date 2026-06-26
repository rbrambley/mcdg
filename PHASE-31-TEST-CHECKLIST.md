# Phase 3.1 Follow-Up Test Checklist

## Disc Bag

- [ ] Craft a **Disc Bag** using leather + string + redstone
- [ ] Right-click the bag while holding it — a 12-slot GUI opens
- [ ] Place a Training Disc, Wooden Disc, Stone Disc, etc. into the bag slots
- [ ] Verify non-disc items (e.g., dirt, sword) cannot be inserted into bag slots
- [ ] Shift-click discs from inventory into the bag
- [ ] Shift-click discs out of the bag into player inventory
- [ ] Close the GUI, reopen the same bag, and confirm discs are still inside
- [ ] Carry multiple discs inside the bag while only using one hotbar slot
- [ ] Place a bag inside a chest, retrieve it, and verify contents persist
- [ ] Move the bag to a different inventory slot while the GUI is open and confirm it closes

## Accessories

- [ ] Craft a **Disc Golf Glove** using leather + green dye
- [ ] Craft a **Disc Towel** using white wool + string
- [ ] Craft a **Range Finder** using glass pane + iron ingot + copper ingot + redstone
- [ ] Hold each accessory and verify its tooltip shows the effect name
- [ ] Keep the Disc Golf Glove in inventory and throw a disc — observe slightly reduced fade
- [ ] Keep the Disc Towel in inventory and throw a disc multiple times — observe occasional durability preservation
- [ ] Keep the Range Finder in inventory and hold a disc — confirm no crash (HUD hook is reserved for future distance feedback)
- [ ] Stack accessories with enchanted discs and confirm no conflicts

## Skill Unlocks

- [ ] Start a fresh world or use a player with no prior MCDG skills
- [ ] Throw 10+ discs and verify `/mcdg skills` shows progress toward **Wind Reading**
- [ ] Throw 500+ discs (or use admin/debug) and confirm **Wind Reading** unlocks
- [ ] Complete a full round and verify `/mcdg skills` shows progress toward **Release Control**
- [ ] Complete 10+ rounds and confirm **Release Control** unlocks
- [ ] Throw at least one disc of each tier (Training, Wooden, Stone, Iron, Gold, Diamond, Netherite) and confirm **Disc Mastery** unlocks
- [ ] Earn XP and verify `/mcdg skills` shows progress toward **Power Control**
- [ ] Earn 100+ XP and confirm **Power Control** unlocks
- [ ] Verify unlocked skills appear in green in the `/mcdg skills` list
- [ ] Verify locked skills show their requirement descriptions in red
- [ ] Log out and back in; confirm skill progress persists from `mcdg-player-skills.json`

## Throw & Physics Integration

- [ ] Throw a disc with **Power Control** unlocked and confirm slightly higher velocity
- [ ] Throw with **Release Control** unlocked using HYZER/ANHYZER and confirm reduced angle penalty
- [ ] Throw with **Wind Reading** unlocked and confirm wind affects the disc less
- [ ] Throw with **Disc Mastery** unlocked and confirm all tier stats receive a small boost
- [ ] Throw with **Focus** unlocked and confirm reduced stamina (hunger) exhaustion
- [ ] Confirm tiered discs still take durability damage on throws (unless towel preserves it)
- [ ] Confirm Training Disc remains indestructible

## Regression / Stability

- [ ] Start a round and play a full 9-hole course without crashes
- [ ] Verify existing round rewards, scorecards, and XP still work
- [ ] Verify existing disc enchantment system still works
- [ ] Verify the Disc Workbench accepts all disc tiers + Disc Enchanted Book
- [ ] Confirm no new PMD/build warnings from the added files
- [ ] Confirm quickRegression passes before any further commits

## Notes

- Placeholder textures are provided for the new items. Final art should be created before release.
- The Disc Bag tracks its stack by UUID so moving the bag while the GUI is open closes the GUI and saves to the correct stack.
- Skill data is saved to the world folder as `mcdg-player-skills.json`.