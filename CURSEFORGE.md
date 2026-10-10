# Inventory Backups

Automatic backups of player inventories and ender chests for Minecraft servers, with a clickable chat browser,
read-only previews and one-click restore for admins.

Lost items to a bug, a lag death or a griefer? Inventory Backups keeps a history of every player's inventory and ender
chest, and lets operators look through it, preview any moment and put it back.

## Features

- **Automatic backups.** The inventory (main slots, armor, offhand and [Curios](https://www.curseforge.com/minecraft/mc-mods/curios)
  slots) is saved every 10 minutes, when a player dies (before the items drop), joins and leaves the server.
  Periodic saves are skipped when nothing has changed.
- **Ender chest backups.** The same for ender chests, optionally also every time a player opens one.
- **Clickable chat browser.** `/inventory list <player>` shows the backups newest first, 10 per page, each with buttons
  to preview, restore or copy it. Quick filters for today, yesterday, this month and deaths; any date (`2025-03`,
  `2025-03-15`) or backup type (`death`, `join`, `quit`, `open`) works as a filter too.
- **Preview.** Open any backup in a read-only chest screen.
- **Restore or copy.** Put a backup back on the player, or copy its items into your own inventory to inspect them.
- **Live editing.** `/inventory player <player>` and `/enderchest player <player>` open a player's current inventory or
  ender chest; changes apply immediately.
- **Offline players.** Browse, preview and copy backups of players who are offline.
- **Tab completion** for player names and backup names.
- **Automatic cleanup.** Backups older than 7 days are deleted.
- **Server side only.** Players do not need the mod. Works in singleplayer too.

## Commands

All commands need operator permission (level 2). `/enderchest` has the same sub-commands as `/inventory`.

- `/inventory list <player> [filter] [page]` - clickable list of backups
- `/inventory view <player> <backup>` - read-only preview
- `/inventory set <player> <backup>` - restore a backup (player online)
- `/inventory copy <player> <backup>` - copy the items into your inventory
- `/inventory player <player>` - edit the player's current inventory (player online)

## Configuration

`config/inventory/InventoryBackups.toml`, also in game (Mods -> Inventory Backups -> Config): every save trigger can be
switched on or off, the save interval (default 10 minutes) and how many days backups are kept (default 7).

## Compatibility

[![Curios API](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/compat/curios.png)](https://www.curseforge.com/minecraft/mc-mods/curios) ✅ **[Curios API](https://www.curseforge.com/minecraft/mc-mods/curios)** (optional) - curio slots are backed up, previewed and restored with the inventory.

## Requirements

[NeoForge](https://neoforged.net) for Minecraft 1.21.1, on the server.

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
