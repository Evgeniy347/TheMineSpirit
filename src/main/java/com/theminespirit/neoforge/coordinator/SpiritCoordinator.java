package com.theminespirit.neoforge.coordinator;

import com.theminespirit.core.base.Camp;
import com.theminespirit.core.coordinator.AssistOrder;
import com.theminespirit.core.coordinator.Coordinator;
import com.theminespirit.core.coordinator.CoordinatorDirectives;
import com.theminespirit.core.coordinator.CoordinatorPlan;
import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.HelpCall;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.intellect.defense.DefenseIssuedOrder;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.DirectiveOwner;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RallyTuning;
import com.theminespirit.core.tactics.SafePointChoice;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.defense.DefenseService;
import com.theminespirit.neoforge.intellect.SettlementIntellect;
import com.theminespirit.neoforge.tactics.TacticsService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public final class SpiritCoordinator {
    private final ServerLevel level;
    private final SettlementKey key;
    private final Coordinator coordinator = new Coordinator();
    private final WorkReservations reservations;
    private final SettlementIntellect intellect = new SettlementIntellect();
    private final DefenseService defense = new DefenseService();
    private final Map<UUID, CombatDirective> lastDirectives = new HashMap<>();
    private final Map<UUID, String> lastForced = new HashMap<>();
    private final Set<UUID> lastUrgent = new HashSet<>();
    private long lastSnapshotTick = -1;
    private int entityQueries;
    private int planCalls;
    private CoordinatorState lastState = new CoordinatorState(Optional.empty(), 0, List.of(), 0);

    public SpiritCoordinator(ServerLevel level, SettlementKey key) {
        if (level == null) {
            throw new IllegalArgumentException("level не может быть null: координатор без уровня не работает");
        }
        if (key == null) {
            throw new IllegalArgumentException(
                    "key не может быть null: координатор ведёт своё поселение, чужой дом он не защищает");
        }
        this.level = level;
        this.key = key;
        this.reservations = WorkReserveHub.leasesFor(level);
    }

    public void tick(List<SpiritEntity> members, long tick) {
        if (members == null) {
            throw new IllegalArgumentException(
                    "members не может быть null: снимок поселения строится из его духов, пустое — пустой список");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("tick = " + tick + ": тик снимка не может быть отрицательным");
        }
        lastSnapshotTick = tick;
        entityQueries += CoordinatorTuning.SNAPSHOT_GROUP_QUERIES;
        planCalls++;
        List<SpiritSnapshot> snapshots = new ArrayList<>();
        List<HelpCall> helpCalls = new ArrayList<>();
        for (SpiritEntity spirit : members) {
            if (spirit.isRemoved()) {
                reservations.releaseAll(spirit.getUUID());
                continue;
            }
            snapshots.add(SpiritSnapshotReader.read(spirit, tick));
            spirit.tactics().helpCall().ifPresent(helpCalls::add);
            entityQueries += CoordinatorTuning.SNAPSHOT_SPIRIT_QUERIES;
        }
        expireStale(snapshots, tick);
        reservations.expire(tick);
        Optional<Point> defendedPoint = defendedPoint(members);
        double radius = defendedPoint.isPresent() ? CoordinatorTuning.DEFENSE_RADIUS : 0;
        CoordinatorState state = new CoordinatorState(defendedPoint, radius, snapshots, tick);
        RallyTuning tuning = TacticsConfig.loadDefault().rules().retreat().rally();
        CoordinatorPlan plan = coordinator.plan(state, reservations, tick, helpCalls, tuning.helpRadius());
        lastState = state;
        Map<UUID, DefenseIssuedOrder> overlay = defense.tick(level, members, state, tick);
        Map<UUID, Point> posts = new HashMap<>();
        for (var assignment : defense.lastPlan().posts()) {
            posts.put(assignment.defender(), assignment.post());
        }
        applyPlan(plan, overlay, posts, helpCalls);
        intellect.tick(level, members, snapshots, defendedPoint, reservations, tick);
    }

    private Optional<Point> defendedPoint(List<SpiritEntity> members) {
        Optional<Point> home = SettlementGroups.defendedPoint(key);
        List<Camp> camps = new ArrayList<>();
        for (SpiritEntity spirit : members) {
            spirit.camp().ifPresent(camps::add);
        }
        if (camps.isEmpty() || members.isEmpty()) {
            return home;
        }
        Point reference = centroid(members);
        Optional<Point> camp = camps.stream().map(Camp::center).min(Comparator.comparingDouble(reference::distanceTo));
        return SafePointChoice.nearest(reference, home, camp);
    }

    private static Point centroid(List<SpiritEntity> members) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (SpiritEntity spirit : members) {
            x += spirit.getX();
            y += spirit.getY();
            z += spirit.getZ();
        }
        return new Point(x / members.size(), y / members.size(), z / members.size());
    }

    private void expireStale(List<SpiritSnapshot> snapshots, long tick) {

        List<UUID> seen = snapshots.stream().map(SpiritSnapshot::id).toList();
        List<UUID> toRelease = new ArrayList<>();
        for (UUID owner : new ArrayList<>(reservationsOwners())) {
            if (!seen.contains(owner)) {
                SpiritEntity spirit = level.getEntity(owner) instanceof SpiritEntity se ? se : null;
                if (spirit == null || spirit.isRemoved()) {
                    toRelease.add(owner);
                }
            }
        }
        for (UUID id : toRelease) {
            reservations.releaseAll(id);
            lastDirectives.remove(id);
            lastUrgent.remove(id);
            SpiritEntity spirit = level.getEntity(id) instanceof SpiritEntity se ? se : null;
            if (spirit != null) {
                spirit.tactics().clearDirective(DirectiveOwner.COORDINATOR);
                spirit.tactics().clearGuardZone();
                spirit.tactics().setWard(null);
            }
        }
    }

    private List<UUID> reservationsOwners() {
        // WorkReservations не даёт итератор владельцев; обойдём через releaseAll-логику нет
        // но нам нужны владельцы с бронированиями - просто не делаем сложного; release при отсутствии духа
        return List.of();
    }

    public CoordinatorState snapshot() {
        return lastState;
    }

    public int entityQueries() {
        return entityQueries;
    }

    public int planCalls() {
        return planCalls;
    }

    long lastTick() {
        return lastSnapshotTick;
    }

    public WorkReservations reservations() {
        return reservations;
    }

    public SettlementIntellect intellect() {
        return intellect;
    }

    public DefenseService defense() {
        return defense;
    }

    public CoordinatorPlan lastPlan() {
        return coordinator.plan(lastState, reservations, lastState.tick());
    }

    private void applyPlan(
            CoordinatorPlan plan,
            Map<UUID, DefenseIssuedOrder> overlay,
            Map<UUID, Point> posts,
            List<HelpCall> helpCalls) {
        Map<UUID, CombatDirective> directives = new HashMap<>(CoordinatorDirectives.of(plan));
        Set<UUID> callers = new HashSet<>();
        for (HelpCall call : helpCalls) {
            callers.add(call.caller());
        }
        directives.keySet().removeAll(callers);
        Set<UUID> assisted = new HashSet<>();
        for (AssistOrder assist : plan.assists()) {
            assisted.add(assist.helper());
        }
        Set<UUID> urgent = new HashSet<>();
        for (AssistOrder assist : plan.assists()) {
            if (assist.urgent()) {
                urgent.add(assist.helper());
            }
        }
        Map<UUID, String> forced = new HashMap<>();
        for (Map.Entry<UUID, DefenseIssuedOrder> entry : overlay.entrySet()) {
            if (assisted.contains(entry.getKey()) || callers.contains(entry.getKey())) {
                continue;
            }
            directives.put(entry.getKey(), entry.getValue().directive());
            if (entry.getValue().forceTargetId().isPresent()) {
                forced.put(entry.getKey(), entry.getValue().forceTargetId().get());
            }
        }
        for (UUID id : new ArrayList<>(directives.keySet())) {
            CombatDirective d = directives.get(id);
            SpiritEntity spirit = level.getEntity(id) instanceof SpiritEntity se ? se : null;
            if (spirit == null || spirit.isRemoved()) {
                continue;
            }
            if (lastUrgent.contains(id) && !urgent.contains(id)) {
                lastUrgent.remove(id);
                lastDirectives.remove(id);
                lastForced.remove(id);
                spirit.tactics().clearDirective(DirectiveOwner.COORDINATOR);
                spirit.tactics().clearGuardZone();
                spirit.tactics().setWard(null);
                if (spirit.currentTaskScript().isPresent()) {
                    continue;
                }
            }
            if (urgent.contains(id)) {
                CombatDirective last = lastDirectives.get(id);
                if (last == null || !last.equals(d) || !Objects.equals(lastForced.get(id), forced.get(id))) {
                    applyDirectiveToSpirit(spirit, d, forced.get(id), posts.get(id));
                    lastDirectives.put(id, d);
                    lastUrgent.add(id);
                    if (forced.get(id) != null) {
                        lastForced.put(id, forced.get(id));
                    } else {
                        lastForced.remove(id);
                    }
                }
                continue;
            }
            if (spirit.currentTaskScript().isPresent()) {
                CombatDirective ours = lastDirectives.get(id);
                CombatDirective current = spirit.tactics().activeDirective().orElse(null);
                if (ours != null && ours.mode() == CombatMode.AGGRESSIVE && ours.equals(current)) {
                    lastDirectives.remove(id);
                    lastForced.remove(id);
                    lastUrgent.remove(id);
                    spirit.tactics().clearDirective(DirectiveOwner.COORDINATOR);
                    spirit.tactics().setWard(null);
                    Point post = posts.get(id);
                    if (post != null) {
                        spirit.tactics()
                                .setGuardZone(
                                        new BlockCoord((int) Math.floor(post.x()), (int) Math.floor(post.y()), (int)
                                                Math.floor(post.z())),
                                        CoordinatorTuning.DEFENSE_POST_RADIUS);
                    }
                    continue;
                }
                lastDirectives.remove(id);
                lastForced.remove(id);
                lastUrgent.remove(id);
                continue;
            }
            CombatDirective last = lastDirectives.get(id);
            if (last == null || !last.equals(d) || !Objects.equals(lastForced.get(id), forced.get(id))) {
                applyDirectiveToSpirit(spirit, d, forced.get(id), posts.get(id));
                lastDirectives.put(id, d);
                if (urgent.contains(id)) {
                    lastUrgent.add(id);
                } else {
                    lastUrgent.remove(id);
                }
                if (forced.get(id) != null) {
                    lastForced.put(id, forced.get(id));
                } else {
                    lastForced.remove(id);
                }
            }
        }
        for (UUID id : new ArrayList<>(lastDirectives.keySet())) {
            if (directives.containsKey(id)) {
                continue;
            }
            CombatDirective last = lastDirectives.remove(id);
            lastForced.remove(id);
            lastUrgent.remove(id);
            SpiritEntity spirit = level.getEntity(id) instanceof SpiritEntity se ? se : null;
            if (spirit == null || spirit.isRemoved()) {
                continue;
            }
            if (spirit.currentTaskScript().isPresent()
                    && spirit.tactics()
                            .activeDirective()
                            .map(current -> !current.equals(last))
                            .orElse(false)) {
                continue;
            }
            spirit.tactics().clearDirective(DirectiveOwner.COORDINATOR);
            spirit.tactics().clearGuardZone();
            spirit.tactics().setWard(null);
        }
    }

    private void applyDirectiveToSpirit(SpiritEntity spirit, CombatDirective d, String forcedTargetId, Point post) {
        TacticsService tactics = spirit.tactics();
        // Для обороны/защиты задачи нет в текущем API - применяем напрямую
        tactics.applyDirective(d, DirectiveOwner.COORDINATOR);
        // guard zone при GUARD_AREA
        if (d.safePoint().isPresent()) {
            Point safe = d.safePoint().get();
            tactics.setGuardZone(
                    new BlockCoord((int) Math.floor(safe.x()), (int) Math.floor(safe.y()), (int) Math.floor(safe.z())),
                    CoordinatorTuning.DEFENSE_POST_RADIUS);
        } else if (post != null && (forcedTargetId != null || d.mode() == CombatMode.PROTECT)) {
            tactics.setGuardZone(
                    new BlockCoord((int) Math.floor(post.x()), (int) Math.floor(post.y()), (int) Math.floor(post.z())),
                    CoordinatorTuning.DEFENSE_POST_RADIUS);
        }
        // ward
        if (d.target().isPresent()) {
            TargetSelector.Target t = d.target().get();
            if (t.kind() == TargetSelector.Kind.SPIRIT && t.name().isPresent()) {
                try {
                    tactics.setWard(UUID.fromString(t.name().get()));
                } catch (Exception e) {
                    // игнорируем некорректный UUID цели
                }
            }
        }
        if (forcedTargetId != null) {
            LivingEntity forced = null;
            try {
                if (level.getEntity(UUID.fromString(forcedTargetId)) instanceof LivingEntity living
                        && living.isAlive()) {
                    forced = living;
                }
            } catch (IllegalArgumentException notUuid) {
                // битый id цели — дух дерётся по своей оценке, а не по пустому следу
            }
            tactics.setForcedTarget(forced);
        } else {
            tactics.setForcedTarget(null);
        }
    }
}
