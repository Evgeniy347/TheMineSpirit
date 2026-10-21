package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

final class SpiritRegistry extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_spirits";
    private static final String ALIVE_KEY = "alive";
    private static final String POSITIONS_KEY = "positions";
    private static final String ID_KEY = "Id";
    private static final String X_KEY = "X";
    private static final String Y_KEY = "Y";
    private static final String Z_KEY = "Z";
    private static final SavedData.Factory<SpiritRegistry> FACTORY =
            new SavedData.Factory<>(SpiritRegistry::new, SpiritRegistry::load);

    private final Set<UUID> alive = new HashSet<>();
    private final Map<UUID, BlockCoord> positions = new HashMap<>();

    private SpiritRegistry() {}

    static SpiritRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    static Optional<SpiritRegistry> find(ServerLevel level) {
        return Optional.ofNullable(level.getDataStorage().get(FACTORY, DATA_NAME));
    }

    Set<UUID> alive() {
        return Collections.unmodifiableSet(alive);
    }

    Map<UUID, BlockCoord> positions() {
        return Collections.unmodifiableMap(positions);
    }

    void markAlive(UUID id, BlockCoord position) {
        boolean changed = alive.add(id);
        BlockCoord previous = positions.put(id, position);
        if (changed || !position.equals(previous)) {
            setDirty();
        }
    }

    void markDead(UUID id) {
        boolean changed = alive.remove(id);
        changed |= positions.remove(id) != null;
        if (changed) {
            setDirty();
        }
    }

    private static SpiritRegistry load(CompoundTag tag, HolderLookup.Provider ignoredRegistries) {
        SpiritRegistry registry = new SpiritRegistry();
        ListTag list = tag.getList(ALIVE_KEY, Tag.TAG_STRING);
        for (Tag element : list) {
            try {
                registry.alive.add(UUID.fromString(element.getAsString()));
            } catch (IllegalArgumentException broken) {
                LOGGER.warn("Битый UUID духа в реестре пропущен: {}", element.getAsString(), broken);
            }
        }
        ListTag positions = tag.getList(POSITIONS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < positions.size(); i++) {
            CompoundTag stored = positions.getCompound(i);
            try {
                UUID id = UUID.fromString(stored.getString(ID_KEY));
                if (registry.alive.contains(id)) {
                    registry.positions.put(
                            id, new BlockCoord(stored.getInt(X_KEY), stored.getInt(Y_KEY), stored.getInt(Z_KEY)));
                }
            } catch (IllegalArgumentException broken) {
                LOGGER.warn("Битый UUID духа в координатах пропущен: {}", stored.getString(ID_KEY), broken);
            }
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (UUID id : alive) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put(ALIVE_KEY, list);
        ListTag positions = new ListTag();
        for (Map.Entry<UUID, BlockCoord> entry : this.positions.entrySet()) {
            CompoundTag stored = new CompoundTag();
            stored.putString(ID_KEY, entry.getKey().toString());
            stored.putInt(X_KEY, entry.getValue().x());
            stored.putInt(Y_KEY, entry.getValue().y());
            stored.putInt(Z_KEY, entry.getValue().z());
            positions.add(stored);
        }
        tag.put(POSITIONS_KEY, positions);
        return tag;
    }
}
