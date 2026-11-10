package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.graph.GraphSiteFacts;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.board.graph.WorldReport;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.FloatingNote;
import com.theminespirit.core.town.TownSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class GraphObserve {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WHEAT_FIELD = "wheat_field";
    private static final List<String> BUILDS = List.of("mine", WHEAT_FIELD, "streets");

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
        logLeases(state);
    }

    private static void logLeases(GraphState state) {
        List<GraphSiteFacts.Lease> leases = state.facts().leases();
        if (leases.isEmpty()) {
            return;
        }
        Map<String, Integer> purposes = new LinkedHashMap<>();
        int cells = 0;
        int inside = 0;
        for (GraphSiteFacts.Lease lease : leases) {
            purposes.merge(lease.purpose(), 1, Integer::sum);
            cells += lease.cells();
            if (lease.inside()) {
                inside++;
            }
        }
        LOGGER.info(
                "[graph] аренды: брошенных {} (клеток {}), внутри поселения {}: {}",
                leases.size(),
                cells,
                inside,
                purposes);
    }

    static void builds(
            ServerLevel level,
            BoardService service,
            SupplyGraph graph,
            GraphState state,
            FirstStepsRunner.StageScan scan) {
        for (String id : BUILDS) {
            Optional<TownSnapshot.Progress> progress = progressOf(level, service, graph, state, scan, id);
            SupplyGraph.Building building = graph.buildings().get(id);
            if (progress.isEmpty() || building == null) {
                continue;
            }
            TownSnapshot.Progress now = progress.get();
            int percent = now.total() == 0 ? 0 : now.done() * 100 / now.total();
            LOGGER.info(
                    "[plot] {} {} этапа {}: {}% ({}/{}){}",
                    label(id),
                    id,
                    building.stage(),
                    percent,
                    now.done(),
                    now.total(),
                    now.damaged() ? " — отмечены повреждения" : "");
        }
    }

    static Optional<TownSnapshot.Progress> progressOf(
            ServerLevel level,
            BoardService service,
            SupplyGraph graph,
            GraphState state,
            FirstStepsRunner.StageScan scan,
            String id) {
        SupplyGraph.Building building = graph.buildings().get(id);
        if (building == null || state.built(id) == GraphState.Built.BUILT) {
            return Optional.empty();
        }
        if (WHEAT_FIELD.equals(id)) {
            Optional<TownSnapshot.Progress> current = wheatField(level, service);
            current.ifPresent(snapshot -> service.tickets.progress.remember(id, snapshot));
            return current.isPresent() ? current : service.tickets.progress.last(id);
        }
        TownSnapshot.Progress current =
                FirstStepsRunner.progress(level, service, building.boardCase(), scan.stage(), scan.wood());
        service.tickets.progress.remember(id, current);
        return Optional.of(current);
    }

    private static Optional<TownSnapshot.Progress> wheatField(ServerLevel level, BoardService service) {
        Optional<BoardTicket> active = service.board().all().stream()
                .filter(ticket -> ticket.id().contains(WHEAT_FIELD))
                .filter(ticket -> switch (service.board().statusOf(ticket.id()).orElse(BoardStatus.PENDING)) {
                    case PENDING, ASSIGNED, WAITING_RESOURCES -> true;
                    default -> false;
                })
                .findFirst();
        if (active.isEmpty()) {
            return Optional.empty();
        }
        BoardTicket found = active.get();
        return StageProgressWatch.placed(
                        level, found, service.targetOf(found.id()).orElse(null))
                .map(placed -> new TownSnapshot.Progress(placed.done(), placed.total(), false));
    }

    private static String label(String id) {
        return switch (id) {
            case "mine" -> "шахта";
            case WHEAT_FIELD -> "грядка";
            case "streets" -> "улицы";
            default -> "постройка";
        };
    }
}
