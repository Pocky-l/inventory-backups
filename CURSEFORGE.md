# Inventory Backups

**Every inventory change of every player, recorded** - roll back any moment with one click, find any item and see who took it, online or offline.

![The admin screen: timeline, inventory at the selected moment and what changed](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/screenshots/mockup-timeline.png)

📦 **Requires** [NeoForge](https://neoforged.net) 1.21.1 · on the server; admins who want the timeline screen also put
it on their client

## ✨ Features

- 📼 **Every change recorded** - inventory, armor, offhand, ender chest and mod slots, merged into one entry per
  burst; about 100 bytes per change and 0.005 ms per player and tick.
- 🕒 **A timeline with context** - deaths with cause and place (recorded before the items drop), joins, quits,
  chests used, restores and edits by admins.
- ↩️ **Restore in one click** - a whole moment or only some slots, with undo; or give yourself a copy and leave the
  player's items alone.
- 🔍 **Find any item** - who has it now, who had it and when, by id, name, enchantment or one exact stack, with its
  trail through drops, death loot and chests; `/invsearch container` shows who emptied a chest.
- 🖥️ **Admin screen** - scrub the timeline, play it back, compare two moments, see what changed and where every item
  came from (`Ctrl+I`, needs the mod on the admin's client).
- 💤 **Offline players too** - restore and edit players who are not online; the change goes into their save file.
- 🎒 **Mod slots** - Curios, Accessories, Cosmetic Armor, Traveler's Backpack, Tool Belt and Backpacked are backed up
  and restored with the rest.
- 🗜️ **Small and safe storage** - compact day files written in the background, thinned with age instead of cut off,
  with disk budgets; a crash loses seconds at most.
- 🔐 **Permissions** - separate view, restore, edit and search nodes for LuckPerms; operators without one. Works for
  vanilla clients through clickable chat.

## 🎮 Controls

`Ctrl+I` opens the admin screen. Without the client mod: `/inventory list <player>` for a clickable timeline,
`/inventory set|undo|copy|player`, `/enderchest ...` the same for the ender chest, `/invsearch <item>` for the item
search. All commands are listed in the [README](https://github.com/Pocky-l/inventory-backups#commands).

## ⚙️ Configuration

`config/inventory/InventoryBackups.toml` or *Mods > Inventory Backups > Config*: what is recorded, how long it is kept,
disk budgets, permission level. 2.x configs and backups are taken over on the first start.

## 🤝 Compatibility

[![Curios API](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/compat/curios.png)](https://www.curseforge.com/minecraft/mc-mods/curios) ✅ **[Curios API](https://www.curseforge.com/minecraft/mc-mods/curios)** (optional) - every curio slot, cosmetic slots included, is recorded, shown on the Mod slots tab, restored with the inventory and editable live.

[![Accessories](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/compat/accessories.png)](https://www.curseforge.com/minecraft/mc-mods/accessories) ✅ **[Accessories](https://www.curseforge.com/minecraft/mc-mods/accessories)** (optional) - accessory and cosmetic slots are recorded and restored the same way; with the Curios compatibility layer each slot is recorded once.

[![Cosmetic Armor Reworked](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/compat/cosmetic-armor-reworked.png)](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked) ✅ **[Cosmetic Armor Reworked](https://www.curseforge.com/minecraft/mc-mods/cosmetic-armor-reworked)** (optional) - the cosmetic armor slots are recorded and restored.

[![Traveler's Backpack](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/compat/travelers-backpack.png)](https://www.curseforge.com/minecraft/mc-mods/travelers-backpack) ✅ **[Traveler's Backpack](https://www.curseforge.com/minecraft/mc-mods/travelers-backpack)** (optional) - the worn backpack and everything in it is recorded and restored, also when it is placed on the ground at death.

[![Tool Belt](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/compat/tool-belt.png)](https://www.curseforge.com/minecraft/mc-mods/tool-belt) ✅ **[Tool Belt](https://www.curseforge.com/minecraft/mc-mods/tool-belt)** (optional) - the belt slot and the tools on the belt are recorded and restored.

[![Backpacked](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/compat/backpacked.png)](https://www.curseforge.com/minecraft/mc-mods/backpacked) ✅ **[Backpacked](https://www.curseforge.com/minecraft/mc-mods/backpacked)** (optional) - the equipped backpacks and their contents are recorded and restored.

[![LuckPerms](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/compat/luckperms.png)](https://luckperms.net) ✅ **[LuckPerms](https://luckperms.net)** (optional) - grant `inventorybackups.view`, `.restore`, `.edit`, `.search` and `.ui` per group instead of operator status.

## 📸 Screenshots

![Restoring selected slots](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/screenshots/mockup-restore.png)

*Restoring selected slots*

![Item search with holders and the transfer trail](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/screenshots/mockup-search.png)

*Item search with holders and the transfer trail*

## 💛 Credits

Made by **Pocky** · [source code](https://github.com/Pocky-l/inventory-backups) · Minecraft 1.20.1 Forge and Fabric
versions by DjJewl · special thanks to PiglinMine.com.

<!-- more-mods:start -->
## More mods by Pocky

**🌦️ Weather & nature**

[![Hoarfrost: Snow & Blizzards](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/more-mods/hoarfrost.png)](https://www.curseforge.com/minecraft/mc-mods/hoarfrost-snow-blizzards) **[Hoarfrost: Snow & Blizzards](https://www.curseforge.com/minecraft/mc-mods/hoarfrost-snow-blizzards)** - seven kinds of snowfall, a living wind and soft snow on the land

[![Petrichor: Rain & Storms](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/more-mods/petrichor.png)](https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms) **[Petrichor: Rain & Storms](https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms)** - realistic rain, puddles, drips and branching lightning

[![Rustling Leaves](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/more-mods/rustling_leaves.png)](https://www.curseforge.com/minecraft/mc-mods/rustling-leaves) **[Rustling Leaves](https://www.curseforge.com/minecraft/mc-mods/rustling-leaves)** - falling leaves and leaf piles you can wade through, rake and blow away

**💡 Light**

[![Lumen Rigs](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/more-mods/lumen_rigs.png)](https://www.curseforge.com/minecraft/mc-mods/lumen-rigs) **[Lumen Rigs](https://www.curseforge.com/minecraft/mc-mods/lumen-rigs)** - aimable spotlights and floodlights with colored light and visible beams

[![Neon Glowsticks](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/more-mods/neon_glowsticks.png)](https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks) **[Neon Glowsticks](https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks)** - throwable glowsticks that bounce, roll and light up the dark

**🎨 Graphics & performance**

[![Turbo for Distant Horizons](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/more-mods/dhturbo.png)](https://www.curseforge.com/minecraft/mc-mods/turbo-for-distant-horizons) **[Turbo for Distant Horizons](https://www.curseforge.com/minecraft/mc-mods/turbo-for-distant-horizons)** - Distant Horizons terrain generated many times faster

[![Pocky Shaders](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/more-mods/pocky_shaders.png)](https://www.curseforge.com/minecraft/mc-mods/pocky-shaders) **[Pocky Shaders](https://www.curseforge.com/minecraft/mc-mods/pocky-shaders)** - a cosy, natural shader pack that sets itself up

**⚔️ Gear & gameplay**

[![Holy Staff](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/more-mods/holy_staff.png)](https://www.curseforge.com/minecraft/mc-mods/holy-staff) **[Holy Staff](https://www.curseforge.com/minecraft/mc-mods/holy-staff)** - a healing staff with three skills and flying heal numbers

[![Rancher's Vacpack](https://raw.githubusercontent.com/Pocky-l/inventory-backups/main/docs/more-mods/vacpack.png)](https://www.curseforge.com/minecraft/mc-mods/ranchers-vacpack) **[Rancher's Vacpack](https://www.curseforge.com/minecraft/mc-mods/ranchers-vacpack)** - a Slime Rancher vacuum gun for items and small mobs

<!-- more-mods:end -->
