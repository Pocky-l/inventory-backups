package com.pockyl.inventorybackups.storage;

/**
 * A stored stack: the content hash of the item without its count, and the count.
 */
public record StackRef(long hash, int count) {
}
