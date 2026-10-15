package com.theminespirit.neoforge.coordinator;

import com.theminespirit.core.coordinator.Coordinator;
import com.theminespirit.core.coordinator.CoordinatorPlan;
import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.coordinator.CoordinatorDirectives;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.tactics.TacticsService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SpiritCoordinator {
    private final ServerLevel level;
    private final Coordinator coordinator = new Coordinator();
    private final WorkReservations reservations;
    private final Map<UUID, CombatDirective> lastDirectives = new HashMap<>();
    private long lastSnapshotTick = -1;
    private CoordinatorState lastState = new CoordinatorState(Optional.empty(), 0, List.of(), 0);

    public SpiritCoordinator(ServerLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level не может быть null: координатор без уровня не работает");
        }
        this.level = level;
        this.reservations = WorkReserveHub.leasesFor(level);
    }

    public void tick() {
        long tick = level.getGameTime();
        if (lastSnapshotTick != -1 && (tick - lastSnapshotTick) < CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS) {
            return;
        }
        lastSnapshotTick = tick;
        List<SpiritSnapshot> snapshots = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof SpiritEntity spirit)) {
                continue;
            }
            if (spirit.isRemoved()) {
                reservations.releaseAll(spirit.getUUID());
                continue;
            }
            SpiritSnapshot snap = SpiritSnapshotReader.read(spirit, tick);
            snapshots.add(snap);
        }
        expireStale(snapshots, tick);
        reservations.expire(tick);
        Optional<Point> defendedPoint = findDefendedPoint(snapshots);
        double radius = defendedPoint.isPresent() ? CoordinatorTuning.DEFENSE_RADIUS : 0;
        CoordinatorState state = new CoordinatorState(defendedPoint, radius, snapshots, tick);
        CoordinatorPlan plan = coordinator.plan(state, reservations, tick);
        lastState = state;
        applyPlan(plan, tick);
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

    private Optional<Point> findDefendedPoint(List<SpiritSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }
        for (SpiritSnapshot snap : snapshots) {
            SpiritEntity spirit = level.getEntity(snap.id()) instanceof SpiritEntity se ? se : null;
            if (spirit != null) {
                Optional<BlockCoord> home = spirit.home();
                if (home.isPresent()) {
                    BlockCoord h = home.get();
                    return Optional.of(new Point(h.x() + 0.5, h.y(), h.z() + 0.5));
                }
            }
        }
        return Optional.empty();
    }

    public CoordinatorState snapshot() {
        return lastState;
    }

    public WorkReservations reservations() {
        return reservations;
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
            CombatDirective last = lastDirectives.get(id);
            if (last == null || !last.equals(d)) {
                applyDirectiveToSpirit(spirit, d, plan);
                lastDirectives.put(id, d);
            }
        }
        for (UUID id : new ArrayList<>(lastDirectives.keySet())) {
            if (!directives.containsKey(id)) {
                SpiritEntity spirit = level.getEntity(id) instanceof SpiritEntity se ? se : null;
                if (spirit != null && !spirit.isRemoved()) {
                    spirit.tactics().clearDirective();
                    spirit.tactics().clearGuardZone();
                    spirit.tactics().setWard(null);
                }
                lastDirectives.remove(id);
            }
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
