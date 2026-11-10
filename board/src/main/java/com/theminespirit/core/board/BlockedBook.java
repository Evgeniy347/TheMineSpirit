package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class BlockedBook {
    private final Map<String, BoardStatus> statuses;
    private final Map<String, BoardTicket> tickets;
    private final Map<String, UUID> claims;
    private final Map<String, Integer> failures;
    private final Map<String, String> reasons;
    private final FailureHistory history;
    private final List<BoardLogEntry> notes;
    private final Map<String, Map<String, Integer>> missing = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> available = new LinkedHashMap<>();
    private final Map<String, Long> since = new LinkedHashMap<>();
    private final Map<String, GraphBlock> blocks = new LinkedHashMap<>();

    BlockedBook(
            Map<String, BoardStatus> statuses,
            Map<String, BoardTicket> tickets,
            Map<String, UUID> claims,
            Map<String, Integer> failures,
            Map<String, String> reasons,
            FailureHistory history,
            List<BoardLogEntry> notes) {
        this.statuses = Objects.requireNonNull(statuses, "Статусы доски не могут быть null: блокировка их меняет");
        this.tickets = Objects.requireNonNull(tickets, "Билеты доски не могут быть null: блокировка их читает");
        this.claims = Objects.requireNonNull(claims, "Держатели не могут быть null: разблокировка их снимает");
        this.failures = Objects.requireNonNull(failures, "Счётчики провалов не могут быть null: блокировка их чистит");
        this.reasons = Objects.requireNonNull(reasons, "Причины провалов не могут быть null: они пишутся в лог");
        this.history = Objects.requireNonNull(history, "История провалов не может быть null: она пишется в лог");
        this.notes = Objects.requireNonNull(notes, "Журнал блокировок не может быть null: переходы пишутся в него");
    }

    record BlockedNote(
            BoardTicket ticket,
            int failuresSoFar,
            Map<String, Integer> missingNow,
            StockView stock,
            long now,
            String node,
            String target,
            GraphBlock.@Nullable Need need) {}

    void note(BlockedNote note) {
        BoardTicket ticket = note.ticket();
        int failuresSoFar = note.failuresSoFar();
        Map<String, Integer> missingNow = note.missingNow();
        long now = note.now();
        statuses.put(ticket.id(), BoardStatus.BLOCKED);
        if (note.need() != null) {
            blocks.put(ticket.id(), new GraphBlock(note.node(), note.target(), note.need()));
        }
        missing.put(ticket.id(), missingNow);
        Map<String, Integer> have = new LinkedHashMap<>();
        ticket.requiredItems()
                .keySet()
                .forEach(item -> have.put(item, note.stock().available().getOrDefault(item, 0)));
        available.put(ticket.id(), Map.copyOf(have));
        since.putIfAbsent(ticket.id(), now);
        notes.add(new BoardLogEntry(
                BoardLog.Event.BLOCKED,
                now,
                ticket.id(),
                ticket.boardCase(),
                ticket.level(),
                ticket.cause(),
                BoardRules.priorityOf(ticket.boardCase()),
                ticket.priority(),
                reason(failuresSoFar, missingNow, ticket.id()),
                null,
                ConfigStamp.current(),
                now));
    }

    void block(BoardTicket ticket, GraphBlock block, long now) {
        statuses.put(ticket.id(), BoardStatus.BLOCKED);
        blocks.put(ticket.id(), block);
        since.putIfAbsent(ticket.id(), now);
        notes.add(new BoardLogEntry(
                BoardLog.Event.BLOCKED,
                now,
                ticket.id(),
                ticket.boardCase(),
                ticket.level(),
                ticket.cause(),
                BoardRules.priorityOf(ticket.boardCase()),
                ticket.priority(),
                List.of("need=" + block.need()),
                null,
                ConfigStamp.current(),
                now));
    }

    List<GraphBlock> blocks() {
        return List.copyOf(blocks.values());
    }

    boolean unblock(String ticketId, long now) {
        BoardTicket ticket = tickets.get(ticketId);
        if (ticket == null || statuses.get(ticketId) != BoardStatus.BLOCKED) {
            return false;
        }
        release(ticket, now, List.of("need=" + blocks.getOrDefault(ticketId, null)));
        return true;
    }

    boolean reprioritize(String ticketId, int priority) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: приоритет меняется у задания по id");
        BoardTicket ticket = tickets.get(ticketId);
        BoardStatus status = statuses.get(ticketId);
        boolean open = status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED
                || status == BoardStatus.BLOCKED;
        if (ticket == null || !open || ticket.priority() == priority) {
            return false;
        }
        tickets.put(ticketId, ticket.withPriority(priority));
        return true;
    }

    boolean release(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: возврат идёт по id задания");
        if (!tickets.containsKey(ticketId) || statuses.get(ticketId) != BoardStatus.ASSIGNED) {
            return false;
        }
        statuses.put(ticketId, BoardStatus.PENDING);
        claims.remove(ticketId);
        return true;
    }

    boolean require(String ticketId, Map<String, Integer> requiredItems) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: материалы освежают по id задания");
        Objects.requireNonNull(requiredItems, "Материалы не могут быть null: пустые задают пустой картой");
        BoardTicket ticket = tickets.get(ticketId);
        BoardStatus status = statuses.get(ticketId);
        if (ticket == null
                || status == null
                || (status != BoardStatus.PENDING
                        && status != BoardStatus.WAITING_RESOURCES
                        && status != BoardStatus.BLOCKED)) {
            return false;
        }
        Map<String, Integer> items = Map.copyOf(requiredItems);
        if (items.equals(ticket.requiredItems())) {
            return false;
        }
        tickets.put(
                ticketId,
                new BoardTicket(
                        ticket.id(),
                        ticket.boardCase(),
                        ticket.assignee(),
                        ticket.priority(),
                        ticket.createdAt(),
                        ticket.level(),
                        ticket.cause(),
                        ticket.dependsOn(),
                        items,
                        ticket.modifiers()));
        if (items.isEmpty() && (status == BoardStatus.WAITING_RESOURCES || status == BoardStatus.BLOCKED)) {
            statuses.put(ticketId, BoardStatus.PENDING);
            clear(ticketId);
        }
        return true;
    }

    void unblock(StockView stock, long now) {
        for (BoardTicket ticket : List.copyOf(tickets.values())) {
            if (statuses.get(ticket.id()) != BoardStatus.BLOCKED) {
                continue;
            }
            GraphBlock block = blocks.get(ticket.id());
            Map<String, Integer> before = missing.getOrDefault(ticket.id(), Map.of());
            if (!satisfied(block, ticket, stock)) {
                continue;
            }
            Map<String, Integer> missingNow = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
            release(ticket, now, changed(before, missingNow));
        }
    }

    private void release(BoardTicket ticket, long now, List<String> change) {
        statuses.put(ticket.id(), BoardStatus.PENDING);
        claims.remove(ticket.id());
        failures.remove(ticket.id());
        reasons.remove(ticket.id());
        missing.remove(ticket.id());
        available.remove(ticket.id());
        since.remove(ticket.id());
        blocks.remove(ticket.id());
        notes.add(new BoardLogEntry(
                BoardLog.Event.UNBLOCKED,
                now,
                ticket.id(),
                ticket.boardCase(),
                ticket.level(),
                ticket.cause(),
                BoardRules.priorityOf(ticket.boardCase()),
                ticket.priority(),
                change,
                null,
                ConfigStamp.current(),
                now));
    }

    void clear(String ticketId) {
        missing.remove(ticketId);
        available.remove(ticketId);
        since.remove(ticketId);
        blocks.remove(ticketId);
    }

    void clear() {
        missing.clear();
        available.clear();
        since.clear();
        blocks.clear();
    }

    List<BoardState.Blocked> snapshot() {
        List<BoardState.Blocked> states = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>(missing.keySet());
        ids.addAll(blocks.keySet());
        for (String id : ids) {
            states.add(new BoardState.Blocked(
                    id,
                    missing.getOrDefault(id, Map.of()),
                    available.getOrDefault(id, Map.of()),
                    since.getOrDefault(id, 0L),
                    blocks.get(id)));
        }
        return List.copyOf(states);
    }

    void restore(List<BoardState.Blocked> states) {
        missing.clear();
        available.clear();
        since.clear();
        blocks.clear();
        for (BoardState.Blocked state : states) {
            missing.put(state.ticketId(), state.missing());
            available.put(state.ticketId(), state.available());
            since.put(state.ticketId(), state.since());
            if (state.block() != null) {
                blocks.put(state.ticketId(), state.block());
            }
        }
    }

    private static boolean satisfied(@Nullable GraphBlock block, BoardTicket ticket, StockView stock) {
        if (block == null) {
            return ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock)
                    .isEmpty();
        }
        if (block.need() instanceof GraphBlock.Item item) {
            return stock.available().getOrDefault(item.item(), 0) >= item.count();
        }
        return false;
    }

    private List<String> reason(int failuresSoFar, Map<String, Integer> missingNow, String ticketId) {
        List<String> reason = new ArrayList<>();
        reason.add("failures=" + failuresSoFar);
        missingNow.forEach((item, count) -> reason.add("missing=" + item + ":" + count));
        String last = reasons.get(ticketId);
        if (last != null) {
            reason.add("last=" + last);
        }
        reason.add("history=" + String.join("; ", history.of(ticketId)));
        return List.copyOf(reason);
    }

    private static List<String> changed(Map<String, Integer> before, Map<String, Integer> after) {
        List<String> changed = new ArrayList<>(List.of("failures=0"));
        Set<String> items = new HashSet<>(before.keySet());
        items.addAll(after.keySet());
        for (String item : items) {
            int was = before.getOrDefault(item, 0);
            int now = after.getOrDefault(item, 0);
            if (was != now) {
                changed.add("changed=" + item + ":" + was + "->" + now);
            }
        }
        return List.copyOf(changed);
    }
}
