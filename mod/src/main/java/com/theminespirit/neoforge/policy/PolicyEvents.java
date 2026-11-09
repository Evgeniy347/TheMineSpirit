package com.theminespirit.neoforge.policy;

import com.theminespirit.neoforge.board.PlotBreakLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public final class PolicyEvents {
    private PolicyEvents() {}

    public static void register(ModContainer container, IEventBus modEventBus) {
        PolicyConfig.register(container, modEventBus);
        NeoForge.EVENT_BUS.register(PolicyEvents.class);
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player && event.getLevel() instanceof ServerLevel level) {
            PlacedBlocksData.get(level).mark(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PlacedBlocksData data = PlacedBlocksData.get(level);
            PlotBreakLog.brokenBy(level, event.getPos(), event.getPlayer(), "BREAK", "команда игрока");
            data.unmark(event.getPos());
            data.unmarkSpirit(event.getPos());
            data.unmarkTemporary(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PlacedBlocksData data = PlacedBlocksData.get(level);
            event.getAffectedBlocks().forEach(pos -> {
                PlotBreakLog.brokenBy(level, pos, null, "EXPLOSION", "взрыв");
                data.unmark(pos);
                data.unmarkSpirit(pos);
                data.unmarkTemporary(pos);
            });
        }
    }
}
