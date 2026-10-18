package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.PlacedBlockCodec;
import com.theminespirit.core.policy.PlacedBlockSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class PlacedBlocksData extends SavedData {
    public static final String NAME = "theminespirit_placed_blocks";

    private static final String CHUNKS = "Chunks";
    private static final String SPIRIT_CHUNKS = "SpiritChunks";
    private static final String CHUNK_X = "X";
    private static final String CHUNK_Z = "Z";
    private static final String OVERFLOWED = "Overflowed";
    private static final String POSITIONS = "Positions";

    private final PlacedBlockSet set;
    private final PlacedBlockSet spiritSet;
    private final Map<BlockCoord, Integer> spiritPlacements = new HashMap<>();

    public PlacedBlocksData() {
        this(new PlacedBlockSet(), new PlacedBlockSet());
    }

    private PlacedBlocksData(PlacedBlockSet set, PlacedBlockSet spiritSet) {
        this.set = set;
        this.spiritSet = spiritSet;
    }

    public static SavedData.Factory<PlacedBlocksData> factory() {
        return new SavedData.Factory<>(PlacedBlocksData::new, PlacedBlocksData::load, null);
    }

    public static PlacedBlocksData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static PlacedBlocksData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new PlacedBlocksData(PlacedBlockCodec.decode(readEntries(tag, CHUNKS)),
                PlacedBlockCodec.decode(readEntries(tag, SPIRIT_CHUNKS)));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(CHUNKS, writeEntries(PlacedBlockCodec.encode(set)));
        tag.put(SPIRIT_CHUNKS, writeEntries(PlacedBlockCodec.encode(spiritSet)));
        return tag;
    }

    private static List<PlacedBlockCodec.ChunkEntry> readEntries(CompoundTag tag, String key) {
        List<PlacedBlockCodec.ChunkEntry> entries = new ArrayList<>();
        ListTag chunks = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < chunks.size(); i++) {
            CompoundTag chunk = chunks.getCompound(i);
            entries.add(new PlacedBlockCodec.ChunkEntry(chunk.getInt(CHUNK_X), chunk.getInt(CHUNK_Z), chunk.getBoolean(OVERFLOWED),
                    chunk.getLongArray(POSITIONS)));
        }
        return entries;
    }

    private static ListTag writeEntries(List<PlacedBlockCodec.ChunkEntry> encoded) {
        ListTag chunks = new ListTag();
        for (PlacedBlockCodec.ChunkEntry entry : encoded) {
            CompoundTag chunk = new CompoundTag();
            chunk.putInt(CHUNK_X, entry.chunkX());
            chunk.putInt(CHUNK_Z, entry.chunkZ());
            chunk.putBoolean(OVERFLOWED, entry.overflowed());
            chunk.putLongArray(POSITIONS, entry.positions());
            chunks.add(chunk);
        }
        return chunks;
    }

    public void mark(BlockPos pos) {
        if (set.add(toCoord(pos))) {
            setDirty();
        }
    }

    public void unmark(BlockPos pos) {
        if (set.remove(toCoord(pos))) {
            setDirty();
        }
    }

    public boolean isMarked(BlockPos pos) {
        return set.contains(toCoord(pos));
    }

    public void markSpirit(BlockPos pos) {
        if (spiritSet.add(toCoord(pos))) {
            setDirty();
        }
        spiritPlacements.merge(toCoord(pos), 1, Integer::sum);
    }

    public void unmarkSpirit(BlockPos pos) {
        if (spiritSet.remove(toCoord(pos))) {
            setDirty();
        }
    }

    public boolean isSpiritMarked(BlockPos pos) {
        return spiritSet.contains(toCoord(pos));
    }

    public int spiritPlacementCount(BlockPos pos) {
        return spiritPlacements.getOrDefault(toCoord(pos), 0);
    }

    public Map<BlockCoord, Integer> spiritPlacementCounts(BlockCoord min, BlockCoord max) {
        Map<BlockCoord, Integer> counts = new HashMap<>();
        for (Map.Entry<BlockCoord, Integer> entry : spiritPlacements.entrySet()) {
            BlockCoord pos = entry.getKey();
            if (pos.x() >= min.x() && pos.x() <= max.x() && pos.y() >= min.y() && pos.y() <= max.y()
                    && pos.z() >= min.z() && pos.z() <= max.z()) {
                counts.put(pos, entry.getValue());
            }
        }
        return Map.copyOf(counts);
    }

    public int spiritSize() {
        return spiritSet.size();
    }

    public int size() {
        return set.size();
    }

    private static BlockCoord toCoord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
