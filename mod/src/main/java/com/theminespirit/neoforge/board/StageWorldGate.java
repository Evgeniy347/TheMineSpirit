package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BuildingLevels;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.StageConfirmation;
import com.theminespirit.core.board.StageGate;
import com.theminespirit.core.board.StageWorld;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphStageGate;
import com.theminespirit.core.town.StageSite;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class StageWorldGate {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<BoardCase> WORLD_STEPS = Set.of(
            BoardCase.BUILD_MINE,
            BoardCase.STOCK_CHESTS,
            BoardCase.BUILD_HOUSE,
            BoardCase.BUILD_ORCHARD,
            BoardCase.BUILD_FENCE,
            BoardCase.LIGHTING,
            BoardCase.BUILD_STREETS);
    private static final Map<Level, StageConfirmation> CONFIRMATIONS = new ConcurrentHashMap<>();

    private StageWorldGate() {}

    static void apply(
            ServerLevel level, BoardService service, StageSite site, String wood, @Nullable GraphFacts facts) {
        BoardLedger ledger = service.board().ledger();
        int previous = ledger.stage();
        StageConfirmation confirmation =
                CONFIRMATIONS.computeIfAbsent(level, ignored -> new StageConfirmation(previous));
        StageWorld world = new AuditedWorld(level, site, wood, facts);
        StageGate gate = confirmation.gate(world);
        int stable = gate.stage();
        ledger.gate(gate);
        if (stable < previous) {
            reopenBroken(service, gate);
        }
        StageHold.release(level, service);
    }

    static void reset(ServerLevel level, int stage) {
        CONFIRMATIONS.put(level, new StageConfirmation(stage));
    }

    static void forget(ServerLevel level) {
        CONFIRMATIONS.remove(level);
    }

    private static void reopenBroken(BoardService service, StageGate gate) {
        for (String name : gate.missing().keySet()) {
            BoardCase step = named(name);
            if (step == null || !WORLD_STEPS.contains(step)) {
                continue;
            }
            StageClose.reopen(service, step, FirstStepsRunner.partsOf(step));
        }
    }

    private static BoardCase named(String name) {
        try {
            return BoardCase.valueOf(name);
        } catch (IllegalArgumentException planIdOrUnknown) {
            return null;
        }
    }

    private static final class AuditedWorld implements StageWorld {
        private final ServerLevel level;
        private final StageSite site;
        private final String wood;
        private final @Nullable GraphFacts facts;
        private final Map<String, Boolean> ready = new LinkedHashMap<>();

        private AuditedWorld(ServerLevel level, StageSite site, String wood, @Nullable GraphFacts facts) {
            this.level = level;
            this.site = site;
            this.wood = wood;
            this.facts = facts;
        }

        @Override
        public boolean built(BoardCase boardCase, int level) {
            if (level < 1) {
                return false;
            }
            int max = BuildingLevels.maxLevel(boardCase);
            for (int candidate = level; candidate <= max; candidate++) {
                if (caseReady(boardCase, candidate)) {
                    return true;
                }
            }
            return false;
        }

        private boolean caseReady(BoardCase boardCase, int level) {
            return ready.computeIfAbsent(boardCase.name() + ":" + level, ignored -> caseReadyNow(boardCase, level));
        }

        private boolean caseReadyNow(BoardCase boardCase, int level) {
            Optional<String> graphId = graphId(boardCase, level);
            if (facts != null && graphId.isPresent() && facts.buildings().containsKey(graphId.orElseThrow())) {
                return clean() && GraphStageGate.buildingReady(facts, graphId.orElseThrow());
            }
            try {
                FirstStepsRunner.Placed placed = FirstStepsRunner.audit(this.level, boardCase, level, site, wood);
                return placed.confident() && placed.total() > 0 && placed.done() >= placed.total();
            } catch (RuntimeException unbuilt) {
                LOGGER.warn(
                        "[stage] {} уровня {} не сверена с миром: {} — этап ждёт",
                        boardCase,
                        level,
                        String.valueOf(unbuilt.getMessage()));
                return false;
            }
        }

        @Override
        public boolean builtPlan(String id, int level) {
            if (facts == null || level != 1 || !clean()) {
                return false;
            }
            return StageCatalog.graphId(id)
                    .filter(graphId -> facts.buildings().containsKey(graphId))
                    .map(graphId -> GraphStageGate.buildingReady(facts, graphId))
                    .orElse(false);
        }

        private boolean clean() {
            return facts != null && GraphStageGate.lotsReady(facts) && GraphStageGate.straysReady(facts);
        }
    }

    private static Optional<String> graphId(BoardCase boardCase, int level) {
        if (level > 1) {
            return switch (boardCase) {
                case BUILD_MINE -> Optional.of("mine_level_" + level);
                case BUILD_WAREHOUSE -> Optional.of("warehouse_" + level);
                default -> Optional.empty();
            };
        }
        return switch (boardCase) {
            case BUILD_MINE -> Optional.of("mine");
            case STOCK_CHESTS -> Optional.of("chests");
            case CRAFT_FURNACE -> Optional.of("furnace");
            case BUILD_HOUSE -> Optional.of("hut");
            case BUILD_ORCHARD -> Optional.of("orchard_oak");
            case BUILD_FENCE -> Optional.of("fence");
            case LIGHTING -> Optional.of("lighting");
            case BUILD_STREETS -> Optional.of("streets");
            case BUILD_WAREHOUSE -> Optional.of("warehouse");
            case CLEAR_GROUNDS -> Optional.of("clearing");
            default -> Optional.of(boardCase.name().toLowerCase(Locale.ROOT));
        };
    }
}
