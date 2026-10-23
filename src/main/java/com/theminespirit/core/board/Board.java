package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.Comparator;
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

/**
 * Доска заданий поселения: очередь с гейтом по ресурсам и зависимостями.
 * Хранит задания, сортирует по приоритету, держит гейт и зависимости, выдаёт духу.
 * Мир из ядра не читает: запасы приходят снимком StockView, тики — поставщиком now.
 */
public final class Board {
    private static final String FIRST_STEPS_PREFIX = "first:";
    private static final List<String> SOURCE_PREFIXES = List.of(FIRST_STEPS_PREFIX, "gather:", "supply:");
    private static final Comparator<BoardTicket> QUEUE_ORDER = Comparator.<BoardTicket>comparingInt(
                    ticket -> BoardRules.priorityOf(ticket.boardCase()))
            .reversed()
            .thenComparingLong(BoardTicket::createdAt)
            .thenComparing(BoardTicket::id);

    private final Map<String, BoardStatus> statuses = new LinkedHashMap<>();
    private final Map<String, BoardTicket> tickets = new LinkedHashMap<>();
    private final Map<String, UUID> claimAssignees = new LinkedHashMap<>();
    private final Map<String, Long> failTicks = new LinkedHashMap<>();
    private final Map<String, Integer> ticketFailures = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> blockedMissing = new LinkedHashMap<>();
    private final Map<String, Long> blockedAt = new LinkedHashMap<>();
    private final Map<SpiritCase, CaseFailures> caseFailures = new LinkedHashMap<>();
    private final List<BoardLogEntry> detachments = new ArrayList<>();
    private final List<BoardLogEntry> blockNotes = new ArrayList<>();
    private final BoardLedger ledger = new BoardLedger();
    private final LongSupplier nowTicks;
    private final BoardEvents events;

    public Board(LongSupplier nowTicks) {
        this(nowTicks, BoardEvents.NONE);
    }

    public Board(LongSupplier nowTicks, BoardEvents events) {
        Objects.requireNonNull(
                nowTicks,
                "nowTicks не может быть null: доска считает просрочку "
                        + "и сортировку по времени, без часов задания жили бы вечно");
        this.nowTicks = nowTicks;
        this.events = Objects.requireNonNull(
                events, "Слушатель переходов не может быть null: без него задают BoardEvents.NONE");
    }

    public long now() {
        return nowTicks.getAsLong();
    }

    public BoardState snapshot() {
        List<BoardState.Entry> entries = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            if (BoardActivities.isActivity(ticket.boardCase())) {
                continue;
            }
            entries.add(new BoardState.Entry(
                    ticket,
                    Objects.requireNonNull(
                            statuses.get(ticket.id()),
                            "У билета '" + ticket.id() + "' нет статуса: доска хранит их вместе"),
                    claimAssignees.get(ticket.id()),
                    failTicks.get(ticket.id()),
                    ticketFailures.getOrDefault(ticket.id(), 0)));
        }
        return new BoardState(entries, ledger.keys());
    }

    public void restore(BoardState state) {
        Objects.requireNonNull(state, "Снимок доски не может быть null: восстанавливать нечего");
        tickets.clear();
        statuses.clear();
        claimAssignees.clear();
        failTicks.clear();
        ticketFailures.clear();
        blockedMissing.clear();
        caseFailures.clear();
        ledger.restore(state.ledger());
        for (BoardState.Entry entry : state.entries()) {
            BoardTicket ticket = entry.ticket();
            tickets.put(ticket.id(), ticket);
            statuses.put(ticket.id(), entry.status());
            if (entry.holder() != null) {
                claimAssignees.put(ticket.id(), entry.holder());
            }
            if (entry.failedAt() != null) {
                failTicks.put(ticket.id(), entry.failedAt());
            }
            if (entry.failures() > 0) {
                ticketFailures.put(ticket.id(), entry.failures());
            }
        }
    }

    public BoardOutcome submit(BoardTicket ticket) {
        Objects.requireNonNull(ticket, "ticket не может быть null: доска хранит задание, " + "а не пустую заявку");
        if (!SettlementStage.allows(ticket.boardCase(), ticket.level(), ledger)) {
            return BoardOutcome.STAGE_LOCKED;
        }
        if (tickets.containsKey(ticket.id())) {
            BoardStatus current = statuses.get(ticket.id());
            if (current == BoardStatus.DONE && ticket.id().startsWith(FIRST_STEPS_PREFIX)) {
                // Шаг первых шагов одноразовый: DONE — это факт «сделано», а не тень для
                // переиздания. Иначе пересборка возвращала бы выполненный кейс в очередь.
                return BoardOutcome.DUPLICATE;
            }
            if (current == BoardStatus.DONE
                    || (current == BoardStatus.BLOCKED && conditionsChanged(tickets.get(ticket.id()), ticket))) {
                tickets.put(ticket.id(), ticket);
                statuses.put(ticket.id(), BoardStatus.PENDING);
                claimAssignees.remove(ticket.id());
                ticketFailures.remove(ticket.id());
                blockedMissing.remove(ticket.id());
                events.transition(BoardLog.Event.SUBMITTED, ticket, null, nowTicks.getAsLong());
                return BoardOutcome.ACCEPTED;
            }
            return BoardOutcome.DUPLICATE;
        }
        long now = nowTicks.getAsLong();
        if (recentlyFailed(ticket.id(), now)) {
            return BoardOutcome.BACKOFF;
        }
        tickets.put(ticket.id(), ticket);
        statuses.put(ticket.id(), BoardStatus.PENDING);
        events.transition(BoardLog.Event.SUBMITTED, ticket, null, now);
        return BoardOutcome.ACCEPTED;
    }

    public boolean recentlyFailed(String ticketId, long now) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: откат считается по id задания");
        requireNow(now);
        Long failedAt = failTicks.get(ticketId);
        return failedAt != null && now - failedAt < BoardTuning.failBackoffTicks();
    }

    public List<BoardTicket> claimable(StockView stock, long now) {
        Objects.requireNonNull(stock, "stock не может быть null: гейт без снимка запасов не проверить");
        requireNow(now);
        unblockChanged(stock, now);
        refreshGateStatuses(stock);
        List<BoardTicket> ready = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            if (statuses.get(ticket.id()) != BoardStatus.PENDING || BoardActivities.isActivity(ticket.boardCase())) {
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
        ready.sort(QUEUE_ORDER);
        return List.copyOf(ready);
    }

    public BoardOutcome claim(String ticketId, UUID spirit, long now) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: выдача идёт по id задания");
        Objects.requireNonNull(spirit, "spirit не может быть null: выдача идёт конкретному духу");
        requireNow(now);
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
        if (!dependenciesDone(ticket)) {
            return BoardOutcome.NOT_FOUND;
        }
        if (status == BoardStatus.ASSIGNED) {
            if (spirit.equals(claimAssignees.get(ticketId))) {
                return BoardOutcome.CLAIMED;
            }
            return BoardOutcome.TAKEN_BY_OTHER;
        }
        if (!availableTo(spirit, ticket.boardCase(), now)) {
            return BoardOutcome.SPIRIT_PAUSED;
        }
        if (status == BoardStatus.WAITING_RESOURCES) {
            return BoardOutcome.GATE_BLOCKED;
        }
        statuses.put(ticketId, BoardStatus.ASSIGNED);
        claimAssignees.put(ticketId, spirit);
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
            caseFailures.remove(new SpiritCase(holder, ticket.boardCase()));
        }
        ticketFailures.remove(ticketId);
        blockedMissing.remove(ticketId);
        statuses.put(ticketId, BoardStatus.DONE);
        ledger.note(ticket.boardCase(), ticket.level());
        events.transition(BoardLog.Event.DONE, ticket, claimAssignees.get(ticketId), nowTicks.getAsLong());
        return true;
    }

    public boolean fail(String ticketId) {
        return fail(ticketId, new StockView(Map.of()));
    }

    public boolean fail(String ticketId, StockView stock) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: провал идёт по id задания");
        Objects.requireNonNull(stock, "stock не может быть null: по нему фиксируются условия блокировки");
        BoardTicket ticket = tickets.get(ticketId);
        if (ticket == null || statuses.get(ticketId) != BoardStatus.ASSIGNED) {
            return false;
        }
        long now = nowTicks.getAsLong();
        statuses.put(ticketId, BoardStatus.FAILED);
        failTicks.put(ticketId, now);
        events.transition(BoardLog.Event.FAILED, ticket, claimAssignees.get(ticketId), now);
        UUID holder = holder(ticket);
        if (holder != null) {
            noteCaseFailure(holder, ticket, now);
        }
        noteTicketFailure(ticket, now, stock);
        return true;
    }

    public boolean availableTo(UUID spirit, BoardCase boardCase, long now) {
        Objects.requireNonNull(spirit, "spirit не может быть null: пауза считается на конкретного духа");
        Objects.requireNonNull(boardCase, "boardCase не может быть null: пауза считается на конкретный кейс");
        requireNow(now);
        CaseFailures failures = caseFailures.get(new SpiritCase(spirit, boardCase));
        return failures == null || (!failures.detached && now >= failures.pausedUntil);
    }

    public boolean caseDetached(UUID spirit, BoardCase boardCase) {
        Objects.requireNonNull(spirit, "spirit не может быть null: снятие считается на конкретного духа");
        Objects.requireNonNull(boardCase, "boardCase не может быть null: снятие считается на конкретный кейс");
        CaseFailures failures = caseFailures.get(new SpiritCase(spirit, boardCase));
        return failures != null && failures.detached;
    }

    public int consecutiveFailures(UUID spirit, BoardCase boardCase) {
        Objects.requireNonNull(spirit, "spirit не может быть null: счёт провалов идёт на конкретного духа");
        Objects.requireNonNull(boardCase, "boardCase не может быть null: счёт провалов идёт на кейс");
        CaseFailures failures = caseFailures.get(new SpiritCase(spirit, boardCase));
        return failures == null ? 0 : failures.consecutive;
    }

    public long casePauseTicks(UUID spirit, BoardCase boardCase) {
        Objects.requireNonNull(spirit, "spirit не может быть null: пауза считается на конкретного духа");
        Objects.requireNonNull(boardCase, "boardCase не может быть null: пауза считается на конкретный кейс");
        CaseFailures failures = caseFailures.get(new SpiritCase(spirit, boardCase));
        return failures == null ? 0 : BoardBackoff.pauseTicks(failures.consecutive);
    }

    public int ticketFailures(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: счёт провалов идёт по заданию");
        return ticketFailures.getOrDefault(ticketId, 0);
    }

    public boolean blocked(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: блокировка читается по заданию");
        return statuses.get(ticketId) == BoardStatus.BLOCKED;
    }

    public List<BoardLogEntry> blocks() {
        return List.copyOf(blockNotes);
    }

    public List<BoardLogEntry> drainBlocks() {
        List<BoardLogEntry> drained = List.copyOf(blockNotes);
        blockNotes.clear();
        return drained;
    }

    public List<BoardLogEntry> detachments() {
        return List.copyOf(detachments);
    }

    public List<BoardLogEntry> drainDetachments() {
        List<BoardLogEntry> drained = List.copyOf(detachments);
        detachments.clear();
        return drained;
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

    public boolean release(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: возврат идёт по id задания");
        if (!tickets.containsKey(ticketId) || statuses.get(ticketId) != BoardStatus.ASSIGNED) {
            return false;
        }
        statuses.put(ticketId, BoardStatus.PENDING);
        claimAssignees.remove(ticketId);
        return true;
    }

    public void occupy(UUID spirit, BoardCase activity, long now) {
        Objects.requireNonNull(spirit, "spirit не может быть null: занятие показывается на конкретного духа");
        requireNow(now);
        String id = BoardActivities.idOf(spirit);
        BoardTicket current = tickets.get(id);
        if (current != null && current.boardCase() == activity) {
            return;
        }
        BoardTicket ticket = BoardActivities.ticket(spirit, activity, now);
        tickets.remove(id);
        tickets.put(id, ticket);
        statuses.put(id, BoardStatus.ASSIGNED);
        claimAssignees.put(id, spirit);
        // Смена занятия видна в журнале доски (INC-35): бой, стража и отдых — тоже работа духа.
        events.transition(BoardLog.Event.ASSIGNED, ticket, spirit, now);
    }

    public boolean vacate(UUID spirit) {
        Objects.requireNonNull(spirit, "spirit не может быть null: занятие снимается с конкретного духа");
        String id = BoardActivities.idOf(spirit);
        BoardTicket ticket = tickets.remove(id);
        if (ticket == null) {
            return false;
        }
        statuses.remove(id);
        claimAssignees.remove(id);
        events.transition(BoardLog.Event.CANCELLED, ticket, spirit, nowTicks.getAsLong());
        return true;
    }

    public Optional<BoardCase> occupation(UUID spirit) {
        Objects.requireNonNull(spirit, "spirit не может быть null: занятие читается у конкретного духа");
        return Optional.ofNullable(tickets.get(BoardActivities.idOf(spirit))).map(BoardTicket::boardCase);
    }

    public List<String> retain(Set<String> liveCauses, long now) {
        Objects.requireNonNull(
                liveCauses,
                "liveCauses не может быть null: без живых причин "
                        + "непонятно, что актуально — пустой набор задают пустым множеством");
        requireNow(now);
        List<String> detached = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            BoardStatus status = statuses.get(ticket.id());
            if (status == BoardStatus.DONE || status == BoardStatus.FAILED || status == BoardStatus.CANCELLED) {
                continue;
            }
            if (BoardTicket.ALWAYS_CAUSE.equals(ticket.cause()) || liveCauses.contains(ticket.cause())) {
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
        // Новая сборка мода — новые правила: задания, заблокированные или поставленные источниками по старым
        // правилам (gather:oak_* в джунглях), сбрасываются, источники ставят их заново; сделанное и выданное
        // остаётся, паузы и счётчики провалов обнуляются.
        List<String> removed = new ArrayList<>();
        for (BoardTicket ticket : List.copyOf(tickets.values())) {
            BoardStatus status = statuses.get(ticket.id());
            boolean sourceMade = SOURCE_PREFIXES.stream().anyMatch(ticket.id()::startsWith);
            boolean waiting = status == BoardStatus.PENDING || status == BoardStatus.WAITING_RESOURCES;
            if (status == BoardStatus.BLOCKED || status == BoardStatus.FAILED || (sourceMade && waiting)) {
                tickets.remove(ticket.id());
                statuses.remove(ticket.id());
                claimAssignees.remove(ticket.id());
                blockedMissing.remove(ticket.id());
                removed.add(ticket.id());
            }
        }
        failTicks.clear();
        ticketFailures.clear();
        caseFailures.clear();
        return List.copyOf(removed);
    }

    public int prune(long now) {
        requireNow(now);
        int removed = 0;
        List<String> removableIds = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            BoardStatus status = statuses.get(ticket.id());
            if (status == BoardStatus.CANCELLED || status == BoardStatus.FAILED) {
                removableIds.add(ticket.id());
            }
        }
        for (String id : removableIds) {
            tickets.remove(id);
            statuses.remove(id);
            removed++;
        }
        failTicks.entrySet().removeIf(mark -> now - mark.getValue() >= BoardTuning.failBackoffTicks());
        retryBlocked(now);
        removed += evictOverflow();
        return removed;
    }

    public List<BoardTicket> pendingFor(UUID spirit) {
        Objects.requireNonNull(spirit, "spirit не может быть null: список заданий строится на духа");
        List<BoardTicket> pending = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            BoardStatus status = statuses.get(ticket.id());
            if ((status == BoardStatus.PENDING
                            || status == BoardStatus.WAITING_RESOURCES
                            || status == BoardStatus.ASSIGNED)
                    && ticket.assignee().filter(spirit::equals).isPresent()) {
                pending.add(ticket);
            }
        }
        pending.sort(QUEUE_ORDER);
        return List.copyOf(pending);
    }

    public List<BoardTicket> assignedTo(UUID spirit) {
        Objects.requireNonNull(spirit, "spirit не может быть null: держатель задания — дух");
        List<BoardTicket> held = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            if (statuses.get(ticket.id()) == BoardStatus.ASSIGNED
                    && holds(ticket, spirit)
                    && !BoardActivities.isActivity(ticket.boardCase())) {
                held.add(ticket);
            }
        }
        held.sort(QUEUE_ORDER);
        return List.copyOf(held);
    }

    public Map<UUID, Integer> assignedCounts() {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (BoardTicket ticket : tickets.values()) {
            if (statuses.get(ticket.id()) != BoardStatus.ASSIGNED || BoardActivities.isActivity(ticket.boardCase())) {
                continue;
            }
            UUID holder = holder(ticket);
            if (holder != null) {
                counts.merge(holder, 1, Integer::sum);
            }
        }
        return counts;
    }

    private boolean holds(BoardTicket ticket, UUID spirit) {
        return ticket.assignee().filter(spirit::equals).isPresent() || spirit.equals(claimAssignees.get(ticket.id()));
    }

    private static boolean conditionsChanged(BoardTicket before, BoardTicket after) {
        return before.level() != after.level()
                || !before.cause().equals(after.cause())
                || !before.dependsOn().equals(after.dependsOn())
                || !before.requiredItems().equals(after.requiredItems());
    }

    private @Nullable UUID holder(BoardTicket ticket) {
        return ticket.assignee().orElseGet(() -> claimAssignees.get(ticket.id()));
    }

    public List<BoardTicket> all() {
        List<BoardTicket> all = new ArrayList<>(tickets.values());
        all.sort(QUEUE_ORDER);
        return List.copyOf(all);
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
        return dependenciesDone(ticket);
    }

    private boolean dependenciesDone(BoardTicket ticket) {
        for (String dependency : ticket.dependsOn()) {
            Optional<BoardLedger.BuildingMark> mark = BoardLedger.parse(dependency);
            if (mark.isPresent()) {
                if (!buildingDone(mark.orElseThrow())) {
                    return false;
                }
                continue;
            }
            if (statuses.getOrDefault(dependency, null) != BoardStatus.DONE) {
                return false;
            }
        }
        return true;
    }

    private boolean buildingDone(BoardLedger.BuildingMark mark) {
        if (ledger.has(mark.boardCase(), mark.level())) {
            return true;
        }
        for (BoardTicket ticket : tickets.values()) {
            if (ticket.boardCase() == mark.boardCase()
                    && ticket.level() == mark.level()
                    && statuses.get(ticket.id()) == BoardStatus.DONE) {
                return true;
            }
        }
        return false;
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
            blockedMissing.remove(ticketId);
            events.transition(event, ticket, claimAssignees.get(ticketId), nowTicks.getAsLong());
        }
        for (BoardTicket dependant : tickets.values()) {
            if (dependant.dependsOn().contains(ticketId)) {
                cancelWithDependants(dependant.id(), cancelled, BoardLog.Event.CANCELLED);
            }
        }
    }

    private int evictOverflow() {
        List<BoardTicket> unissued = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            BoardStatus status = statuses.get(ticket.id());
            if (status == BoardStatus.PENDING || status == BoardStatus.WAITING_RESOURCES) {
                unissued.add(ticket);
            }
        }
        unissued.sort(Comparator.comparingLong(BoardTicket::createdAt).thenComparing(BoardTicket::id));
        int evicted = 0;
        while (unissued.size() > BoardTuning.maxPending()) {
            BoardTicket oldest = unissued.remove(0);
            cancel(oldest.id());
            evicted++;
        }
        return evicted;
    }

    private void noteCaseFailure(UUID spirit, BoardTicket ticket, long now) {
        SpiritCase key = new SpiritCase(spirit, ticket.boardCase());
        CaseFailures failures = caseFailures.computeIfAbsent(key, ignored -> new CaseFailures());
        failures.consecutive++;
        failures.pausedUntil = now + BoardBackoff.pauseTicks(failures.consecutive);
        if (!failures.detached && failures.consecutive >= BoardTuning.caseFailLimit()) {
            failures.detached = true;
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
    }

    private void noteTicketFailure(BoardTicket ticket, long now, StockView stock) {
        int failures = ticketFailures.merge(ticket.id(), 1, Integer::sum);
        if (failures < BoardTuning.ticketFailLimit()) {
            return;
        }
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
        statuses.put(ticket.id(), BoardStatus.BLOCKED);
        blockedMissing.put(ticket.id(), missing);
        blockNotes.add(new BoardLogEntry(
                BoardLog.Event.BLOCKED,
                now,
                ticket.id(),
                ticket.boardCase(),
                ticket.level(),
                ticket.cause(),
                BoardRules.priorityOf(ticket.boardCase()),
                ticket.priority(),
                reason(failures, missing),
                null,
                ConfigStamp.current(),
                now));
    }

    private void unblockChanged(StockView stock, long now) {
        for (BoardTicket ticket : List.copyOf(tickets.values())) {
            if (statuses.get(ticket.id()) != BoardStatus.BLOCKED) {
                continue;
            }
            Map<String, Integer> missing = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
            if (missing.equals(blockedMissing.getOrDefault(ticket.id(), Map.of()))) {
                continue;
            }
            statuses.put(ticket.id(), BoardStatus.PENDING);
            ticketFailures.remove(ticket.id());
            blockedMissing.remove(ticket.id());
            blockNotes.add(new BoardLogEntry(
                    BoardLog.Event.UNBLOCKED,
                    now,
                    ticket.id(),
                    ticket.boardCase(),
                    ticket.level(),
                    ticket.cause(),
                    BoardRules.priorityOf(ticket.boardCase()),
                    ticket.priority(),
                    reason(0, missing),
                    null,
                    ConfigStamp.current(),
                    now));
        }
    }

    private void retryBlocked(long now) {
        blockedAt.keySet().removeIf(id -> statuses.get(id) != BoardStatus.BLOCKED);
        for (BoardTicket ticket : List.copyOf(tickets.values())) {
            if (statuses.get(ticket.id()) != BoardStatus.BLOCKED) {
                continue;
            }
            long since = blockedAt.computeIfAbsent(ticket.id(), id -> now);
            if (now - since < BoardTuning.blockedRetryTicks()) {
                continue;
            }
            statuses.put(ticket.id(), BoardStatus.PENDING);
            ticketFailures.remove(ticket.id());
            blockedMissing.remove(ticket.id());
            blockedAt.remove(ticket.id());
            blockNotes.add(new BoardLogEntry(
                    BoardLog.Event.UNBLOCKED,
                    now,
                    ticket.id(),
                    ticket.boardCase(),
                    ticket.level(),
                    ticket.cause(),
                    BoardRules.priorityOf(ticket.boardCase()),
                    ticket.priority(),
                    List.of("retry_after=" + BoardTuning.blockedRetryTicks()),
                    null,
                    ConfigStamp.current(),
                    now));
        }
    }

    private static List<String> reason(int failures, Map<String, Integer> missing) {
        List<String> reason = new ArrayList<>();
        reason.add("failures=" + failures);
        missing.forEach((item, count) -> reason.add("missing=" + item + ":" + count));
        return List.copyOf(reason);
    }

    private record SpiritCase(UUID spirit, BoardCase boardCase) {}

    private static final class CaseFailures {
        private int consecutive;
        private long pausedUntil;
        private boolean detached;
    }

    private void requireNow(long now) {
        if (now < 0) {
            throw new IllegalArgumentException("now равен " + now + " и отрицателен: время пересборки "
                    + "не может идти назад, иначе сортировка сломается");
        }
    }
}
