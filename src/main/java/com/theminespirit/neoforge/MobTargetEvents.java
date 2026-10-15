package com.theminespirit.neoforge;

import com.theminespirit.core.tactics.SpiritTargets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class MobTargetEvents {
    private static final int DEFAULT_TARGET_PRIORITY = 2;

    private MobTargetEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(MobTargetEvents::onJoinLevel);
    }

    private static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!SpiritConfig.mobsTargetSpirits() || !attacksSpirits(mob) || alreadyTargetsSpirits(mob)) {
            return;
        }
        mob.targetSelector.addGoal(targetPriority(mob), new SpiritAttackGoal(mob));
    }

    private static boolean attacksSpirits(Mob mob) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        return SpiritTargets.attacksSpirits(type);
    }

    private static boolean alreadyTargetsSpirits(Mob mob) {
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof SpiritAttackGoal) {
                return true;
            }
        }
        return false;
    }

    private static int targetPriority(Mob mob) {
        int lowest = Integer.MAX_VALUE;
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof NearestAttackableTargetGoal<?>) {
                lowest = Math.min(lowest, wrapped.getPriority());
            }
        }
        return lowest == Integer.MAX_VALUE ? DEFAULT_TARGET_PRIORITY : lowest;
    }

    private static final class SpiritAttackGoal extends NearestAttackableTargetGoal<SpiritEntity> {
        private SpiritAttackGoal(Mob mob) {
            super(mob, SpiritEntity.class, true);
        }
    }
}