package com.theminespirit.neoforge;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SpiritTaskStore {
    public static final String KEY = "SpiritTask";

    private SpiritTaskStore() {
    }

    public static void write(CompoundTag tag, String snapshotJson) {
        if (tag == null) {
            return;
        }
        if (snapshotJson == null) {
            tag.remove(KEY);
            return;
        }
        tag.putString(KEY, snapshotJson);
    }

    public static Optional<String> read(CompoundTag tag) {
        if (tag == null || !tag.contains(KEY, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        String value = tag.getString(KEY);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
