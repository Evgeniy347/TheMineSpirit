package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolGate;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.engine.FailureFact;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritBrain;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class GraphNeeds {
    private GraphNeeds() {}

    static GraphBlock.@Nullable Need of(
            Board board,
            BoardTicket ticket,
            @Nullable TicketTarget target,
            SpiritEntity spirit,
            StockView stock,
            long now) {
        ServerLevel level = spirit.level() instanceof ServerLevel server ? server : null;
        FailureFact fact =
                spirit.brainIfPresent().flatMap(SpiritBrain::failureFact).orElse(null);
        return of(board, ticket, target, spirit.getUUID().toString(), SpiritItems.ids(spirit), stock, now, level, fact);
    }

    static GraphBlock.@Nullable Need of(
            Board board,
            BoardTicket ticket,
            @Nullable TicketTarget target,
            @Nullable String holder,
            Collection<String> items,
            StockView stock,
            long now) {
        return of(board, ticket, target, holder, items, stock, now, null, null);
    }

    static GraphBlock.@Nullable Need of(
            Board board,
            BoardTicket ticket,
            @Nullable TicketTarget target,
            @Nullable String holder,
            Collection<String> items,
            StockView stock,
            long now,
            FailureFact fact) {
        return of(board, ticket, target, holder, items, stock, now, null, fact);
    }

    private static GraphBlock.@Nullable Need of(
            Board board,
            BoardTicket ticket,
            @Nullable TicketTarget target,
            @Nullable String holder,
            Collection<String> items,
            StockView stock,
            long now,
            @Nullable ServerLevel level,
            @Nullable FailureFact fact) {
        if (holder != null && board.stuck().isStuck(UUID.fromString(holder), now)) {
            return new GraphBlock.Path(point(target));
        }
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
        if (!missing.isEmpty()) {
            Map.Entry<String, Integer> first = missing.entrySet().iterator().next();
            return new GraphBlock.Item(first.getKey(), first.getValue());
        }
        String tool = ToolGate.required(ticket).orElse(null);
        if (tool != null && holder != null && !ToolGate.allows(ticket, items)) {
            return new GraphBlock.Tool(tool, 0, holder);
        }
        if (fact == FailureFact.NIGHT) {
            return new GraphBlock.World(GraphSurroundings.TIME_DAY);
        }
        if (fact == FailureFact.NO_TREES) {
            String tag = treeTag(ticket, target);
            if (tag != null) {
                return new GraphBlock.World(tag);
            }
        }
        if (depositOnly(ticket)) {
            return null;
        }
        String made = madeItem(ticket);
        if (made != null) {
            int count = target == null ? 1 : Math.max(1, target.supplyCount());
            return new GraphBlock.Item(made, count);
        }
        GraphBlock.Item planned = plannedItem(ticket, target, stock, level);
        if (planned != null) {
            return planned;
        }
        if (target != null) {
            return new GraphBlock.Path(point(target));
        }
        return null;
    }

    static boolean depositOnly(BoardTicket ticket) {
        return ticket.id().startsWith("supply_gather:") || ticket.id().startsWith("supply:");
    }

    private static @Nullable String treeTag(BoardTicket ticket, @Nullable TicketTarget target) {
        String item = target == null ? null : target.supplyItem();
        if (item != null) {
            Optional<String> species = WoodSpecies.speciesOf(item);
            if (species.isPresent()) {
                return "tree:" + species.orElseThrow();
            }
        }
        String id = ticket.id();
        int at = id.indexOf("chop_");
        if (at < 0) {
            return null;
        }
        String tail = id.substring(at + "chop_".length());
        int cut = tail.indexOf('@');
        if (cut >= 0) {
            tail = tail.substring(0, cut);
        }
        for (String species : WoodSpecies.ALL) {
            if (tail.startsWith(species)) {
                return "tree:" + species;
            }
        }
        return null;
    }

    static @Nullable String madeItem(BoardTicket ticket) {
        String prefix = ticket.id().startsWith("graph:make:") ? "graph:make:" : null;
        if (prefix == null) {
            return null;
        }
        String item = ticket.id().substring(prefix.length());
        int at = item.indexOf('@');
        if (at >= 0) {
            item = item.substring(0, at);
        }
        int part = item.indexOf(':');
        return part < 0 ? item : item.substring(0, part);
    }

    static String point(@Nullable TicketTarget target) {
        if (target == null) {
            return "";
        }
        return target.target().x() + "," + target.target().y() + ","
                + target.target().z();
    }

    private static GraphBlock.@Nullable Item plannedItem(
            BoardTicket ticket, @Nullable TicketTarget target, StockView stock, @Nullable ServerLevel level) {
        if (ticket.boardCase() != BoardCase.REPAIR_BUILDING || target == null || target.plan() == null) {
            return null;
        }
        TaskNode plan = target.plan();
        if (plan instanceof ScriptCall call
                && "place_block".equals(call.script())
                && call.params().get("block") instanceof String block) {
            String placed = withoutState(block);
            return stock.available().getOrDefault(placed, 0) < 1 ? new GraphBlock.Item(placed, 1) : null;
        }
        MaterialSource source = level == null ? null : FirstStepsRunner.materialSource(level);
        if (source == null) {
            return null;
        }
        Map<String, Integer> need = needOf(plan, source);
        if (need == null) {
            return null;
        }
        return firstMissing(BuildRemainder.left(level, ticket.boardCase(), plan, need), stock);
    }

    static GraphBlock.@Nullable Item materialNeed(
            BoardCase boardCase, TaskNode plan, MaterialSource source, StockView stock) {
        Map<String, Integer> need = needOf(plan, source);
        return need == null ? null : firstMissing(need, stock);
    }

    private static @Nullable Map<String, Integer> needOf(TaskNode plan, MaterialSource source) {
        try {
            return CaseMaterials.need(plan, source);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static GraphBlock.@Nullable Item firstMissing(Map<String, Integer> need, StockView stock) {
        for (Map.Entry<String, Integer> entry : new TreeMap<>(need).entrySet()) {
            int have = stock.available().getOrDefault(entry.getKey(), 0);
            if (have < entry.getValue()) {
                return new GraphBlock.Item(entry.getKey(), Math.max(1, entry.getValue() - have));
            }
        }
        return null;
    }

    private static String withoutState(String block) {
        int state = block.indexOf('[');
        return state < 0 ? block : block.substring(0, state);
    }
}
