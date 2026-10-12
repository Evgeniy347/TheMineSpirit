package com.theminespirit.neoforge.tactics;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SpiritTacticsStore {
    public static final String KEY = "SpiritTactics";

    private SpiritTacticsStore() {
    }

    public static void write(CompoundTag tag, String stateJson) {
        if (tag == null) {
            return;
        }
        if (stateJson == null) {
            tag.remove(KEY);
            return;
        }
        tag.putString(KEY, stateJson);
    }

    public static Optional<String> read(CompoundTag tag) {
        if (tag == null || !tag.contains(KEY, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        String value = tag.getString(KEY);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
