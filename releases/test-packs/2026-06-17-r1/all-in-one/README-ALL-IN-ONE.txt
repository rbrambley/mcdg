MCDG All-in-One Test Pack 2026-06-17-r1
========================================

This package contains everything needed for testing MCDG in a single download.

Package Contents:
-----------------
- 2026-06-17-r1-client.zip - Complete client test pack (includes resource pack)
- 2026-06-17-r1-server.zip - Complete server test pack
- MCDG-Test-Resources.zip - Standalone resource pack (for manual installation)
- README-RELEASE.txt - Detailed release notes
- MANUAL-TEST-NOTES.md - Manual testing notes and regression snapshots
- MULTIPLAYER-TEST-SHEET.md - Comprehensive multiplayer test cases
- MULTIPLAYER-TEST-SHEET-QUICKPASS.md - Quick pass multiplayer verification

Quick Start:
------------
1. Extract 2026-06-17-r1-client.zip to your .minecraft directory
2. Extract 2026-06-17-r1-server.zip to your server directory
3. Launch Minecraft - the resource pack is already included in the client pack
4. Review test documentation for testing procedures

What's Included:
---------------
- Performance optimizations (minimap cache, autosave, particle trails)
- Server tick timing metrics for monitoring
- Phase 1.2 completion (33% overall progress)
- Updated mcdg-0.1.0.jar with commit e1b5856
- Test resource pack for improved gameplay readability
- Complete test documentation suite

Installation:
-------------
Client:
1. Backup your current Minecraft installation
2. Extract 2026-06-17-r1-client.zip to .minecraft
3. Overwrite existing files when prompted
4. Launch Minecraft 1.20.6 with Fabric
5. Enable the MCDG-Test-Resources resource pack in Options > Resource Packs

Server:
1. Backup your current server installation
2. Extract 2026-06-17-r1-server.zip to your server directory
3. Overwrite existing files when prompted
4. Start the server with Java 21
5. Configure server properties as needed

Build Information:
------------------
- Release ID: 2026-06-17-r1
- Generated: 2026-06-17 21:45:00 -04:00
- Git Commit: e1b58562caeb69a29d1652ae770443e65a683fec
- Minecraft: 1.20.6
- Fabric Loader: 0.16.10
- Java: 21

Testing Focus:
--------------
- Client frame rate stability with minimap active
- Server freeze elimination during autosave operations
- Multiplayer reliability and state consistency
- Performance monitoring with new tick timing metrics

Report Issues:
--------------
Use BUG-REPORT-TEMPLATE.txt included in client/server packages
Include test sheet results and log files when reporting issues
