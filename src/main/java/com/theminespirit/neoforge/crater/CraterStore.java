package com.theminespirit.neoforge.crater;

import com.theminespirit.core.building.Crater;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Память воронок от взрывов на уровне: центр, радиус и уровень края каждой.
 *
 * <p>Запись переживает перезапуск сервера, чтобы незакрытая воронка не потерялась.
 */
public final class CraterStore extends SavedData {
    public static final String NAME = "theminespirit_craters";
    public static final int MAX_CRATERS = 256;

    private static final String VERSION_KEY = "Version";
    private static final int VERSION = 1;
    private static final String CRATERS_KEY = "Craters";
    private static final String ID_KEY = "Id";
    private static final String X_KEY = "X";
    private static final String Y_KEY = "Y";
    private static final String Z_KEY = "Z";
    private static final String RADIUS_KEY = "Radius";
    private static final String LEVEL_KEY = "Level";
    private static final String TICK_KEY = "Tick";

    private final List<Crater> craters = new ArrayList<>();

    public CraterStore() {
    }

    public static SavedData.Factory<CraterStore> factory() {
        return new SavedData.Factory<>(CraterStore::new, CraterStore::load, null);
    }

    public static CraterStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static CraterStore load(CompoundTag tag, HolderLookup.Provider registries) {
        CraterStore store = new CraterStore();
        int version = tag.getInt(VERSION_KEY);
        if (version != VERSION && tag.contains(CRATERS_KEY, Tag.TAG_LIST)) {
            throw new IllegalStateException("Воронки записаны версией " + version + ", поддерживается " + VERSION
                    + ": обновите мод, старые воронки не читаются");
        }
        ListTag list = tag.getList(CRATERS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            store.craters.add(readCrater(list.getCompound(i)));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(VERSION_KEY, VERSION);
        ListTag list = new ListTag();
        for (Crater crater : craters) {
            list.add(writeCrater(crater));
        }
        tag.put(CRATERS_KEY, list);
        return tag;
    }

    private static CompoundTag writeCrater(Crater crater) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID_KEY, crater.id());
        tag.putInt(X_KEY, crater.center().x());
        tag.putInt(Y_KEY, crater.center().y());
        tag.putInt(Z_KEY, crater.center().z());
        tag.putInt(RADIUS_KEY, crater.radius());
        tag.putInt(LEVEL_KEY, crater.groundLevel());
        tag.putLong(TICK_KEY, crater.createdTick());
        return tag;
    }

    private static Crater readCrater(CompoundTag tag) {
        return new Crater(tag.getUUID(ID_KEY),
                new BlockCoord(tag.getInt(X_KEY), tag.getInt(Y_KEY), tag.getInt(Z_KEY)),
                tag.getInt(RADIUS_KEY), tag.getInt(LEVEL_KEY), tag.getLong(TICK_KEY));
    }

    public void add(Crater crater) {
        Objects.requireNonNull(crater, "Воронка не может быть null: без неё нечего запоминать");
        craters.removeIf(existing -> existing.id().equals(crater.id()));
        craters.add(crater);
        if (craters.size() > MAX_CRATERS) {
            craters.remove(0);
        }
        setDirty();
    }

    public Optional<Crater> byId(UUID id) {
        Objects.requireNonNull(id, "Id не может быть null: без него воронку не найти");
        return craters.stream().filter(crater -> crater.id().equals(id)).findFirst();
    }

    public Optional<Crater> nearest(BlockCoord pos) {
        Objects.requireNonNull(pos, "Позиция не может быть null: без неё воронку не найти");
        Crater covering = null;
        Crater closest = null;
        long coveringDistance = Long.MAX_VALUE;
        long closestDistance = Long.MAX_VALUE;
        for (Crater crater : craters) {
            long distance = distanceSquared(crater.center(), pos);
            if (crater.covers(pos) && distance < coveringDistance) {
                covering = crater;
                coveringDistance = distance;
            }
            if (distance < closestDistance) {
                closest = crater;
                closestDistance = distance;
            }
        }
        return Optional.ofNullable(covering != null ? covering : closest);
    }

    public List<Crater> all() {
        return List.copyOf(craters);
    }

    public int size() {
        return craters.size();
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }
}