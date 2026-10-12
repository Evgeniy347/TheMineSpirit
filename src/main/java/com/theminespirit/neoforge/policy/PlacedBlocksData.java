package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.PlacedBlockCodec;
import com.theminespirit.core.policy.PlacedBlockSet;
import java.util.ArrayList;
import java.util.List;
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
    private static final String CHUNK_X = "X";
    private static final String CHUNK_Z = "Z";
    private static final String OVERFLOWED = "Overflowed";
    private static final String POSITIONS = "Positions";

    private final PlacedBlockSet set;

    public PlacedBlocksData() {
        this(new PlacedBlockSet());
    }

    private PlacedBlocksData(PlacedBlockSet set) {
        this.set = set;
    }

    public static SavedData.Factory<PlacedBlocksData> factory() {
        return new SavedData.Factory<>(PlacedBlocksData::new, PlacedBlocksData::load, null);
    }

    public static PlacedBlocksData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static PlacedBlocksData load(CompoundTag tag, HolderLookup.Provider registries) {
        List<PlacedBlockCodec.ChunkEntry> entries = new ArrayList<>();
        ListTag chunks = tag.getList(CHUNKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < chunks.size(); i++) {
            CompoundTag chunk = chunks.getCompound(i);
            entries.add(new PlacedBlockCodec.ChunkEntry(chunk.getInt(CHUNK_X), chunk.getInt(CHUNK_Z), chunk.getBoolean(OVERFLOWED),
                    chunk.getLongArray(POSITIONS)));
        }
        return new PlacedBlocksData(PlacedBlockCodec.decode(entries));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag chunks = new ListTag();
        for (PlacedBlockCodec.ChunkEntry entry : PlacedBlockCodec.encode(set)) {
            CompoundTag chunk = new CompoundTag();
            chunk.putInt(CHUNK_X, entry.chunkX());
            chunk.putInt(CHUNK_Z, entry.chunkZ());
            chunk.putBoolean(OVERFLOWED, entry.overflowed());
            chunk.putLongArray(POSITIONS, entry.positions());
            chunks.add(chunk);
        }
        tag.put(CHUNKS, chunks);
        return tag;
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

    public int size() {
        return set.size();
    }

    private static BlockCoord toCoord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
