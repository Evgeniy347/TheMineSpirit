package com.theminespirit.neoforge.board;

import com.theminespirit.core.ModIdentity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class More6Store extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_more6";
    private static final String IDLE = "Idle";
    private static final String ID = "Id";
    private static final String SINCE = "Since";
    private static final SavedData.Factory<More6Store> FACTORY =
            new SavedData.Factory<>(More6Store::new, More6Store::load);

    private final Map<String, Long> idle = new LinkedHashMap<>();

    private More6Store() {}

    public static More6Store get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public long noteIdle(Collection<String> idleIds, long now) {
        Map<String, Long> next = new LinkedHashMap<>();
        long longest = 0;
        for (String id : idleIds) {
            long since = idle.getOrDefault(id, now);
            next.put(id, since);
            longest = Math.max(longest, now - since);
        }
        if (!next.equals(idle)) {
            idle.clear();
            idle.putAll(next);
            setDirty();
        }
        return longest;
    }

    private static More6Store load(CompoundTag tag, HolderLookup.Provider ignored) {
        More6Store store = new More6Store();
        for (Tag element : tag.getList(IDLE, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            store.idle.put(entry.getString(ID), entry.getLong(SINCE));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        idle.forEach((id, since) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(ID, id);
            entry.putLong(SINCE, since);
            entries.add(entry);
        });
        tag.put(IDLE, entries);
        return tag;
    }
}
