package com.theminespirit.neoforge.rest;

import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

public final class SpiritIndex {
    private final MinecraftServer server;
    private final Map<UUID, SpiritEntity> spirits = new LinkedHashMap<>();
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
        entriesRead += spirits.size();
        return new ArrayList<>(spirits.values());
    }

    public Optional<SpiritEntity> find(UUID id) {
        ensureServerThread();
        entriesRead++;
        return Optional.ofNullable(spirits.get(id));
    }

    public long entriesRead() {
        return entriesRead;
    }

    private void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof SpiritEntity spirit)) {
            return;
        }
        ensureServerThread();
        spirits.put(spirit.getUUID(), spirit);
    }

    private void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof SpiritEntity spirit)) {
            return;
        }
        ensureServerThread();
        spirits.remove(spirit.getUUID(), spirit);
    }

    private void ensureServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Индекс духов читается и меняется только на серверном потоке, а вызван из "
                    + "потока '" + Thread.currentThread().getName() + "': HTTP-потоки обращаются к миру через "
                    + "ServerThreadCall, иначе состояние сущностей читается в гонке с тиком");
        }
    }
}
