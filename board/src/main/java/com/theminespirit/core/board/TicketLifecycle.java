package com.theminespirit.core.board;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.api.ConfigStamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

final class TicketLifecycle {
    private final Map<String, BoardStatus> statuses;
    private final Map<String, BoardTicket> tickets;
    private final Map<String, UUID> claimAssignees;
    private final Map<String, Long> failTicks;
    private final Map<String, Integer> ticketFailures;
    private final Map<String, String> lastFailReasons;
    private final WaitNotes waitNotes;
    private final HoldBook holds;
    private final BlockedBook blocked;
    private final CasePauseBook casePauses;
    private final StuckSpirits stuckSpirits;
    private final FailureHistory failHistory;
    private final BoardLedger ledger;
    private final BoardEvents events;
    private final BoardDependencies dependencies;
    private final BoardFailureDesk failures;
    private final List<BoardLogEntry> detachments;
    private final LongSupplier nowTicks;

    TicketLifecycle(
            Map<String, BoardStatus> statuses,
            Map<String, BoardTicket> tickets,
            Map<String, UUID> claimAssignees,
            Map<String, Long> failTicks,
            Map<String, Integer> ticketFailures,
            Map<String, String> lastFailReasons,
            WaitNotes waitNotes,
            HoldBook holds,
            BlockedBook blocked,
            CasePauseBook casePauses,
            StuckSpirits stuckSpirits,
            FailureHistory failHistory,
            BoardLedger ledger,
            BoardEvents events,
            BoardDependencies dependencies,
            BoardFailureDesk failures,
            List<BoardLogEntry> detachments,
            LongSupplier nowTicks) {
        this.statuses = statuses;
        this.tickets = tickets;
        this.claimAssignees = claimAssignees;
        this.failTicks = failTicks;
        this.ticketFailures = ticketFailures;
        this.lastFailReasons = lastFailReasons;
        this.waitNotes = waitNotes;
        this.holds = holds;
        this.blocked = blocked;
        this.casePauses = casePauses;
        this.stuckSpirits = stuckSpirits;
        this.failHistory = failHistory;
        this.ledger = ledger;
        this.events = events;
        this.dependencies = dependencies;
        this.failures = failures;
        this.detachments = detachments;
        this.nowTicks = nowTicks;
    }

    BoardOutcome submit(BoardTicket ticket) {
        Objects.requireNonNull(ticket, "ticket не может быть null: доска хранит задание, " + "а не пустую заявку");
        if (!SettlementStage.allows(ticket.boardCase(), ticket.level(), ledger.stage())) {
            return BoardOutcome.STAGE_LOCKED;
        }
        long now = nowTicks.getAsLong();
        BoardTicket existing = tickets.get(ticket.id());
        if (existing != null) {
            BoardStatus current = statuses.get(ticket.id());
            boolean stage = StageTicket.stage(ticket.id());
            boolean changed = BoardRules.changed(current, ticket, existing, stage);
            boolean retry = current == BoardStatus.FAILED
                    && !recentlyFailed(ticket.id(), now)
                    && (stage || BoardRules.retriedFromWorld(ticket.boardCase()));
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

    BoardOutcome claim(String ticketId, UUID spirit, long now) {
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
        if (!dependencies.met(ticket)) {
            return BoardOutcome.NOT_FOUND;
        }
        if (status == BoardStatus.ASSIGNED) {
            if (spirit.equals(claimAssignees.get(ticketId))) {
                return BoardOutcome.CLAIMED;
            }
            return BoardOutcome.TAKEN_BY_OTHER;
        }
        if (!RestTickets.is(ticketId) && holdsRestTicket(spirit)) {
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

    boolean complete(String ticketId) {
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
        ledger.complete(ticket.boardCase(), ticket.level(), ticket.id());
        events.transition(BoardLog.Event.DONE, ticket, claimAssignees.get(ticketId), nowTicks.getAsLong());
        ticketFailures.remove(ticketId);
        lastFailReasons.remove(ticketId);
        blocked.clear(ticketId);
        holds.release(ticketId);
        return true;
    }

    boolean fail(String id, StockView s, @Nullable String why, GraphBlock.@Nullable Need need, boolean lost) {
        Objects.requireNonNull(id, "ticketId не может быть null: провал идёт по id задания");
        Objects.requireNonNull(s, "stock не может быть null: по нему фиксируются условия блокировки");
        BoardTicket ticket = tickets.get(id);
        if (ticket == null || statuses.get(id) != BoardStatus.ASSIGNED) {
            return false;
        }
        long now = nowTicks.getAsLong();
        statuses.put(id, BoardStatus.FAILED);
        failTicks.put(id, now);
        UUID holder = holder(ticket);
        if (holder != null) {
            noteCaseFailure(holder, ticket, now);
            if (lost || need instanceof GraphBlock.Path) {
                stuckSpirits.mark(holder, now, why == null ? "провал движения" : why, true);
            }
        }
        String previous = why == null ? null : lastFailReasons.put(id, why);
        failHistory.note(id, now, why);
        if (why != null && why.equals(previous)) {
            ticketFailures.put(id, BoardTuning.ticketFailLimit() - 1);
        }
        noteTicketFailure(ticket, now, s, need);
        holds.release(id);
        events.transition(BoardLog.Event.FAILED, ticket.withFailure(why), claimAssignees.get(id), now);
        return true;
    }

    boolean reopen(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: переоткрытие идёт по id задания");
        BoardTicket ticket = tickets.get(ticketId);
        if (ticket == null || statuses.get(ticketId) != BoardStatus.DONE) {
            return false;
        }
        tickets.remove(ticketId);
        statuses.remove(ticketId);
        claimAssignees.remove(ticketId);
        ledger.reopen(ticket.boardCase(), ticket.level(), ticket.id());
        events.transition(BoardLog.Event.REOPENED, ticket, null, nowTicks.getAsLong());
        return true;
    }

    boolean recentlyFailed(String ticketId, long now) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: откат считается по id задания");
        BoardTiming.require(now);
        Long failedAt = failTicks.get(ticketId);
        return failedAt != null && now - failedAt < BoardTuning.failBackoffTicks();
    }

    boolean availableTo(UUID spirit, BoardTicket ticket, long now) {
        if (RestTickets.is(ticket.id())) {
            return true;
        }
        boolean rescue = ticket.boardCase() == BoardCase.RESCUE_SPIRIT;
        return (rescue || !stuckSpirits.isStuck(spirit, now)) && casePauses.available(spirit, ticket, now);
    }

    private boolean holdsRestTicket(UUID spirit) {
        for (Map.Entry<String, UUID> entry : claimAssignees.entrySet()) {
            if (spirit.equals(entry.getValue())
                    && RestTickets.is(entry.getKey())
                    && statuses.get(entry.getKey()) == BoardStatus.ASSIGNED) {
                return true;
            }
        }
        return false;
    }

    private @Nullable UUID holder(BoardTicket ticket) {
        return claimAssignees.containsKey(ticket.id())
                ? claimAssignees.get(ticket.id())
                : ticket.assignee().orElse(null);
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
}
