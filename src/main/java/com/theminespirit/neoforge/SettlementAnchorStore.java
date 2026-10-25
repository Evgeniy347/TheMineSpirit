package com.theminespirit.neoforge;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

final class SettlementAnchorStore extends SavedData {
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_settlement_anchors";
    private static final String ENTRIES = "Entries";
    private static final String ID = "Id";
    private static final String HOME = "Home";
    private static final String CAMP = "Camp";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";
    private static final SavedData.Factory<SettlementAnchorStore> FACTORY =
            new SavedData.Factory<>(SettlementAnchorStore::new, SettlementAnchorStore::load);

    private final Map<UUID, Spot> spots = new ConcurrentHashMap<>();
    private Set<BlockCoord> anchors = Set.of();

    private SettlementAnchorStore() {}

    static SettlementAnchorStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    void record(UUID spirit, Optional<BlockCoord> home, Optional<BlockCoord> camp) {
        Objects.requireNonNull(spirit, "Дух без UUID не задаёт якорь поселения");
        Spot next = new Spot(home, camp);
        if (next.isEmpty()) {
            if (spots.remove(spirit) != null) {
                rebuild();
            }
            return;
        }
        if (!next.equals(spots.put(spirit, next))) {
            rebuild();
        }
    }

    void remove(UUID spirit) {
        if (spots.remove(spirit) != null) {
            rebuild();
        }
    }

    Set<BlockCoord> anchors() {
        return anchors;
    }

    private void rebuild() {
        Set<BlockCoord> unique = new LinkedHashSet<>();
        for (Spot spot : spots.values()) {
            spot.home().ifPresent(unique::add);
            spot.camp().ifPresent(unique::add);
        }
        anchors = Collections.unmodifiableSet(unique);
        setDirty();
    }

    private static SettlementAnchorStore load(CompoundTag tag, HolderLookup.Provider ignoredRegistries) {
        SettlementAnchorStore store = new SettlementAnchorStore();
        ListTag entries = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID id = NbtUtils.loadUUID(entry.get(ID));
            Spot spot = new Spot(point(entry, HOME), point(entry, CAMP));
            if (!spot.isEmpty()) {
                store.spots.put(id, spot);
            }
        }
        store.rebuild();
        store.setDirty(false);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, Spot> entry : spots.entrySet()) {
            CompoundTag stored = new CompoundTag();
            stored.put(ID, NbtUtils.createUUID(entry.getKey()));
            entry.getValue().home().ifPresent(home -> stored.put(HOME, writePoint(home)));
            entry.getValue().camp().ifPresent(camp -> stored.put(CAMP, writePoint(camp)));
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

    private static Optional<BlockCoord> point(CompoundTag entry, String key) {
        if (!entry.contains(key, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag point = entry.getCompound(key);
        return Optional.of(new BlockCoord(point.getInt(X), point.getInt(Y), point.getInt(Z)));
    }

    private record Spot(Optional<BlockCoord> home, Optional<BlockCoord> camp) {
        Spot {
            Objects.requireNonNull(home, "Дом якоря задаётся Optional.empty(), а не null");
            Objects.requireNonNull(camp, "Лагерь якоря задаётся Optional.empty(), а не null");
        }

        boolean isEmpty() {
            return home.isEmpty() && camp.isEmpty();
        }
    }
}
