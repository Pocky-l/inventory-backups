# Inventory Backups

Every inventory and ender chest change of every player, with item search, transfer trails and an optional timeline
screen for admins.

Lost items to a bug, a lag death or a thief? Inventory Backups records every change of every player's inventory and
ender chest, lets operators scrub through that history, find any item across all players and see how it moved
from player to player, and put any moment back with one click.

## Features

- **Every change, recorded cheaply.** Main inventory, armor, offhand, [Curios](https://www.curseforge.com/minecraft/mc-mods/curios)
  slots and the ender chest. Changes are detected through the slot sync vanilla already runs, so nothing is serialized
  per tick; bursts are merged into one entry once the inventory is still for 2.5 seconds (at most 30 seconds later).
  Measured: about 0.005 ms of main-thread time per player and tick, about 100 bytes per recorded change.
- **Timeline markers with context.** Deaths (with cause and position, recorded before the items drop), joins, quits,
  container sessions (which chest, where), admin restores and live edits (which admin), snapshots.
- **Compact, crash-safe storage.** Binary day files per player with keyframes, slot-level deltas, an item pool that
  stores identical stacks once and compression; written on a background thread, append-only, CRC-checked. A crash
  loses at most the last few seconds.
- **Retention that thins instead of cutting.** Every change for 24 hours, then one state per hour for 7 days, one per
  day for 30 days; deaths and admin actions for 90 days; the newest state of a player is always kept. Disk budgets per
  player and in total delete the oldest days first.
- **Item search and transfer trail.** Who has an item now, who had it and when, by item id, custom name, enchantment or
  the fingerprint of one specific stack (it survives wear). Transfers are linked exactly through drops, death loot and
  shared containers; timing-based links are marked as probable. "Who took the diamonds from my chest?" is
  `/invsearch container <pos>`.
- **Admin screen (optional client mod).** A player browser, a timeline scrubber with markers, zoom, playback, A/B
  pins and death jumps, the inventory at any moment with changes highlighted, tooltips that tell when and where an
  item got into its slot, and confirmed actions: restore all or selected slots, give yourself a copy, live edit,
  undo, snapshot, teleport. The item search with holders and the transfer trail is one tab away.
- **Works without the client mod.** Clickable chat commands for everything, in English for vanilla clients and in the
  admin's language with the mod (English and Russian included).
- **Permissions.** Separate `inventorybackups.view`, `.restore`, `.edit`, `.search` and `.ui` nodes through NeoForge's
  permission API; operator level 2 without a permission mod.
- **Imports 2.x backups** once on the first start and keeps reading the 2.x config file.

![Admin screen mockup](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/screenshots/mockup-timeline.png)

## Controls

| Key | Action |
|---|---|
| `Ctrl+I` (configurable) | Open the admin screen |
| `←` `→` / `Shift` | Previous / next entry, 10 at a time |
| `Home` `End` | First / newest entry |
| `Space` | Play the timeline |
| `D` | Jump to the previous death |
| `A` `B` | Pin the entry as A or B; `C` compares with previous, A or B |
| `Ctrl+F` | Item search |
| `Ctrl+A` | Select all shown slots |

## Commands

Each command needs its permission node (default: operator level 2). `/enderchest` has the same sub-commands as
`/inventory` and works on the ender chest part of the timeline.

| Command | What it does |
|---|---|
| `/inventory list <player> [filter] [page]` | Clickable timeline; filters: `2025-03`, `2025-03-15`, `death`, `restore`, `container`, ... |
| `/inventory view <player> <time>` | Read-only preview of a state |
| `/inventory set <player> <time>` | Restore a state (the previous one stays recorded) |
| `/inventory undo <player>` | Undo the last restore |
| `/inventory copy <player> <time>` | Copy the items of a state into your inventory |
| `/inventory player <player>` | Edit the live inventory, recorded as one edit |
| `/inventory snapshot <player>` | Record the current state now |
| `/inventory stats` | Main-thread cost, bytes per change, entries written |
| `/inventory ui` | Open the admin screen (client mod) |
| `/invsearch <item> [player\|*] [since]` | Holders and transfers of an item, `since` like `2h`, `3d` |
| `/invsearch name <text>` / `ench <id>` / `trace <fingerprint>` | Search by custom name, enchantment or one stack |
| `/invsearch at <time> <item>` | Who held the item at that time |
| `/invsearch container <pos> [since]` | Who put items into a container and who took them out |

`<time>` is what the chat buttons fill in, `latest`, or a `yyyy-MM-dd-HH-mm-ss` timestamp (2.x backup names work).

## Configuration

`config/inventory/InventoryBackups.toml` (also in game: Mods -> Inventory Backups -> Config). The 2.x keys stay valid.

| Option | Default | Meaning |
|---|---|---|
| `general.tickSaveEnabled` | `true` | Record inventory changes |
| `general.deadSaveEnabled` / `joinSaveEnabled` / `quitSaveEnabled` | `true` | Record deaths, mark joins and quits |
| `general.retentionDays` | `30` | Days to keep one state per day |
| `enderchest.enderChestEnabled` | `true` | Back up ender chests, `/enderchest` |
| `enderchest.enderChestTickSaveEnabled` | `true` | Record ender chest changes |
| `capture.settleSeconds` / `maxDelaySeconds` | `2.5` / `30` | Merge bursts of changes |
| `capture.scanSeconds` | `5` | Full slot comparison for changes no event reports |
| `capture.containerSessions` | `true` | Record puts and takes at containers |
| `storage.directory` | empty | Empty: `<world>/inventorybackups`; otherwise a path relative to the server folder |
| `storage.keyframeInterval` | `64` | Entries between full states |
| `retention.keepAllHours` / `hourlyDays` / `importantDays` | `24` / `7` / `90` | Thinning by age |
| `retention.playerBudgetMb` / `totalBudgetMb` | `64` / `4096` | Disk budgets, 0 = no limit |
| `migration.legacyBackups` | `ARCHIVE` | 2.x JSON backups after import: `KEEP`, `ARCHIVE`, `DELETE` |
| `permissions.permissionLevel` | `2` | Operator level when no permission mod decides |
| `search.probableLinkSeconds` / `probableLinkDistance` | `15` / `24` | Window for probable transfers |
| `search.maxResults` | `500` | Limit per search or request |

`preservationPeriod` and the ender chest death/join/quit/open options are deprecated and ignored. The client config
(`inventorybackups-client.toml`) sets playback speed, default zoom, 24 hour clock, highlighting and tooltip history.

## Installation

1. Install [NeoForge](https://neoforged.net) 21.1 for Minecraft 1.21.1 on the server and put the mod into `mods`.
2. Optional: admins who want the timeline screen put the same jar into their client's `mods` folder. Other players
   do not need it.

## Compatibility

[![Curios API](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/compat/curios.png)](https://www.curseforge.com/minecraft/mc-mods/curios) ✅ **[Curios API](https://www.curseforge.com/minecraft/mc-mods/curios)** (optional) - curio slots are recorded, shown on their own tab and restored with the inventory.

[![LuckPerms](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/compat/luckperms.png)](https://luckperms.net) ✅ **[LuckPerms](https://luckperms.net)** (optional) - grant `inventorybackups.view`, `.restore`, `.edit`, `.search` and `.ui` per group instead of operator status.

## Credits

Made by **Pocky**. Source code: [GitHub](https://github.com/Pocky-l/inventory-backups)

Minecraft 1.20.1 Forge and Fabric versions by DjJewl. Special thanks to PiglinMine.com.

<!-- more-mods:start -->
## More mods by Pocky

[![Turbo for Distant Horizons](https://raw.githubusercontent.com/Pocky-l/dhturbo/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/turbo-for-distant-horizons)

**[Turbo for Distant Horizons](https://www.curseforge.com/minecraft/mc-mods/turbo-for-distant-horizons)** - Distant Horizons addon: generates distant terrain from the world noise many times faster, with real trees nearby. ([source](https://github.com/Pocky-l/dhturbo))

[![Holy Staff](https://raw.githubusercontent.com/Pocky-l/holy-staff/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/holy-staff)

**[Holy Staff](https://www.curseforge.com/minecraft/mc-mods/holy-staff)** - A holy staff with three healing skills, aim previews and flying heal numbers. ([source](https://github.com/Pocky-l/holy-staff))

[![Lumen Rigs](https://raw.githubusercontent.com/Pocky-l/lumen-rigs/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/lumen-rigs)

**[Lumen Rigs](https://www.curseforge.com/minecraft/mc-mods/lumen-rigs)** - Aimable spotlights, floodlights, searchlights and soft panels with colored light and visible beams. ([source](https://github.com/Pocky-l/lumen-rigs))

[![Neon Glowsticks](https://raw.githubusercontent.com/Pocky-l/neon-glowsticks/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks)

**[Neon Glowsticks](https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks)** - Throwable glowsticks that bounce, roll and light up the dark with colored light. ([source](https://github.com/Pocky-l/neon-glowsticks))

[![Petrichor: Rain & Storms](https://raw.githubusercontent.com/Pocky-l/petrichor/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms)

**[Petrichor: Rain & Storms](https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms)** - Realistic rain and storms: rain types, puddles, runoff and drips, branching lightning with delayed thunder. ([source](https://github.com/Pocky-l/petrichor))

[![Rustling Leaves](https://raw.githubusercontent.com/Pocky-l/rustling-leaves/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/rustling-leaves)

**[Rustling Leaves](https://www.curseforge.com/minecraft/mc-mods/rustling-leaves)** - Physically simulated leaves: falling leaves, leaf piles you can wade through, rake and blow away, gusts, whirlwinds and leaf tools. ([source](https://github.com/Pocky-l/rustling-leaves))

[![Rancher's Vacpack](https://raw.githubusercontent.com/Pocky-l/ranchers-vacpack/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/ranchers-vacpack)

**[Rancher's Vacpack](https://www.curseforge.com/minecraft/mc-mods/ranchers-vacpack)** - A Slime Rancher inspired vacuum gun: suck up items and small mobs, store them in a tank and shoot them back out. ([source](https://github.com/Pocky-l/ranchers-vacpack))

<!-- more-mods:end -->
