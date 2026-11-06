package com.theminespirit.neoforge;

import com.theminespirit.core.movement.GotoTuning;
import com.theminespirit.core.movement.PathRefreshPolicy;
import com.theminespirit.core.movement.StuckWatcher;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.movement.DeadEndRefusal;
import com.theminespirit.neoforge.movement.PassageWalk;
import com.theminespirit.neoforge.movement.PathNetworkService;
import com.theminespirit.neoforge.movement.StuckRescue;
import com.theminespirit.neoforge.policy.NightLeashGate;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class Approach {
    public enum State {
        MOVING,
        ARRIVED,
        STUCK,
        TIMEOUT
    }

    private static final double MOVE_SPEED = 1.0;
    private static final double NETWORK_GAP = 3.0;
    private static final int NETWORK_ATTEMPTS = 2;

    private final SpiritEntity spirit;
    private PassageWalk passages;
    private PathRefreshPolicy pathRefresh;
    private StuckWatcher stuckWatcher;
    private Vec3 targetPoint;
    private Entity targetEntity;
    private double arrival;
    private int timeoutTicks;
    private int elapsed;
    private int deadEndTicks;
    private final StuckRescue rescue;
    private boolean active;
    private PathMode mode = PathMode.NORMAL;
    private String refusal;
    private @Nullable NetworkLegs legs;
    private int networkAttempts;
    private int networkRoutes;

    public Approach(SpiritEntity spirit) {
        this.spirit = spirit;
        this.rescue = new StuckRescue(spirit);
    }

    public void begin(Vec3 target, double arrival) {
        begin(target, arrival, PathMode.NORMAL);
    }

    public void begin(Vec3 target, double arrival, PathMode pathMode) {
        mode = pathMode;
        start(target, null, arrival);
    }

    public void beginWithRoutes(Vec3 target, double arrival) {
        begin(target, arrival, PathMode.NORMAL);
        rescue.reset(true);
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
        String night = NightLeashGate.refusal(spirit, blockCoord(target));
        if (night != null) {
            refusal = night;
            finish();
            return State.STUCK;
        }
        if (elapsed++ >= timeoutTicks) {
            finish();
            return State.TIMEOUT;
        }
        if (rescue.walking()) {
            return State.MOVING;
        }
        boolean opened = passages.tick();
        if (stuckWatcher.observe(spirit.getX(), spirit.getY(), spirit.getZ())) {
            if (opened || passages.openToward(blockCoord(target))) {
                stuckWatcher = new StuckWatcher(GotoTuning.STUCK_MIN_MOVEMENT, GotoTuning.STUCK_TICKS);
                pathRefresh = new PathRefreshPolicy();
                return State.MOVING;
            }
            if (rescued(target)) {
                return State.MOVING;
            }
            finish();
            return State.STUCK;
        }
        Vec3 goal = goal(target);
        if (pathRefresh.shouldRefresh(
                goal.x, goal.y, goal.z, spirit.getNavigation().isDone())) {
            moveToTarget(goal);
            spirit.countPathBuild();
            Path path = spirit.getNavigation().getPath();
            String descent = mode == PathMode.CAREFUL && spirit.onGround()
                    ? CarefulPath.refusal(path)
                    : CarefulPath.descentRefusal(path, (int) Math.floor(spirit.getY()), (int) Math.floor(goal.y));
            if (descent != null) {
                if (mode != PathMode.CAREFUL && rescued(target)) return State.MOVING;
                refusal = descent;
                finish();
                return State.STUCK;
            }
            followNetwork(target);
        }
        String deadEnd =
                legs == null ? DeadEndRefusal.refusal(spirit, target, targetEntity != null, mode, arrival) : null;
        if (deadEnd != null) {
            deadEndTicks++;
            if (deadEndTicks >= GotoTuning.DEAD_END_CONFIRM_TICKS) {
                if (passages.openToward(blockCoord(target))) {
                    pathRefresh = new PathRefreshPolicy();
                    deadEndTicks = 0;
                    return State.MOVING;
                }
                if (rescued(target)) {
                    deadEndTicks = 0;
                    return State.MOVING;
                }
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

    public boolean detouring() {
        return active && rescue.routing();
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

    public String networkNote() {
        return "сеть: запросов " + networkAttempts + ", маршрутов " + networkRoutes;
    }

    private void start(Vec3 point, Entity entity, double arrivalDistance) {
        if (active) {
            spirit.getNavigation().stop();
        }
        pathRefresh = new PathRefreshPolicy();
        stuckWatcher = new StuckWatcher(GotoTuning.STUCK_MIN_MOVEMENT, GotoTuning.STUCK_TICKS);
        passages = new PassageWalk(spirit);
        targetPoint = point;
        targetEntity = entity;
        arrival = arrivalDistance;
        timeoutTicks = GotoTuning.timeoutTicks(spirit.position().distanceTo(point));
        elapsed = 0;
        deadEndTicks = 0;
        rescue.reset(false);
        refusal = null;
        legs = null;
        networkAttempts = 0;
        networkRoutes = 0;
        active = true;
    }

    private Vec3 goal(Vec3 target) {
        if (legs == null) {
            return target;
        }
        legs.advance(spirit.position());
        if (legs.done()) {
            legs = null;
            return target;
        }
        return legs.current();
    }

    private void followNetwork(Vec3 target) {
        if (mode != PathMode.NORMAL || targetEntity != null || !(spirit.level() instanceof ServerLevel level)) {
            return;
        }
        Path path = spirit.getNavigation().getPath();
        if (legs != null) {
            if (path == null && !spirit.onGround()) {
                return;
            }
            if (unreached(path, legs.current())) {
                PathNetworkService.legFailed(level, legs.from(), legs.to());
                legs = null;
                networkAttempts = NETWORK_ATTEMPTS;
            }
            return;
        }
        if (networkAttempts >= NETWORK_ATTEMPTS || !spirit.onGround() || !unreached(path, target)) {
            return;
        }
        networkAttempts++;
        Optional<List<BlockCoord>> route =
                PathNetworkService.route(level, blockCoord(spirit.position()), blockCoord(target));
        if (route.isEmpty()) {
            return;
        }
        networkRoutes++;
        NetworkLegs walk = new NetworkLegs(route.get());
        walk.advance(spirit.position());
        if (walk.done()) {
            return;
        }
        legs = walk;
        timeoutTicks = Math.max(
                timeoutTicks,
                elapsed
                        + GotoTuning.timeoutTicks(
                                walk.length() + spirit.position().distanceTo(target)));
        Vec3 next = walk.current();
        pathRefresh.shouldRefresh(next.x, next.y, next.z, false);
        moveToTarget(next);
        spirit.countPathBuild();
    }

    private static boolean unreached(@Nullable Path path, Vec3 goal) {
        if (path == null || path.getNodeCount() == 0) {
            return true;
        }
        if (path.canReach()) {
            return false;
        }
        Node end = path.getEndNode();
        return end == null || new Vec3(end.x + 0.5, end.y, end.z + 0.5).distanceTo(goal) > NETWORK_GAP;
    }

    private Vec3 currentTarget() {
        return targetEntity == null ? targetPoint : targetEntity.position();
    }

    private static BlockCoord blockCoord(Vec3 point) {
        return new BlockCoord((int) Math.floor(point.x), (int) Math.floor(point.y), (int) Math.floor(point.z));
    }

    private boolean rescued(Vec3 target) {
        if (!rescue.free(mode, blockCoord(target), (int) Math.floor(arrival), targetEntity != null)) {
            refusal = rescue.refusal();
            return false;
        }
        timeoutTicks = Math.max(timeoutTicks, elapsed + rescue.ticksBudget());
        stuckWatcher = new StuckWatcher(GotoTuning.STUCK_MIN_MOVEMENT, GotoTuning.STUCK_TICKS);
        pathRefresh = new PathRefreshPolicy();
        return true;
    }

    private void moveToTarget(Vec3 target) {
        spirit.getNavigation().moveTo(target.x, target.y, target.z, 0, MOVE_SPEED);
    }

    private void finish() {
        rescue.cancel();
        spirit.getNavigation().stop();
        passages.settle();
        active = false;
    }
}
