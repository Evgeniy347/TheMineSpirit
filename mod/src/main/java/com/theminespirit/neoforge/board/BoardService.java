package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.CraterProfiles;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.PersistBatch;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.SupplyGate;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

public final class BoardService {
    public final BoardSources sources;
    final BoardNotices notices = new BoardNotices();
    final BoardTicketDesk tickets;
    final @Nullable BoardStateStore store;
    final Map<String, TicketTarget> targets = new HashMap<>();
    private final Board board;
    private final JournalService journal;
    private final CraterProfiles craters = new CraterProfiles(64);
    private final BoardHerdDesk herd;
    private final BoardSupplyDesk supply;
    private final BoardDispatchDesk dispatch;
    private final BoardCycle cycle;
    private final BoardBuildMeter meter = new BoardBuildMeter();
    private final PersistBatch persistBatch = new PersistBatch();

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
        this.cycle = new BoardCycle(this);
        BoardRestore.restore(board, store, targets, tickets, journal, sources);
    }

    BoardDispatchDesk dispatch() {
        return dispatch;
    }

    BoardSupplyDesk supply() {
        return supply;
    }

    BoardHerdDesk herd() {
        return herd;
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
        cycle.tick(level, now);
    }

    public void rebuild(ServerLevel level, long now) {
        cycle.rebuild(level, now);
    }

    public BoardBuildMeter meter() {
        return meter;
    }

    public void close() {
        BoardPersistence.close(this);
    }

    void persistBoard() {
        if (persistBatch.deferred()) {
            return;
        }
        BoardPersistence.persistBoard(this);
    }

    void beginPersistBatch() {
        persistBatch.begin();
    }

    void endPersistBatch() {
        if (persistBatch.end()) {
            BoardPersistence.persist(this);
        }
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
            persistBoard();
        } else if (outcome == BoardOutcome.DUPLICATE) {
            if (SupplyGate.isSupply(ticket.id()) && targets.containsKey(ticket.id())) {
                TicketTarget merged = SupplyGate.merge(targets.get(ticket.id()), target);
                if (!merged.equals(targets.get(ticket.id()))) {
                    targets.put(ticket.id(), merged);
                    persistBoard();
                }
            } else {
                targets.putIfAbsent(ticket.id(), target);
            }
        }
        return outcome;
    }

    public BoardOutcome submit(BoardTicket ticket) {
        BoardOutcome outcome = board.submit(ticket);
        if (outcome == BoardOutcome.ACCEPTED) {
            journal.record(BoardLog.Event.POSTED, ticket, null, board.now());
            persistBoard();
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

    long issuedGeneration(String ticketId) {
        return dispatch.issuedGeneration(ticketId);
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

    public Optional<TicketTarget> targetOf(String ticketId, ServerLevel level, List<SpiritEntity> spirits) {
        return tickets.targetOf(ticketId, level, spirits);
    }

    boolean hasTarget(String ticketId) {
        return tickets.hasTarget(ticketId);
    }
}
