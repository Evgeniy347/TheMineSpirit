package com.theminespirit.neoforge;

import com.theminespirit.core.base.Camp;
import com.theminespirit.core.tactics.Point;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SpiritCampStore {
    public static final String KEY = "SpiritCamp";
    public static final int VERSION = 1;

    private static final String VERSION_KEY = "Version";

    private SpiritCampStore() {}

    public static void write(CompoundTag tag, Camp camp) {
        if (camp == null) {
            tag.remove(KEY);
            return;
        }
        CompoundTag stored = new CompoundTag();
        stored.putInt(VERSION_KEY, VERSION);
        stored.putDouble("X", camp.center().x());
        stored.putDouble("Y", camp.center().y());
        stored.putDouble("Z", camp.center().z());
        stored.putDouble("R", camp.radius());
        stored.putInt("Owner", camp.owner());
        stored.putLong("SetAt", camp.setAt());
        tag.put(KEY, stored);
    }

    public static Optional<Camp> read(CompoundTag tag) {
        if (!tag.contains(KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag stored = tag.getCompound(KEY);
        int version = stored.getInt(VERSION_KEY);
        if (version != VERSION) {
            throw new IllegalStateException("Лагерь духа записан версией " + version + ", поддерживается " + VERSION
                    + ": обновите мод или снимите лагерь командой /spirit camp <дух> clear");
        }
        return Optional.of(new Camp(
                new Point(stored.getDouble("X"), stored.getDouble("Y"), stored.getDouble("Z")),
                stored.getDouble("R"),
                stored.getInt("Owner"),
                stored.getLong("SetAt")));
    }
}
