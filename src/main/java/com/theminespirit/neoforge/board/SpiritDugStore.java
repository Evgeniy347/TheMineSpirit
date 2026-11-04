package com.theminespirit.neoforge.board;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.DugColumns;
import java.util.OptionalInt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class SpiritDugStore extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_spirit_dug";
    private static final String COLUMNS = "Columns";
    private static final String X = "X";
    private static final String Z = "Z";
    private static final String TOP = "Top";
    private static final SavedData.Factory<SpiritDugStore> FACTORY =
            new SavedData.Factory<>(SpiritDugStore::new, SpiritDugStore::load);

    private final DugColumns columns = new DugColumns();

    private SpiritDugStore() {}

    public static SpiritDugStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void remember(BlockCoord at) {
        columns.remember(at.x(), at.y(), at.z());
        setDirty();
    }

    public OptionalInt top(int x, int z) {
        return columns.top(x, z);
    }

    private static SpiritDugStore load(CompoundTag tag, HolderLookup.Provider ignoredRegistries) {
        SpiritDugStore store = new SpiritDugStore();
        ListTag entries = tag.getList(COLUMNS, Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            store.columns.remember(entry.getInt(X), entry.getInt(TOP), entry.getInt(Z));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (DugColumns.Column column : columns.columns()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(X, column.x());
            entry.putInt(Z, column.z());
            entry.putInt(TOP, column.top());
            entries.add(entry);
        }
        tag.put(COLUMNS, entries);
        return tag;
    }
}
