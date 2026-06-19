MCDG Test Pack Release 2026-06-18-r2
=====================================

Release Contents:
-----------------
- 2026-06-18-r2-client.zip - Client test pack (resource pack built-in and auto-enabled)
- 2026-06-18-r2-server.zip - Server test pack
- 2026-06-18-r2-all-in-one.zip - Complete package with everything
- MCDG-Test-Resources.zip - Standalone resource pack for manual installation
- MULTIPLAYER-TEST-SHEET.md - Comprehensive multiplayer test cases
- MULTIPLAYER-TEST-SHEET-QUICKPASS.md - Quick pass multiplayer verification
- MANUAL-TEST-NOTES.md - Manual testing notes and regression snapshots
- MULTI-PHASE-IMPLEMENTATION-PLAN.md - Current implementation roadmap

Build Information:
------------------
- Release ID: 2026-06-18-r2
- Generated: 2026-06-18 20:15:00 -04:00
- Git Commit: 68fbce5fb69a90e69f90786a0e990489c20197ba
- Minecraft: 1.20.6
- Fabric Loader: 0.16.10
- Java: 21

Key Changes from Previous Release (2026-06-18-r1):
--------------------------------------------------
- FIX: Power bar now appears correctly on dedicated server multiplayer
- PERF: Minimap terrain scan cached per player (eliminates ~85k heightmap lookups/tick)
- PERF: Minimap sync throttled from 20 Hz to 4 Hz (every 5 ticks)
- PERF: Player session save is now async (Save & Leave Round no longer blocks tick)
- PERF: Round session autosave interval increased from 10s to 30s
- FEAT: productionMode config flag (default true) disables debug logging in production

Mod Versions:
--------------
- fabric-api-0.100.8+1.20.6.jar
- ferritecore-6.1.1-fabric.jar
- lithium-fabric-mc1.20.6-0.12.5.jar
- mcdg-0.1.0.jar (build commit 68fbce5)

Resource Pack:
--------------
- MCDG-Test-Resources is built into the mod jar and auto-enabled by default.
- A standalone zip is included in the all-in-one pack for manual reinstall if needed.

Installation:
-------------
Option 1 - All-in-One (Recommended):
1. Extract 2026-06-18-r2-all-in-one.zip
2. Extract 2026-06-18-r2-client.zip to your Minecraft instance directory.
3. Extract 2026-06-18-r2-server.zip to your server directory.
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
1. Upload 2026-06-18-r2-server.zip to your Oracle Cloud instance.
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
Client SHA256: 9E49A893C79EDB7B0947650E0E7EDEC4AAC1E38153E312E6D98DA15505ABE864
Server SHA256: 625A3189CF3BA167196C4C273D47312441E5C0CA1EE13339B2377BB018459E1E
All-in-One SHA256: 58BAD12A1574D1C9F98DBACDB9189FA6396E2EE802D4D2D2105090E56055E433

Testing Focus:
--------------
- Dedicated server multiplayer power bar visibility
- Minimap performance under 4+ player load
- Save & Leave Round responsiveness (no tick freeze)
- General multiplayer state consistency and performance

Report Issues:
--------------
Use the BUG-REPORT-TEMPLATE.txt included in client/server packages.
Include test sheet results and log files when reporting issues.
