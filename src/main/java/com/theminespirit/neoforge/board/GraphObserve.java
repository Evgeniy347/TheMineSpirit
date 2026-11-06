package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.board.graph.WorldReport;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.FloatingNote;
import com.theminespirit.core.town.TownSnapshot;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class GraphObserve {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GraphObserve() {}

    static void remember(GraphState state) {
        WorldFactsStore.set(WorldReport.of(state.facts()));
    }

    static void world(ServerLevel level, GraphState state) {
        WorldReport report = WorldReport.of(state.facts());
        LOGGER.info(
                "[graph] мир: лишних {} (кусков {}), ям {}, насыпей на лотах {}, природного {} (не работа), "
                        + "висящих {}, повреждено построек {}{}",
                report.strayBlocks(),
                report.strayGroups(),
                report.pits(),
                report.mounds(),
                report.natural(),
                report.hangingText(),
                report.damaged(),
                report.unknown() ? " (чанки не загружены)" : "");
        List<FloatingBlocks.Finding> floating = FloatingBlocks.countedInGraph(FloatingScanCycle.latest(level));
        if (!floating.isEmpty()) {
            LOGGER.info("[graph] висящее: {}", FloatingNote.line(floating));
        }
    }

    static void mine(
            ServerLevel level,
            BoardService service,
            SupplyGraph graph,
            GraphState state,
            FirstStepsRunner.StageScan scan) {
        SupplyGraph.Building mine = graph.buildings().get("mine");
        if (mine == null || state.built("mine") == GraphState.Built.BUILT) {
            return;
        }
        TownSnapshot.Progress progress =
                FirstStepsRunner.progress(level, service, mine.boardCase(), scan.stage(), scan.wood());
        int percent = progress.total() == 0 ? 0 : progress.done() * 100 / progress.total();
        LOGGER.info(
                "[plot] шахта {} этапа {}: {}% ({}/{}){}",
                mine.id(),
                mine.stage(),
                percent,
                progress.done(),
                progress.total(),
                progress.damaged() ? " — отмечены повреждения" : "");
    }
}
