MCDG Test Pack Release 2026-06-28-r1
=====================================

Release Contents:
-----------------
- 2026-06-28-r1-client.zip - Client test pack (resource pack built-in and auto-enabled)
- 2026-06-28-r1-server.zip - Server test pack
- 2026-06-28-r1-all-in-one.zip - Complete package with everything
- MCDG-Test-Resources.zip - Standalone resource pack for manual installation
- MULTIPLAYER-TEST-SHEET.md - Comprehensive multiplayer test cases
- MULTIPLAYER-TEST-SHEET-QUICKPASS.md - Quick pass multiplayer verification
- MANUAL-TEST-NOTES.md - Manual testing notes and regression snapshots
- MULTI-PHASE-IMPLEMENTATION-PLAN.md - Current implementation roadmap

Build Information:
------------------
- Release ID: 2026-06-28-r1
- Generated: 2026-06-28 16:59:00 -04:00
- Git Commit: f8fa2c2ab9ba40d477347bc95b7d9c077b00f2a1
- Minecraft: 1.20.6
- Fabric Loader: 0.19.3
- Java: 21

Key Changes from Previous Release (2026-06-21-r1):
--------------------------------------------------
- refactor: extract command execution methods to specialized classes to fix architectural issues
- fix: persist and sync next-throw power multiplier across sessions
- feat: add debug commands for inspecting course and hole hazards
- fix: update hazard descriptions and debug command output after power-penalty refactor
- feat: replace hazard Slowness with next-throw power penalty
- fix: recognize all MCDG disc tiers in item checks
- feat: render scorecard tooltip when looking at framed scorecards
- feat: add /mcdg wind calm and gust commands
- feat: add prominent relative wind direction HUD arrow
- fix: remove Phase 6 placeholder sound system and add Instant serialization

Mod Versions (Client & Server):
-------------------------------
Shared:
- fabric-api-0.100.8+1.20.6.jar
- ferritecore-6.1.1-fabric.jar
- lithium-fabric-mc1.20.6-0.12.5.jar
- mcdg-0.1.0.jar
- BiomesOPlenty-fabric-1.20.6-18.4.0.8.jar
- TerraBlender-fabric-1.20.6-3.5.0.5.jar
- GlitchCore-fabric-1.20.6-1.1.0.10.jar
- SereneSeasons-fabric-1.20.6-9.4.0.6.jar
- spark-1.10.65-fabric.jar
- Veinminer-fabric-3.1.2+mc1.20.6.jar
- cloth-config-14.0.139-fabric.jar
- placeholder-api-2.4.0-pre.2+1.20.5.jar

Client-only:
- ClientSort-Fabric-1.20.6-0.9.0.jar
- emi-1.1.22+1.20.6+fabric.jar
- modmenu-10.0.0.jar
- XaerosWorldMap_1.39.12_Fabric_1.20.6.jar
- Xaeros_Minimap_25.2.10_Fabric_1.20.6.jar

Resource Pack:
--------------
- MCDG-Test-Resources is built into the mod jar and auto-enabled by default.
- A standalone zip is included in the all-in-one pack for manual reinstall if needed.

Installation:
-------------
Option 1 - All-in-One (Recommended):
1. Extract 2026-06-28-r1-all-in-one.zip
2. Extract 2026-06-28-r1-client.zip to your Minecraft instance directory.
3. Extract 2026-06-28-r1-server.zip to your server directory.
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
1. Upload 2026-06-28-r1-server.zip to your Oracle Cloud instance.
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
SHA256 checksums are available in SHA256SUMS.txt.

Testing Focus:
--------------
- Command execution refactoring and architectural improvements
- Next-throw power multiplier persistence and synchronization
- Debug commands for course and hole hazard inspection
- Hazard system improvements (power penalty instead of Slowness)
- Disc tier recognition in item checks
- Scorecard tooltip rendering
- Wind system enhancements (calm/gust commands, HUD direction arrow)
- General multiplayer state consistency and performance

Report Issues:
--------------
Use the BUG-REPORT-TEMPLATE.txt included in client/server packages.
Include test sheet results and log files when reporting issues.
