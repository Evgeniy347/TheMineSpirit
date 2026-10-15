package com.theminespirit.neoforge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SpiritChildStore {
    public static final String KEY = "SpiritChild";

    private SpiritChildStore() {
    }

    public static void write(CompoundTag tag, boolean child) {
        if (!child) {
            tag.remove(KEY);
            return;
        }
        tag.putBoolean(KEY, true);
    }

    public static boolean read(CompoundTag tag) {
        return tag.contains(KEY, Tag.TAG_BYTE) && tag.getBoolean(KEY);
    }
}
