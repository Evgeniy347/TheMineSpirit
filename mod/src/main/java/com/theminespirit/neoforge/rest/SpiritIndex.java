package com.theminespirit.neoforge.rest;

import com.mojang.logging.LogUtils;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

public final class SpiritIndex {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MinecraftServer server;
    private long readTick = Long.MIN_VALUE;
    private List<SpiritEntity> live = List.of();
    private Map<UUID, SpiritEntity> byId = Map.of();

    public SpiritIndex(MinecraftServer server) {
        this.server = server;
    }

    public List<SpiritEntity> snapshot() {
        ensureServerThread();
        census();
        return live;
    }

    public Optional<SpiritEntity> find(UUID id) {
        ensureServerThread();
        census();
        return Optional.ofNullable(byId.get(id));
    }

    private void census() {
        long tick = server.getTickCount();
        if (tick == readTick) {
            return;
        }
        List<SpiritEntity> found = new ArrayList<>();
        Map<UUID, SpiritEntity> ids = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof SpiritEntity spirit) || spirit.isRemoved()) {
                    continue;
                }
                SpiritEntity previous = ids.putIfAbsent(spirit.getUUID(), spirit);
                if (previous != null) {
                    LOGGER.warn(
                            "[bots] в мире две сущности духа {}: в списке остаётся одна в {}, вторая в {}",
                            spirit.displayName(),
                            previous.blockPosition(),
                            spirit.blockPosition());
                    continue;
                }
                found.add(spirit);
            }
        }
        live = List.copyOf(found);
        byId = Map.copyOf(ids);
        readTick = tick;
    }

    private void ensureServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Индекс духов читается только на серверном потоке, а вызван из "
                    + "потока '" + Thread.currentThread().getName() + "': HTTP-потоки обращаются к миру через "
                    + "ServerThreadCall, иначе состояние сущностей читается в гонке с тиком");
        }
    }
}
