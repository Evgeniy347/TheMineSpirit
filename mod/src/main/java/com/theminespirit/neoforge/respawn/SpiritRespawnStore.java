package com.theminespirit.neoforge.respawn;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class SpiritRespawnStore extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_spirit_respawns";
    private static final String ENTRIES = "Entries";
    private static final String ID = "Id";
    private static final String NAME = "Name";
    private static final String HOME = "Home";
    private static final String BASE = "Base";
    private static final String DEATH = "Death";
    private static final String CAUSE = "Cause";
    private static final String DUE = "Due";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";
    private static final SavedData.Factory<SpiritRespawnStore> FACTORY =
            new SavedData.Factory<>(SpiritRespawnStore::new, SpiritRespawnStore::load);

    private final Map<UUID, Pending> pending = new LinkedHashMap<>();

    private SpiritRespawnStore() {}

    public static SpiritRespawnStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<Pending> pending(UUID spirit) {
        Objects.requireNonNull(spirit, "UUID духа не может быть null: по нему ищется отложенное возрождение");
        return Optional.ofNullable(pending.get(spirit));
    }

    public void add(Pending entry) {
        Objects.requireNonNull(entry, "Запись возрождения не может быть null: класть в очередь нечего");
        if (pending.putIfAbsent(entry.id(), entry) == null) {
            setDirty();
        }
    }

    public void remove(UUID spirit) {
        if (pending.remove(spirit) != null) {
            setDirty();
        }
    }

    public void reschedule(UUID spirit, long due) {
        Pending entry = pending.get(spirit);
        if (entry != null && entry.due() != due) {
            pending.put(spirit, entry.withDue(due));
            setDirty();
        }
    }

    public List<Pending> due(long now) {
        List<Pending> ready = new ArrayList<>();
        for (Pending entry : pending.values()) {
            if (entry.due() <= now) {
                ready.add(entry);
            }
        }
        ready.sort(Comparator.comparingLong(Pending::due)
                .thenComparing(entry -> entry.id().toString()));
        return ready;
    }

    public void clear() {
        if (!pending.isEmpty()) {
            pending.clear();
            setDirty();
        }
    }

    private static SpiritRespawnStore load(CompoundTag tag, HolderLookup.Provider ignoredRegistries) {
        SpiritRespawnStore store = new SpiritRespawnStore();
        ListTag entries = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            Pending restored = new Pending(
                    NbtUtils.loadUUID(entry.get(ID)),
                    entry.getString(NAME),
                    readPoint(entry, HOME),
                    readPoint(entry, BASE)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Запись возрождения без базы: духа некуда возвращать, проверьте " + DATA_NAME)),
                    readPoint(entry, DEATH)
                            .orElseThrow(
                                    () -> new IllegalStateException(
                                            "Запись возрождения без места смерти: строка [respawn] не расскажет, что случилось")),
                    entry.getString(CAUSE),
                    entry.getLong(DUE));
            store.pending.put(restored.id(), restored);
        }
        store.setDirty(false);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Pending entry : pending.values()) {
            CompoundTag stored = new CompoundTag();
            stored.put(ID, NbtUtils.createUUID(entry.id()));
            stored.putString(NAME, entry.name());
            stored.putString(CAUSE, entry.cause());
            stored.putLong(DUE, entry.due());
            entry.home().ifPresent(home -> stored.put(HOME, writePoint(home)));
            stored.put(BASE, writePoint(entry.base()));
            stored.put(DEATH, writePoint(entry.death()));
            entries.add(stored);
        }
        tag.put(ENTRIES, entries);
        return tag;
    }

    private static CompoundTag writePoint(BlockCoord point) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(X, point.x());
        tag.putInt(Y, point.y());
        tag.putInt(Z, point.z());
        return tag;
    }

    private static Optional<BlockCoord> readPoint(CompoundTag entry, String key) {
        if (!entry.contains(key, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag point = entry.getCompound(key);
        return Optional.of(new BlockCoord(point.getInt(X), point.getInt(Y), point.getInt(Z)));
    }

    public record Pending(
            UUID id,
            String name,
            Optional<BlockCoord> home,
            BlockCoord base,
            BlockCoord death,
            String cause,
            long due) {
        public Pending {
            Objects.requireNonNull(id, "UUID духа не может быть null: возрождённый дух обязан сохранить личность");
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Имя погибшего духа пусто: дух поселения не может стать безымянным");
            }
            Objects.requireNonNull(home, "Дом задаётся Optional.empty(), а не null: у духа может не быть дома");
            Objects.requireNonNull(base, "Точка базы не может быть null: возрождать духа некуда");
            Objects.requireNonNull(death, "Место смерти не может быть null: по нему ищут выпавший инвентарь");
            if (cause == null || cause.isBlank()) {
                throw new IllegalArgumentException("Причина смерти пуста: строка [respawn] обязана её назвать");
            }
            if (due < 0) {
                throw new IllegalArgumentException("Срок возрождения " + due + " отрицателен: время мира идёт вперёд");
            }
        }

        Pending withDue(long next) {
            return new Pending(id, name, home, base, death, cause, next);
        }
    }
}
