package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
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
        if (!WoodSpecies.OAK.equals(post.scan.wood())) {
            target = target.withWood(post.scan.wood());
        }
        post.submit(
                post.ticket(
                        ticket.id(),
                        entry.boardCase(),
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        Map.of()),
                target);
        return true;
    }
}
