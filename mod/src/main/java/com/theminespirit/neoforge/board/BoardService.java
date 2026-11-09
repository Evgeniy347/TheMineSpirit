package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.ActivityCadence;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardReserve;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.CraterProfiles;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.StockParts;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import com.theminespirit.neoforge.net.BuildFingerprint;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class BoardService {
    private static final Logger LOGGER = LogUtils.getLogger();
    public final BoardSources sources;
    final BoardNotices notices = new BoardNotices();
    final BoardTicketDesk tickets;
    private final Board board;
    private final JournalService journal;
    final @Nullable BoardStateStore store;
    private final CraterProfiles craters = new CraterProfiles(64);
    final Map<String, TicketTarget> targets = new HashMap<>();
    private long lastActivity;
    private final BoardHerdDesk herd;
    private final BoardSupplyDesk supply;
    private final BoardDispatchDesk dispatch;
    private Set<String> liveCauses = Set.of();
    private long lastRebuild = -1;
    private final BoardBuildMeter meter = new BoardBuildMeter();

    public BoardService(
            Board board, HerdCycles animals, StockReader stockReader, BoardSources sources, JournalService journal) {
        this(board, animals, stockReader, sources, journal, null);
    }

    public BoardService(
            Board board,
            HerdCycles animals,
            StockReader stockReader,
            BoardSources sources,
            JournalService journal,
            @Nullable BoardStateStore store) {
        this.board = Objects.requireNonNull(board, "board не может быть null: очередь заданий живёт в ядре");
        this.sources = Objects.requireNonNull(sources, "sources не может быть null: источники событий доски");
        this.journal =
                Objects.requireNonNull(journal, "journal не может быть null: без него переходы не попадут в журнал");
        this.store = store;
        this.herd = new BoardHerdDesk(
                board, Objects.requireNonNull(animals, "animals не может быть null: циклы животных по видам"), targets);
        this.supply = new BoardSupplyDesk(
                board,
                Objects.requireNonNull(stockReader, "stockReader не может быть null: снимок запасов собирает адаптер"),
                targets);
        this.dispatch = new BoardDispatchDesk(this, board, targets);
        this.tickets = new BoardTicketDesk(this, board, targets);
        if (store != null) {
            BoardState restored = store.state();
            BoardState byGraph = restored.withoutLegacy(Set.of(GraphTickets.PREFIX));
            int dropped = restored.entries().size() - byGraph.entries().size();
            if (dropped > 0) {
                LOGGER.warn(
                        "[board] при загрузке снято {} заданий мимо графа: их ставят заново источники графа", dropped);
            }
            BoardState owned = byGraph.withoutActivities();
            int activities = byGraph.entries().size() - owned.entries().size();
            if (activities > 0) {
                LOGGER.info(
                        "[board] при загрузке снято {} занятий: духи потеряли задачи перезапуска, "
                                + "источники графа поставят их заново",
                        activities);
            }
            if (!owned.isEmpty()) {
                board.restore(owned);
            }
            cancelRestoredRescues(board);
            String build = BuildFingerprint.current();
            if (!build.equals(store.build())) {
                List<String> reset = resetForNewRules();
                if (!reset.isEmpty()) {
                    LOGGER.warn(
                            "[incident] сборка мода сменилась ({} → {}): сброшено {} заданий старых правил {}, источники "
                                    + "поставят их заново",
                            store.build().isEmpty() ? "?" : store.build(),
                            build,
                            reset.size(),
                            reset);
                }
                store.markBuild(build);
                store.update(board.snapshot());
            }
            BoardTargetsNbt.restore(store, board, targets, sources);
        }
        journal.restore(board.ledger());
    }

    private static void cancelRestoredRescues(Board board) {
        board.all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.RESCUE_SPIRIT)
                .forEach(ticket -> board.cancel(ticket.id()));
    }

    public static BoardService create(ServerLevel level) {
        LongSupplier now = level::getGameTime;
        JournalService journal = JournalService.inWorld(level.getServer());
        Board board = TransitionEvents.create(now, journal, level);
        return new BoardService(
                board,
                new HerdCycles(board),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                BoardSources.create(),
                journal,
                BoardStateStore.get(level));
    }

    public Board board() {
        return board;
    }

    public StockView stock() {
        return supply.stock();
    }

    public int claims() {
        return dispatch.claims();
    }

    public void tick(ServerLevel level, long now) {
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

    public void rebuild(ServerLevel level, long now) {
        meter.rebuildStart();
        meter.pruneStart();
        board.prune(now, liveCauses);
        meter.pruneEnd();
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
        supply.ensure(level, this, spirits, now);
        StockView unreserved = parts.free(BoardReserve.items(board));
        Set<String> live = sources.tick(level, this, spirits, unreserved, now);
        live.addAll(herd.liveCauses(level));
        live.addAll(herd.liveDropCauses(level));
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
        flushBlockNotes(level);
        lastRebuild = now;
        journal.flush(JournalService.FLUSH_BUDGET);
        BoardPersistence.persist(this);
        meter.rebuildEnd();
    }

    public BoardBuildMeter meter() {
        return meter;
    }

    public void close() {
        BoardPersistence.close(this);
    }

    void persistBoard() {
        BoardPersistence.persistBoard(this);
    }

    Map<String, Long> supplyClosed() {
        return supply.closed();
    }

    public JournalService journal() {
        return journal;
    }

    public CraterProfiles craters() {
        return craters;
    }

    public void noteCapture(String captureId, Species species, UUID animal, TicketTarget penTarget) {
        herd.noteCapture(captureId, species, animal, penTarget);
    }

    public void captureFailed(String captureId) {
        herd.captureFailed(captureId);
    }

    public Set<UUID> capturedAnimals(Species species) {
        return herd.capturedAnimals(species);
    }

    public Optional<TicketTarget> targetOf(String ticketId) {
        return tickets.targetOf(ticketId);
    }

    public BoardOutcome submit(BoardTicket ticket, TicketTarget target) {
        BoardOutcome outcome = board.submit(ticket);
        if (outcome == BoardOutcome.ACCEPTED) {
            dispatch.submitted();
            targets.put(ticket.id(), target);
            journal.record(BoardLog.Event.POSTED, ticket, null, board.now());
            BoardPersistence.persist(this);
        } else if (outcome == BoardOutcome.DUPLICATE) {
            targets.putIfAbsent(ticket.id(), target);
        }
        return outcome;
    }

    public BoardOutcome submit(BoardTicket ticket) {
        BoardOutcome outcome = board.submit(ticket);
        if (outcome == BoardOutcome.ACCEPTED) {
            journal.record(BoardLog.Event.POSTED, ticket, null, board.now());
            BoardPersistence.persist(this);
        }
        return outcome;
    }

    public boolean reopen(String ticketId) {
        return tickets.reopen(ticketId);
    }

    public boolean cancel(ServerLevel level, String ticketId) {
        return tickets.cancel(level, ticketId);
    }

    public void releaseSpirit(SpiritEntity spirit) {
        dispatch.release(spirit);
        WorkReserveHub.releaseAll(spirit);
    }

    BoardActivityDesk activities() {
        return dispatch.activityDesk();
    }

    void activityIssued(String id, long generation) {
        dispatch.activityIssued(id, generation);
    }

    void detachForAction(SpiritEntity spirit) {
        board.assignedTo(spirit.getUUID()).forEach(ticket -> board.release(ticket.id()));
    }

    public void completeCapture(String captureId) {
        herd.completeCapture(captureId);
    }

    public void completePen(String penId) {
        herd.completePen(penId);
    }

    public void completeLure(Species species) {
        herd.completeLure(species);
    }

    public void completeLure() {
        herd.completeLure();
    }

    public void notifyAnimalDied(Species species) {
        herd.notifyAnimalDied(species);
    }

    public void notifyAnimalDied() {
        herd.notifyAnimalDied();
    }

    Species speciesFor(BoardTicket ticket) {
        return herd.speciesFor(ticket);
    }

    public List<String> resetForNewRules() {
        return tickets.resetForNewRules();
    }

    void flushBlockNotes(ServerLevel level) {
        notices.blocks(level, board, journal, stock(), board.now());
    }

    public TicketTarget targetOf(String ticketId, List<SpiritEntity> spirits) {
        return tickets.targetOf(ticketId, spirits);
    }

    boolean hasTarget(String ticketId) {
        return tickets.hasTarget(ticketId);
    }

    BlockCoord targetPoint(String ticketId, List<SpiritEntity> spirits) {
        return tickets.targetPoint(ticketId, spirits);
    }
}
