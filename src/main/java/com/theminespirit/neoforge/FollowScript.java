package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.PathRefreshPolicy;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.script.FollowParams;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

final class FollowScript implements ScriptInstance {
    private static final double MOVE_SPEED = 1.0;
    private static final int PICKUP_RADIUS = 4;
    private static final int PICKUP_COOLDOWN_TICKS = PickupTuning.REFRESH_TICKS;
    private static final double COMPANION_SEARCH_RADIUS = 64.0;

    private final SpiritEntity spirit;
    private final FollowParams params;
    private final PathRefreshPolicy pathRefresh = new PathRefreshPolicy();
    private DropCollector pickup;
    private BlockCoord pickupCenter;
    private int pickupCooldown;
    private SpiritEntity companion;
    private String failureReason;

    FollowScript(SpiritEntity spirit, FollowParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            stopNavigation();
            failureReason = "дух исчез во время сопровождения";
            return ScriptStatus.FAILED;
        }
        Entity target = findTarget();
        if (target == null) {
            stopNavigation();
            failureReason = "цель '" + params.target() + "' не найдена";
            return ScriptStatus.FAILED;
        }
        pickupTick();
        BlockPos targetCell = target.blockPosition();
        if (spirit.protectionGate().checkTravel(new BlockCoord(targetCell.getX(), targetCell.getY(), targetCell.getZ()))
                instanceof BlockProtection.Refused refused) {
            stopNavigation();
            failureReason = "цель вышла за границы рабочей зоны: " + refused.reason();
            return ScriptStatus.FAILED;
        }
        if (spirit.position().distanceTo(target.position()) <= params.radius()) {
            return ScriptStatus.RUNNING;
        }
        Vec3 targetPos = target.position();
        boolean pathFinished = spirit.getNavigation().isDone();
        if (pathRefresh.shouldRefresh(targetPos.x, targetPos.y, targetPos.z, pathFinished)) {
            if (spirit.protectionGate()
                            .checkPortalTarget(new BlockCoord(targetCell.getX(), targetCell.getY(), targetCell.getZ()))
                    instanceof BlockProtection.Refused refused) {
                stopNavigation();
                failureReason = refused.reason();
                return ScriptStatus.FAILED;
            }
            spirit.getNavigation().moveTo(target, MOVE_SPEED);
            spirit.countPathBuild();
        }
        return ScriptStatus.RUNNING;
    }

    @Override
    public void cancel() {
        stopNavigation();
        if (pickup != null) {
            pickup.cancel();
            pickup = null;
        }
    }

    @Override
    public void suspend() {
        stopNavigation();
        if (pickup != null) {
            pickup.suspend();
        }
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    private void pickupTick() {
        if (pickupCooldown > 0) {
            pickupCooldown--;
            return;
        }
        BlockPos pos = spirit.blockPosition();
        if (pickup != null
                && pickupCenter != null
                && (long) (pos.getX() - pickupCenter.x()) * (pos.getX() - pickupCenter.x())
                                + (long) (pos.getZ() - pickupCenter.z()) * (pos.getZ() - pickupCenter.z())
                        > PICKUP_RADIUS * PICKUP_RADIUS) {
            pickup.cancel();
            pickup = null;
        }
        if (pickup == null) {
            pickupCenter = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
            pickup = new DropCollector(
                    spirit,
                    IdFilter.matchAll(),
                    pickupCenter,
                    PICKUP_RADIUS,
                    PickupTuning.DEFAULT,
                    OverflowMode.BY_VALUE);
        }
        if (pickup.tick() != DropCollector.State.RUNNING) {
            pickup.cancel();
            pickup = null;
            pickupCooldown = PICKUP_COOLDOWN_TICKS;
        }
    }

    private void stopNavigation() {
        spirit.getNavigation().stop();
    }

    private Entity findTarget() {
        ServerPlayer player = findPlayer();
        if (player != null) {
            return player;
        }
        if (companion == null || companion.isRemoved()) {
            if (!(spirit.level() instanceof ServerLevel serverLevel)) {
                return null;
            }
            List<SpiritEntity> nearby = serverLevel.getEntitiesOfClass(
                    SpiritEntity.class, spirit.getBoundingBox().inflate(COMPANION_SEARCH_RADIUS));
            companion = nearby.stream()
                    .filter(other -> other != spirit && other.getStringUUID().equals(params.target()))
                    .findFirst()
                    .orElse(null);
        }
        return companion;
    }

    private ServerPlayer findPlayer() {
        if (!(spirit.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (player.getName().getString().equals(params.target())) {
                return player;
            }
        }
        return null;
    }
}
