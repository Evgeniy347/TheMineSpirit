package com.theminespirit.neoforge;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SpiritMemoryStore {
    public static final String KEY = "SpiritMemory";
    private static final AtomicInteger CORRUPT_LOADS = new AtomicInteger();

    private SpiritMemoryStore() {
    }

    public static void write(CompoundTag tag, String memoryJson) {
        if (tag == null) {
            return;
        }
        if (memoryJson == null) {
            tag.remove(KEY);
            return;
        }
        tag.putString(KEY, memoryJson);
    }

    public static Optional<String> read(CompoundTag tag) {
        if (tag == null || !tag.contains(KEY, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        String value = tag.getString(KEY);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    static void countCorruptLoad() {
        CORRUPT_LOADS.incrementAndGet();
    }

    public static int corruptLoads() {
        return CORRUPT_LOADS.get();
    }
}
