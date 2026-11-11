package com.theminespirit.neoforge.board;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BreakLogKeys;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class PlotBreakSeen extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_plot_break";
    private static final String KEYS = "Keys";
    private static final String KEY = "K";
    private static final String TICK = "T";
    private static final int MAX_KEYS = 4096;
    private static final SavedData.Factory<PlotBreakSeen> FACTORY =
            new SavedData.Factory<>(PlotBreakSeen::new, PlotBreakSeen::load);

    private BreakLogKeys keys = BreakLogKeys.empty();

    private PlotBreakSeen() {}

    public static PlotBreakSeen get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public boolean recently(String key, long now, int dedupeTicks) {
        return keys.recently(key, now, dedupeTicks);
    }

    public int size() {
        return keys.snapshot().size();
    }

    public void remember(String key, long now) {
        keys.remember(key, now, MAX_KEYS);
        setDirty();
    }

    static PlotBreakSeen load(CompoundTag tag, HolderLookup.Provider registries) {
        Map<String, Long> seen = new HashMap<>();
        ListTag list = tag.getList(KEYS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            seen.put(entry.getString(KEY), entry.getLong(TICK));
        }
        PlotBreakSeen data = new PlotBreakSeen();
        data.keys = BreakLogKeys.of(seen);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        keys.snapshot().forEach((key, tick) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY, key);
            entry.putLong(TICK, tick);
            list.add(entry);
        });
        tag.put(KEYS, list);
        return tag;
    }
}
