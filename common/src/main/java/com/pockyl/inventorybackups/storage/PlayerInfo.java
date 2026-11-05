package com.pockyl.inventorybackups.storage;

import java.util.UUID;

/**
 * What the storage knows about a player without reading their files.
 *
 * @param lastSeen  time of the newest entry recorded while the player was online
 * @param lastDeath time of the newest death, 0 when none is stored
 * @param lastEntry time of the newest entry
 */
public record PlayerInfo(UUID uuid, String name, long lastSeen, long lastDeath, long lastEntry) {
}
