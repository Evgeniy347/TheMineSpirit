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
    private static final String SPIRIT_CHUNKS = "SpiritChunks";
    private static final String TEMPORARY_CHUNKS = "TemporaryChunks";
    private static final String CHUNK_X = "X";
    private static final String CHUNK_Z = "Z";
    private static final String OVERFLOWED = "Overflowed";
    private static final String POSITIONS = "Positions";

    private final PlacedBlockSet set;
    private final PlacedBlockSet spiritSet;
    private final PlacedBlockSet temporarySet;

    public PlacedBlocksData() {
        this(new PlacedBlockSet(), new PlacedBlockSet(), new PlacedBlockSet());
    }

    private PlacedBlocksData(PlacedBlockSet set, PlacedBlockSet spiritSet, PlacedBlockSet temporarySet) {
        this.set = set;
        this.spiritSet = spiritSet;
        this.temporarySet = temporarySet;
    }

    public static SavedData.Factory<PlacedBlocksData> factory() {
        return new SavedData.Factory<>(PlacedBlocksData::new, PlacedBlocksData::load, null);
    }

    public static PlacedBlocksData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static PlacedBlocksData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new PlacedBlocksData(
                PlacedBlockCodec.decode(readEntries(tag, CHUNKS)),
                PlacedBlockCodec.decode(readEntries(tag, SPIRIT_CHUNKS)),
                PlacedBlockCodec.decode(readEntries(tag, TEMPORARY_CHUNKS)));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(CHUNKS, writeEntries(PlacedBlockCodec.encode(set)));
        tag.put(SPIRIT_CHUNKS, writeEntries(PlacedBlockCodec.encode(spiritSet)));
        tag.put(TEMPORARY_CHUNKS, writeEntries(PlacedBlockCodec.encode(temporarySet)));
        return tag;
    }

    private static List<PlacedBlockCodec.ChunkEntry> readEntries(CompoundTag tag, String key) {
        List<PlacedBlockCodec.ChunkEntry> entries = new ArrayList<>();
        ListTag chunks = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < chunks.size(); i++) {
            CompoundTag chunk = chunks.getCompound(i);
            entries.add(new PlacedBlockCodec.ChunkEntry(
                    chunk.getInt(CHUNK_X),
                    chunk.getInt(CHUNK_Z),
                    chunk.getBoolean(OVERFLOWED),
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
    }

    public void unmarkSpirit(BlockPos pos) {
        if (spiritSet.remove(toCoord(pos))) {
            setDirty();
        }
    }

    public boolean isSpiritMarked(BlockPos pos) {
        return spiritSet.contains(toCoord(pos));
    }

    public List<BlockCoord> spiritMarkedIn(int chunkX, int chunkZ) {
        return spiritSet.inChunk(chunkX, chunkZ);
    }

    public void unmarkTemporary(BlockPos pos) {
        if (temporarySet.remove(toCoord(pos))) {
            setDirty();
        }
    }

    public boolean isTemporary(BlockPos pos) {
        return temporarySet.contains(toCoord(pos));
    }

    public boolean isOwn(BlockPos pos) {
        return isSpiritMarked(pos) || isTemporary(pos);
    }

    public List<BlockCoord> temporaryIn(int chunkX, int chunkZ) {
        return temporarySet.inChunk(chunkX, chunkZ);
    }

    public List<BlockCoord> temporaryCells() {
        return temporarySet.cells();
    }

    public int size() {
        return set.size();
    }

    private static BlockCoord toCoord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
