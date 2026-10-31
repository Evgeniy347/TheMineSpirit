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

    void unblock(StockView stock, long now) {
        for (BoardTicket ticket : List.copyOf(tickets.values())) {
            if (statuses.get(ticket.id()) != BoardStatus.BLOCKED) {
                continue;
            }
            Map<String, Integer> missingNow = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
            Map<String, Integer> before = missing.getOrDefault(ticket.id(), Map.of());
            boolean improved = ShortageChange.improved(before, missingNow);
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
        since.remove(ticketId);
    }

    void clear() {
        missing.clear();
        since.clear();
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
