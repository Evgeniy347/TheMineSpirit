package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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

    void note(BoardTicket ticket, int failuresSoFar, Map<String, Integer> missingNow, long now) {
        statuses.put(ticket.id(), BoardStatus.BLOCKED);
        missing.put(ticket.id(), missingNow);
        Map<String, Integer> have = new LinkedHashMap<>();
        ticket.requiredItems()
                .forEach((item, need) -> have.put(item, Math.max(0, need - missingNow.getOrDefault(item, 0))));
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
            Map<String, Integer> missingNow = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
            Map<String, Integer> before = missing.getOrDefault(ticket.id(), Map.of());
            boolean improved = improved(ticket.id(), stock);
            if (!improved && !expired(ticket.id(), now)) {
                continue;
            }
            release(ticket, now, changed(improved, before, missingNow));
        }
    }

    int releaseAll(long now) {
        int released = 0;
        for (BoardTicket ticket : List.copyOf(tickets.values())) {
            if (statuses.get(ticket.id()) != BoardStatus.BLOCKED) {
                continue;
            }
            release(ticket, now, List.of("failures=0", "idle=" + BoardTuning.idleReleaseTicks()));
            released++;
        }
        return released;
    }

    private void release(BoardTicket ticket, long now, List<String> change) {
        statuses.put(ticket.id(), BoardStatus.PENDING);
        claims.remove(ticket.id());
        failures.remove(ticket.id());
        reasons.remove(ticket.id());
        missing.remove(ticket.id());
        available.remove(ticket.id());
        since.remove(ticket.id());
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
    }

    void clear() {
        missing.clear();
        available.clear();
        since.clear();
    }

    private boolean improved(String ticketId, StockView stock) {
        Map<String, Integer> before = available.getOrDefault(ticketId, Map.of());
        for (Map.Entry<String, Integer> entry : before.entrySet()) {
            if (stock.available().getOrDefault(entry.getKey(), 0) > entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    private boolean expired(String ticketId, long now) {
        Long blockedAt = since.get(ticketId);
        return blockedAt != null && now - blockedAt >= BoardTuning.caseFailPauseCapTicks();
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

    private static List<String> changed(boolean improved, Map<String, Integer> before, Map<String, Integer> after) {
        List<String> changed = new ArrayList<>(List.of("failures=0"));
        if (!improved) {
            changed.add("timeout=" + BoardTuning.caseFailPauseCapTicks());
            return List.copyOf(changed);
        }
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
