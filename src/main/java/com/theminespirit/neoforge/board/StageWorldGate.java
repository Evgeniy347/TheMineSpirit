package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.StageGate;
import com.theminespirit.core.board.StageWorld;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownSnapshot;
import java.util.LinkedHashMap;
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

    static void apply(ServerLevel level, BoardService service, StageSite site, String wood) {
        BoardLedger ledger = service.board().ledger();
        int journal = SettlementStage.of(ledger);
        StageWorld world = new StageOneWorld(progress(level, service, site, wood));
        StageGate gate = SettlementStage.gate(ledger, world);
        service.board().ledger().gate(gate);
        if (gate.stage() < journal) {
            reopenBroken(service, gate);
        }
        StageHold.release(level, service);
    }

    private static Map<String, TownSnapshot.Progress> progress(
            ServerLevel level, BoardService service, StageSite site, String wood) {
        Map<String, TownSnapshot.Progress> progress = new LinkedHashMap<>();
        progress.put(
                BoardCase.BUILD_MINE.name(),
                FirstStepsRunner.progress(level, service, BoardCase.BUILD_MINE, site, wood));
        progress.put(
                BoardCase.STOCK_CHESTS.name(),
                sum(
                        sum(
                                FirstStepsRunner.progress(level, service, BoardCase.STOCK_CHESTS, site, wood),
                                FirstStepsRunner.progress(level, service, BoardCase.CRAFT_FURNACE, site, wood)),
                        YardFloorWatch.progress(level, site)));
        progress.put(
                BoardCase.BUILD_HOUSE.name(),
                FirstStepsRunner.progress(level, service, BoardCase.BUILD_HOUSE, site, wood));
        progress.put(
                BoardCase.BUILD_ORCHARD.name(),
                FirstStepsRunner.progress(level, service, BoardCase.BUILD_ORCHARD, site, wood));
        progress.put(
                BoardCase.BUILD_FENCE.name(),
                FirstStepsRunner.progress(level, service, BoardCase.BUILD_FENCE, site, wood));
        progress.put(
                BoardCase.LIGHTING.name(), FirstStepsRunner.progress(level, service, BoardCase.LIGHTING, site, wood));
        progress.put(
                BoardCase.BUILD_STREETS.name(),
                FirstStepsRunner.progress(level, service, BoardCase.BUILD_STREETS, site, wood));
        return progress;
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

    private static TownSnapshot.Progress sum(TownSnapshot.Progress first, TownSnapshot.Progress second) {
        return new TownSnapshot.Progress(
                first.done() + second.done(), first.total() + second.total(), first.damaged() || second.damaged());
    }

    private record StageOneWorld(Map<String, TownSnapshot.Progress> progress) implements StageWorld {
        @Override
        public boolean built(BoardCase boardCase, int level) {
            if (level != 1 || !WORLD_STEPS.contains(boardCase)) {
                return true;
            }
            TownSnapshot.Progress found = progress.get(boardCase.name());
            return found == null || (found.total() > 0 && found.done() >= found.total());
        }

        @Override
        public boolean builtPlan(String id, int level) {
            return true;
        }
    }
}
