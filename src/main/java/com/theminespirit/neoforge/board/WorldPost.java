package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.LevelRepair;
import com.theminespirit.core.board.StageAudit;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WorldPost {
    static final String REPAIR_NODE = "repair:";
    private static final String REPAIR = REPAIR_NODE;

    private WorldPost() {}

    static boolean handles(String node) {
        return switch (node) {
            case "backfill", "level_lot", "restore_trace" -> true;
            default -> node.startsWith("repair:");
        };
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        if (!ticket.node().startsWith(REPAIR)) {
            return false;
        }
        String id = ticket.node().substring(REPAIR.length());
        SupplyGraph.Building building = post.graph.buildings().get(id);
        if (building == null) {
            return false;
        }
        BoardCase step = building.boardCase();
        String wood = BuildingWoodChoice.stored(post.level, step, post.scan.stage(), post.scan.wood());
        BuildGap.Gap gap = FirstStepsRunner.gap(post.level, step, post.scan.stage(), wood);
        if (!gap.confident()) {
            return false;
        }
        TicketTarget target = FirstStepsRunner.targetFor(post.level, step, post.scan.stage(), wood);
        if (step == BoardCase.CLEAR_GROUNDS) {
            PlotAudit.Audit audit = PlotAudit.audit(post.level, post.scan.stage(), post.now);
            if (audit.extras().isEmpty()) {
                return false;
            }
            target = target.withLeveling(LevelRepair.leveling(List.of(), audit.extras()));
        }
        target = StageSettle.repairTarget(step, target, gap);
        BoardOutcome outcome = StageRepair.post(
                post.service,
                step,
                target,
                new StageRepair.Gap(
                        gap.shortage(),
                        gap.total(),
                        need(post.graph, ticket.task().consumes(), post.scan.wood())),
                post.now);
        return StageAudit.keeps(outcome);
    }

    static Map<String, Integer> need(SupplyGraph graph, Map<String, Integer> consumes, String wood) {
        Map<String, Integer> need = new LinkedHashMap<>();
        consumes.forEach((item, count) -> {
            String named = GraphNames.item(graph, item, wood);
            need.merge(
                    named.startsWith("minecraft:") ? named.substring("minecraft:".length()) : named,
                    count,
                    Integer::sum);
        });
        return Map.copyOf(need);
    }
}
