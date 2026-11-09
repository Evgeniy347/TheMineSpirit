package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.ActivityCadence;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardReserve;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.StockParts;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class BoardCycle {
    private final BoardService service;
    private final Board board;
    private final Map<String, TicketTarget> targets;
    private final BoardDispatchDesk dispatch;
    private final BoardTicketDesk tickets;
    private final BoardSupplyDesk supply;
    private final BoardSources sources;
    private final BoardHerdDesk herd;
    private final BoardNotices notices;
    private final JournalService journal;
    private final @Nullable BoardStateStore store;
    private Set<String> liveCauses = Set.of();
    private long lastRebuild = -1;
    private long lastActivity;

    BoardCycle(
            BoardService service,
            Board board,
            Map<String, TicketTarget> targets,
            BoardDispatchDesk dispatch,
            BoardTicketDesk tickets,
            BoardSupplyDesk supply,
            BoardSources sources,
            BoardHerdDesk herd,
            BoardNotices notices,
            JournalService journal,
            @Nullable BoardStateStore store) {
        this.service = service;
        this.board = board;
        this.targets = targets;
        this.dispatch = dispatch;
        this.tickets = tickets;
        this.supply = supply;
        this.sources = sources;
        this.herd = herd;
        this.notices = notices;
        this.journal = journal;
        this.store = store;
    }

    void tick(ServerLevel level, long now) {
        journal.flush(JournalService.FLUSH_BUDGET);
        long since = now - lastRebuild;
        if (since >= BoardTuning.boardIntervalTicks()) {
            rebuild(level, now);
            return;
        }
        if (ActivityCadence.due(lastActivity, now)) {
            lastActivity = now;
            dispatch.reconcile(SpiritCensus.all(level), false);
        }
        dispatch.tick(level, now);
    }

    void rebuild(ServerLevel level, long now) {
        board.prune(now, liveCauses);
        Set<String> alive = new HashSet<>();
        for (BoardTicket ticket : board.all()) {
            alive.add(ticket.id());
        }
        targets.keySet().retainAll(alive);
        dispatch.retainTickets(alive);
        List<SpiritEntity> spirits = SpiritCensus.all(level);
        tickets.cancelOrphans(level, spirits);
        StockParts parts = supply.refresh(level, spirits);
        dispatch.settleOwners(spirits);
        supply.ensure(level, service, spirits, now);
        StockView unreserved = parts.free(BoardReserve.items(board));
        Set<String> live = sources.tick(level, service, spirits, unreserved, now);
        live.addAll(liveHerdCauses(level));
        live.addAll(liveDropCauses(level));
        liveCauses = live;
        if (!sources.failedRound()) {
            board.retain(live, now);
        }
        ForceloadGuard.tick(level, spirits, board, targets);
        dispatch.issue(level, spirits, now);
        notices.watchPending(board, stock(), now);
        dispatch.reconcile(spirits, true);
        dispatch.releaseDeadHolders(level, spirits, now);
        dispatch.dropAbsentOwners(spirits, now);
        flushBlockNotes();
        lastRebuild = now;
        journal.flush(JournalService.FLUSH_BUDGET);
        persist();
    }

    void persist() {
        if (store != null) {
            BoardTargetsNbt.persist(store, board.snapshot(), targets);
        }
    }

    private Set<String> liveDropCauses(ServerLevel level) {
        return herd.liveDropCauses(level);
    }

    private Set<String> liveHerdCauses(ServerLevel level) {
        return herd.liveCauses(level);
    }

    private StockView stock() {
        return supply.stock();
    }

    private void flushBlockNotes() {
        notices.blocks(board, journal, stock(), board.now());
    }
}
