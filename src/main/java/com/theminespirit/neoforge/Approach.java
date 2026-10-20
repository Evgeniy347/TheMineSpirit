package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.movement.GotoTuning;
import com.theminespirit.core.movement.PathRefreshPolicy;
import com.theminespirit.core.movement.StuckWatcher;
import com.theminespirit.core.movement.Unstuck;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.movement.PathNetworkService;
import com.theminespirit.neoforge.policy.NightLeashGate;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
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
    // Конец пути игры дальше 3 блоков от цели — путь не дошёл; ближе — обычный подход к сундуку или верстаку,
    // к самому блоку путь не ведёт.
    private static final double NETWORK_GAP = 3.0;
    private static final int NETWORK_ATTEMPTS = 2;
    // Сеть — при любом недошедшем пути, и вблизи: цель за стеной или этажом выше близка по прямой, а пешком — через
    // дверь или лестницу в другом конце дома, дальше поиска пути игры (дом игрока 3, дом семьи 3, 21.10).

    private final SpiritEntity spirit;
    private PathRefreshPolicy pathRefresh;
    private StuckWatcher stuckWatcher;
    private Vec3 targetPoint;
    private Entity targetEntity;
    private double arrival;
    private int timeoutTicks;
    private int elapsed;
    private int deadEndTicks;
    private int teleports;
    private boolean active;
    private PathMode mode = PathMode.NORMAL;
    private String refusal;
    private @Nullable NetworkLegs legs;
    private int networkAttempts;
    private int networkRoutes;

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
        if (stuckWatcher.observe(spirit.getX(), spirit.getY(), spirit.getZ())) {
            if (teleportCloser(target)) {
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
            if (mode == PathMode.CAREFUL && spirit.onGround()) {
                refusal = CarefulPath.refusal(spirit.getNavigation().getPath());
                if (refusal != null) {
                    finish();
                    return State.STUCK;
                }
            }
            followNetwork(target);
        }
        String deadEnd = legs == null ? deadEndRefusal(target) : null;
        if (deadEnd != null) {
            deadEndTicks++;
            if (deadEndTicks >= GotoTuning.DEAD_END_CONFIRM_TICKS) {
                if (teleportCloser(target)) {
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

    public boolean isActive() {
        return active;
    }

    public int timeoutTicks() {
        return timeoutTicks;
    }

    public String refusal() {
        return refusal;
    }

    // Для разбора трудных подходов: сколько раз спрашивали сеть тропинок и сколько маршрутов она дала.
    public String networkNote() {
        return "сеть: запросов " + networkAttempts + ", маршрутов " + networkRoutes;
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
        teleports = 0;
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

    // Сеть тропинок (решение владельца 20.10): путь игры к цели в пределах 32 не дошёл — по сети отрезками, каждый
    // в пределах пути игры. Отрезок, который не прошёлся, — к пересчёту, дальше как без сети.
    private void followNetwork(Vec3 target) {
        if (mode != PathMode.NORMAL || targetEntity != null || !(spirit.level() instanceof ServerLevel level)) {
            return;
        }
        Path path = spirit.getNavigation().getPath();
        if (legs != null) {
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

    private String deadEndRefusal(Vec3 target) {
        if (targetEntity != null || mode == PathMode.CAREFUL || mode == PathMode.SWIM) {
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

    public int teleports() {
        return teleports;
    }

    private boolean teleportCloser(Vec3 target) {
        Unstuck rules = DataTables.INSTANCE.unstuck().current();
        if (teleports >= rules.teleportsPerApproach() || mode == PathMode.SWIM || mode == PathMode.CAREFUL) {
            return false;
        }
        LevelSafetyView safety = new LevelSafetyView(spirit.level());
        Optional<BlockCoord> step = rules.step(
                blockCoord(spirit.position()),
                blockCoord(target),
                cell -> MiningSafety.canStand(cell, safety) && allowedThere(cell),
                this::clearPath);
        if (step.isEmpty()) {
            return false;
        }
        BlockCoord cell = step.get();
        spirit.getNavigation().stop();
        spirit.teleportTo(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
        teleports++;
        spirit.countTeleport();
        stuckWatcher = new StuckWatcher(GotoTuning.STUCK_MIN_MOVEMENT, GotoTuning.STUCK_TICKS);
        pathRefresh = new PathRefreshPolicy();
        return true;
    }

    private boolean allowedThere(BlockCoord cell) {
        if (NightLeashGate.refusal(spirit, cell) != null) {
            return false;
        }
        return spirit.workZone().map(zone -> zone.excess(cell) == 0).orElse(true);
    }

    private boolean clearPath(BlockCoord cell) {
        Vec3 eye = spirit.getEyePosition();
        Vec3 head = new Vec3(cell.x() + 0.5, cell.y() + spirit.getEyeHeight(), cell.z() + 0.5);
        return spirit.level()
                        .clip(new ClipContext(eye, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, spirit))
                        .getType()
                == HitResult.Type.MISS;
    }

    private void moveToTarget(Vec3 target) {
        spirit.getNavigation().moveTo(target.x, target.y, target.z, 0, MOVE_SPEED);
    }

    private void finish() {
        spirit.getNavigation().stop();
        active = false;
    }
}
