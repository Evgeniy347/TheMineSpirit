package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SpiritBedStore {
    public static final String KEY = "SpiritBed";
    public static final int VERSION = 1;

    private static final String VERSION_KEY = "Version";

    private SpiritBedStore() {
    }

    public static void write(CompoundTag tag, BlockCoord bed) {
        if (bed == null) {
            tag.remove(KEY);
            return;
        }
        CompoundTag stored = new CompoundTag();
        stored.putInt(VERSION_KEY, VERSION);
        stored.putInt("X", bed.x());
        stored.putInt("Y", bed.y());
        stored.putInt("Z", bed.z());
        tag.put(KEY, stored);
    }

    public static Optional<BlockCoord> read(CompoundTag tag) {
        if (!tag.contains(KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag stored = tag.getCompound(KEY);
        int version = stored.getInt(VERSION_KEY);
        if (version != VERSION) {
            throw new IllegalStateException("Своя кровать духа записана версией " + version + ", поддерживается "
                    + VERSION + ": обновите мод или дайте духу заснуть в новой кровати");
        }
        return Optional.of(new BlockCoord(stored.getInt("X"), stored.getInt("Y"), stored.getInt("Z")));
    }
}
