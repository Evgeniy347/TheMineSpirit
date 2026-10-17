package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityCatalog;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.ActivityLedger;
import com.theminespirit.core.intellect.Expedition;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.StrategicQueue;
import com.theminespirit.core.intellect.StrengthVerdict;
import com.theminespirit.core.intellect.TacticalDecision;
import com.theminespirit.core.intellect.TacticalIntellect;
import com.theminespirit.core.intellect.activity.ChopActivity;
import com.theminespirit.core.intellect.activity.CraftActivity;
import com.theminespirit.core.intellect.activity.DefendActivity;
import com.theminespirit.core.intellect.activity.FarmActivity;
import com.theminespirit.core.intellect.activity.ForageActivity;
import com.theminespirit.core.intellect.activity.MineActivity;
import com.theminespirit.core.intellect.activity.RestActivity;
import com.theminespirit.core.intellect.activity.SmeltActivity;
import com.theminespirit.core.intellect.activity.UnloadActivity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.tactics.ArmorModel;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public final class SettlementIntellect {
    private final TacticalIntellect intellect;
    private final ActivityCatalog catalog;
    private final ActivityLedger ledger = new ActivityLedger();
    private final StrategicQueue queue = new StrategicQueue();
    private final TaskStarter starter = new TaskStarter();
    private final ExpeditionController expeditions = new ExpeditionController(starter);
    private final EnemyProfiles profiles = TacticsConfig.loadDefault().enemies();
    private SettlementView lastView;
    private TacticalDecision lastDecision;
    private long lastTick = Long.MIN_VALUE;
    private int decideCalls;
    private int stockReads;
    private int stockContainers;
    private int entityQueries;

    public SettlementIntellect() {
        this.catalog = ActivityCatalog.adultsOnly(
                List.of(new ChopActivity(), new FarmActivity(), new ForageActivity(), new MineActivity(),
                        new RestActivity(), new DefendActivity(), new UnloadActivity(),
                        new SmeltActivity(), new CraftActivity()));
        ArmorModel armor = TacticsConfig.loadDefault().weights().scales().armor();
        this.intellect = new TacticalIntellect(catalog, profiles, armor);
    }

    public void tick(ServerLevel level, List<SpiritEntity> members, List<SpiritSnapshot> snapshots,
            Optional<Point> home, WorkReservations reservations, long tick) {
        if (level == null || members == null || snapshots == null || home == null || reservations == null) {
            throw new IllegalArgumentException(
                    "Интеллект без уровня, духов, снимков, дома или броней не тикает: решение зависит от всех сразу");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("Тик интеллекта " + tick + ": время не может быть отрицательным");
        }
        if (lastTick != Long.MIN_VALUE
                && tick - lastTick < IntellectTuning.loadDefault().demandIntervalTicks()) {
            return;
        }
        lastTick = tick;
        decideCalls++;
        StockReader.Stocks stocks = StockReader.read(level, members, home);
        stockReads += stocks.reads();
        stockContainers += stocks.containers();
        SettlementScanner.Scan scan =
                SettlementScanner.scan(level, members, snapshots, home, stocks.containerFood());
        entityQueries++;
        SettlementView view = SettlementViewBuilder.build(snapshots, members, stocks.lots(), home, scan.enemies(),
                scan.forage(), level, tick);
        lastView = view;
        intellect.trackEnemies(scan.enemies());
        intellect.trackWeaponStock(stocks.lots().weapons() > 0);
        intellect.trackVein(veinBelow(home, view));
        TacticalDecision decision = intellect.decide(view, ledger, reservations, queue, tick);
        lastDecision = decision;
        applyStrengths(members, decision, home, tick);
        applyActivities(members, snapshots, decision, view, home, tick);
        expeditions.update(level, decision.expedition(), members, tick);
    }

    public StrategicQueue queue() {
        return queue;
    }

    public ExpeditionController expeditions() {
        return expeditions;
    }

    public Optional<SettlementView> view() {
        return Optional.ofNullable(lastView);
    }

    public Optional<TacticalDecision> decision() {
        return Optional.ofNullable(lastDecision);
    }

    public int decideCalls() {
        return decideCalls;
    }

    public int stockReads() {
        return stockReads;
    }

    public int stockContainers() {
        return stockContainers;
    }

    public int entityQueries() {
        return entityQueries;
    }

    public int taskStarts() {
        return starter.starts();
    }

    public int taskFailures() {
        return starter.failures();
    }

    private Point veinBelow(Optional<Point> home, SettlementView view) {
        IntellectTuning tuning = IntellectTuning.loadDefault();
        if (home.isEmpty() || !view.needsOre(tuning.oreTarget(), tuning.ironTarget(), tuning.weaponTarget())) {
            return null;
        }
        Point point = home.get();
        return new Point(point.x(), Math.max(-60, point.y() - 10), point.z());
    }

    private void applyStrengths(List<SpiritEntity> members, TacticalDecision decision,
            Optional<Point> home, long tick) {
        Map<String, SpiritEntity> byId = new HashMap<>();
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved()) {
                byId.put(spirit.getUUID().toString(), spirit);
            }
        }
        for (StrengthVerdict verdict : decision.strengths()) {
            SpiritEntity spirit = byId.get(verdict.spirit().toString());
            if (spirit != null) {
                StrengthDirector.apply(spirit, verdict, starter, tick, home.isPresent());
                ledger.clear(spirit.getUUID());
            }
        }
    }

    private void applyActivities(List<SpiritEntity> members, List<SpiritSnapshot> snapshots,
            TacticalDecision decision, SettlementView view, Optional<Point> home, long tick) {
        if (home.isEmpty()) {
            return;
        }
        Map<String, SpiritEntity> byId = new HashMap<>();
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved()) {
                byId.put(spirit.getUUID().toString(), spirit);
            }
        }
        Map<String, SpiritSnapshot> shots = new HashMap<>();
        for (SpiritSnapshot snapshot : snapshots) {
            shots.put(snapshot.id().toString(), snapshot);
        }
        Set<UUID> team = new HashSet<>();
        expeditions.active().ifPresent(stand -> team.addAll(stand.team()));
        Set<UUID> defenders = new HashSet<>();
        for (var assignment : decision.activities().assignments()) {
            if (assignment.activity() == ActivityId.DEFEND) {
                defenders.add(assignment.spirit());
            }
        }
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved()) {
                spirit.setDefenceAssigned(defenders.contains(spirit.getUUID()));
            }
        }
        for (var assignment : decision.activities().assignments()) {
            if (assignment.activity() == ActivityId.DEFEND) {
                continue;
            }
            if (team.contains(assignment.spirit())) {
                continue;
            }
            SpiritEntity spirit = byId.get(assignment.spirit().toString());
            SpiritSnapshot snapshot = shots.get(assignment.spirit().toString());
            if (spirit == null || snapshot == null) {
                continue;
            }
            Activity activity = catalog.byId(assignment.activity());
            ActivityIntent intent = activity.order(snapshot, view);
            Optional<TaskNode> task = ActivityTaskFactory.taskFor(intent);
            if (task.isEmpty()) {
                continue;
            }
            String script = intent.script().get();
            starter.start(spirit, script, task.get(), tick);
        }
    }
}
