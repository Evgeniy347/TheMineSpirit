package com.theminespirit.neoforge.rest;

import com.mojang.logging.LogUtils;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class SpiritIndex {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MinecraftServer server;
    private final Map<UUID, SpiritEntity> spirits = new LinkedHashMap<>();
    private final TickEntityCache<SpiritEntity> levelCache = new TickEntityCache<>();
    private final Consumer<EntityJoinLevelEvent> joinListener = this::onJoin;
    private final Consumer<EntityLeaveLevelEvent> leaveListener = this::onLeave;
    private long entriesRead;

    public SpiritIndex(MinecraftServer server) {
        this.server = server;
    }

    public void register(IEventBus bus) {
        bus.addListener(joinListener);
        bus.addListener(leaveListener);
    }

    public void unregister(IEventBus bus) {
        bus.unregister(joinListener);
        bus.unregister(leaveListener);
    }

    public List<SpiritEntity> snapshot() {
        ensureServerThread();
        List<SpiritEntity> live = new ArrayList<>(spirits.size());
        for (Map.Entry<UUID, SpiritEntity> entry : spirits.entrySet()) {
            SpiritEntity spirit = canonical(entry.getKey(), entry.getValue());
            if (spirit == null) {
                continue;
            }
            if (!spirit.equals(entry.getValue())) {
                LOGGER.warn(
                        "[bots] индекс духа {} хранил сущность вне мира в {}: беру живую из мира в {}",
                        spirit.displayName(),
                        entry.getValue().blockPosition(),
                        spirit.blockPosition());
                entry.setValue(spirit);
            }
            live.add(spirit);
        }
        entriesRead += live.size();
        return live;
    }

    public Optional<SpiritEntity> find(UUID id) {
        ensureServerThread();
        entriesRead++;
        SpiritEntity stored = spirits.get(id);
        if (stored == null) {
            return Optional.empty();
        }
        SpiritEntity spirit = canonical(id, stored);
        if (spirit == null) {
            spirits.remove(id);
            return Optional.empty();
        }
        if (!spirit.equals(stored)) {
            LOGGER.warn(
                    "[bots] индекс духа {} хранил сущность вне мира в {}: беру живую из мира в {}",
                    spirit.displayName(),
                    stored.blockPosition(),
                    spirit.blockPosition());
            spirits.put(id, spirit);
        }
        return Optional.of(spirit);
    }

    public long entriesRead() {
        return entriesRead;
    }

    private void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof SpiritEntity spirit)) {
            return;
        }
        ensureServerThread();
        invalidateLevelCache();
        SpiritEntity live = fromLevels(spirit.getUUID());
        if (live != null && !live.equals(spirit)) {
            LOGGER.warn(
                    "[bots] отклонённая копия духа {} в {} не попала в индекс: живая в {}",
                    spirit.displayName(),
                    spirit.blockPosition(),
                    live.blockPosition());
            return;
        }
        spirits.put(spirit.getUUID(), spirit);
    }

    private void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof SpiritEntity spirit)) {
            return;
        }
        ensureServerThread();
        invalidateLevelCache();
        spirits.remove(spirit.getUUID(), spirit);
    }

    private SpiritEntity canonical(UUID id, SpiritEntity stored) {
        SpiritEntity live = fromLevels(id);
        return live != null ? live : stored.isRemoved() ? null : stored;
    }

    private @Nullable SpiritEntity fromLevels(UUID id) {
        return levelCache.get(id, server.getTickCount(), this::scanLevels).orElse(null);
    }

    private @Nullable SpiritEntity scanLevels(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof SpiritEntity spirit) {
                return spirit;
            }
        }
        return null;
    }

    private void invalidateLevelCache() {
        levelCache.invalidate();
    }

    private void ensureServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Индекс духов читается и меняется только на серверном потоке, а вызван из "
                    + "потока '" + Thread.currentThread().getName() + "': HTTP-потоки обращаются к миру через "
                    + "ServerThreadCall, иначе состояние сущностей читается в гонке с тиком");
        }
    }
}
