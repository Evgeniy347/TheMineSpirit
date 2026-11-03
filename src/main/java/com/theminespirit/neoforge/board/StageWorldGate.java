package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.StageGate;
import com.theminespirit.core.board.StageWorld;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphStageGate;
import com.theminespirit.core.board.graph.GraphWorldFacts;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

final class StageWorldGate {
    private static final Set<BoardCase> WORLD_STEPS = Set.of(
            BoardCase.BUILD_MINE,
            BoardCase.STOCK_CHESTS,
            BoardCase.BUILD_HOUSE,
            BoardCase.BUILD_ORCHARD,
            BoardCase.BUILD_FENCE,
            BoardCase.LIGHTING,
            BoardCase.BUILD_STREETS);

    private StageWorldGate() {}

    static void apply(ServerLevel level, BoardService service, StageSite site, String wood, GraphWorldFacts area) {
        BoardLedger ledger = service.board().ledger();
        int journal = SettlementStage.of(ledger);
        StageWorld world = new StageOneWorld(progress(level, site, wood), facts(area));
        StageGate gate = SettlementStage.gate(ledger, world);
        service.board().ledger().gate(gate);
        if (gate.stage() < journal) {
            reopenBroken(service, gate);
        }
        StageHold.release(level, service);
    }

    private static Map<String, FirstStepsRunner.Placed> progress(ServerLevel level, StageSite site, String wood) {
        Map<String, FirstStepsRunner.Placed> progress = new LinkedHashMap<>();
        progress.put(BoardCase.BUILD_MINE.name(), FirstStepsRunner.audit(level, BoardCase.BUILD_MINE, site, wood));
        progress.put(
                BoardCase.STOCK_CHESTS.name(),
                sum(
                        sum(
                                FirstStepsRunner.audit(level, BoardCase.STOCK_CHESTS, site, wood),
                                FirstStepsRunner.audit(level, BoardCase.CRAFT_FURNACE, site, wood)),
                        floor(YardFloorWatch.progress(level, site))));
        progress.put(BoardCase.BUILD_HOUSE.name(), FirstStepsRunner.audit(level, BoardCase.BUILD_HOUSE, site, wood));
        progress.put(
                BoardCase.BUILD_ORCHARD.name(), FirstStepsRunner.audit(level, BoardCase.BUILD_ORCHARD, site, wood));
        progress.put(BoardCase.BUILD_FENCE.name(), FirstStepsRunner.audit(level, BoardCase.BUILD_FENCE, site, wood));
        progress.put(BoardCase.LIGHTING.name(), FirstStepsRunner.audit(level, BoardCase.LIGHTING, site, wood));
        progress.put(
                BoardCase.BUILD_STREETS.name(), FirstStepsRunner.audit(level, BoardCase.BUILD_STREETS, site, wood));
        return progress;
    }

    private static FirstStepsRunner.Placed floor(TownSnapshot.Progress progress) {
        return new FirstStepsRunner.Placed(progress.done(), progress.total(), 0);
    }

    private static FirstStepsRunner.Placed sum(FirstStepsRunner.Placed first, FirstStepsRunner.Placed second) {
        return new FirstStepsRunner.Placed(
                first.done() + second.done(), first.total() + second.total(), first.unknown() + second.unknown());
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

    private record StageOneWorld(Map<String, FirstStepsRunner.Placed> progress, GraphFacts facts)
            implements StageWorld {
        @Override
        public boolean built(BoardCase boardCase, int level) {
            if (level != 1 || !WORLD_STEPS.contains(boardCase)) {
                return true;
            }
            if (facts == null) {
                return fallback(boardCase);
            }
            if (!clean()) {
                return false;
            }
            String id = graphId(boardCase);
            if (facts.buildings().containsKey(id)) {
                return GraphStageGate.buildingReady(facts, id);
            }
            return fallback(boardCase);
        }

        private boolean fallback(BoardCase boardCase) {
            FirstStepsRunner.Placed found = progress.get(boardCase.name());
            return found == null || !found.confident() || (found.total() > 0 && found.done() >= found.total());
        }

        @Override
        public boolean builtPlan(String id, int level) {
            return facts == null
                    || !facts.buildings().containsKey(id)
                    || (clean() && GraphStageGate.buildingReady(facts, id));
        }

        private boolean clean() {
            return facts == null || (GraphStageGate.lotsReady(facts) && GraphStageGate.straysReady(facts));
        }
    }

    private static GraphFacts facts(GraphWorldFacts area) {
        return area == null
                ? null
                : new GraphFacts(
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        area.buildings(),
                        area.strays(),
                        area.terrain(),
                        new GraphFacts.StageReqs(Map.of(), Map.of()));
    }

    private static String graphId(BoardCase boardCase) {
        return switch (boardCase) {
            case BUILD_MINE -> "mine";
            case STOCK_CHESTS -> "chests";
            case CRAFT_FURNACE -> "furnace";
            case BUILD_HOUSE -> "hut";
            case BUILD_ORCHARD -> "orchard_oak";
            case BUILD_FENCE -> "fence";
            case LIGHTING -> "lighting";
            case BUILD_STREETS -> "streets";
            case CLEAR_GROUNDS -> "clearing";
            default -> boardCase.name().toLowerCase(Locale.ROOT);
        };
    }
}
