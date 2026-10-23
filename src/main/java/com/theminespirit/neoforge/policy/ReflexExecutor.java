package com.theminespirit.neoforge.policy;

import com.theminespirit.core.mining.BreathSearch;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.ReflexAction;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

final class ReflexExecutor {
    static final double HOP_DISTANCE = 2.5;
    static final double RUN_SPEED = 1.3;
    static final double LAVA_PUSH = 0.15;
    static final int BREATH_REFRESH_TICKS = 20;

    private BlockCoord lastTarget;
    private Optional<BlockCoord> breath = Optional.empty();
    private int breathAge = BREATH_REFRESH_TICKS;

    void execute(SpiritEntity spirit, ReflexAction action) {
        switch (action) {
            case ReflexAction.Flee flee -> moveTo(spirit, flee.cell());
            case ReflexAction.ExitWater exit -> {
                moveTo(spirit, exit.cell());
                swimUp(spirit);
            }
            case ReflexAction.StepOutOfPortal step -> moveTo(spirit, step.cell());
            case ReflexAction.ReturnToZone back -> moveTo(spirit, back.cell());
            case ReflexAction.Surface ignored -> surface(spirit);
            case ReflexAction.StopMoving ignored -> release(spirit);
            case ReflexAction.Eat ignored -> spirit.hunger().eatBest();
        }
    }

    void release(SpiritEntity spirit) {
        spirit.getNavigation().stop();
        lastTarget = null;
        breathAge = BREATH_REFRESH_TICKS;
    }

    private void moveTo(SpiritEntity spirit, BlockCoord cell) {
        Vec3 target = new Vec3(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
        if (spirit.position().distanceTo(target) <= HOP_DISTANCE) {
            spirit.getNavigation().stop();
            spirit.getMoveControl().setWantedPosition(target.x, target.y, target.z, RUN_SPEED);
            pushThroughLava(spirit, target);
            lastTarget = cell;
            return;
        }
        if (!cell.equals(lastTarget) || spirit.getNavigation().isDone()) {
            spirit.getNavigation().moveTo(target.x, target.y, target.z, RUN_SPEED);
            lastTarget = cell;
        }
    }

    private void pushThroughLava(SpiritEntity spirit, Vec3 target) {
        if (spirit.isInLava()) {
            Vec3 toward = new Vec3(target.x - spirit.getX(), 0, target.z - spirit.getZ());
            if (toward.lengthSqr() > 0) {
                spirit.setDeltaMovement(
                        spirit.getDeltaMovement().add(toward.normalize().scale(LAVA_PUSH)));
            }
            spirit.getJumpControl().jump();
        }
    }

    // Аварийный подъём (INC-19): в затопленной шахте прыжок на месте не выносит к воздуху — дух плывёт к ближайшей
    // по воде клетке, где голова над водой.
    private void surface(SpiritEntity spirit) {
        if (++breathAge >= BREATH_REFRESH_TICKS) {
            breathAge = 0;
            BlockCoord feet = new BlockCoord(
                    spirit.blockPosition().getX(),
                    spirit.blockPosition().getY(),
                    spirit.blockPosition().getZ());
            breath = BreathSearch.nearest(new LevelSafetyView(spirit.level()), feet);
        }
        breath.ifPresent(cell -> moveTo(spirit, cell));
        swimUp(spirit);
    }

    private void swimUp(SpiritEntity spirit) {
        if (spirit.isInWater() || spirit.isInLava()) {
            spirit.getJumpControl().jump();
        }
    }
}
