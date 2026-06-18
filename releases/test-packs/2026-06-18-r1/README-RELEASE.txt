MCDG Test Pack Release 2026-06-18-r1
=====================================

Release Contents:
-----------------
- 2026-06-18-r1-client.zip - Client test pack (resource pack built-in and auto-enabled)
- 2026-06-18-r1-server.zip - Server test pack
- 2026-06-18-r1-all-in-one.zip - Complete package with everything
- MCDG-Test-Resources.zip - Standalone resource pack for manual installation
- MULTIPLAYER-TEST-SHEET.md - Comprehensive multiplayer test cases
- MULTIPLAYER-TEST-SHEET-QUICKPASS.md - Quick pass multiplayer verification
- MANUAL-TEST-NOTES.md - Manual testing notes and regression snapshots
- MULTI-PHASE-IMPLEMENTATION-PLAN.md - Current implementation roadmap

Build Information:
------------------
- Release ID: 2026-06-18-r1
- Generated: 2026-06-18 12:44:25 -04:00
- Git Commit: 6250eb09a3b74635b8c496589cacffa5c1f08a79
- Minecraft: 1.20.6
- Fabric Loader: 0.16.10
- Java: 21

Key Changes from Previous Release (2026-06-17-r1):
--------------------------------------------------
- Global mobGriefing permanently disabled (no dynamic protection system)
- Multiplayer turn-change and hole-completion broadcast notifications
- GUI-based multiplayer round invitation system
- Angle keybinds decoupled from power meter with keybind hints
- Throw HUD polished with compact scaling and distance fixes
- Setup HUD added with smooth fade-out for round HUDs
- Hole transition fixes: player and lie marker placed directly on tee
- Inactive payload sent immediately when round ends so HUDs can fade out

Mod Versions:
--------------
- fabric-api-0.100.8+1.20.6.jar
- ferritecore-6.1.1-fabric.jar
- lithium-fabric-mc1.20.6-0.12.5.jar
- mcdg-0.1.0.jar (build commit 6250eb0)

Resource Pack:
--------------
- MCDG-Test-Resources is built into the mod jar and auto-enabled by default.
- A standalone zip is included in the all-in-one pack for manual reinstall if needed.

Installation:
-------------
Option 1 - All-in-One (Recommended):
1. Extract 2026-06-18-r1-all-in-one.zip
2. Extract 2026-06-18-r1-client.zip to your Minecraft instance directory.
3. Extract 2026-06-18-r1-server.zip to your server directory.
4. Resource pack is already built into the mod jar and auto-enabled.
5. Review test documentation for testing procedures.

Option 2 - Individual Downloads:
1. Extract the appropriate zip (client or server) to your target directory.
2. For client: extract to your Minecraft instance directory.
3. For server: extract to server directory.
4. Resource pack is built into the mod jar and auto-enabled.
5. Review test documentation for testing procedures.

Server Setup (Oracle Cloud Self-Hosted):
----------------------------------------
1. Upload 2026-06-18-r1-server.zip to your Oracle Cloud instance.
2. Stop the running Minecraft server.
3. Back up the current server directory.
4. Extract the server pack into the server directory.
5. Start the server and verify mod loading in the console log.
6. Distribute the client pack zip to testers.

Verification:
-------------
Client SHA256: 0482261216A061361E43DC72A37CE5D4D520FAF231A1D1E214EDA35098778791
Server SHA256: 767D14481ED47977170CF4CB77ACFCCB28E1E8ADCE17E8A0BB1D2548594907C5
All-in-One SHA256: 326EA9BACC5A68FA7A05104D1B552506CE0B1CC22FBA5D9643B73418D295DF3A

Testing Focus:
--------------
- Multiplayer round invitation flow via GUI
- Turn-change broadcast notifications across multiple clients
- Hole-completion notifications and leaderboard accuracy
- Client HUD fade-out behavior when rounds end
- mobGriefing interaction (should be permanently disabled)
- General multiplayer state consistency and performance

Report Issues:
--------------
Use the BUG-REPORT-TEMPLATE.txt included in client/server packages.
Include test sheet results and log files when reporting issues.
