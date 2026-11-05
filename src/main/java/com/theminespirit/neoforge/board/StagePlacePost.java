package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolGate;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.building.SchematicGround;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StagePlacePost {
    private StagePlacePost() {}

    static boolean handles(String node) {
        return StageCatalog.placed(node);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        StageCatalog.Entry entry = StageCatalog.entry(ticket.node())
                .orElseThrow(() -> new IllegalStateException("Постройка " + ticket.node()
                        + " без кейса и схемы: задание постройки этапа не собрать без места и схемы"));
        if (post.open(ticket.id())) {
            return true;
        }
        Optional<BlockCoord> at = SettlementPlotStore.get(post.level)
                .town()
                .flatMap(town -> StageCatalog.origin(
                        ticket.node(), town, post.scan.stage().anchor().y()));
        if (at.isEmpty()) {
            return false;
        }
        TicketTarget target =
                TicketTarget.at(at.orElseThrow()).withScheme(entry.scheme()).withBuilding(ticket.node());
        if (groundLayer(entry.scheme())) {
            target = level(target, post, ticket.node());
        }
        if (!WoodSpecies.OAK.equals(post.scan.wood())) {
            target = target.withWood(post.scan.wood());
        }
        List<String> modifiers = tilled(entry.scheme()) ? List.of(ToolGate.TOOL_MODIFIER + "hoe") : List.of();
        post.submit(
                post.ticket(
                        ticket.id(),
                        entry.boardCase(),
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        Map.of(),
                        modifiers),
                target);
        return true;
    }

    private static TicketTarget level(TicketTarget target, GraphPost post, String node) {
        return SettlementPlotStore.get(post.level)
                .town()
                .flatMap(town -> town.placement(node))
                .map(place -> {
                    int levelY = post.scan.stage().anchor().y();
                    PlotLeveling plan = PlotLeveling.plan(
                            new BlockCoord(place.x(), levelY, place.z()),
                            place.mask(),
                            (x, z) -> GroundProbe.top(post.level, x, z, levelY));
                    return target.withLeveling(plan);
                })
                .orElse(target);
    }

    private static boolean groundLayer(String scheme) {
        return DataTables.INSTANCE
                .presets()
                .current()
                .byId(scheme)
                .map(preset -> SchematicGround.groundLayer(preset.schematic()))
                .orElse(false);
    }

    private static boolean tilled(String scheme) {
        return DataTables.INSTANCE
                .presets()
                .current()
                .byId(scheme)
                .map(preset -> SchematicGround.tilled(preset.schematic()))
                .orElse(false);
    }
}
