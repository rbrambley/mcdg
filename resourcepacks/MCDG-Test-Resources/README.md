# MCDG Test Resources

This is a scaffold resource pack for private multiplayer testing.

## Purpose

- Improve gameplay readability for tee, basket, lie marker, and HUD cues.
- Keep assets lightweight and easy to iterate each test cycle.

## Current Status

- Folder skeleton is ready.
- Asset specs are in ASSET-SPECS.md.
- No production art has been added yet.

## Important Note About Current Item Models

The current mod item models point to vanilla textures:

- training_disc -> minecraft:item/ender_pearl
- scorecard -> minecraft:item/writable_book

If you want custom mod item textures from this pack, update item model paths in src/main/resources/assets/mcdg/models/item to use mcdg:item/... texture keys.

## Testing Workflow

1. Add PNG and OGG assets following ASSET-SPECS.md.
2. Zip this folder or copy it into an instance resourcepacks folder.
3. Enable the pack in-game.
4. Validate readability in daylight, night, and water-adjacent holes.
