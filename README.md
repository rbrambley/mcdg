# MCDG — Minecraft Disc Golf

A Fabric mod for Minecraft 1.20.6 that brings disc golf to your world. Build procedural courses, compete in multiplayer rounds, and manage a full resort experience.

## Quick Links

- [Latest Test Pack Release](releases/test-packs/2026-06-18-r3)
- [Multiplayer Test Sheet](MULTIPLAYER-TEST-SHEET.md)
- [User Guide](USERGUIDE.md)
- [Server Setup Guide](SERVER-SETUP-GUIDE.md)
- [Bug Report Template](releases/test-packs/2026-06-18-r3/client/BUG-REPORT-TEMPLATE.txt)

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.20.6 |
| Fabric Loader | 0.16.10 |
| Java | 21 |

## Latest Release (2026-06-18-r3)

**Download:** See the [releases/test-packs/2026-06-18-r3](releases/test-packs/2026-06-18-r3) directory.

| Pack | File |
|------|------|
| Client | `2026-06-18-r3-client.zip` |
| Server | `2026-06-18-r3-server.zip` |
| All-in-One | `2026-06-18-r3-all-in-one.zip` |

### Recent Changes

- PERF: Hazard overlay baked into cached minimap texture (eliminates ~10k world lookups/frame)
- PERF: Minimap join-warmup rebuilds throttled from every tick to every 5 ticks
- PERF: Surface shading uses fast path with zero world lookups (was 5 getTopY calls per pixel)
- PERF: Basket beacon particles reduced from 30 every 4 ticks to 6 every 10 ticks

## Installation

### Client

1. Download `2026-06-18-r3-client.zip`.
2. Extract into your Minecraft instance directory.
3. Launch with the Fabric profile.
4. The MCDG-Test-Resources pack is built into the mod jar and auto-enabled.

### Server (Oracle Cloud / Self-Hosted)

1. Download `2026-06-18-r3-server.zip`.
2. Stop your server and back up the server directory.
3. Extract the server pack into the server directory.
4. Start the server and verify mod loading in the console log.
5. Distribute the client pack to testers.

See [SERVER-SETUP-GUIDE.md](SERVER-SETUP-GUIDE.md) for detailed Oracle Cloud setup.

## Playing

- Press **G** (default) to open the MCDG menu.
- All actions are available via the menu; no need to memorize commands.
- See [USERGUIDE.md](USERGUIDE.md) for the full tester flow, keybinds, and troubleshooting.

## Development

```bash
./gradlew build       # Compile both client and server
./gradlew test        # Run JUnit tests
./gradlew quickRegression   # Fast invariant/determinism checks
./gradlew smokeRegression   # Pre-deploy smoke tests
./gradlew fullRegression    # Complete test suite
```

## Contributing / Testing

We run regular multiplayer test sessions on a self-hosted Oracle Cloud server. If you'd like to participate:

1. Grab the latest test pack from the releases folder.
2. Follow the [User Guide](USERGUIDE.md) for install and test flow.
3. Report bugs using the included template.

## License

All rights reserved.
