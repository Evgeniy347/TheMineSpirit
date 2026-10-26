package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.inventory.ContainerOpenHold;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class ContainerOpenService {
    private static final int HOLD_TICKS = 10;

    private static ContainerOpenService instance;

    private final Map<ServerLevel, ContainerOpenHold> holds = new HashMap<>();

    private ContainerOpenService() {}

    public static void register() {
        if (instance != null) {
            throw new IllegalStateException(
                    "ContainerOpenService уже зарегистрирован: второй экземпляр удвоил бы анимацию контейнеров");
        }
        ContainerOpenService service = new ContainerOpenService();
        NeoForge.EVENT_BUS.register(service);
        instance = service;
    }

    public static void hold(SpiritEntity spirit, BlockPos pos) {
        if (spirit.level() instanceof ServerLevel level) {
            instance().touch(level, pos.immutable(), spirit.getUUID());
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tick(level);
        }
    }

    private static ContainerOpenService instance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "ContainerOpenService не зарегистрирован: мод обязан зарегистрировать его в TheMineSpirit");
        }
        return instance;
    }

    private void touch(ServerLevel level, BlockPos pos, UUID spirit) {
        ContainerOpenHold hold = holds.computeIfAbsent(level, key -> new ContainerOpenHold(HOLD_TICKS));
        BlockCoord coord = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        boolean opened = hold.touch(coord, spirit, level.getGameTime());
        int openers = hold.openers(coord);
        if (!opened) {
            ContainerLid.keep(level, pos, openers);
            return;
        }
        int players = ContainerLid.playerOpeners(level, pos);
        ContainerLid.begin(level, pos, openers + players, players == 0);
    }

    private void tick(ServerLevel level) {
        ContainerOpenHold hold = holds.get(level);
        if (hold == null) {
            return;
        }
        List<BlockCoord> closing = hold.tick(level.getGameTime());
        for (BlockCoord coord : closing) {
            ContainerLid.close(level, new BlockPos(coord.x(), coord.y(), coord.z()));
        }
        if (hold.isEmpty()) {
            holds.remove(level);
        }
    }
}
