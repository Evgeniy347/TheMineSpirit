package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SpiritHomeStore {
    public static final String KEY = "SpiritHome";
    public static final int VERSION = 1;

    private static final String VERSION_KEY = "Version";

    private SpiritHomeStore() {
    }

    public static void write(CompoundTag tag, BlockCoord home) {
        if (home == null) {
            tag.remove(KEY);
            return;
        }
        CompoundTag stored = new CompoundTag();
        stored.putInt(VERSION_KEY, VERSION);
        stored.putInt("X", home.x());
        stored.putInt("Y", home.y());
        stored.putInt("Z", home.z());
        tag.put(KEY, stored);
    }

    public static Optional<BlockCoord> read(CompoundTag tag) {
        if (!tag.contains(KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag stored = tag.getCompound(KEY);
        int version = stored.getInt(VERSION_KEY);
        if (version != VERSION) {
            throw new IllegalStateException("Дом духа записан версией " + version + ", поддерживается " + VERSION
                    + ": обновите мод или снимите дом командой /spirit home <дух> clear");
        }
        return Optional.of(new BlockCoord(stored.getInt("X"), stored.getInt("Y"), stored.getInt("Z")));
    }
}
