package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolGate;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class GraphNeeds {
    private GraphNeeds() {}

    static GraphBlock.@Nullable Need of(
            Board board,
            BoardTicket ticket,
            @Nullable TicketTarget target,
            @Nullable String holder,
            Collection<String> items,
            StockView stock,
            long now) {
        if (holder != null && board.stuck().isStuck(UUID.fromString(holder), now)) {
            return new GraphBlock.Path(point(target));
        }
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
        if (!missing.isEmpty()) {
            Map.Entry<String, Integer> first = missing.entrySet().iterator().next();
            return new GraphBlock.Item(first.getKey(), first.getValue());
        }
        String placed = placedBlock(ticket, target);
        if (placed != null && stock.available().getOrDefault(placed, 0) < 1) {
            return new GraphBlock.Item(placed, 1);
        }
        String tool = ToolGate.required(ticket).orElse(null);
        if (tool != null && holder != null && !ToolGate.allows(ticket, items)) {
            return new GraphBlock.Tool(tool, 0, holder);
        }
        return null;
    }

    static String point(@Nullable TicketTarget target) {
        if (target == null) {
            return "";
        }
        return target.target().x() + "," + target.target().y() + ","
                + target.target().z();
    }

    private static @Nullable String placedBlock(BoardTicket ticket, @Nullable TicketTarget target) {
        if (ticket.boardCase() != BoardCase.REPAIR_BUILDING || target == null) {
            return null;
        }
        TaskNode plan = target.plan();
        if (plan instanceof ScriptCall call
                && "place_block".equals(call.script())
                && call.params().get("block") instanceof String block) {
            int state = block.indexOf('[');
            return state < 0 ? block : block.substring(0, state);
        }
        return null;
    }
}
