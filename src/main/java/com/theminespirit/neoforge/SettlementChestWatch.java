package com.theminespirit.neoforge;

import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class SettlementChestWatch {
    private SettlementChestWatch() {}

    public static void register(IEventBus bus) {
        Objects.requireNonNull(bus, "Шина не может быть null: без неё реестр не узнает о сундуках");
        bus.addListener(SettlementChestWatch::onPlace);
        bus.addListener(SettlementChestWatch::onBreak);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ContainerAccess.allowed(event.getPlacedBlock())) {
            return;
        }
        SettlementChestStore.get(level).track(event.getPos());
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ContainerAccess.allowed(event.getState())) {
            return;
        }
        SettlementChestStore.get(level).forget(event.getPos());
    }

    static void placed(ServerLevel level, BlockPos pos, BlockState state) {
        if (ContainerAccess.allowed(state)) {
            SettlementChestStore.get(level).track(pos);
        }
    }

    static void broken(ServerLevel level, BlockPos pos, BlockState state) {
        if (ContainerAccess.allowed(state)) {
            SettlementChestStore.get(level).forget(pos);
        }
    }
}
