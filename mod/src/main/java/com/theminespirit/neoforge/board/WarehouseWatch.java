package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.UpgradePlan;
import com.theminespirit.core.board.WarehouseOrder;
import com.theminespirit.core.board.WarehouseRules;
import com.theminespirit.core.board.WarehouseSpot;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WarehouseWatch {
    private static final String NODE = "warehouse";
    private static final Logger LOGGER = LoggerFactory.getLogger(WarehouseWatch.class);

    private WarehouseWatch() {}

    static boolean handles(String node) {
        return NODE.equals(node) || node.startsWith(NODE + "_");
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        WarehouseOrder order = WarehouseOrderStore.get(post.level).order();
        if (order == null) {
            return false;
        }
        BoardLedger ledger = post.service.board().ledger();
        int level = WarehouseRules.levelOf(ticket.node());
        if (level > order.level() || WarehouseRules.builtLevel(ledger) >= level) {
            return true;
        }
        if (post.open(ticket.id())) {
            return true;
        }
        BlockCoord site = order.site();
        String previous =
                level > 1 && ledger.has(BoardCase.BUILD_WAREHOUSE, level - 1) ? WarehouseRules.scheme(level - 1) : null;
        post.submit(
                post.ticket(
                        ticket.id(),
                        BoardCase.BUILD_WAREHOUSE,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        UpgradePlan.required(previous, WarehouseRules.scheme(level))),
                TicketTarget.at(site).withScheme(WarehouseRules.scheme(level)));
        LOGGER.info(
                "{} — задание {}",
                WarehouseSpot.line(level, site, site, "место стратега: " + order.reason()),
                ticket.id());
        return true;
    }
}
