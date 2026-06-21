MCDG Test Pack Release 2026-06-21-r1
=====================================

Release Contents:
-----------------
- 2026-06-21-r1-client.zip - Client test pack (resource pack built-in and auto-enabled)
- 2026-06-21-r1-server.zip - Server test pack
- 2026-06-21-r1-all-in-one.zip - Complete package with everything
- MCDG-Test-Resources.zip - Standalone resource pack for manual installation
- MULTIPLAYER-TEST-SHEET.md - Comprehensive multiplayer test cases
- MULTIPLAYER-TEST-SHEET-QUICKPASS.md - Quick pass multiplayer verification
- MANUAL-TEST-NOTES.md - Manual testing notes and regression snapshots
- MULTI-PHASE-IMPLEMENTATION-PLAN.md - Current implementation roadmap

Build Information:
------------------
- Release ID: 2026-06-21-r1
- Generated: 2026-06-21 11:59:43 -04:00
- Git Commit: 06ae2db5c91911d1988aea642de72e59b151b151
- Minecraft: 1.20.6
- Fabric Loader: 0.19.3
- Java: 21

Key Changes from Previous Release (2026-06-19-r1):
--------------------------------------------------
- feat: bot multiplayer round support and round completion fixes
- feat: add ruleset visibility and course management UX improvements
- fix: decouple HUD fade from cinematic and ensure instant trail feedback
- feat: improve HUD scaling and cinematic fade synchronization
- feat: implement real-time progressive trail rendering
- feat: enhance HUD scaling and Xaero's Minimap integration
- feat: add GUI scaling support for HUD elements
- fix: add hazard grid computation for incremental course placement

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
1. Extract 2026-06-21-r1-all-in-one.zip
2. Extract 2026-06-21-r1-client.zip to your Minecraft instance directory.
3. Extract 2026-06-21-r1-server.zip to your server directory.
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
1. Upload 2026-06-21-r1-server.zip to your Oracle Cloud instance.
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
- Multiplayer round consistency with bot players
- Ruleset visibility and course management UX
- HUD scaling and cinematic fade behavior
- Real-time trail rendering performance and visual quality
- Xaero's Minimap positioning with MCDG HUD elements
- General multiplayer state consistency and performance

Report Issues:
--------------
Use the BUG-REPORT-TEMPLATE.txt included in client/server packages.
Include test sheet results and log files when reporting issues.
