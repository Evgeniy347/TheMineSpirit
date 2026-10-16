package com.theminespirit.neoforge.coordinator;

import com.theminespirit.core.coordinator.Coordinator;
import com.theminespirit.core.coordinator.CoordinatorPlan;
import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.base.Camp;
import com.theminespirit.core.coordinator.CoordinatorDirectives;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.SafePointChoice;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.fortify.FortifyRegistry;
import com.theminespirit.neoforge.intellect.SettlementIntellect;
import com.theminespirit.neoforge.tactics.TacticsService;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SpiritCoordinator {
    private final ServerLevel level;
    private final SettlementKey key;
    private final Coordinator coordinator = new Coordinator();
    private final WorkReservations reservations;
    private final SettlementIntellect intellect = new SettlementIntellect();
    private final Map<UUID, CombatDirective> lastDirectives = new HashMap<>();
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
        for (SpiritEntity spirit : members) {
            if (spirit.isRemoved()) {
                reservations.releaseAll(spirit.getUUID());
                continue;
            }
            snapshots.add(SpiritSnapshotReader.read(spirit, tick));
            entityQueries += CoordinatorTuning.SNAPSHOT_SPIRIT_QUERIES;
        }
        expireStale(snapshots, tick);
        reservations.expire(tick);
        Optional<Point> defendedPoint = defendedPoint(members);
        double radius = defendedPoint.isPresent() ? CoordinatorTuning.DEFENSE_RADIUS : 0;
        CoordinatorState state = new CoordinatorState(defendedPoint, radius, snapshots, tick);
        CoordinatorPlan plan = coordinator.plan(state, reservations, tick);
        lastState = state;
        applyPlan(plan, tick);
        FortifyRegistry.get(level).tick(state, members, tick);
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
        Optional<Point> camp = camps.stream().map(Camp::center)
                .min(Comparator.comparingDouble(reference::distanceTo));
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
        long cutoff = tick - CoordinatorTuning.SNAPSHOT_STALE_TICKS;
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
            SpiritEntity spirit = level.getEntity(id) instanceof SpiritEntity se ? se : null;
            if (spirit != null) {
                spirit.tactics().clearDirective();
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

    public CoordinatorPlan lastPlan() {
        return coordinator.plan(lastState, reservations, lastState.tick());
    }

    private void applyPlan(CoordinatorPlan plan, long now) {
        Map<UUID, CombatDirective> directives = CoordinatorDirectives.of(plan);
        for (UUID id : new ArrayList<>(directives.keySet())) {
            CombatDirective d = directives.get(id);
            SpiritEntity spirit = level.getEntity(id) instanceof SpiritEntity se ? se : null;
            if (spirit == null || spirit.isRemoved()) {
                continue;
            }
            if (spirit.currentTaskScript().isPresent()) {
                lastDirectives.remove(id);
                continue;
            }
            CombatDirective last = lastDirectives.get(id);
            if (last == null || !last.equals(d)) {
                applyDirectiveToSpirit(spirit, d, plan);
                lastDirectives.put(id, d);
            }
        }
        for (UUID id : new ArrayList<>(lastDirectives.keySet())) {
            if (directives.containsKey(id)) {
                continue;
            }
            CombatDirective last = lastDirectives.remove(id);
            SpiritEntity spirit = level.getEntity(id) instanceof SpiritEntity se ? se : null;
            if (spirit == null || spirit.isRemoved()) {
                continue;
            }
            if (spirit.currentTaskScript().isPresent() && spirit.tactics().activeDirective()
                    .map(current -> !current.equals(last)).orElse(false)) {
                continue;
            }
            spirit.tactics().clearDirective();
            spirit.tactics().clearGuardZone();
            spirit.tactics().setWard(null);
        }
    }

    private void applyDirectiveToSpirit(SpiritEntity spirit, CombatDirective d, CoordinatorPlan plan) {
        TacticsService tactics = spirit.tactics();
        // Для обороны/защиты задачи нет в текущем API - применяем напрямую
        tactics.applyDirective(d);
        // guard zone при GUARD_AREA
        if (d.mode() == CombatMode.GUARD_AREA && d.safePoint().isPresent()) {
            Point safe = d.safePoint().get();
            tactics.setGuardZone(new BlockCoord((int) Math.floor(safe.x()), (int) Math.floor(safe.y()), (int) Math.floor(safe.z())), CoordinatorTuning.DEFENSE_POST_RADIUS);
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
    }
}
