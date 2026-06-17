# Custom Throw Animation Plan

**Status:** Future Work - Not Implemented  
**Created:** 2026-06-17  
**Current State:** Phase 6 complete with UseAction placeholder approach

---

## Current Implementation

**Phase 6 Status:** ✅ Complete with simplified approach
- **Method:** Client-side mixin returning different `UseAction` values per stance
- **Mappings:** Overhand → SPEAR, Backhand → CROSSBOW, Forehand → BOW
- **Benefits:** Immediate visual feedback, zero build complexity, minimal code
- **Limitation:** Vanilla arm poses don't match authentic disc golf throwing mechanics

**User Feedback:** Vanilla throws are fine but the "feel" is wrong for each throw type.

---

## Recommended Approach: Event-Based Rendering Hooks

**Rationale:** Best balance of ease of implementation, flexibility, and low risk.

### Why This Approach

1. **Ease of Implementation**
   - Fabric provides clear documentation for rendering events
   - Less invasive than mixing into complex Minecraft classes
   - Can be implemented incrementally
   - No external dependencies required
   - Leverages existing patterns in the codebase

2. **Flexibility**
   - Can achieve specific "feel" for each throw type
   - Fine-grained control over arm transformations
   - Easy to iterate and refine based on visual feedback

3. **Low Risk**
   - Incremental development approach
   - Easy to test and refine
   - Can fall back to current UseAction solution
   - Uses existing Fabric infrastructure

---

## Implementation Plan

### Phase 1: Foundation Setup
1. **Add rendering event hook**
   - Register `WorldRenderEvents.END` in `McdgClientMod`
   - Create dedicated `ThrowAnimationRenderer` class
   - Integrate with existing `ThrowPreferenceManager` for stance detection

2. **Basic arm transformation framework**
   - Implement simple rotation/translation system
   - Create stance-specific transformation constants
   - Add debug visualization for transformation testing

### Phase 2: Stance-Specific Animations
1. **Overhand Animation**
   - Vertical arm positioning (overhead throw motion)
   - Subtle backswing rotation
   - Release point timing

2. **Backhand Animation**
   - Horizontal arm extension to side
   - Forward motion simulation
   - Natural follow-through rotation

3. **Forehand Animation**
   - Arm across body positioning
   - Flick motion simulation
   - Sidearm release mechanics

### Phase 3: Refinement and Polish
1. **Timing and smoothing**
   - Add interpolation between animation states
   - Sync with charge cycle timing
   - Smooth transitions between stances

2. **Visual feedback enhancement**
   - Combine with existing UseAction base poses
   - Add subtle particle effects for throw initiation
   - Refine based on in-game testing

### Phase 4: Integration and Testing
1. **Multiplayer validation**
   - Test arm animations in multiplayer context
   - Ensure client-side only rendering doesn't affect other players
   - Validate performance impact

2. **Compatibility testing**
   - Test with other rendering mods
   - Ensure no conflicts with existing visual systems
   - Verify with different Minecraft settings

---

## Fallback Options

If event-based rendering encounters issues:

### Option 1: Custom Item Renderer Override
- Extend `ItemRenderer` for disc item
- Apply stance-specific transformations in render method
- Leverage existing item model infrastructure
- **Complexity:** Medium
- **Risk:** Low

### Option 2: Enhanced Hybrid Approach
- Keep current UseAction mappings
- Add supplementary particle effects
- Focus on "feel" through timing and feedback
- **Complexity:** Very Low
- **Risk:** Very Low

### Option 3: Revert to Current Solution
- Maintain UseAction placeholder approach
- Focus on other game features
- Revisit custom animations when Fabric API matures
- **Complexity:** None
- **Risk:** None

---

## Development Approach

### Incremental Strategy
1. **Start Simple:** Basic arm rotations based on stance
2. **Test Early:** Visual feedback drives refinement
3. **Iterate Frequently:** Small, testable changes
4. **Document Changes:** Track what transformations work best

### Success Criteria
- Each throw type has distinct, authentic arm motion
- Animations feel natural to disc golf mechanics
- Performance impact is minimal
- No conflicts with existing systems
- Multiplayer compatibility maintained

---

## Technical Considerations

### Existing Infrastructure to Leverage
- `ThrowPreferenceManager` - Already tracks current stance
- `McdgClientMod` - Already has event registration patterns
- `ClientTickEvents` - Can sync animations with charge cycle
- `WorldRenderEvents` - Already used for other visual features

### Potential Challenges
- **Timing:** Syncing animations with charge cycle
- **Performance:** Rendering overhead of custom transformations
- **Compatibility:** Conflicts with other rendering mods
- **Multiplayer:** Ensuring client-side only rendering

### Mitigation Strategies
- **Performance:** Profile rendering impact, optimize if needed
- **Compatibility:** Test with common mod combinations
- **Multiplayer:** Strict client-side only rendering logic
- **Timing:** Use existing charge cycle timing infrastructure

---

## Timeline Estimate

- **Phase 1 (Foundation):** 2-4 hours
- **Phase 2 (Stance Animations):** 4-8 hours  
- **Phase 3 (Refinement):** 2-4 hours
- **Phase 4 (Testing):** 2-4 hours

**Total:** 10-20 hours of development time

---

## Next Steps

When ready to implement custom animations:

1. Review Fabric rendering event documentation
2. Set up basic event hook in development environment
3. Implement simple test transformation
4. Iterate based on visual feedback
5. Follow phased approach outlined above

**Current Status:** Ready to begin when resources are available. Core disc physics system is complete and stable, providing solid foundation for visual enhancements.
