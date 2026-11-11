package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

final class BoardCycle {
    private static final long SLOW_BOARD_MILLIS = 250;
    private static final long SLOW_BOARD_LOG_TICKS = 1200;
    private static final Logger LOGGER = LogUtils.getLogger();
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
    private Set<String> liveCauses = Set.of();
    private long lastRebuild = -1;
    private long lastActivity;
    private long slowLoggedAt = -SLOW_BOARD_LOG_TICKS;

    BoardCycle(BoardService service) {
        this.service = service;
        this.board = service.board();
        this.targets = service.targets;
        this.dispatch = service.dispatch();
        this.tickets = service.tickets;
        this.supply = service.supply();
        this.sources = service.sources;
        this.herd = service.herd();
        this.notices = service.notices;
        this.journal = service.journal();
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
        service.meter().rebuildStart();
        // lib: за один пересбор доска ставит и сливает десятки заявок; пишем её на диск один раз в конце,
        // а не на каждую слитую заявку. Исключение тоже закрывает пакет — накопленное не теряется.
        service.beginPersistBatch();
        try {
            rebuildBoard(level, now);
        } finally {
            service.meter().rebuildEnd();
            service.endPersistBatch();
        }
        if (service.meter().rebuildMillis() >= SLOW_BOARD_MILLIS && now - slowLoggedAt >= SLOW_BOARD_LOG_TICKS) {
            slowLoggedAt = now;
            LOGGER.warn(
                    "[board] пересборка {} мс: {} (строка одна на {} тиков)",
                    service.meter().rebuildMillis(),
                    service.meter().breakdown(),
                    SLOW_BOARD_LOG_TICKS);
        }
    }

    private void rebuildBoard(ServerLevel level, long now) {
        service.meter().pruneStart();
        board.prune(now, liveCauses);
        service.meter().pruneEnd();
        Set<String> alive = new HashSet<>();
        for (BoardTicket ticket : board.tickets()) {
            alive.add(ticket.id());
        }
        targets.keySet().retainAll(alive);
        dispatch.retainTickets(alive);
        List<SpiritEntity> spirits = SpiritCensus.all(level);
        service.meter().phaseStart("tickets");
        tickets.cancelOrphans(level, spirits);
        service.meter().phaseEnd("tickets");
        service.meter().phaseStart("stock");
        StockParts parts = supply.refresh(level, spirits);
        service.meter().phaseEnd("stock");
        service.meter().phaseStart("settle");
        dispatch.settleOwners(spirits);
        service.meter().phaseEnd("settle");
        service.meter().phaseStart("supply");
        supply.ensure(level, service, spirits, now);
        service.meter().phaseEnd("supply");
        StockView unreserved = parts.free(BoardReserve.items(board));
        service.meter().phaseStart("sources");
        Set<String> live = sources.tick(level, service, spirits, unreserved, now);
        service.meter().phaseEnd("sources");
        sources.sourceMillis().forEach((name, millis) -> service.meter().phaseAdd("src." + name, millis));
        sources.graphPhases().forEach((name, millis) -> service.meter().phaseAdd("graph." + name, millis));
        live.addAll(liveHerdCauses(level));
        live.addAll(liveDropCauses(level));
        liveCauses = live;
        if (!sources.failedRound()) {
            board.retain(live, now);
        }
        service.meter().phaseStart("issue");
        ForceloadGuard.tick(level, spirits, board, targets);
        dispatch.issue(level, spirits, now);
        service.meter().phaseEnd("issue");
        notices.watchPending(board, stock(), now);
        service.meter().phaseStart("reconcile");
        dispatch.reconcile(spirits, true);
        dispatch.releaseDeadHolders(level, spirits, now);
        dispatch.dropAbsentOwners(spirits, now);
        service.meter().phaseEnd("reconcile");
        flushBlockNotes(level);
        lastRebuild = now;
        journal.flush(JournalService.FLUSH_BUDGET);
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

    private void flushBlockNotes(ServerLevel level) {
        notices.blocks(level, board, journal, stock(), board.now());
    }
}
