package com.pockyl.inventorybackups.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import com.pockyl.inventorybackups.InventoryBackups;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The binary file format: an 8-byte header ({@code IBK3} and a format version) followed by frames. A frame is
 * {@code [int length][int crc32][deflated NBT]}; its compound holds the pool items it introduces ({@code p}) and its
 * entries ({@code e}). Entries are appended one frame at a time, so a crash can at worst leave a torn last frame, which
 * the CRC detects and the next writer truncates. Retention rewrites a file as a single frame through a temporary file
 * and an atomic move.
 */
public final class LogFile {
    public static final String EXTENSION = ".ibk";

    private static final byte[] MAGIC = "IBK3".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    private static final int HEADER_SIZE = 8;
    private static final int MAX_FRAME = 64 * 1024 * 1024;
    /**
     * Preset deflate dictionary with strings found in nearly every item, so that small single-entry frames compress too.
     * Changing it makes existing files unreadable.
     */
    private static final byte[] DICTIONARY = ("minecraft:components minecraft:damage minecraft:enchantments levels "
            + "minecraft:custom_name minecraft:lore minecraft:stored_enchantments minecraft:potion_contents "
            + "minecraft:container minecraft:bundle_contents minecraft:dyed_color minecraft:trim count id slot item "
            + "minecraft:diamond_ minecraft:netherite_ minecraft:iron_ minecraft:golden_ minecraft:stone minecraft:dirt "
            + "minecraft:cobblestone minecraft:overworld minecraft:the_nether minecraft:the_end minecraft:chest "
            + "minecraft:barrel minecraft:shulker_box minecraft:ender_chest").getBytes(StandardCharsets.UTF_8);

    private LogFile() {
    }

    /** A decoded file and the length of its valid part (shorter than the file when the last frame is torn). */
    public record Read(DayLog log, long validLength) {
    }

    public static Read read(Path file, LocalDate day) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        DayLog log = new DayLog(day, 0);
        if (bytes.length < HEADER_SIZE || !hasMagic(bytes)) {
            throw new IOException("Not an inventory backup file: " + file);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.position(HEADER_SIZE);
        long valid = HEADER_SIZE;
        boolean first = true;
        while (buffer.remaining() >= 8) {
            int length = buffer.getInt();
            int crc = buffer.getInt();
            if (length <= 0 || length > MAX_FRAME || length > buffer.remaining()) {
                break;
            }
            byte[] payload = new byte[length];
            buffer.get(payload);
            CRC32 checksum = new CRC32();
            checksum.update(payload);
            if ((int) checksum.getValue() != crc) {
                break;
            }
            CompoundTag frame;
            try {
                frame = NbtIo.read(new DataInputStream(new ByteArrayInputStream(inflate(payload))), NbtAccounter.unlimitedHeap());
            } catch (DataFormatException | IOException e) {
                InventoryBackups.LOGGER.warn("Ignoring a damaged frame at byte {} of {}", valid, file);
                break;
            }
            if (first) {
                log.setThin(frame.getInt("thin"));
                first = false;
            }
            for (Tag item : frame.getList("p", Tag.TAG_COMPOUND)) {
                CompoundTag pooled = (CompoundTag) item;
                log.addToPool(pooled.getLong("h"), pooled.getCompound("i"));
            }
            for (Tag entry : frame.getList("e", Tag.TAG_COMPOUND)) {
                log.addEntry(Entry.load((CompoundTag) entry));
            }
            valid = buffer.position();
        }
        return new Read(log, valid);
    }

    /**
     * Appends a frame with new pool items and entries. Creates the file with its header if needed and cuts off a torn
     * frame first when {@code validLength} is shorter than the file.
     *
     * @return the number of bytes written
     */
    public static int append(Path file, long validLength, int thin, List<PoolItem> pool, List<Entry> entries) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] frame = frame(thin, pool, entries);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            long size = channel.size();
            int written = frame.length;
            if (size < HEADER_SIZE) {
                channel.truncate(0);
                channel.write(ByteBuffer.wrap(header()), 0);
                size = HEADER_SIZE;
                written += HEADER_SIZE;
            } else if (validLength >= HEADER_SIZE && validLength < size) {
                channel.truncate(validLength);
                size = validLength;
            }
            channel.write(ByteBuffer.wrap(frame), size);
            return written;
        }
    }

    /** Replaces a file with a single frame holding the whole log. */
    public static long rewrite(Path file, DayLog log, List<PoolItem> pool) throws IOException {
        Files.createDirectories(file.getParent());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header());
        out.write(frame(log.thin(), pool, log.entries()));
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, out.toByteArray());
        moveAtomically(temp, file);
        return out.size();
    }

    /** Writes a small NBT file through a temporary file and an atomic move. */
    public static void writeCompound(Path file, CompoundTag tag) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        NbtIo.writeCompressed(tag, temp);
        moveAtomically(temp, file);
    }

    public static CompoundTag readCompound(Path file) throws IOException {
        return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
    }

    /** An item of a frame's pool. */
    public record PoolItem(long hash, CompoundTag tag) {
    }

    private static void moveAtomically(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] header() {
        return ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).putInt(VERSION).array();
    }

    private static boolean hasMagic(byte[] bytes) {
        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] frame(int thin, List<PoolItem> pool, List<Entry> entries) throws IOException {
        CompoundTag frame = new CompoundTag();
        if (thin != 0) {
            frame.putInt("thin", thin);
        }
        ListTag poolTag = new ListTag();
        for (PoolItem item : pool) {
            CompoundTag pooled = new CompoundTag();
            pooled.putLong("h", item.hash());
            pooled.put("i", item.tag());
            poolTag.add(pooled);
        }
        if (!poolTag.isEmpty()) {
            frame.put("p", poolTag);
        }
        ListTag entryTag = new ListTag();
        for (Entry entry : entries) {
            entryTag.add(entry.save());
        }
        frame.put("e", entryTag);

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        NbtIo.write(frame, new DataOutputStream(raw));
        byte[] payload = deflate(raw.toByteArray());
        CRC32 checksum = new CRC32();
        checksum.update(payload);
        return ByteBuffer.allocate(8 + payload.length).putInt(payload.length).putInt((int) checksum.getValue()).put(payload).array();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setDictionary(DICTIONARY);
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 2));
            byte[] chunk = new byte[8192];
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] data) throws DataFormatException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
            byte[] chunk = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(chunk);
                if (count == 0) {
                    if (inflater.needsDictionary()) {
                        inflater.setDictionary(DICTIONARY);
                    } else if (inflater.needsInput()) {
                        throw new DataFormatException("Truncated frame");
                    }
                }
                out.write(chunk, 0, count);
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }
}
