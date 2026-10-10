<p align="center">
  <img src="src/main/resources/logo.png" alt="Inventory Backups" width="160">
</p>

<h1 align="center">Inventory Backups</h1>

<p align="center">
  Automatic backups of player inventories and ender chests for Minecraft servers, with a clickable chat browser,
  read-only previews and one-click restore for admins.
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/inventory-backups"><img alt="CurseForge downloads" src="https://img.shields.io/curseforge/dt/836385?logo=curseforge&label=CurseForge&color=F16436"></a>
  <img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A">
  <a href="https://neoforged.net"><img alt="NeoForge" src="https://img.shields.io/badge/Loader-NeoForge-F16436"></a>
  <img alt="Server side" src="https://img.shields.io/badge/Side-Server-7A5CFA">
  <img alt="License MIT" src="https://img.shields.io/badge/License-MIT-blue">
</p>

Lost items to a bug, a lag death or a griefer? Inventory Backups keeps a history of every player's inventory and ender
chest, and lets operators look through it, preview any moment and put it back.

## Features

- **Automatic backups.** The inventory (main slots, armor, offhand and [Curios](https://www.curseforge.com/minecraft/mc-mods/curios)
  slots) is saved every 10 minutes, when a player dies (before the items drop), joins and leaves the server.
  Periodic saves are skipped when nothing has changed, and empty inventories are never saved.
- **Ender chest backups.** The same for ender chests, optionally also every time a player opens one.
- **Clickable chat browser.** `/inventory list <player>` shows the backups newest first, 10 per page, each with buttons
  to preview, restore or copy it. Quick filters for today, yesterday, this month and deaths; any date prefix
  (`2025-03`, `2025-03-15`) or backup type (`death`, `join`, `quit`, `open`) works as a filter too.
- **Preview.** Open any backup in a read-only chest screen: main inventory, then armor, offhand and curios.
- **Restore or copy.** Put a backup back on the player (online), or copy its items into your own inventory to inspect
  them. Restores are written to the server log.
- **Live editing.** `/inventory player <player>` and `/enderchest player <player>` open a player's current inventory or
  ender chest; changes apply immediately.
- **Offline players.** Browse, preview and copy backups of players who are offline.
- **Tab completion** for player names and backup names.
- **Automatic cleanup.** Backups older than 7 days are deleted (on server start and every hour).
- **Server side only.** Players do not need the mod. Admins without it see the chat in English; with it, in their own
  language (English and Russian included). Works in singleplayer too.

## Commands

All commands need operator permission (level 2). `/enderchest` has the same sub-commands as `/inventory`.

| Command | What it does |
|---|---|
| `/inventory` | Help |
| `/inventory list <player> [filter] [page]` | Clickable list of backups |
| `/inventory view <player> <backup>` | Read-only preview of a backup |
| `/inventory set <player> <backup>` | Restore a backup to an online player |
| `/inventory copy <player> <backup>` | Copy the items of a backup into your inventory |
| `/inventory player <player>` | Open the player's current inventory for editing |

`list`, `set` also work from the server console. Filter examples: `2025-03` (March 2025), `2025-03-15` (one day),
`death` (death backups), `"" 2` (all backups, page 2).

## Crafting

The mod adds no blocks or items.

## Configuration

`config/inventory/InventoryBackups.toml` (also in game: Mods -> Inventory Backups -> Config):

| Option | Default | Meaning |
|---|---|---|
| `general.tickSaveEnabled` | `true` | Periodic inventory saves |
| `general.preservationPeriod` | `600` | Seconds between periodic saves (below 1 = off) |
| `general.deadSaveEnabled` | `true` | Save on death |
| `general.joinSaveEnabled` / `quitSaveEnabled` | `true` | Save on join / quit |
| `general.retentionDays` | `7` | Days to keep backups (1-365) |
| `enderchest.enderChestEnabled` | `true` | Ender chest backups and `/enderchest` |
| `enderchest.enderChestTickSaveEnabled` | `true` | Periodic ender chest saves |
| `enderchest.enderChestDeadSaveEnabled` | `true` | Save the ender chest on death |
| `enderchest.enderChestJoinSaveEnabled` / `enderChestQuitSaveEnabled` | `true` | Save the ender chest on join / quit |
| `enderchest.enderChestOpenSaveEnabled` | `false` | Save the ender chest every time it is opened |

Backups are JSON files in the server directory: `InventoryLog/inventory/<player uuid>/` and
`InventoryLog/enderchest/<player uuid>/`, named `yyyy-MM-dd-HH-mm-ss[-death|-join|-quit|-open].json`.

## Compatibility

[![Curios API](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/compat/curios.png)](https://www.curseforge.com/minecraft/mc-mods/curios) ✅ **[Curios API](https://www.curseforge.com/minecraft/mc-mods/curios)** (optional) - curio slots are backed up, previewed and restored with the inventory.

## Installation

1. Install [NeoForge](https://neoforged.net) for Minecraft 1.21.1 on the server.
2. Put this mod into the server's `mods` folder. Players do not need it.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/`.

## Credits

- Author: **Pocky**.
- DjJewl - the Minecraft 1.20.1 Forge and Fabric versions.
- Special thanks to PiglinMine.com.

<!-- more-mods:start -->
## More mods by Pocky

<table>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/turbo-for-distant-horizons"><img src="https://raw.githubusercontent.com/Pocky-l/dhturbo/main/docs/icon.png" width="96" alt="Turbo for Distant Horizons"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/turbo-for-distant-horizons"><b>Turbo for Distant Horizons</b></a><br>
      Distant Horizons addon: generates distant terrain from the world noise many times faster, with real trees nearby.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/turbo-for-distant-horizons"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1734835?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/dhturbo"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/holy-staff"><img src="https://raw.githubusercontent.com/Pocky-l/holy-staff/main/docs/icon.png" width="96" alt="Holy Staff"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/holy-staff"><b>Holy Staff</b></a><br>
      A holy staff with three healing skills, aim previews and flying heal numbers.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/holy-staff"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1725465?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/holy-staff"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/lumen-rigs"><img src="https://raw.githubusercontent.com/Pocky-l/lumen-rigs/main/docs/icon.png" width="96" alt="Lumen Rigs"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/lumen-rigs"><b>Lumen Rigs</b></a><br>
      Aimable spotlights, floodlights, searchlights and soft panels with colored light and visible beams.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/lumen-rigs"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1727739?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/lumen-rigs"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks"><img src="https://raw.githubusercontent.com/Pocky-l/neon-glowsticks/main/docs/icon.png" width="96" alt="Neon Glowsticks"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks"><b>Neon Glowsticks</b></a><br>
      Throwable glowsticks that bounce, roll and light up the dark with colored light.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1727688?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/neon-glowsticks"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms"><img src="https://raw.githubusercontent.com/Pocky-l/petrichor/main/docs/icon.png" width="96" alt="Petrichor: Rain & Storms"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms"><b>Petrichor: Rain & Storms</b></a><br>
      Realistic rain and storms: rain types, puddles, runoff and drips, branching lightning with delayed thunder.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1729574?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/petrichor"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/rustling-leaves"><img src="https://raw.githubusercontent.com/Pocky-l/rustling-leaves/main/docs/icon.png" width="96" alt="Rustling Leaves"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/rustling-leaves"><b>Rustling Leaves</b></a><br>
      Physically simulated leaves: falling leaves, leaf piles you can wade through, rake and blow away, gusts, whirlwinds and leaf tools.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/rustling-leaves"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1729578?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/rustling-leaves"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
  <tr>
    <td align="center" width="112"><a href="https://www.curseforge.com/minecraft/mc-mods/ranchers-vacpack"><img src="https://raw.githubusercontent.com/Pocky-l/ranchers-vacpack/main/docs/icon.png" width="96" alt="Rancher's Vacpack"></a></td>
    <td>
      <a href="https://www.curseforge.com/minecraft/mc-mods/ranchers-vacpack"><b>Rancher's Vacpack</b></a><br>
      A Slime Rancher inspired vacuum gun: suck up items and small mobs, store them in a tank and shoot them back out.<br>
      <a href="https://www.curseforge.com/minecraft/mc-mods/ranchers-vacpack"><img alt="CurseForge" src="https://img.shields.io/curseforge/dt/1725381?logo=curseforge&label=CurseForge&color=F16436"></a>
      <a href="https://github.com/Pocky-l/ranchers-vacpack"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-source-181717?logo=github"></a>
    </td>
  </tr>
</table>
<!-- more-mods:end -->

## License

[MIT](LICENSE)
