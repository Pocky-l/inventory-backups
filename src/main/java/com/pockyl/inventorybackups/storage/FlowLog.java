package com.pockyl.inventorybackups.storage;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Item flows of all players, one file per UTC day in {@code flows/}, in the frame format of {@link LogFile}: each frame
 * holds the pool items it introduces ({@code p}) and its flows ({@code f}). Owned by the storage thread.
 */
final class FlowLog {
    private static final int CACHED_DAYS = 16;

    private final Path dir;
    @Nullable
    private LocalDate openDay;
    private long openLength;
    private final Long2IntOpenHashMap openPool = new Long2IntOpenHashMap();
    private final List<Flow> openFlows = new ArrayList<>();
    private final LinkedHashMap<LocalDate, List<Flow>> cache = new LinkedHashMap<>(CACHED_DAYS, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LocalDate, List<Flow>> eldest) {
            return size() > CACHED_DAYS;
        }
    };

    FlowLog(Path dir) {
        this.dir = dir;
        openPool.defaultReturnValue(-1);
    }

    Path dir() {
        return dir;
    }

    /** Appends flows; they may span days, each day gets its own frame. */
    void append(List<FlowDraft> drafts, HolderLookup.Provider registries) throws IOException {
        int start = 0;
        for (int i = 1; i <= drafts.size(); i++) {
            if (i == drafts.size() || !DayFiles.day(drafts.get(i).time()).equals(DayFiles.day(drafts.get(start).time()))) {
                appendDay(drafts.subList(start, i), registries);
                start = i;
            }
        }
    }

    private void appendDay(List<FlowDraft> drafts, HolderLookup.Provider registries) throws IOException {
        if (drafts.isEmpty()) {
            return;
        }
        LocalDate day = DayFiles.day(drafts.get(0).time());
        if (!day.equals(openDay)) {
            open(day);
        }
        ListTag pool = new ListTag();
        ListTag flows = new ListTag();
        for (FlowDraft draft : drafts) {
            LogFile.PoolItem item = ItemCodec.encode(registries, draft.stack());
            int ref = openPool.get(item.hash());
            if (ref < 0) {
                ref = openPool.size();
                openPool.put(item.hash(), ref);
                CompoundTag pooled = new CompoundTag();
                pooled.putLong("h", item.hash());
                pooled.put("i", item.tag());
                pool.add(pooled);
            }
            Flow flow = new Flow(draft.time(), draft.type(), draft.player(), draft.playerName(), item.hash(), item.tag(),
                    draft.stack().getCount(), draft.dimension(), draft.pos(), draft.source(), draft.sourceName(), draft.container(),
                    draft.containerPos());
            flows.add(save(flow, ref));
            openFlows.add(flow);
        }
        CompoundTag frame = new CompoundTag();
        if (!pool.isEmpty()) {
            frame.put("p", pool);
        }
        frame.put("f", flows);
        int written = LogFile.appendFrame(DayFiles.file(dir, day), openLength, frame);
        openLength = (openLength < 8 ? 0 : openLength) + written;
    }

    private void open(LocalDate day) {
        openDay = day;
        openLength = 0;
        openPool.clear();
        openFlows.clear();
        Path file = DayFiles.file(dir, day);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            LogFile.Frames frames = LogFile.readFrames(file);
            openLength = frames.validLength();
            openFlows.addAll(decode(frames.frames(), openPool));
        } catch (IOException | RuntimeException e) {
            InventoryBackups.LOGGER.error("Could not read {}, setting it aside", file, e);
            DayFiles.setAside(file);
        }
    }

    /** Flows of a day in time order. */
    List<Flow> day(LocalDate day) {
        if (day.equals(openDay)) {
            return openFlows;
        }
        List<Flow> cached = cache.get(day);
        if (cached != null) {
            return cached;
        }
        Path file = DayFiles.file(dir, day);
        List<Flow> flows = List.of();
        if (Files.isRegularFile(file)) {
            try {
                flows = decode(LogFile.readFrames(file).frames(), new Long2IntOpenHashMap());
            } catch (IOException | RuntimeException e) {
                InventoryBackups.LOGGER.error("Could not read {}", file, e);
            }
        }
        cache.put(day, flows);
        return flows;
    }

    /** Flows between two times, oldest first. */
    List<Flow> between(long from, long to) {
        List<Flow> result = new ArrayList<>();
        LocalDate first = DayFiles.day(from);
        LocalDate last = DayFiles.day(to);
        for (LocalDate day : DayFiles.days(dir)) {
            if (day.isBefore(first) || day.isAfter(last)) {
                continue;
            }
            for (Flow flow : day(day)) {
                if (flow.time() >= from && flow.time() <= to) {
                    result.add(flow);
                }
            }
        }
        return result;
    }

    /** Deletes the files of days before the cutoff and returns how many. */
    int deleteBefore(LocalDate cutoff) {
        int deleted = 0;
        for (LocalDate day : DayFiles.days(dir)) {
            if (day.isBefore(cutoff)) {
                try {
                    Files.deleteIfExists(DayFiles.file(dir, day));
                    cache.remove(day);
                    deleted++;
                } catch (IOException e) {
                    InventoryBackups.LOGGER.warn("Could not delete flows of {}", day, e);
                }
            }
        }
        DayFiles.deleteIfEmpty(dir);
        return deleted;
    }

    private static List<Flow> decode(List<CompoundTag> frames, Long2IntOpenHashMap poolIndex) {
        List<CompoundTag> poolTags = new ArrayList<>();
        List<Long> poolHashes = new ArrayList<>();
        List<Flow> flows = new ArrayList<>();
        for (CompoundTag frame : frames) {
            for (Tag tag : frame.getList("p", Tag.TAG_COMPOUND)) {
                CompoundTag pooled = (CompoundTag) tag;
                poolIndex.put(pooled.getLong("h"), poolTags.size());
                poolHashes.add(pooled.getLong("h"));
                poolTags.add(pooled.getCompound("i"));
            }
            for (Tag tag : frame.getList("f", Tag.TAG_COMPOUND)) {
                CompoundTag flow = (CompoundTag) tag;
                int ref = flow.getInt("r");
                if (ref < 0 || ref >= poolTags.size()) {
                    continue;
                }
                flows.add(load(flow, poolHashes.get(ref), poolTags.get(ref)));
            }
        }
        return flows;
    }

    private static CompoundTag save(Flow flow, int ref) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("t", flow.time());
        tag.putByte("y", (byte) flow.type().id());
        tag.putUUID("u", flow.player());
        tag.putString("n", flow.playerName());
        tag.putInt("r", ref);
        tag.putInt("c", flow.count());
        tag.putString("d", flow.dimension());
        tag.putIntArray("p", new int[] {flow.pos().getX(), flow.pos().getY(), flow.pos().getZ()});
        if (flow.source() != null) {
            tag.putUUID("s", flow.source());
            tag.putString("sn", flow.sourceName() == null ? "" : flow.sourceName());
        }
        if (flow.container() != null) {
            tag.putString("k", flow.container());
        }
        if (flow.containerPos() != null) {
            tag.putLong("kp", flow.containerPos().asLong());
        }
        return tag;
    }

    private static Flow load(CompoundTag tag, long hash, CompoundTag item) {
        BlockPos pos = BlockPos.ZERO;
        if (tag.get("p") instanceof IntArrayTag array && array.size() == 3) {
            int[] xyz = array.getAsIntArray();
            pos = new BlockPos(xyz[0], xyz[1], xyz[2]);
        }
        UUID source = tag.hasUUID("s") ? tag.getUUID("s") : null;
        return new Flow(tag.getLong("t"), FlowType.byId(tag.getByte("y")), tag.getUUID("u"), tag.getString("n"), hash, item,
                tag.getInt("c"), tag.getString("d"), pos, source, source == null ? null : tag.getString("sn"),
                tag.contains("k", Tag.TAG_STRING) ? tag.getString("k") : null,
                tag.contains("kp", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("kp")) : null);
    }
}
