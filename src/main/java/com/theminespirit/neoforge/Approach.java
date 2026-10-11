package com.theminespirit.neoforge;

import com.theminespirit.core.movement.GotoTuning;
import com.theminespirit.core.movement.PathRefreshPolicy;
import com.theminespirit.core.movement.StuckWatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Approach {
    public enum State {
        MOVING,
        ARRIVED,
        STUCK,
        TIMEOUT
    }

    private static final double MOVE_SPEED = 1.0;

    private final SpiritEntity spirit;
    private PathRefreshPolicy pathRefresh;
    private StuckWatcher stuckWatcher;
    private Vec3 targetPoint;
    private Entity targetEntity;
    private double arrival;
    private int timeoutTicks;
    private int elapsed;
    private boolean active;

    public Approach(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public void begin(Vec3 target, double arrival) {
        start(target, null, arrival);
    }

    public void begin(Entity target, double arrival) {
        start(target.position(), target, arrival);
    }

    public State tick() {
        if (!active) {
            throw new IllegalStateException("Подход не начат: сначала begin(...), иначе дух не знает, куда идти");
        }
        Vec3 target = currentTarget();
        if (spirit.position().distanceTo(target) <= arrival) {
            finish();
            return State.ARRIVED;
        }
        if (elapsed++ >= timeoutTicks) {
            finish();
            return State.TIMEOUT;
        }
        if (stuckWatcher.observe(spirit.getX(), spirit.getY(), spirit.getZ())) {
            finish();
            return State.STUCK;
        }
        if (pathRefresh.shouldRefresh(target.x, target.y, target.z, spirit.getNavigation().isDone())) {
            moveToTarget(target);
            spirit.countPathBuild();
        }
        return State.MOVING;
    }

    public void cancel() {
        if (active) {
            finish();
        }
    }

    public boolean isActive() {
        return active;
    }

    public int timeoutTicks() {
        return timeoutTicks;
    }

    private void start(Vec3 point, Entity entity, double arrivalDistance) {
        if (active) {
            spirit.getNavigation().stop();
        }
        pathRefresh = new PathRefreshPolicy();
        stuckWatcher = new StuckWatcher(GotoTuning.STUCK_MIN_MOVEMENT, GotoTuning.STUCK_TICKS);
        targetPoint = point;
        targetEntity = entity;
        arrival = arrivalDistance;
        timeoutTicks = GotoTuning.timeoutTicks(spirit.position().distanceTo(point));
        elapsed = 0;
        active = true;
    }

    private Vec3 currentTarget() {
        return targetEntity == null ? targetPoint : targetEntity.position();
    }

    private void moveToTarget(Vec3 target) {
        spirit.getNavigation().moveTo(target.x, target.y, target.z, 0, MOVE_SPEED);
    }

    private void finish() {
        spirit.getNavigation().stop();
        active = false;
    }
}
