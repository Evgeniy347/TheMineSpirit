package com.theminespirit.neoforge.board;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.ScaffoldClaims;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class ScaffoldStore extends SavedData {
    public static final long EXPIRY_TICKS = 24000;
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_scaffolds";
    private static final String CLAIMS = "Claims";
    private static final String ID = "Id";
    private static final String EXECUTOR = "Executor";
    private static final String EXPIRES = "Expires";
    private static final String CELLS = "Cells";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";
    private static final SavedData.Factory<ScaffoldStore> FACTORY =
            new SavedData.Factory<>(ScaffoldStore::new, ScaffoldStore::load);

    private final ScaffoldClaims claims = new ScaffoldClaims();

    private ScaffoldStore() {}

    public static ScaffoldStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void claim(String id, UUID executor, long now, List<BlockCoord> cells) {
        claims.claim(new ScaffoldClaims.Claim(id, executor, now + EXPIRY_TICKS, new LinkedHashSet<>(cells)));
        setDirty();
    }

    public boolean blocked(UUID spirit, BlockCoord cell, long now) {
        return claims.blocked(spirit, cell, now);
    }

    public boolean claimed(BlockCoord cell, long now) {
        return claims.claimed(cell, now);
    }

    public Set<BlockCoord> protectedCells(long now) {
        return claims.protectedCells(now);
    }

    public void removed(BlockCoord cell) {
        claims.removed(cell);
        setDirty();
    }

    public void release(String id) {
        claims.remove(id);
        setDirty();
    }

    private static ScaffoldStore load(CompoundTag tag, HolderLookup.Provider ignoredRegistries) {
        ScaffoldStore store = new ScaffoldStore();
        ListTag entries = tag.getList(CLAIMS, Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            Set<BlockCoord> cells = new LinkedHashSet<>();
            ListTag list = entry.getList(CELLS, Tag.TAG_COMPOUND);
            for (int cell = 0; cell < list.size(); cell++) {
                CompoundTag at = list.getCompound(cell);
                cells.add(new BlockCoord(at.getInt(X), at.getInt(Y), at.getInt(Z)));
            }
            if (cells.isEmpty()) {
                continue;
            }
            store.claims.claim(new ScaffoldClaims.Claim(
                    entry.getString(ID), UUID.fromString(entry.getString(EXECUTOR)), entry.getLong(EXPIRES), cells));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (ScaffoldClaims.Claim claim : claims.claims(Long.MIN_VALUE)) {
            CompoundTag entry = new CompoundTag();
            entry.putString(ID, claim.id());
            entry.putString(EXECUTOR, claim.executor().toString());
            entry.putLong(EXPIRES, claim.expiresAt());
            ListTag cells = new ListTag();
            for (BlockCoord cell : claim.cells()) {
                CompoundTag at = new CompoundTag();
                at.putInt(X, cell.x());
                at.putInt(Y, cell.y());
                at.putInt(Z, cell.z());
                cells.add(at);
            }
            entry.put(CELLS, cells);
            entries.add(entry);
        }
        tag.put(CLAIMS, entries);
        return tag;
    }
}
