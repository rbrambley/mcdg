MCDG Test Pack Release 2026-06-17-r1
=====================================

Release Contents:
-----------------
- 2026-06-17-r1-client.zip - Client test pack
- 2026-06-17-r1-server.zip - Server test pack
- MCDG-Test-Resources.zip - Resource pack for testing
- MANUAL-TEST-NOTES.md - Manual testing notes and regression snapshots
- MULTIPLAYER-TEST-SHEET.md - Comprehensive multiplayer test cases
- MULTIPLAYER-TEST-SHEET-QUICKPASS.md - Quick pass multiplayer verification
- MULTI-PHASE-IMPLEMENTATION-PLAN.md - Current implementation roadmap

Build Information:
------------------
- Release ID: 2026-06-17-r1
- Generated: 2026-06-17 21:45:00 -04:00
- Git Commit: e1b58562caeb69a29d1652ae770443e65a683fec
- Minecraft: 1.20.6
- Fabric Loader: 0.16.10
- Java: 21

Key Changes from Previous Release (2026-06-09-r1):
--------------------------------------------------
- Performance optimizations:
  * Minimap cache throttle increased (350ms → 750ms)
  * Autosave interval increased (20 → 100 ticks)
  * Particle trail duration reduced (3s → 2s)
  * Server tick timing metrics added for performance monitoring
- Phase 1.2 marked as completed (33% overall progress)
- Updated tester documentation with latest build information

Mod Versions:
--------------
- fabric-api-0.100.8+1.20.6.jar
- ferritecore-6.1.1-fabric.jar
- lithium-fabric-mc1.20.6-0.12.5.jar
- mcdg-0.1.0.jar (build commit e1b5856)

Installation:
-------------
1. Extract the appropriate zip (client or server) to your Minecraft instance
2. For client: Extract to .minecraft directory
3. For server: Extract to server directory
4. Install the resource pack (MCDG-Test-Resources.zip) in-game
5. Review test documentation for testing procedures

Verification:
-------------
Client SHA256: a6d35a21f6bd033fcbbcf511cab775e34c350cebc8d43d57a1ecfec7e7f7967e
Server SHA256: 08f2e385d177c8398025c6e85a057f8bb3c95d715f478372b5b91ad0a8407194

Testing Focus:
--------------
- Client frame rate stability with minimap active
- Server freeze elimination during autosave operations
- Multiplayer reliability and state consistency
- Performance monitoring with new tick timing metrics

Report Issues:
--------------
Use the BUG-REPORT-TEMPLATE.txt included in client/server packages
Include test sheet results and log files when reporting issues
