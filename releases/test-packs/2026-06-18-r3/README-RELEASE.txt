MCDG Test Pack Release 2026-06-18-r3
=====================================

Release Contents:
-----------------
- 2026-06-18-r3-client.zip - Client test pack (resource pack built-in and auto-enabled)
- 2026-06-18-r3-server.zip - Server test pack
- 2026-06-18-r3-all-in-one.zip - Complete package with everything
- MCDG-Test-Resources.zip - Standalone resource pack for manual installation
- MULTIPLAYER-TEST-SHEET.md - Comprehensive multiplayer test cases
- MULTIPLAYER-TEST-SHEET-QUICKPASS.md - Quick pass multiplayer verification
- MANUAL-TEST-NOTES.md - Manual testing notes and regression snapshots
- MULTI-PHASE-IMPLEMENTATION-PLAN.md - Current implementation roadmap

Build Information:
------------------
- Release ID: 2026-06-18-r3
- Generated: 2026-06-18 23:25:00 -04:00
- Git Commit: 6ee5cf4fb69a90e69f90786a0e990489c20197ba
- Minecraft: 1.20.6
- Fabric Loader: 0.16.10
- Java: 21

Key Changes from Previous Release (2026-06-18-r2):
--------------------------------------------------
- PERF: Hazard overlay baked into cached minimap texture (eliminates ~10k world lookups/frame)
- PERF: Minimap join-warmup rebuilds throttled from every tick to every 5 ticks
- PERF: Surface shading uses fast path with zero world lookups (was 5 getTopY calls per pixel)
- PERF: Basket beacon particles reduced from 30 every 4 ticks to 6 every 10 ticks

Mod Versions:
--------------
- fabric-api-0.100.8+1.20.6.jar
- ferritecore-6.1.1-fabric.jar
- lithium-fabric-mc1.20.6-0.12.5.jar
- mcdg-0.1.0.jar (build commit 6ee5cf4)

Resource Pack:
--------------
- MCDG-Test-Resources is built into the mod jar and auto-enabled by default.
- A standalone zip is included in the all-in-one pack for manual reinstall if needed.

Installation:
-------------
Option 1 - All-in-One (Recommended):
1. Extract 2026-06-18-r3-all-in-one.zip
2. Extract 2026-06-18-r3-client.zip to your Minecraft instance directory.
3. Extract 2026-06-18-r3-server.zip to your server directory.
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
1. Upload 2026-06-18-r3-server.zip to your Oracle Cloud instance.
2. Stop the running Minecraft server.
3. Back up the current server directory.
4. Extract the server pack into the server directory.
5. Start the server and verify mod loading in the console log.
6. Distribute the client pack zip to testers.

Recommended JVM Flags for 4 vCPU / 24GB RAM:
--------------------------------------------
java -Xms6G -Xmx6G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -jar fabric-server-mc.1.20.6-loader.0.19.3-launcher.1.1.1.jar nogui

Verification:
-------------
Client SHA256: F7803E77426BBC4A24772CB366A55ED63CCB30B7BC5F145E8157970E10C91571
Server SHA256: C1B870C60C5AF30B51647626060FBA3D0CFABF5B863592DD187B89D899725620
All-in-One SHA256: 69F2FBCEAB741E36AF2FB215A4AE5BC01A30E93818112A4BFD23C368B5F37F1D

Testing Focus:
--------------
- Client FPS improvement during rounds (target: above 30 FPS)
- Minimap responsiveness on initial server join
- Visual fidelity of hazard overlay on minimap
- General multiplayer state consistency and performance

Report Issues:
--------------
Use the BUG-REPORT-TEMPLATE.txt included in client/server packages.
Include test sheet results and log files when reporting issues.
