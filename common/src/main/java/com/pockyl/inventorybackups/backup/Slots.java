package com.pockyl.inventorybackups.backup;

/**
 * Slot numbers of a stored state. One state holds the inventory and the ender chest, so both share one timeline. The
 * inventory numbers are the ones of the 2.x backup files: 0-35 main inventory (0-8 hotbar), 100-103 armor (feet to
 * head), -106 offhand, 1000-1999 Curios in the order Curios lists them (2.x backups only); 2000-2026 ender chest.
 * From 3000 on are the slots of other mods (Curios, Accessories, ...), numbered once per world by
 * {@link com.pockyl.inventorybackups.storage.SlotKeys}, so a number keeps meaning the same slot when mods add slots.
 */
public final class Slots {
    public static final int MAIN_SIZE = 36;
    public static final int ARMOR_START = 100;
    public static final int ARMOR_SIZE = 4;
    public static final int OFFHAND = -106;
    public static final int CURIOS_START = 1000;
    public static final int CURIOS_END = 2000;
    public static final int ENDER_START = 2000;
    public static final int ENDER_SIZE = 27;
    public static final int MOD_START = 3000;
    public static final int MOD_END = 1_000_000;

    private Slots() {
    }

    public enum Section {
        MAIN, ARMOR, OFFHAND, CURIOS, ENDER_CHEST, MOD, UNKNOWN
    }

    public static Section section(int slot) {
        if (slot >= 0 && slot < MAIN_SIZE) {
            return Section.MAIN;
        }
        if (slot >= ARMOR_START && slot < ARMOR_START + ARMOR_SIZE) {
            return Section.ARMOR;
        }
        if (slot == OFFHAND) {
            return Section.OFFHAND;
        }
        if (slot >= CURIOS_START && slot < CURIOS_END) {
            return Section.CURIOS;
        }
        if (slot >= ENDER_START && slot < ENDER_START + ENDER_SIZE) {
            return Section.ENDER_CHEST;
        }
        if (slot >= MOD_START && slot < MOD_END) {
            return Section.MOD;
        }
        return Section.UNKNOWN;
    }

    public static boolean isEnderChest(int slot) {
        return section(slot) == Section.ENDER_CHEST;
    }

    /** A curio slot of a 2.x backup, numbered by its position in the Curios list. */
    public static boolean isCurio(int slot) {
        return section(slot) == Section.CURIOS;
    }

    public static boolean isModSlot(int slot) {
        return section(slot) == Section.MOD;
    }

    /** A slot added by another mod: a numbered mod slot or a 2.x curio. */
    public static boolean isExtra(int slot) {
        Section section = section(slot);
        return section == Section.MOD || section == Section.CURIOS;
    }

    /** Index in {@link net.minecraft.world.entity.player.Inventory#getItem(int)}, or -1 for slots outside it. */
    public static int vanillaIndex(int slot) {
        return switch (section(slot)) {
            case MAIN -> slot;
            case ARMOR -> MAIN_SIZE + slot - ARMOR_START;
            case OFFHAND -> MAIN_SIZE + ARMOR_SIZE;
            default -> -1;
        };
    }

    /** Stored slot of an index in {@link net.minecraft.world.entity.player.Inventory#getItem(int)}. */
    public static int fromVanillaIndex(int index) {
        if (index < MAIN_SIZE) {
            return index;
        }
        if (index < MAIN_SIZE + ARMOR_SIZE) {
            return ARMOR_START + index - MAIN_SIZE;
        }
        return OFFHAND;
    }
}
