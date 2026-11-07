package com.theminespirit.neoforge.coordinator;

import com.theminespirit.core.base.Camp;
import com.theminespirit.core.coordinator.Coordinator;
import com.theminespirit.core.coordinator.CoordinatorPlan;
import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.HelpCall;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RallyTuning;
import com.theminespirit.core.tactics.SafePointChoice;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.intellect.SettlementIntellect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public final class SpiritCoordinator {

    private final SettlementKey key;
    private final Coordinator coordinator = new Coordinator();
    private final WorkReservations reservations;
    private final SettlementIntellect intellect = new SettlementIntellect();
    private final CoordinatorOrders orders;
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

        this.key = key;
        this.reservations = WorkReserveHub.leasesFor(level);
        this.orders = new CoordinatorOrders(level);
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
        reservations.expire(tick);
        Optional<Point> defendedPoint = defendedPoint(members);
        double radius = defendedPoint.isPresent() ? CoordinatorTuning.DEFENSE_RADIUS : 0;
        CoordinatorState state = new CoordinatorState(defendedPoint, radius, snapshots, tick);
        RallyTuning tuning = TacticsConfig.loadDefault().rules().retreat().rally();
        CoordinatorPlan plan = coordinator.plan(state, reservations, tick, helpCalls, tuning.helpRadius());
        lastState = state;
        orders.applyPlan(plan, helpCalls);
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
}
