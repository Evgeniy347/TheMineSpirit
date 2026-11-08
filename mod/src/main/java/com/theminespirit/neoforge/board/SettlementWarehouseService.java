package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.UpgradePlan;
import com.theminespirit.core.board.WarehouseOrder;
import com.theminespirit.core.board.WarehouseRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.WarehouseChange;
import com.theminespirit.core.rest.WarehouseDecision;
import com.theminespirit.core.rest.WarehouseView;
import com.theminespirit.core.town.TownPlan;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class SettlementWarehouseService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DECISION = "warehouse_decision";

    private final ServerLevel level;

    private SettlementWarehouseService(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "Уровень не может быть null: склад живёт в мире");
    }

    public static SettlementWarehouseService of(ServerLevel level) {
        return new SettlementWarehouseService(level);
    }

    public WarehouseView view() {
        BoardService service = BoardRegistry.get(level);
        BoardLedger ledger = service.board().ledger();
        int stage = ledger.stage();
        int required = requiredLevel(stage);
        int built = WarehouseRules.builtLevel(ledger);
        WarehouseOrder order = WarehouseOrderStore.get(level).order();
        int planned = order == null ? required : order.level();
        int levelOfMaterials = Math.min(WarehouseRules.MAX_LEVEL, Math.max(required, planned));
        return new WarehouseView(
                stage,
                required,
                built,
                built < Math.max(required, planned),
                order,
                WarehouseLocations.planSite(level).orElse(null),
                UpgradePlan.materials(WarehouseRules.scheme(levelOfMaterials)));
    }

    public WarehouseChange decide(WarehouseDecision decision) {
        Objects.requireNonNull(decision, "Решение о складе не может быть null: без него нечего записывать");
        WarehouseOrderStore store = WarehouseOrderStore.get(level);
        WarehouseOrder previous = store.order();
        BoardService service = BoardRegistry.get(level);
        BoardLedger ledger = service.board().ledger();
        int built = WarehouseRules.builtLevel(ledger);
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        if (town.isEmpty()) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "План поселения не сохранён: место склада не с чем сверить, выполните обмер участка",
                    Map.of("site", site(decision.site())));
        }
        if (!town.orElseThrow().onPlot(decision.site().x(), decision.site().z())) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Место склада " + site(decision.site()) + " вне плана поселения: выберите клетку участка",
                    Map.of("site", site(decision.site())));
        }
        if (built > 0 && previous != null && !previous.site().equals(decision.site())) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "Склад уже построен в " + site(previous.site()) + ": повышение уровня идёт на том же месте",
                    Map.of("built", site(previous.site()), "requested", site(decision.site())));
        }
        WarehouseOrder order = new WarehouseOrder(
                decision.level(), decision.site(), decision.by(), decision.reason(), level.getGameTime());
        store.order(order);
        LOGGER.info(
                "[warehouse] решение стратега: уровень {}, место {}, причина {}",
                order.level(),
                site(order.site()),
                order.reason());
        service.journal()
                .recordEntry(BoardLog.note(
                        DECISION,
                        BoardCase.BUILD_WAREHOUSE,
                        level.getGameTime(),
                        List.of(
                                "level=" + order.level(),
                                "site=" + site(order.site()),
                                "by=" + order.by(),
                                "reason=" + order.reason()),
                        order.by(),
                        ConfigStamp.current()));
        return new WarehouseChange(previous, order, !order.equals(previous));
    }

    private static int requiredLevel(int stage) {
        StageRules rules = DataTables.INSTANCE.stages().current();
        if (stage < 1 || stage > rules.stages().size()) {
            return 1;
        }
        return Optional.ofNullable(rules.stages().get(stage - 1).buildings().get(BoardCase.BUILD_WAREHOUSE))
                .orElse(1);
    }

    private static String site(@Nullable BlockCoord at) {
        return at == null ? "—" : at.x() + "," + at.y() + "," + at.z();
    }
}
