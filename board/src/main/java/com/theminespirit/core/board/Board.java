package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

public final class Board {
    private static final String FIRST_STEPS_PREFIX = "first:";
    private static final String STONE_PART_PREFIX = "stone:";
    private static final String FENCE_PART_PREFIX = "fence:";
    private static final List<String> SOURCE_PREFIXES = List.of(
            FIRST_STEPS_PREFIX,
            STONE_PART_PREFIX,
            FENCE_PART_PREFIX,
            "gather:",
            "graph:",
            "supply:",
            "mine_level_",
            "repair_tool:");

    private final Map<String, BoardStatus> statuses = new LinkedHashMap<>();
    private final Map<String, BoardTicket> tickets = new LinkedHashMap<>();
    private final Map<String, UUID> claimAssignees = new LinkedHashMap<>();
    private final Map<String, Long> failTicks = new LinkedHashMap<>();
    private final Map<String, Integer> ticketFailures = new LinkedHashMap<>();
    private final Map<String, String> lastFailReasons = new LinkedHashMap<>();
    private final WaitNotes waitNotes = new WaitNotes();
    private final HoldBook holds = new HoldBook();
    private final BlockedBook blocked;
    private final BoardFailureDesk failures;
    private final BoardAssignments assignments;
    private final CasePauseBook casePauses = new CasePauseBook();
    private final StuckSpirits stuckSpirits = new StuckSpirits();
    private final List<BoardLogEntry> detachments = new ArrayList<>();
    private final List<BoardLogEntry> blockNotes = new ArrayList<>();
    private final FailureHistory failHistory = new FailureHistory();
    private final BoardLedger ledger = new BoardLedger();
    private final BoardDependencies dependencies;
    private final LongSupplier nowTicks;
    private final BoardEvents events;

    public Board(LongSupplier nowTicks) {
        this(nowTicks, BoardEvents.NONE);
    }

    public Board(LongSupplier nowTicks, BoardEvents events) {
        Objects.requireNonNull(nowTicks, "nowTicks не может быть null: без часов доска не считает просрочку");
        this.nowTicks = nowTicks;
        this.events = Objects.requireNonNull(events, "Слушатель переходов не может быть null: иначе BoardEvents.NONE");
        this.assignments = new BoardAssignments(tickets, statuses, claimAssignees, nowTicks);
        this.blocked = new BlockedBook(
                statuses, tickets, claimAssignees, ticketFailures, lastFailReasons, failHistory, blockNotes);
        this.failures =
                new BoardFailureDesk(blocked, tickets, statuses, claimAssignees, stuckSpirits, blockNotes, detachments);
        this.dependencies = new BoardDependencies(tickets, statuses, ledger);
    }

    public long now() {
        return nowTicks.getAsLong();
    }

    public BoardState snapshot() {
        List<BoardState.Entry> entries = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            entries.add(new BoardState.Entry(
                    ticket,
                    Objects.requireNonNull(statuses.get(ticket.id()), "У билета '" + ticket.id() + "' нет статуса"),
                    claimAssignees.get(ticket.id()),
                    failTicks.get(ticket.id()),
                    ticketFailures.getOrDefault(ticket.id(), 0)));
        }
        return new BoardState(
                entries, ledger.keys(), blocked.snapshot(), casePauses.snapshot(), stuckSpirits.snapshot());
    }

    public void restore(BoardState state) {
        Objects.requireNonNull(state, "Снимок доски не может быть null: восстанавливать нечего");
        tickets.clear();
        statuses.clear();
        claimAssignees.clear();
        failTicks.clear();
        ticketFailures.clear();
        blocked.clear();
        casePauses.clear();
        stuckSpirits.clear();
        blocked.restore(state.blocked());
        casePauses.restore(state.pauses());
        stuckSpirits.restore(state.stuck(), nowTicks.getAsLong());
        ledger.clear();
        ledger.restore(state.ledger());
        holds.clear();
        for (BoardState.Entry entry : state.entries()) {
            BoardTicket ticket = entry.ticket();
            tickets.put(ticket.id(), ticket);
            statuses.put(ticket.id(), entry.status());
            if (entry.holder() != null) claimAssignees.put(ticket.id(), entry.holder());
            if (entry.failedAt() != null) failTicks.put(ticket.id(), entry.failedAt());
            if (entry.failures() > 0) ticketFailures.put(ticket.id(), entry.failures());
        }
    }

    public BoardOutcome submit(BoardTicket ticket) {
        Objects.requireNonNull(ticket, "ticket не может быть null: доска хранит задание, " + "а не пустую заявку");
        if (!SettlementStage.allows(ticket.boardCase(), ticket.level(), ledger.stage())) {
            return BoardOutcome.STAGE_LOCKED;
        }
        long now = nowTicks.getAsLong();
        BoardTicket existing = tickets.get(ticket.id());
        if (existing != null) {
            BoardStatus current = statuses.get(ticket.id());
            boolean stage = StageTicket.stage(ticket.id());
            boolean changed = (current == BoardStatus.BLOCKED && !ticket.sameDetails(existing))
                    || (current == BoardStatus.DONE && ticket.replacesDone(existing, stage));
            boolean retry = current == BoardStatus.FAILED
                    && !recentlyFailed(ticket.id(), now)
                    && (stage || retriedFromWorld(ticket.boardCase()));
            if (current == BoardStatus.BLOCKED) {
                if (changed) {
                    tickets.put(ticket.id(), ticket);
                    events.transition(BoardLog.Event.SUBMITTED, ticket, null, now);
                }
                return BoardOutcome.DUPLICATE;
            }
            if (!changed && !retry) {
                return BoardOutcome.DUPLICATE;
            }
            tickets.put(ticket.id(), ticket);
            statuses.put(ticket.id(), BoardStatus.PENDING);
            claimAssignees.remove(ticket.id());
            if (changed) {
                ticketFailures.remove(ticket.id());
                lastFailReasons.remove(ticket.id());
                blocked.clear(ticket.id());
            }
            events.transition(BoardLog.Event.SUBMITTED, ticket, null, now);
            return BoardOutcome.ACCEPTED;
        }
        if (recentlyFailed(ticket.id(), now)) {
            return BoardOutcome.BACKOFF;
        }
        tickets.put(ticket.id(), ticket);
        statuses.put(ticket.id(), BoardStatus.PENDING);
        events.transition(BoardLog.Event.SUBMITTED, ticket, null, now);
        return BoardOutcome.ACCEPTED;
    }

    private static boolean retriedFromWorld(BoardCase boardCase) {
        return boardCase == BoardCase.RESCUE_SPIRIT
                || boardCase == BoardCase.FILL_CRATER
                || boardCase == BoardCase.DISMANTLE_SHELTER
                || boardCase == BoardCase.REPAIR_FENCE;
    }

    public boolean recentlyFailed(String ticketId, long now) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: откат считается по id задания");
        BoardTiming.require(now);
        Long failedAt = failTicks.get(ticketId);
        return failedAt != null && now - failedAt < BoardTuning.failBackoffTicks();
    }

    public List<BoardTicket> claimable(StockView stock, long now) {
        Objects.requireNonNull(stock, "stock не может быть null: гейт без снимка запасов не проверить");
        BoardTiming.require(now);
        releaseStuckHolders(now);
        unblockChanged(stock, now);
        refreshGateStatuses(stock);
        List<BoardTicket> ready = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            if (statuses.get(ticket.id()) != BoardStatus.PENDING || holds.held(ticket.id())) {
                continue;
            }
            if (!dependenciesDone(ticket)) {
                continue;
            }
            if (ResourceGate.gate(new StockNeed(ticket.requiredItems()), stock) != GateOutcome.READY) {
                continue;
            }
            ready.add(ticket);
        }
        waitNotes.retain(ready.stream().map(BoardTicket::id).toList());
        ready.sort(QueueOrder.of(tickets.values(), statuses, now()));
        return List.copyOf(ready);
    }

    public void noteWait(String ticketId, @Nullable String reason) {
        waitNotes.note(ticketId, reason, nowTicks.getAsLong());
    }

    public Optional<String> waitNote(String ticketId) {
        return waitNotes.of(ticketId, nowTicks.getAsLong());
    }

    public HoldBook holds() {
        return holds;
    }

    public BoardOutcome claim(String ticketId, UUID spirit, long now) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: выдача идёт по id задания");
        Objects.requireNonNull(spirit, "spirit не может быть null: выдача идёт конкретному духу");
        BoardTiming.require(now);
        BoardTicket ticket = tickets.get(ticketId);
        if (ticket == null) {
            return BoardOutcome.NOT_FOUND;
        }
        BoardStatus status = statuses.get(ticketId);
        if (status == BoardStatus.DONE || status == BoardStatus.FAILED || status == BoardStatus.CANCELLED) {
            return BoardOutcome.NOT_FOUND;
        }
        if (status == BoardStatus.BLOCKED) {
            return BoardOutcome.BLOCKED;
        }
        if (holds.held(ticketId)) {
            return BoardOutcome.GATE_BLOCKED;
        }
        if (!dependenciesDone(ticket)) {
            return BoardOutcome.NOT_FOUND;
        }
        if (status == BoardStatus.ASSIGNED) {
            if (spirit.equals(claimAssignees.get(ticketId))) {
                return BoardOutcome.CLAIMED;
            }
            return BoardOutcome.TAKEN_BY_OTHER;
        }
        if (!availableTo(spirit, ticket, now)) {
            return BoardOutcome.SPIRIT_PAUSED;
        }
        if (status == BoardStatus.WAITING_RESOURCES) {
            return BoardOutcome.GATE_BLOCKED;
        }
        statuses.put(ticketId, BoardStatus.ASSIGNED);
        claimAssignees.put(ticketId, spirit);
        waitNotes.forget(ticketId);
        holds.release(ticketId);
        events.transition(BoardLog.Event.ASSIGNED, ticket, spirit, now);
        return BoardOutcome.CLAIMED;
    }

    public boolean complete(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: завершение идёт по id задания");
        BoardTicket ticket = tickets.get(ticketId);
        if (ticket == null || statuses.get(ticketId) != BoardStatus.ASSIGNED) {
            return false;
        }
        UUID holder = holder(ticket);
        if (holder != null) {
            casePauses.reset(holder, ticket.boardCase());
        }
        RescuePlans.complete(stuckSpirits, ticket, holder, nowTicks.getAsLong());
        statuses.put(ticketId, BoardStatus.DONE);
        ledger.complete(ticket.boardCase(), ticket.level());
        events.transition(BoardLog.Event.DONE, ticket, claimAssignees.get(ticketId), nowTicks.getAsLong());
        ticketFailures.remove(ticketId);
        lastFailReasons.remove(ticketId);
        blocked.clear(ticketId);
        holds.release(ticketId);
        return true;
    }

    public boolean fail(String ticketId) {
        return fail(ticketId, new StockView(Map.of()));
    }

    public boolean fail(String ticketId, StockView stock) {
        return fail(ticketId, stock, null);
    }

    public boolean fail(String ticketId, StockView stock, @Nullable String reason) {
        return fail(ticketId, stock, reason, null);
    }

    public boolean fail(String ticketId, StockView stock, @Nullable String reason, GraphBlock.@Nullable Need need) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: провал идёт по id задания");
        Objects.requireNonNull(stock, "stock не может быть null: по нему фиксируются условия блокировки");
        BoardTicket ticket = tickets.get(ticketId);
        if (ticket == null || statuses.get(ticketId) != BoardStatus.ASSIGNED) {
            return false;
        }
        long now = nowTicks.getAsLong();
        statuses.put(ticketId, BoardStatus.FAILED);
        failTicks.put(ticketId, now);
        UUID holder = holder(ticket);
        if (holder != null) {
            noteCaseFailure(holder, ticket, now);
            if (need instanceof GraphBlock.Path) {
                stuckSpirits.mark(holder, now, reason == null ? "провал движения" : reason, true);
            }
        }
        String previous = reason == null ? null : lastFailReasons.put(ticketId, reason);
        failHistory.note(ticketId, now, reason);
        if (reason != null && reason.equals(previous)) {
            ticketFailures.put(ticketId, BoardTuning.ticketFailLimit() - 1);
        }
        noteTicketFailure(ticket, now, stock, need);
        holds.release(ticketId);
        events.transition(BoardLog.Event.FAILED, ticket.withFailure(reason), claimAssignees.get(ticketId), now);
        return true;
    }

    public StuckSpirits stuck() {
        return stuckSpirits;
    }

    public boolean availableTo(UUID spirit, BoardCase boardCase, long now) {
        BoardTiming.require(now);
        return !stuckSpirits.isStuck(spirit, now) && casePauses.available(spirit, boardCase, now);
    }

    public boolean availableTo(UUID spirit, BoardTicket ticket, long now) {
        BoardTiming.require(now);
        boolean rescue = ticket.boardCase() == BoardCase.RESCUE_SPIRIT;
        return (rescue || !stuckSpirits.isStuck(spirit, now)) && casePauses.available(spirit, ticket, now);
    }

    public boolean caseDetached(UUID spirit, BoardCase boardCase) {
        return casePauses.detached(spirit, boardCase);
    }

    public int releaseDetached() {
        return casePauses.releaseDetached();
    }

    public int consecutiveFailures(UUID spirit, BoardCase boardCase) {
        return casePauses.consecutive(spirit, boardCase);
    }

    public long casePauseTicks(UUID spirit, BoardCase boardCase) {
        return casePauses.pauseTicks(spirit, boardCase);
    }

    public int ticketFailures(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: счёт провалов идёт по заданию");
        return ticketFailures.getOrDefault(ticketId, 0);
    }

    public boolean blocked(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: блокировка читается по заданию");
        return statuses.get(ticketId) == BoardStatus.BLOCKED;
    }

    public BoardFailureDesk failures() {
        return failures;
    }

    public boolean cancel(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: отмена идёт по id задания");
        if (!tickets.containsKey(ticketId)) {
            return false;
        }
        Set<String> cancelled = new HashSet<>();
        cancelWithDependants(ticketId, cancelled, BoardLog.Event.CANCELLED);
        return true;
    }

    public boolean reprioritize(String ticketId, int priority) {
        return blocked.reprioritize(ticketId, priority);
    }

    public boolean reopen(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: переоткрытие идёт по id задания");
        BoardTicket ticket = tickets.get(ticketId);
        if (ticket == null || statuses.get(ticketId) != BoardStatus.DONE) {
            return false;
        }
        tickets.remove(ticketId);
        statuses.remove(ticketId);
        claimAssignees.remove(ticketId);
        ledger.reopen(ticket.boardCase(), ticket.level());
        events.transition(BoardLog.Event.REOPENED, ticket, null, nowTicks.getAsLong());
        return true;
    }

    public boolean require(String ticketId, Map<String, Integer> requiredItems) {
        return blocked.require(ticketId, requiredItems);
    }

    public boolean built(String ticketId) {
        BoardTicket ticket = tickets.get(ticketId);
        BoardStatus status = statuses.get(ticketId);
        if (ticket == null || status == null || status == BoardStatus.DONE || status == BoardStatus.ASSIGNED) {
            return false;
        }
        statuses.put(ticketId, BoardStatus.DONE);
        ledger.complete(ticket.boardCase(), ticket.level());
        events.transition(BoardLog.Event.DONE, ticket, null, nowTicks.getAsLong());
        ticketFailures.remove(ticketId);
        lastFailReasons.remove(ticketId);
        blocked.clear(ticketId);
        return true;
    }

    public boolean release(String ticketId) {
        return blocked.release(ticketId);
    }

    public List<HolderRelease> releaseDeadHolders(Set<UUID> live) {
        Objects.requireNonNull(live, "live не может быть null: без живых духов не понять, чей держатель погиб");
        List<HolderRelease> released = new ArrayList<>();
        for (BoardTicket ticket : List.copyOf(tickets.values())) {
            if (statuses.get(ticket.id()) != BoardStatus.ASSIGNED) {
                continue;
            }
            UUID holder = holder(ticket);
            if (holder == null || live.contains(holder)) {
                continue;
            }
            if (release(ticket.id())) {
                released.add(new HolderRelease(
                        ticket.id(), ticket.boardCase(), holder, BoardActivities.isActivity(ticket.boardCase())));
            }
        }
        return List.copyOf(released);
    }

    public Optional<BoardCase> occupation(UUID spirit) {
        return assignments.occupation(spirit);
    }

    public List<String> retain(Set<String> liveCauses, long now) {
        Objects.requireNonNull(liveCauses, "liveCauses не может быть null: без живых причин не понять, что актуально");
        BoardTiming.require(now);
        List<String> detached = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            BoardStatus status = statuses.get(ticket.id());
            if (status == BoardStatus.DONE || status == BoardStatus.FAILED || status == BoardStatus.CANCELLED) {
                continue;
            }
            if (SupplyGate.retains(ticket, liveCauses)) {
                continue;
            }
            detached.add(ticket.id());
        }
        Set<String> cancelled = new HashSet<>();
        for (String id : detached) {
            cancelWithDependants(id, cancelled, BoardLog.Event.DETACHED);
        }
        return List.copyOf(detached);
    }

    public List<String> resetForNewRules() {
        List<String> removed = new ArrayList<>();
        for (BoardTicket ticket : List.copyOf(tickets.values())) {
            BoardStatus status = statuses.get(ticket.id());
            boolean sourceMade = SOURCE_PREFIXES.stream().anyMatch(ticket.id()::startsWith) || ticket.fromGraph();
            boolean waiting = status == BoardStatus.PENDING
                    || status == BoardStatus.WAITING_RESOURCES
                    || status == BoardStatus.ASSIGNED;
            if (status == BoardStatus.BLOCKED || status == BoardStatus.FAILED || (sourceMade && waiting)) {
                tickets.remove(ticket.id());
                statuses.remove(ticket.id());
                claimAssignees.remove(ticket.id());
                blocked.clear(ticket.id());
                holds.release(ticket.id());
                removed.add(ticket.id());
            }
        }
        failTicks.clear();
        ticketFailures.clear();
        casePauses.clear();
        return List.copyOf(removed);
    }

    public int prune(long now) {
        return prune(now, Set.of());
    }

    public int prune(long now, Set<String> liveCauses) {
        Objects.requireNonNull(liveCauses, "Живые причины не могут быть null: пустой набор задают пустым");
        BoardTiming.require(now);
        int removed = 0;
        List<String> removableIds = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            if (StageTicket.removable(statuses.get(ticket.id()), ticket.id())
                    || BoardActivities.stale(ticket, statuses.get(ticket.id()), liveCauses)) {
                removableIds.add(ticket.id());
            }
        }
        for (String id : removableIds) {
            cancelDependants(id);
        }
        for (String id : removableIds) {
            tickets.remove(id);
            statuses.remove(id);
            holds.release(id);
            removed++;
        }
        failTicks.entrySet().removeIf(mark -> now - mark.getValue() >= BoardTuning.failBackoffTicks());
        holds.retain(tickets.keySet());
        removed += evictOverflow(liveCauses);
        return removed;
    }

    public List<BoardTicket> assignedTo(UUID spirit) {
        return assignments.assignedTo(spirit);
    }

    public Map<UUID, Integer> assignedCounts() {
        return assignments.counts();
    }

    public boolean updatePending(BoardTicket ticket) {
        return assignments.updatePending(ticket);
    }

    private @Nullable UUID holder(BoardTicket ticket) {
        return claimAssignees.containsKey(ticket.id())
                ? claimAssignees.get(ticket.id())
                : ticket.assignee().orElse(null);
    }

    public List<BoardTicket> all() {
        List<BoardTicket> all = new ArrayList<>(tickets.values());
        all.sort(QueueOrder.of(tickets.values(), statuses, now()));
        return List.copyOf(all);
    }

    public List<BoardTicket> tickets() {
        return List.copyOf(tickets.values());
    }

    public Optional<BoardTicket> find(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: поиск идёт по id задания");
        return Optional.ofNullable(tickets.get(ticketId));
    }

    public Optional<BoardStatus> statusOf(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: статус читается по id задания");
        return Optional.ofNullable(statuses.get(ticketId));
    }

    public BoardLedger ledger() {
        return ledger;
    }

    public Optional<UUID> holderOf(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: держатель читается по id задания");
        BoardTicket ticket = tickets.get(ticketId);
        if (ticket == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(holder(ticket));
    }

    private void refreshGateStatuses(StockView stock) {
        for (BoardTicket ticket : tickets.values()) {
            BoardStatus status = statuses.get(ticket.id());
            if (status != BoardStatus.PENDING && status != BoardStatus.WAITING_RESOURCES) {
                continue;
            }
            GateOutcome gate = ResourceGate.gate(new StockNeed(ticket.requiredItems()), stock);
            if (gate == GateOutcome.WAITING && status == BoardStatus.PENDING) {
                statuses.put(ticket.id(), BoardStatus.WAITING_RESOURCES);
            } else if (gate == GateOutcome.READY && status == BoardStatus.WAITING_RESOURCES) {
                statuses.put(ticket.id(), BoardStatus.PENDING);
            }
        }
    }

    public boolean dependenciesMet(BoardTicket ticket) {
        Objects.requireNonNull(ticket, "ticket не может быть null: предшественники считаются у задания");
        return dependencies.met(ticket);
    }

    private boolean dependenciesDone(BoardTicket ticket) {
        return dependencies.met(ticket);
    }

    private void cancelDependants(String ticketId) {
        Set<String> cancelled = new HashSet<>();
        cancelled.add(ticketId);
        for (BoardTicket dependant : List.copyOf(tickets.values())) {
            if (dependant.dependsOn().contains(ticketId)) {
                cancelWithDependants(dependant.id(), cancelled, BoardLog.Event.CANCELLED);
            }
        }
    }

    private void cancelWithDependants(String ticketId, Set<String> cancelled, BoardLog.Event event) {
        if (!cancelled.add(ticketId)) {
            return;
        }
        BoardTicket ticket = tickets.get(ticketId);
        BoardStatus status = statuses.get(ticketId);
        if (ticket != null
                && status != null
                && status != BoardStatus.DONE
                && status != BoardStatus.FAILED
                && status != BoardStatus.CANCELLED) {
            statuses.put(ticketId, BoardStatus.CANCELLED);
            ticketFailures.remove(ticketId);
            lastFailReasons.remove(ticketId);
            blocked.clear(ticketId);
            holds.release(ticketId);
            events.transition(event, ticket, claimAssignees.get(ticketId), nowTicks.getAsLong());
        }
        for (BoardTicket dependant : tickets.values()) {
            if (dependant.dependsOn().contains(ticketId)) {
                cancelWithDependants(dependant.id(), cancelled, BoardLog.Event.CANCELLED);
            }
        }
    }

    private int evictOverflow(Set<String> liveCauses) {
        return PendingEvict.evict(tickets, statuses, this::cancel, liveCauses);
    }

    private void noteCaseFailure(UUID spirit, BoardTicket ticket, long now) {
        if (!casePauses.noteFailure(spirit, ticket, now)) {
            return;
        }
        detachments.add(new BoardLogEntry(
                BoardLog.Event.DETACHED,
                now,
                ticket.id(),
                ticket.boardCase(),
                ticket.level(),
                ticket.cause(),
                ticket.priority(),
                ticket.priority(),
                List.of(),
                spirit.toString(),
                ConfigStamp.current(),
                now));
    }

    private void noteTicketFailure(BoardTicket ticket, long now, StockView stock, GraphBlock.@Nullable Need need) {
        int failuresSoFar = ticketFailures.merge(ticket.id(), 1, Integer::sum);
        failures.noteFailure(ticket, failuresSoFar, now, stock, need);
    }

    private void unblockChanged(StockView stock, long now) {
        blocked.unblock(stock, now);
    }

    private void releaseStuckHolders(long now) {
        failures.releaseStuckHolders(now);
    }

    public List<String> failureHistory(String ticketId) {
        Objects.requireNonNull(ticketId, "id задания не может быть null: история читается по заданию");
        return failHistory.of(ticketId);
    }
}
