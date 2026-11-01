package com.theminespirit.neoforge.policy;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.BreathSearch;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.ReflexAction;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class ReflexExecutor {
    static final double HOP_DISTANCE = 2.5;
    static final double RUN_SPEED = 1.3;
    static final double LAVA_PUSH = 0.15;
    static final double SWIM_UP = 0.16;
    static final double SWIM_STEER = 0.12;
    static final int BREATH_REFRESH_TICKS = 20;

    private static final Logger LOGGER = LogUtils.getLogger();

    private BlockCoord lastTarget;
    private Optional<BlockCoord> breath = Optional.empty();
    private int breathAge = BREATH_REFRESH_TICKS;
    private int attempts;

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
        attempts = 0;
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

    private void surface(SpiritEntity spirit) {
        if (++breathAge >= BREATH_REFRESH_TICKS || breath.isEmpty()) {
            refreshBreath(spirit);
        }
        breath.ifPresent(cell -> swimTo(spirit, cell));
        swimUp(spirit);
    }

    private void refreshBreath(SpiritEntity spirit) {
        breathAge = 0;
        if (attempts == 0) {
            spirit.getNavigation().stop();
        }
        attempts++;
        BlockCoord feet = new BlockCoord(
                spirit.blockPosition().getX(),
                spirit.blockPosition().getY(),
                spirit.blockPosition().getZ());
        LevelSafetyView view = new LevelSafetyView(spirit.level());
        Optional<BlockCoord> air = BreathSearch.nearest(view, feet);
        breath = BreathSearch.nextStep(view, feet);
        LOGGER.info(
                "[air] дух {}: всплытие, попытка {}, ноги {}, запас воздуха {}, шаг {}, воздух {}, расстояние {}, "
                        + "навигация завершена {}",
                spirit.getName().getString(),
                attempts,
                feet,
                spirit.getAirSupply(),
                breath.map(BlockCoord::toString).orElse("не найден — всплываю вертикально"),
                air.map(BlockCoord::toString).orElse("не найден"),
                air.map(cell -> String.format(Locale.ROOT, "%.1f", distance(feet, cell)))
                        .orElse("-"),
                spirit.getNavigation().isDone());
    }

    private void swimTo(SpiritEntity spirit, BlockCoord cell) {
        double dx = cell.x() + 0.5 - spirit.getX();
        double dz = cell.z() + 0.5 - spirit.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        Vec3 motion = spirit.getDeltaMovement();
        double vx = horizontal > 0.5 ? dx / horizontal * SWIM_STEER : motion.x;
        double vz = horizontal > 0.5 ? dz / horizontal * SWIM_STEER : motion.z;
        spirit.setDeltaMovement(vx, motion.y, vz);
    }

    private static double distance(BlockCoord from, BlockCoord to) {
        double dx = from.x() - to.x();
        double dy = from.y() - to.y();
        double dz = from.z() - to.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void swimUp(SpiritEntity spirit) {
        if (spirit.isInWater()) {
            Vec3 motion = spirit.getDeltaMovement();
            spirit.setDeltaMovement(motion.x, Math.max(motion.y, SWIM_UP), motion.z);
            spirit.getJumpControl().jump();
            return;
        }
        if (spirit.isInLava()) {
            spirit.getJumpControl().jump();
        }
    }
}
