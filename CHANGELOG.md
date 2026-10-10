# Changelog

All notable changes to this mod are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1.0] - Unreleased

### Added
- Tab completion for player names and backup names in all commands.
- A "Deaths" quick filter in the backup list; `death`, `join`, `quit` and `open` work as list filters.
- `list` and `set` of `/inventory` and `/enderchest` also work from the server console.
- Ender chest backups taken when the chest was opened are marked as such in the list.
- A logo in the mod list and readable option names in the in-game config screen.

### Changed
- Admins who do not have the mod installed see the chat messages in English instead of translation keys; with the mod
  installed they appear in their own language.
- `/inventory player` and `/enderchest player` now edit the live inventory: changes apply immediately, and items the
  player picks up while the screen is open are no longer overwritten when it closes.
- Backup previews show armor, offhand and curio slots in fixed places after the main inventory.
- Periodic ender chest saves no longer depend on periodic inventory saves being enabled.
- Restores are written to the server log.
- Expired backups are also removed right after the server starts.
- The mod is now under the MIT license.

### Fixed
- With "save when opened" on, opening any other container with 27 slots (a chest, barrel or shulker box) also saved
  the ender chest.
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
