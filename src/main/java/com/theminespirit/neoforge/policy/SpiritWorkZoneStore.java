package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.WorkZone;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SpiritWorkZoneStore {
    public static final String KEY = "SpiritWorkZone";
    public static final int VERSION = 1;

    private static final String VERSION_KEY = "Version";

    private SpiritWorkZoneStore() {}

    public static void write(CompoundTag tag, WorkZone zone) {
        if (zone == null) {
            tag.remove(KEY);
            return;
        }
        CompoundTag stored = new CompoundTag();
        stored.putInt(VERSION_KEY, VERSION);
        stored.putInt("X", zone.center().x());
        stored.putInt("Y", zone.center().y());
        stored.putInt("Z", zone.center().z());
        stored.putInt("Radius", zone.radius());
        tag.put(KEY, stored);
    }

    public static Optional<WorkZone> read(CompoundTag tag) {
        if (!tag.contains(KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag stored = tag.getCompound(KEY);
        int version = stored.getInt(VERSION_KEY);
        if (version != VERSION) {
            throw new IllegalStateException("Рабочая зона духа записана версией " + version + ", поддерживается "
                    + VERSION + ": обновите мод или снимите зону командой /spirit zone <дух> clear");
        }
        return Optional.of(new WorkZone(
                new BlockCoord(stored.getInt("X"), stored.getInt("Y"), stored.getInt("Z")), stored.getInt("Radius")));
    }
}
