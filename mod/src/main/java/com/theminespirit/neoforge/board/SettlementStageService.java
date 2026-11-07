package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.StageRollback;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.StageView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.SettlementChange;
import com.theminespirit.core.rest.SettlementStageChange;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class SettlementStageService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String STAGE_ID = "stage";

    private final ServerLevel level;

    private SettlementStageService(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "Уровень не может быть null: этап поселения живёт в мире");
    }

    public static SettlementStageService of(ServerLevel level) {
        return new SettlementStageService(level);
    }

    public StageView view() {
        BoardService service = BoardRegistry.get(level);
        StageRules rules = rules();
        return StageRollback.view(rules, buildingStages(), service.board().ledger(), this::readiness);
    }

    public SettlementStageChange rollback(SettlementChange change) {
        Objects.requireNonNull(change, "Запрос отката не может быть null: без него неизвестен целевой этап");
        BoardService service = BoardRegistry.get(level);
        Board board = service.board();
        StageRules rules = rules();
        Map<String, Integer> stages = buildingStages();
        StageRollback.Plan plan = StageRollback.plan(change.stage(), rules, stages, board.ledger(), this::readiness);
        if (plan.refused()) {
            throw new ApiException(
                    ErrorCode.CONFLICT, plan.refusal(), Map.of("stage", plan.target(), "previous", plan.previous()));
        }
        long now = board.now();
        List<String> forgotten = forget(service, plan, change, now);
        List<String> cancelled = cancel(service, change.stage(), rules, stages, change, now);
        StageWorldGate.reset(level, change.stage());
        String line = "[stage] откат " + plan.previous() + "→" + change.stage() + ": причина " + change.reason()
                + ", кем " + change.by();
        LOGGER.info("{}", line);
        service.journal()
                .recordEntry(BoardLog.note(
                        STAGE_ID,
                        BoardCase.ACTIVITY_EXTERNAL,
                        now,
                        List.of(
                                "откат " + plan.previous() + "→" + change.stage(),
                                "причина: " + change.reason(),
                                "кем: " + change.by(),
                                "снято отметок: " + forgotten.size(),
                                "снято заданий: " + cancelled.size()),
                        change.by(),
                        ConfigStamp.current()));
        service.journal().flushAll();
        return new SettlementStageChange(
                plan.previous(), change.stage(), !forgotten.isEmpty() || !cancelled.isEmpty(), forgotten, cancelled);
    }

    private List<String> forget(BoardService service, StageRollback.Plan plan, SettlementChange change, long now) {
        List<String> forgotten = new ArrayList<>(plan.forget().size());
        for (StageRollback.Mark mark : plan.forget()) {
            if (mark.planId() != null) {
                service.board().ledger().forgetPlan(mark.planId(), mark.level());
            } else {
                service.board().ledger().forget(mark.boardCase(), mark.level());
            }
            forgotten.add(mark.key());
            service.journal().recordEntry(reopened(mark, change.by(), now));
        }
        return List.copyOf(forgotten);
    }

    private List<String> cancel(
            BoardService service,
            int target,
            StageRules rules,
            Map<String, Integer> stages,
            SettlementChange change,
            long now) {
        List<String> cancelled = new ArrayList<>();
        for (BoardTicket ticket : List.copyOf(service.board().all())) {
            if (BoardActivities.isActivity(ticket.boardCase())) {
                continue;
            }
            BoardStatus status = service.board().statusOf(ticket.id()).orElse(null);
            if (status == null || status == BoardStatus.DONE || status == BoardStatus.CANCELLED) {
                continue;
            }
            OptionalInt stage =
                    StageRollback.ticketStage(ticket.id(), ticket.boardCase(), ticket.level(), rules, stages);
            if (stage.isEmpty() || stage.getAsInt() <= target) {
                continue;
            }
            if (!service.cancel(level, ticket.id())) {
                continue;
            }
            cancelled.add(ticket.id());
            service.journal()
                    .recordEntry(BoardLog.note(
                            ticket.id(),
                            ticket.boardCase(),
                            now,
                            List.of(
                                    "снято откатом этапа " + target,
                                    "этап задания: " + stage.getAsInt(),
                                    "причина: " + change.reason(),
                                    "кем: " + change.by()),
                            change.by(),
                            ConfigStamp.current()));
        }
        return List.copyOf(cancelled);
    }

    private StageRollback.Readiness readiness(
            @Nullable BoardCase boardCase, @Nullable String planId, int levelOfBuilding) {
        try {
            if (planId != null) {
                return planReady(planId);
            }
            return stageOneReady(
                    Objects.requireNonNull(
                            boardCase, "Кейс постройки не может быть null: без него готовность не проверить"),
                    levelOfBuilding);
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "[stage] готовность постройки (кейс {}, план {}, уровень {}) не проверена: {}",
                    boardCase,
                    planId,
                    levelOfBuilding,
                    failure.getMessage());
            return StageRollback.Readiness.UNKNOWN;
        }
    }

    private StageRollback.Readiness stageOneReady(BoardCase boardCase, int levelOfBuilding) {
        Optional<StageSite> site = SettlementPlotStore.get(level).site();
        if (site.isEmpty()) {
            return StageRollback.Readiness.UNKNOWN;
        }
        if (levelOfBuilding == 1 && boardCase == BoardCase.STOCK_CHESTS) {
            TownSnapshot.Progress yard = YardFloorWatch.progress(level, site.orElseThrow());
            if (yard.total() > 0 && yard.damaged()) {
                return StageRollback.Readiness.NOT_READY;
            }
        }
        String wood = wood();
        FirstStepsRunner.Placed placed =
                FirstStepsRunner.audit(level, boardCase, levelOfBuilding, site.orElseThrow(), wood);
        if (placed.total() == 0) {
            return StageRollback.Readiness.UNKNOWN;
        }
        return placed.done() >= placed.total() ? StageRollback.Readiness.READY : StageRollback.Readiness.NOT_READY;
    }

    private StageRollback.Readiness planReady(String planId) {
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        Optional<StageCatalog.Entry> entry = StageCatalog.entry(planId);
        if (town.isEmpty() || entry.isEmpty()) {
            return StageRollback.Readiness.UNKNOWN;
        }
        Optional<BlockCoord> origin = StageCatalog.origin(planId, town.orElseThrow(), plotY());
        if (origin.isEmpty()) {
            return StageRollback.Readiness.UNKNOWN;
        }
        TicketTarget target = TicketTarget.at(origin.orElseThrow())
                .withScheme(entry.orElseThrow().scheme())
                .withBuilding(planId);
        FirstStepsRunner.Placed placed =
                FirstStepsRunner.placedBlocks(level, TicketPlans.plan(BoardCase.BUILD_PLACED, 1, target), wood());
        if (placed.total() == 0) {
            return StageRollback.Readiness.UNKNOWN;
        }
        return placed.done() >= placed.total() ? StageRollback.Readiness.READY : StageRollback.Readiness.NOT_READY;
    }

    private int plotY() {
        Optional<StageSite> site = SettlementPlotStore.get(level).site();
        if (site.isPresent()) {
            return site.orElseThrow().anchor().y();
        }
        return SettlementPlotStore.get(level)
                .plot()
                .map(SettlementPlot::anchor)
                .map(BlockCoord::y)
                .orElseThrow(() -> new IllegalStateException(
                        "Участок не обмерен: высоту построек плана не взять ни из места этапа, ни из участка"));
    }

    private String wood() {
        return SettlementPlotStore.get(level)
                .plot()
                .map(SettlementPlot::wood)
                .orElseThrow(() -> new IllegalStateException(
                        "Порода участка не выбрана: готовность построек не сравнить со схемой"));
    }

    private static StageRules rules() {
        return DataTables.INSTANCE.stages().current();
    }

    private static Map<String, Integer> buildingStages() {
        Map<String, Integer> stages = new LinkedHashMap<>();
        for (Map.Entry<String, SupplyGraph.Building> entry :
                DataTables.INSTANCE.graph().current().buildings().entrySet()) {
            stages.put(entry.getKey(), entry.getValue().stage());
        }
        return Collections.unmodifiableMap(stages);
    }

    private static BoardLogEntry reopened(StageRollback.Mark mark, String by, long now) {
        BoardCase boardCase = mark.boardCase() == null ? BoardCase.BUILD_PLACED : mark.boardCase();
        String ticketId = mark.planId() == null ? "rollback:" + mark.key() : "graph:building:" + mark.planId();
        int priority = BoardRules.priorityOf(boardCase);
        return new BoardLogEntry(
                BoardLog.Event.REOPENED,
                now,
                ticketId,
                boardCase,
                mark.level(),
                StageRollback.CAUSE,
                priority,
                priority,
                List.of("rollback"),
                by,
                ConfigStamp.current(),
                now);
    }
}
