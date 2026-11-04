package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.board.BoardReserve;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.intellect.ActivityCatalog;
import com.theminespirit.core.intellect.ActivityLedger;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.StrategicQueue;
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
import com.theminespirit.core.tactics.ArmorModel;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementIntellect {

    private final TacticalIntellect intellect;
    private final ActivityCatalog catalog;
    private final ActivityLedger ledger = new ActivityLedger();
    private final StrategicQueue queue = new StrategicQueue();
    private final TaskStarter starter = new TaskStarter();
    private final ExpeditionController expeditions = new ExpeditionController(starter);
    private final EnemyProfiles profiles = TacticsConfig.loadDefault().enemies();
    private int stage = 1;
    private SettlementView lastView;
    private TacticalDecision lastDecision;
    private final DecisionLog decisions = new DecisionLog();
    private long lastTick = Long.MIN_VALUE;
    private int decideCalls;
    private int stockReads;
    private int stockContainers;
    private int entityQueries;

    public SettlementIntellect() {
        this.catalog = ActivityCatalog.adultsOnly(List.of(
                new ChopActivity(() -> SettlementStage.logCap(stage)),
                new FarmActivity(),
                new ForageActivity(),
                new MineActivity(),
                new RestActivity(),
                new DefendActivity(),
                new UnloadActivity(),
                new SmeltActivity(),
                new CraftActivity()));
        ArmorModel armor = TacticsConfig.loadDefault().weights().scales().armor();
        this.intellect = new TacticalIntellect(catalog, profiles, armor);
    }

    public void tick(
            ServerLevel level,
            List<SpiritEntity> members,
            List<SpiritSnapshot> snapshots,
            Optional<Point> home,
            WorkReservations reservations,
            long tick) {
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
        BoardService boardService = BoardRegistry.get(level);
        stage = boardService.board().ledger().stage();
        StockReader.Stocks stocks = StockReader.read(level, members, home, BoardReserve.items(boardService.board()));
        stockReads += stocks.reads();
        stockContainers += stocks.containers();
        SettlementScanner.Scan scan = SettlementScanner.scan(level, members, snapshots, home, stocks.containerFood());
        entityQueries++;
        SettlementView view = SettlementViewBuilder.build(new SettlementViewBuilder.Input(
                snapshots, members, stocks.lots(), home, scan.enemies(), scan.forage(), level, tick));
        lastView = view;
        intellect.trackEnemies(scan.enemies());
        intellect.trackWeaponStock(stocks.lots().weapons() > 0);
        intellect.trackVein(veinBelow(home, view));
        intellect.trackHome(home.orElse(null));
        intellect.trackPlayerEscort(
                playerEscort(level, home, intellect.outsideGate().escortRadius()));
        TacticalDecision decision = intellect.decide(view, ledger, reservations, queue, tick);
        lastDecision = decision;
        decisions.tick(decision.refusals(), decision.notes());
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

    private static boolean playerEscort(ServerLevel level, Optional<Point> home, double radius) {
        if (home.isEmpty()) {
            return false;
        }
        Point point = home.get();
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - point.x();
            double dy = player.getY() - point.y();
            double dz = player.getZ() - point.z();
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= radius) {
                return true;
            }
        }
        return false;
    }

    private Point veinBelow(Optional<Point> home, SettlementView view) {
        IntellectTuning tuning = IntellectTuning.loadDefault();
        if (home.isEmpty() || !view.needsOre(tuning.oreTarget(), tuning.ironTarget(), tuning.weaponTarget())) {
            return null;
        }
        Point point = home.get();
        return new Point(point.x(), Math.max(-60, point.y() - 10), point.z());
    }
}
