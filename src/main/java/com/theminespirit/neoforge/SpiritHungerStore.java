package com.theminespirit.neoforge;

import com.theminespirit.core.hunger.SpiritHunger;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SpiritHungerStore {
    public static final String KEY = "SpiritHunger";
    public static final int VERSION = 1;

    private static final String VERSION_KEY = "Version";

    private SpiritHungerStore() {
    }

    public static void write(CompoundTag tag, SpiritHunger hunger) {
        if (hunger == null) {
            tag.remove(KEY);
            return;
        }
        CompoundTag stored = new CompoundTag();
        stored.putInt(VERSION_KEY, VERSION);
        stored.putInt("FoodLevel", hunger.foodLevel());
        stored.putDouble("Saturation", hunger.saturation());
        stored.putDouble("Exhaustion", hunger.exhaustion());
        stored.putInt("RegenTimer", hunger.regenTimer());
        tag.put(KEY, stored);
    }

    public static Optional<SpiritHunger> read(CompoundTag tag) {
        if (!tag.contains(KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag stored = tag.getCompound(KEY);
        int version = stored.getInt(VERSION_KEY);
        if (version != VERSION) {
            throw new IllegalStateException("Сытость духа записана версией " + version + ", поддерживается " + VERSION
                    + ": обновите мод или накормите духа заново");
        }
        return Optional.of(new SpiritHunger(stored.getInt("FoodLevel"), stored.getDouble("Saturation"),
                stored.getDouble("Exhaustion"), stored.getInt("RegenTimer")));
    }
}
