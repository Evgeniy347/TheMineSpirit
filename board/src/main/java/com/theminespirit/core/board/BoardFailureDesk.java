package com.theminespirit.core.board;

import com.theminespirit.core.board.graph.GraphFailureMemory;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class BoardFailureDesk {
    private final BlockedBook blocked;
    private final Map<String, BoardTicket> tickets;
    private final Map<String, BoardStatus> statuses;
    private final Map<String, UUID> claims;
    private final StuckSpirits stuck;
    private final List<BoardLogEntry> blockNotes;
    private final List<BoardLogEntry> detachments;

    BoardFailureDesk(
            BlockedBook blocked,
            Map<String, BoardTicket> tickets,
            Map<String, BoardStatus> statuses,
            Map<String, UUID> claims,
            StuckSpirits stuck,
            List<BoardLogEntry> blockNotes,
            List<BoardLogEntry> detachments) {
        this.blocked = Objects.requireNonNull(blocked, "Книга блокировок не может быть null");
        this.tickets = Objects.requireNonNull(tickets, "Билеты не могут быть null");
        this.statuses = Objects.requireNonNull(statuses, "Статусы не могут быть null");
        this.claims = Objects.requireNonNull(claims, "Держатели не могут быть null");
        this.stuck = Objects.requireNonNull(stuck, "Метки застревания не могут быть null");
        this.blockNotes = Objects.requireNonNull(blockNotes, "Журнал блокировок не может быть null");
        this.detachments = Objects.requireNonNull(detachments, "Журнал снятия не может быть null");
    }

    public List<BoardLogEntry> drainBlocks() {
        List<BoardLogEntry> drained = List.copyOf(blockNotes);
        blockNotes.clear();
        return drained;
    }

    public List<BoardLogEntry> drainDetachments() {
        List<BoardLogEntry> drained = List.copyOf(detachments);
        detachments.clear();
        return drained;
    }

    public List<GraphBlock> graphBlocks() {
        return blocked.blocks();
    }

    public boolean block(String ticketId, GraphBlock block, long now) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: блокировка адресная");
        Objects.requireNonNull(block, "Блокировка не может быть null: без неё нечего снимать");
        BoardTicket ticket = tickets.get(ticketId);
        if (ticket == null) {
            return false;
        }
        blocked.block(ticket, block, now);
        return true;
    }

    public boolean unblock(String ticketId, long now) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: разблокировка адресная");
        return blocked.unblock(ticketId, now);
    }

    void noteFailure(BoardTicket ticket, int failures, long now, StockView stock, GraphBlock.@Nullable Need need) {
        if (ticket.boardCase() == BoardCase.RESCUE_SPIRIT) {
            return;
        }
        String node = GraphFailureMemory.node(ticket.id(), ticket.cause());
        String address = node == null ? ticket.id() : node;
        if (need != null && (!(need instanceof GraphBlock.Path) || retryAfterKeyChange(ticket))) {
            blocked.block(ticket, new GraphBlock(address, "", need), now);
            return;
        }
        if (failures < BoardTuning.ticketFailLimit()) {
            return;
        }
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
        if (missing.isEmpty()) {
            return;
        }
        Map.Entry<String, Integer> first = missing.entrySet().iterator().next();
        int count = ticket.requiredItems().getOrDefault(first.getKey(), first.getValue());
        blocked.note(new BlockedBook.BlockedNote(
                ticket, failures, missing, stock, now, address, "", new GraphBlock.Item(first.getKey(), count)));
    }

    private static boolean retryAfterKeyChange(BoardTicket ticket) {
        return ticket.boardCase() == BoardCase.ACTIVITY_UNDO;
    }

    void releaseStuckHolders(long now) {
        for (BoardTicket ticket : List.copyOf(tickets.values())) {
            if (statuses.get(ticket.id()) != BoardStatus.ASSIGNED) {
                continue;
            }
            UUID holder = claims.get(ticket.id());
            if (holder == null || !stuck.isStuck(holder, now)) {
                continue;
            }
            if (blocked.release(ticket.id())) {
                detachments.add(new BoardLogEntry(
                        BoardLog.Event.DETACHED,
                        now,
                        ticket.id(),
                        ticket.boardCase(),
                        ticket.level(),
                        ticket.cause(),
                        ticket.priority(),
                        ticket.priority(),
                        List.of("держатель " + holder + " застрял: задание достаётся свободному"),
                        holder.toString(),
                        ConfigStamp.current(),
                        now));
            }
        }
    }
}
