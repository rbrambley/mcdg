# MCDG Multiplayer Tester User Guide

**Release:** 2026-06-18-r1  
**Minecraft:** 1.20.6  
**Fabric Loader:** 0.16.10  
**Java:** 21

---

## Table of Contents

1. [Install Summary](#install-summary)
2. [Resource Pack](#resource-pack)
3. [Opening the Menu](#opening-the-menu)
4. [Keybinds](#keybinds)
5. [Admin Tester Flow](#admin-tester-flow)
6. [Player Tester Flow](#player-tester-flow)
7. [Rules / Surface Preset](#rules--surface-preset)
8. [Advanced Commands](#advanced-commands)
9. [Server Setup (Oracle Cloud)](#server-setup-oracle-cloud)
10. [Troubleshooting](#troubleshooting)
11. [Bug Reports](#bug-reports)

---

## Install Summary

- **Client:** Merge pack files into your test client instance directory.
- **Server host:** Merge server pack files into the server folder while the server is stopped.
- **All testers must use the same release ID and matching mod files.**

---

## Resource Pack

MCDG-Test-Resources is **built into the mod jar** and is **auto-enabled by default**.

- You do **not** need to manually enable it in Options -> Resource Packs.
- If you ever need to reinstall it manually, use the standalone `MCDG-Test-Resources.zip` in the resourcepacks folder.

---

## Opening the Menu

- Press **G** (default) to open the MCDG menu from anywhere in-game.
- Or type `/mcdg` in chat (a clickable link is shown on join).
- All actions are available via the menu — no need to memorize commands.
- The keybind can be rebound in **Options -> Controls -> MCDG**.

---

## Keybinds

All keybinds are rebindable in **Options -> Controls -> MCDG**.

| Key | Action |
|-----|--------|
| G | Open MCDG menu |
| M | Add waypoint at current position (opens name/color screen) |
| N | Remove nearest waypoint |
| L | Toggle waypoint labels |
| +/- | Minimap size up/down |

---

## Admin Tester Flow

1. Join the world/server and press **G** to open the menu.
2. **Build a course:**
   - Menu -> **Auto Build Course** -> type a name and press Enter.
   - (or: Menu -> **Manual Build Course** for seed-based generation)
3. **Start a round:**
   - Menu -> Admin -> **Start Round**
4. **Other players join:**
   - Menu -> **Join Round**
   - (or admin uses Menu -> Admin -> **Join Round** for all)
5. **During play**, if a player desyncs from the lie marker:
   - Menu -> **Go to Lie** (or `/mcdg gotolie`)
6. **View scores** at any time:
   - Menu -> **Leaderboard**
7. **End and clean up** after the test cycle:
   - Menu -> **End Round** -> Menu -> Admin -> **Cleanup Course**

---

## Player Tester Flow

1. Join the world/server and press **G** to open the menu.
2. Join an active round: Menu -> **Join Round**.
3. Play holes normally — throw your disc, walk to it, repeat.
4. Use Menu -> **Go to Lie** if you need to teleport to your lie.
5. Save progress if leaving early: Menu -> **Save & Leave Round**.

---

## Rules / Surface Preset

- Menu -> **Rules** to view and change ruleset (Casual / Strict).
- Strict surface presets:
  - **Fast** — forgiving
  - **Balanced** — default
  - **Tournament** — hardest

---

## Advanced Commands

Some commands are intentionally hidden unless advanced mode is enabled.

**Enable advanced commands by setting:**
- Environment variable: `MCDG_SHOW_ADVANCED_COMMANDS=true`
- Or JVM property: `-Dmcdg.showAdvancedCommands=true`

**Typical advanced commands:**
- `practicecourse`
- `resumecourse`
- `usecourse`
- `prunecourses`
- `gotocourse`
- `roundstatus`
- `validateplacement`
- `buildcamp`
- `autotests`

---

## Server Setup (Oracle Cloud)

For a self-hosted Oracle Cloud server:

1. Upload `2026-06-18-r1-server.zip` to your Oracle Cloud instance.
2. Stop the running Minecraft server.
3. Back up the current server directory.
4. Extract the server pack into the server directory.
5. Start the server and verify mod loading in the console log.
6. Distribute the client pack zip to testers.

For a full walkthrough, see [SERVER-SETUP-GUIDE.md](SERVER-SETUP-GUIDE.md).

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Release ID mismatch | Confirm `VERSION.txt` matches across all participants. |
| Fabric / Java version mismatch | Verify Fabric Loader `0.16.10` and Java `21`. |
| Resource pack visuals differ | Ensure `MCDG-Test-Resources` is loaded. It is auto-enabled, but check **Options -> Resource Packs** if needed. |
| Command permission fails | Ensure OP level >= 2 on dedicated server. |
| G keybind conflict | Rebind in **Options -> Controls -> MCDG**. |

---

## Bug Reports

Use the **BUG-REPORT-TEMPLATE.txt** included in client/server packages.

Include:
- Exact steps (menu path or command)
- Course seed and hole number
- Expected vs actual behavior
- Log snippets and screenshots where possible

---

*Happy testing!*
