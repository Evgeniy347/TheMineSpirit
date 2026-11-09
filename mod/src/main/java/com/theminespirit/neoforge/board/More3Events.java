package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritDamageWatch;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class More3Events {
    private More3Events() {}

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus не может быть null: без шины история опасных мест не соберётся");
        }
        bus.addListener(More3Events::onDamage);
        bus.addListener(More3Events::onDeath);
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof SpiritEntity spirit)
                || spirit.level().isClientSide()
                || event.getNewDamage() <= 0) {
            return;
        }
        if (!(spirit.level() instanceof ServerLevel level)) {
            return;
        }
        SpiritDamageWatch.note(
                spirit.getUUID(),
                source(event.getSource()),
                event.getNewDamage(),
                level.getGameTime(),
                DataTables.INSTANCE.graph().current().costs().life().damageWindowTicks());
        BlockPos at = spirit.blockPosition();
        More3Store.get(level)
                .noteDamage(
                        new BlockCoord(at.getX(), at.getY(), at.getZ()),
                        event.getSource().getMsgId(),
                        level.getGameTime());
    }

    private static String source(DamageSource source) {
        Entity attacker = source.getEntity();
        return attacker == null
                ? source.getMsgId()
                : BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString();
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof SpiritEntity spirit)
                || spirit.level().isClientSide()) {
            return;
        }
        if (!(spirit.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos at = spirit.blockPosition();
        More3Store.get(level).noteDeath(new BlockCoord(at.getX(), at.getY(), at.getZ()), level.getGameTime());
    }
}
