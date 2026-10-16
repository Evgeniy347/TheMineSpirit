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

/**
 * Доска заданий поселения: очередь с гейтом по ресурсам и зависимостями.
 * Хранит задания, сортирует по приоритету, держит гейт и зависимости, выдаёт духу.
 * Мир из ядра не читает: запасы приходят снимком StockView, тики — поставщиком now.
 */
public final class Board {
    private static final Comparator<BoardTicket> QUEUE_ORDER = Comparator
            .<BoardTicket>comparingInt(ticket -> BoardRules.priorityOf(ticket.boardCase())).reversed()
            .thenComparingLong(BoardTicket::createdAt)
            .thenComparing(BoardTicket::id);

    private final Map<String, BoardStatus> statuses = new LinkedHashMap<>();
    private final Map<String, BoardTicket> tickets = new LinkedHashMap<>();
    private final Map<String, UUID> claimAssignees = new LinkedHashMap<>();
    private final Map<String, Long> failTicks = new LinkedHashMap<>();
    private final BoardLedger ledger = new BoardLedger();
    private final LongSupplier nowTicks;

    public Board(LongSupplier nowTicks) {
        Objects.requireNonNull(nowTicks, "nowTicks не может быть null: доска считает просрочку "
                + "и сортировку по времени, без часов задания жили бы вечно");
        this.nowTicks = nowTicks;
    }

    /**
     * Текущее время доски: источники жизненного цикла ставят задания этим временем,
     * а не нулём — иначе через 5 минут работы сервера задание просрочено уже при постановке.
     */
    public long now() {
        return nowTicks.getAsLong();
    }

    public BoardOutcome submit(BoardTicket ticket) {
        Objects.requireNonNull(ticket, "ticket не может быть null: доска хранит задание, "
                + "а не пустую заявку");
        if (tickets.containsKey(ticket.id())) {
            return BoardOutcome.DUPLICATE;
        }
        long now = nowTicks.getAsLong();
        if (recentlyFailed(ticket.id(), now)) {
            return BoardOutcome.BACKOFF;
        }
        tickets.put(ticket.id(), ticket);
        statuses.put(ticket.id(), BoardStatus.PENDING);
        return BoardOutcome.ACCEPTED;
    }

    /**
     * Задание с этим id провалено недавно: до {@link BoardTuning#FAIL_BACKOFF_TICKS} его не
     * переиздают. Метка держится по id задания, а не по духу (урок ветки 32): одна метка на
     * духа вытесняла бы провал другого задания и доска выдавала бы проваленное заново.
     */
    public boolean recentlyFailed(String ticketId, long now) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: откат считается по id задания");
        requireNow(now);
        Long failedAt = failTicks.get(ticketId);
        return failedAt != null && now - failedAt < BoardTuning.failBackoffTicks();
    }

    public List<BoardTicket> claimable(StockView stock, long now) {
        Objects.requireNonNull(stock, "stock не может быть null: гейт без снимка запасов не проверить");
        requireNow(now);
        refreshGateStatuses(stock);
        List<BoardTicket> ready = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            if (statuses.get(ticket.id()) != BoardStatus.PENDING) {
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
        if (!dependenciesDone(ticket)) {
            return BoardOutcome.NOT_FOUND;
        }
        if (status == BoardStatus.ASSIGNED) {
            if (spirit.equals(claimAssignees.get(ticketId))) {
                return BoardOutcome.CLAIMED;
            }
            return BoardOutcome.TAKEN_BY_OTHER;
        }
        if (status == BoardStatus.WAITING_RESOURCES) {
            return BoardOutcome.GATE_BLOCKED;
        }
        statuses.put(ticketId, BoardStatus.ASSIGNED);
        claimAssignees.put(ticketId, spirit);
        return BoardOutcome.CLAIMED;
    }

    public boolean complete(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: завершение идёт по id задания");
        BoardTicket ticket = tickets.get(ticketId);
        if (ticket == null || statuses.get(ticketId) != BoardStatus.ASSIGNED) {
            return false;
        }
        statuses.put(ticketId, BoardStatus.DONE);
        ledger.note(ticket.boardCase(), ticket.level());
        return true;
    }

    public boolean fail(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: провал идёт по id задания");
        if (!tickets.containsKey(ticketId) || statuses.get(ticketId) != BoardStatus.ASSIGNED) {
            return false;
        }
        statuses.put(ticketId, BoardStatus.FAILED);
        failTicks.put(ticketId, nowTicks.getAsLong());
        return true;
    }

    public boolean cancel(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: отмена идёт по id задания");
        if (!tickets.containsKey(ticketId)) {
            return false;
        }
        Set<String> cancelled = new HashSet<>();
        cancelWithDependants(ticketId, cancelled);
        return true;
    }

    /**
     * Возврат задания в очередь после выдачи: исполнитель погиб, не завершив задание,
     * — задание снова доступно любому свободному, а не висит навсегда. Выданное задание
     * ({@code ASSIGNED}) возвращается в {@code PENDING} со сбросом владельца; задание в другом
     * статусе не трогаем — вернуть в работу завершённое нельзя.
     */
    public boolean release(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: возврат идёт по id задания");
        if (!tickets.containsKey(ticketId) || statuses.get(ticketId) != BoardStatus.ASSIGNED) {
            return false;
        }
        statuses.put(ticketId, BoardStatus.PENDING);
        claimAssignees.remove(ticketId);
        return true;
    }

    /**
     * Проверка актуальности вместо срока жизни: снимает в {@code CANCELLED} каскадом
     * по зависимым все задания, чья причина не входит в живые и не {@code "always"}.
     * Выполненные не трогает — {@code DONE} остаётся условием для других заданий.
     * Возвращает id снятых в порядке очереди: адаптер пишет их в журнал.
     */
    public List<String> retain(Set<String> liveCauses, long now) {
        Objects.requireNonNull(liveCauses, "liveCauses не может быть null: без живых причин "
                + "непонятно, что актуально — пустой набор задают пустым множеством");
        requireNow(now);
        List<String> detached = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            BoardStatus status = statuses.get(ticket.id());
            if (status == BoardStatus.DONE || status == BoardStatus.FAILED
                    || status == BoardStatus.CANCELLED) {
                continue;
            }
            if (BoardTicket.ALWAYS_CAUSE.equals(ticket.cause()) || liveCauses.contains(ticket.cause())) {
                continue;
            }
            detached.add(ticket.id());
        }
        Set<String> cancelled = new HashSet<>();
        for (String id : detached) {
            cancelWithDependants(id, cancelled);
        }
        return List.copyOf(detached);
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
        removed += evictOverflow();
        return removed;
    }

    public List<BoardTicket> pendingFor(UUID spirit) {
        Objects.requireNonNull(spirit, "spirit не может быть null: список заданий строится на духа");
        List<BoardTicket> pending = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            BoardStatus status = statuses.get(ticket.id());
            if ((status == BoardStatus.PENDING || status == BoardStatus.WAITING_RESOURCES
                    || status == BoardStatus.ASSIGNED)
                    && ticket.assignee().orElse(null) == spirit) {
                pending.add(ticket);
            }
        }
        pending.sort(QUEUE_ORDER);
        return List.copyOf(pending);
    }

    /**
     * Задания, выданные доской и ещё не закрытые, где дух назван держателем: либо владельцем
     * из заявки, либо тем, кто взял задание (claim). Одного {@code pendingFor} мало: у задания
     * без владельца (добыча, поставленная доской) держателя видно только по выдаче, и без этого
     * провал духа не закрывает задание — оно висит ASSIGNED до срока и блокирует духа.
     */
    public List<BoardTicket> assignedTo(UUID spirit) {
        Objects.requireNonNull(spirit, "spirit не может быть null: держатель задания — дух");
        List<BoardTicket> held = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            if (statuses.get(ticket.id()) == BoardStatus.ASSIGNED && holds(ticket, spirit)) {
                held.add(ticket);
            }
        }
        held.sort(QUEUE_ORDER);
        return List.copyOf(held);
    }

    /** Число незакрытых выданных заданий на каждого духа-держателя: занятость для выдачи. */
    public Map<UUID, Integer> assignedCounts() {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (BoardTicket ticket : tickets.values()) {
            if (statuses.get(ticket.id()) != BoardStatus.ASSIGNED) {
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
        return ticket.assignee().orElse(null) == spirit || spirit.equals(claimAssignees.get(ticket.id()));
    }

    private UUID holder(BoardTicket ticket) {
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

    /** Отметки «постройка стоит»: условие для зависимых и восстановление после перезапуска. */
    public BoardLedger ledger() {
        return ledger;
    }

    /**
     * Держатель задания для наблюдателя: владелец из заявки или тот, кто взял задание.
     * Пустой ответ — задание ждёт любого свободного духа (ещё не выдано или отпущено).
     */
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

    /**
     * Зависимости выполнены: предшественник либо ещё стоит {@code DONE}, либо уже снят
     * пересборкой, оставив отметку в {@link #completed}. Без отметки снятое завершённое
     * задание навсегда блокировало бы зависящие: цепочка «отлов → загон → приманка»
     * рвалась бы на первом же {@code prune}.
     */
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
            if (ticket.boardCase() == mark.boardCase() && ticket.level() == mark.level()
                    && statuses.get(ticket.id()) == BoardStatus.DONE) {
                return true;
            }
        }
        return false;
    }

    private void cancelWithDependants(String ticketId, Set<String> cancelled) {
        if (!cancelled.add(ticketId)) {
            return;
        }
        BoardStatus status = statuses.get(ticketId);
        if (status != null && status != BoardStatus.DONE && status != BoardStatus.FAILED
                && status != BoardStatus.CANCELLED) {
            statuses.put(ticketId, BoardStatus.CANCELLED);
        }
        for (BoardTicket ticket : tickets.values()) {
            if (ticket.dependsOn().contains(ticketId)) {
                cancelWithDependants(ticket.id(), cancelled);
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

    private void requireNow(long now) {
        if (now < 0) {
            throw new IllegalArgumentException("now равен " + now + " и отрицателен: время пересборки "
                    + "не может идти назад, иначе сортировка сломается");
        }
    }
}
