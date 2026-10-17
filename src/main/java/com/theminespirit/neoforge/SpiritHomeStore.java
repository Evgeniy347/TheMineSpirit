package com.theminespirit.neoforge;

import com.theminespirit.core.base.SpiritHome;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SpiritHomeStore {
    public static final String KEY = "SpiritHome";
    public static final int VERSION = 1;

    public static final String SET_KEY = "Set";

    private static final String VERSION_KEY = "Version";

    private SpiritHomeStore() {
    }

    public static void write(CompoundTag tag, SpiritHome home) {
        if (home == null) {
            tag.remove(KEY);
            return;
        }
        CompoundTag stored = new CompoundTag();
        stored.putInt(VERSION_KEY, VERSION);
        stored.putBoolean(SET_KEY, home.isSet());
        home.point().ifPresent(point -> {
            stored.putInt("X", point.x());
            stored.putInt("Y", point.y());
            stored.putInt("Z", point.z());
        });
        tag.put(KEY, stored);
    }

    public static Optional<SpiritHome> read(CompoundTag tag) {
        if (!tag.contains(KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag stored = tag.getCompound(KEY);
        int version = stored.getInt(VERSION_KEY);
        if (version != VERSION) {
            throw new IllegalStateException("Дом духа записан версией " + version + ", поддерживается " + VERSION
                    + ": обновите мод или снимите дом командой /spirit home <дух> clear");
        }
        boolean set = !stored.contains(SET_KEY) || stored.getBoolean(SET_KEY);
        if (!set) {
            return Optional.of(SpiritHome.none());
        }
        return Optional.of(SpiritHome.at(
                new BlockCoord(stored.getInt("X"), stored.getInt("Y"), stored.getInt("Z"))));
    }
}
