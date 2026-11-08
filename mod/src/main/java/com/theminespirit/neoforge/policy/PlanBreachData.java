package com.theminespirit.neoforge.policy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

public final class PlanBreachData extends SavedData {
    public static final String NAME = "theminespirit_plan_breaches";
    private static final String BREACHES = "Breaches";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";
    private static final String BLOCK = "Block";
    private static final String SPIRIT = "Spirit";
    private static final String TICK = "Tick";

    public record Breach(
            BlockPos pos, String blockId, @Nullable UUID spirit, long tick) {}

    private final Map<BlockPos, Breach> breaches = new LinkedHashMap<>();

    public PlanBreachData() {}

    private PlanBreachData(Map<BlockPos, Breach> known) {
        breaches.putAll(known);
    }

    public static SavedData.Factory<PlanBreachData> factory() {
        return new SavedData.Factory<>(PlanBreachData::new, PlanBreachData::load, null);
    }

    public static PlanBreachData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static PlanBreachData load(CompoundTag tag, HolderLookup.Provider registries) {
        Map<BlockPos, Breach> known = new LinkedHashMap<>();
        ListTag list = tag.getList(BREACHES, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            BlockPos pos = new BlockPos(entry.getInt(X), entry.getInt(Y), entry.getInt(Z));
            UUID spirit = entry.hasUUID(SPIRIT) ? entry.getUUID(SPIRIT) : null;
            known.put(pos, new Breach(pos, entry.getString(BLOCK), spirit, entry.getLong(TICK)));
        }
        return new PlanBreachData(known);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Breach breach : breaches.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(X, breach.pos().getX());
            entry.putInt(Y, breach.pos().getY());
            entry.putInt(Z, breach.pos().getZ());
            entry.putString(BLOCK, breach.blockId());
            if (breach.spirit() != null) {
                entry.putUUID(SPIRIT, breach.spirit());
            }
            entry.putLong(TICK, breach.tick());
            list.add(entry);
        }
        tag.put(BREACHES, list);
        return tag;
    }

    public void record(BlockPos pos, String blockId, @Nullable UUID spirit, long tick) {
        breaches.put(pos.immutable(), new Breach(pos.immutable(), blockId, spirit, tick));
        setDirty();
    }

    public @Nullable Breach breach(BlockPos pos) {
        return breaches.get(pos);
    }

    public Map<BlockPos, Breach> open() {
        return Map.copyOf(breaches);
    }

    public boolean clear(BlockPos pos) {
        boolean removed = breaches.remove(pos) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public int size() {
        return breaches.size();
    }
}
