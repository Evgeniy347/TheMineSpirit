package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;

public final class More5Events {
    private static final double SETTLEMENT_RADIUS = 64.0;

    private More5Events() {}

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus не может быть null: без шины сломанные мобами блоки не запомнятся");
        }
        bus.addListener(More5Events::onDestroy);
    }

    private static void onDestroy(LivingDestroyBlockEvent event) {
        if (!(event.getEntity() instanceof Monster) || event.getEntity().isRemoved()) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (level.getEntitiesOfClass(SpiritEntity.class, new AABB(pos).inflate(SETTLEMENT_RADIUS))
                .isEmpty()) {
            return;
        }
        String block =
                BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
        More5Store.get(level)
                .noteBroken(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()), block, level.getGameTime());
    }
}
