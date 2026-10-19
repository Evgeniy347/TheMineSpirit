package com.theminespirit.neoforge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SpiritDemoStore {
    public static final String KEY = "SpiritDemo";

    private SpiritDemoStore() {}

    public static void write(CompoundTag tag, boolean demo) {
        if (!demo) {
            tag.remove(KEY);
            return;
        }
        tag.putBoolean(KEY, true);
    }

    public static boolean read(CompoundTag tag) {
        return tag.contains(KEY, Tag.TAG_BYTE) && tag.getBoolean(KEY);
    }
}
