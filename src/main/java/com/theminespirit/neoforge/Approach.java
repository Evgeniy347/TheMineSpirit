package com.theminespirit.neoforge;

import com.theminespirit.core.movement.GotoTuning;
import com.theminespirit.core.movement.PathRefreshPolicy;
import com.theminespirit.core.movement.StuckWatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
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
    private int deadEndTicks;
    private boolean active;
    private PathMode mode = PathMode.NORMAL;
    private String refusal;

    public Approach(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public void begin(Vec3 target, double arrival) {
        begin(target, arrival, PathMode.NORMAL);
    }

    public void begin(Vec3 target, double arrival, PathMode pathMode) {
        mode = pathMode;
        start(target, null, arrival);
    }

    public void begin(Entity target, double arrival) {
        mode = PathMode.NORMAL;
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
            if (mode == PathMode.CAREFUL && spirit.onGround()) {
                refusal = CarefulPath.refusal(spirit.getNavigation().getPath());
                if (refusal != null) {
                    finish();
                    return State.STUCK;
                }
            }
        }
        String deadEnd = deadEndRefusal(target);
        if (deadEnd != null) {
            deadEndTicks++;
            if (deadEndTicks >= GotoTuning.DEAD_END_CONFIRM_TICKS) {
                refusal = deadEnd;
                finish();
                return State.STUCK;
            }
        } else {
            deadEndTicks = 0;
        }
        return State.MOVING;
    }

    public void cancel() {
        if (active) {
            finish();
        }
    }

    public void suspend() {
        if (active) {
            spirit.getNavigation().stop();
        }
    }

    public boolean isActive() {
        return active;
    }

    public int timeoutTicks() {
        return timeoutTicks;
    }

    public String refusal() {
        return refusal;
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
        deadEndTicks = 0;
        refusal = null;
        active = true;
    }

    private Vec3 currentTarget() {
        return targetEntity == null ? targetPoint : targetEntity.position();
    }

    private String deadEndRefusal(Vec3 target) {
        if (targetEntity != null || mode == PathMode.CAREFUL) {
            return null;
        }
        if (spirit.position().distanceTo(target) > GotoTuning.EARLY_REFUSAL_RANGE) {
            return null;
        }
        Path path = spirit.getNavigation().getPath();
        if (path == null || !spirit.getNavigation().isDone()) {
            return null;
        }
        Vec3 end = new Vec3(path.getEndNode().x + 0.5, path.getEndNode().y, path.getEndNode().z + 0.5);
        if (end.distanceTo(target) <= arrival) {
            return null;
        }
        if (spirit.position().distanceTo(end) > arrival) {
            return null;
        }
        return "путь упёрся в тупик в пределах " + GotoTuning.EARLY_REFUSAL_RANGE
                + " блоков от цели: стоянка за преградой";
    }

    private void moveToTarget(Vec3 target) {
        spirit.getNavigation().moveTo(target.x, target.y, target.z, 0, MOVE_SPEED);
    }

    private void finish() {
        spirit.getNavigation().stop();
        active = false;
    }
}
