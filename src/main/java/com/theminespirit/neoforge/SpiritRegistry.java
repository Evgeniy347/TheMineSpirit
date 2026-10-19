package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import java.util.Collections;
import java.util.HashSet;
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
    private static final SavedData.Factory<SpiritRegistry> FACTORY =
            new SavedData.Factory<>(SpiritRegistry::new, SpiritRegistry::load);

    private final Set<UUID> alive = new HashSet<>();

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

    void markAlive(UUID id) {
        if (alive.add(id)) {
            setDirty();
        }
    }

    void markDead(UUID id) {
        if (alive.remove(id)) {
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
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (UUID id : alive) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put(ALIVE_KEY, list);
        return tag;
    }
}
