# Changelog

All notable changes to this mod are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] - 2026-10-12
### Added
- Every inventory, armor, offhand, ender chest and mod slot change is recorded, merged into one entry per burst of
  changes, instead of a save every 10 minutes. Deaths, joins, quits, container sessions, restores, live edits and
  snapshots are marked on the timeline with position, death cause, container and admin.
- An admin screen for admins who also install the mod on their client (`Ctrl+I` or `/inventory ui`): player browser,
  timeline scrubber with zoom, playback, A/B pins and death jumps, the inventory at any moment with changes
  highlighted, slot history in tooltips, and confirmed restore, copy, live edit, undo, snapshot and teleport actions.
- Support for the slots of more mods: Curios (now with cosmetic slots), Accessories, Cosmetic Armor Reworked,
  Traveler's Backpack, Tool Belt and Backpacked. They are shown grouped by mod with their names on the Mod slots tab
  of the admin screen (with the empty slots of online players, to restore into) and after the offhand in chat
  previews, restored with the inventory, and editable in `/inventory player`. Opening the inventory screens of these
  mods is not recorded as a container session.
  A slot keeps its place in old backups when mods add or remove slots; an item whose slot is gone goes into the
  inventory on restore instead of being lost.
- Death entries show the slots as they were at the moment of death, even when a grave or backpack mod moves the items
  away first.
- Item search across all players, online and offline: `/invsearch` by item, custom name, enchantment or one specific
  stack, who has it now, who had it at a given time, and the transfer trail through drops, death loot and shared
  containers, with probable links marked as such. `/invsearch container <pos>` shows who put items into a chest and
  who took them out.
- Offline players can be restored, live edited and undone like online ones, in the admin screen and with
  `/inventory set|player|undo` and `/enderchest set|player`: the change goes straight into their save file, mod slots
  included, and is on the timeline naming the admin. When the player logs in, open editors close by themselves.
- `/inventory undo`, `/inventory snapshot`, `/inventory stats`, and a teleport button on timeline entries.
- Permission nodes `inventorybackups.view`, `.restore`, `.edit`, `.search` and `.ui` for LuckPerms and other permission
  mods; operator level 2 without one.
- Tab completion for player names and backup times in all commands; the timeline list works from the server console.
- A logo in the mod list and readable option names in the in-game config screen.

### Changed
- Backups are stored compactly in the world folder (`<world>/inventorybackups`): about 100 bytes per change, written
  in the background, safe against crashes.
- Old states are thinned instead of deleted: every change for a day, hourly for a week, daily for 30 days, deaths and
  admin actions for 90 days, the newest state always; with disk budgets per player and in total.
- 2.x JSON backups are imported once on the first start, then archived (configurable).
- `/inventory` and `/enderchest` work on the same timeline; `/enderchest` shows and restores the ender chest part.
- Admins without the mod see the chat in English instead of translation keys; with it, in their own language.
- `/inventory player` edits the live inventory and records the edit as one entry naming the admin.
- Previews show armor, offhand and mod slots in fixed places; restores are written to the server log.
- `preservationPeriod` and the ender chest death, join, quit and open options are no longer used.
- The mod is now under the MIT license.

### Fixed
- With "save when opened" on, opening any other container with 27 slots also saved the ender chest.
- Copying a backup into a nearly full inventory could duplicate part of a stack.
- Backup names in commands could point to files outside the backup folder.

## [2.0.2] - 2025-11-08

### Added
- Ender chest backups (periodic, on death, join, quit and optionally on opening) and the `/enderchest` command.
- `/inventory player <player>` to view and edit a player's current inventory.
- Backups of offline players can be listed, previewed and copied.

## [2.0.0] - 2025-10-31

### Added
- First NeoForge 1.21.1 version: backups on death, join, quit and every 10 minutes, a clickable chat list with pages
  and quick filters, read-only previews, restore and copy, automatic cleanup of old backups, Curios support and
  English and Russian translations.
