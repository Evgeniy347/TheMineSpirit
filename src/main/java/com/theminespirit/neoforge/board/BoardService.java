package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.AnimalLifecycle;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.ArrayList;
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
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

/**
 * Доска одного уровня: пересборка раз в {@code BOARD_INTERVAL_TICKS}, опрос духов-владельцев,
 * выдача свободным, снимок запасов с бюджетом. Мир читает только здесь — ядро
 * ({@code Board}) остаётся чистым. Точки исполнения хранит отдельно от билета: мир из
 * ядра не читает, а план собирается из них при выдаче.
 */
public final class BoardService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Board board;
    private final AnimalLifecycle animals;
    private final StockReader stockReader;
    private final BoardSources sources;
    private final Map<String, TicketTarget> targets = new HashMap<>();
    private StockView stock = new StockView(Map.of());
    private long lastRebuild = -1;
    private int rebuilds;
    private int claims;
    private long stockBlockReads;
    private long stockContainers;

    public BoardService(Board board, AnimalLifecycle animals, StockReader stockReader, BoardSources sources) {
        this.board = Objects.requireNonNull(board, "board не может быть null: очередь заданий живёт в ядре");
        this.animals = Objects.requireNonNull(animals,
                "animals не может быть null: жизненный цикл животного — составная часть доски");
        this.stockReader = Objects.requireNonNull(stockReader,
                "stockReader не может быть null: снимок запасов собирает адаптер");
        this.sources = Objects.requireNonNull(sources,
                "sources не может быть null: источники событий доски");
    }

    public static BoardService create(ServerLevel level) {
        LongSupplier now = level::getGameTime;
        Board board = new Board(now);
        return new BoardService(board, new AnimalLifecycle(board, "wheat"),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                BoardSources.create());
    }

    public Board board() {
        return board;
    }

    /**
     * Последний снимок запасов пересборки: наблюдатель REST показывает по нему {@code missing},
     * не читая мир лишний раз. До первой пересборки снимок пуст.
     */
    public StockView stock() {
        return stock;
    }

    public int rebuilds() {
        return rebuilds;
    }

    public int claims() {
        return claims;
    }

    /** Блоков мира, прочитанных снимком запасов: тест масштаба держит число в бюджете. */
    public long stockBlockReads() {
        return stockBlockReads;
    }

    /** Контейнеров, прочитанных снимком запасов: тест масштаба держит число в бюджете. */
    public long stockContainers() {
        return stockContainers;
    }

    public void tick(ServerLevel level, long now) {
        if (now - lastRebuild < BoardTuning.boardIntervalTicks()) {
            return;
        }
        rebuild(level, now);
    }

    /** Пересборка без интервала: вызывается событиями и тестами, идемпотентна. */
    public void rebuild(ServerLevel level, long now) {
        board.prune(now);
        Set<String> alive = new HashSet<>();
        for (BoardTicket ticket : board.all()) {
            alive.add(ticket.id());
        }
        targets.keySet().retainAll(alive);
        List<SpiritEntity> spirits = census(level);
        settleOwners(spirits);
        issue(level, spirits, now);
        sources.tick(level, this, spirits, stock, now);
        rebuilds++;
        lastRebuild = now;
    }

    public BoardOutcome submit(BoardTicket ticket, TicketTarget target) {
        BoardOutcome outcome = board.submit(ticket);
        if (outcome == BoardOutcome.ACCEPTED) {
            targets.put(ticket.id(), target);
        }
        return outcome;
    }

    /**
     * Постановка задания без точки (источник — REST стратега): цель не задана, при выдаче
     * берётся дом поселения, как для заявок без цели.
     */
    public BoardOutcome submit(BoardTicket ticket) {
        return board.submit(ticket);
    }

    /**
     * Отмена задания: доска снимает его и зависимые, цели забываются, а духи-держатели
     * прекращают работу ({@code cancelTask}) и отпускают брони работ — иначе отменённое
     * задание продолжало бы исполняться, а бронь держала бы занятость духа.
     */
    public boolean cancel(ServerLevel level, String ticketId) {
        Set<UUID> holders = new HashSet<>();
        collectHolders(ticketId, holders, new HashSet<>());
        boolean cancelled = board.cancel(ticketId);
        if (!cancelled) {
            return false;
        }
        targets.keySet().removeIf(id -> board.statusOf(id).orElse(null) == BoardStatus.CANCELLED);
        for (UUID holder : holders) {
            if (level.getEntity(holder) instanceof SpiritEntity spirit) {
                spirit.cancelTask();
                WorkReserveHub.releaseAll(spirit);
            }
        }
        return true;
    }

    /** Держатели отменяемого задания и всех зависимых от него: их работу отменяем вместе. */
    private void collectHolders(String ticketId, Set<UUID> holders, Set<String> visited) {
        if (!visited.add(ticketId)) {
            return;
        }
        board.holderOf(ticketId).ifPresent(holders::add);
        for (BoardTicket ticket : board.all()) {
            if (ticket.dependsOn().contains(ticketId)) {
                collectHolders(ticket.id(), holders, visited);
            }
        }
    }

    /** Дух погиб: его выданные задания возвращаются в очередь, брони снимаются. */
    public void releaseSpirit(SpiritEntity spirit) {
        for (BoardTicket ticket : board.assignedTo(spirit.getUUID())) {
            board.release(ticket.id());
        }
        WorkReserveHub.releaseAll(spirit);
    }

    public void completeCapture(String captureId) {
        animals.captured(captureId);
    }

    /**
     * Загон построен: следующее звено — «приманить» с гейтом по приманке. Пара к
     * {@link #completeCapture}: производство зовёт из учёта исполнителя, GameTest — напрямую.
     */
    public void completePen(String penId) {
        animals.penBuilt(penId);
    }

    /** Животное в загоне: ставится «разобрать укрытие». */
    public void completeLure() {
        animals.lured();
    }

    /** Животное погибло: «приманить» снимается, ставится «разобрать укрытие». */
    public void notifyAnimalDied() {
        animals.animalDied();
    }

    private void settleOwners(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()) {
                releaseSpirit(spirit);
                continue;
            }
            RunStatus status = spirit.taskStatus().orElse(null);
            if (status != RunStatus.SUCCEEDED && status != RunStatus.FAILED) {
                continue;
            }
            List<BoardTicket> held = board.assignedTo(spirit.getUUID());
            if (held.isEmpty()) {
                continue;
            }
            // Дух исполняет одно задание за раз (MAX_ACTIVE_PER_SPIRIT): закрываем первое
            // по очереди, а остальные — следующими пересборками.
            settle(spirit, held.get(0), status == RunStatus.SUCCEEDED);
        }
    }

    private void settle(SpiritEntity spirit, BoardTicket ticket, boolean success) {
        if (success) {
            board.complete(ticket.id());
            switch (ticket.boardCase()) {
                case CAPTURE_ANIMAL -> animals.captured(ticket.id());
                case BUILD_PEN -> animals.penBuilt(ticket.id());
                case LURE_ANIMAL -> animals.lured();
                case DISMANTLE_SHELTER -> {
                }
                default -> {
                }
            }
        } else {
            board.fail(ticket.id());
            if (ticket.boardCase() == BoardCase.CAPTURE_ANIMAL
                    || ticket.boardCase() == BoardCase.BUILD_PEN
                    || ticket.boardCase() == BoardCase.LURE_ANIMAL) {
                animals.animalDied();
            }
        }
        WorkReserveHub.releaseAll(spirit);
        LOGGER.info("Дух {} {} задание {}: статус закрыт доской", spirit.displayName(),
                success ? "завершил" : "провалил", ticket.id());
    }

    private void issue(ServerLevel level, List<SpiritEntity> spirits, long now) {
        StockReader.Snapshot snapshot = stockReader.collect(level, spirits, gateCenters(spirits));
        stockBlockReads += snapshot.blockReads();
        stockContainers += snapshot.containers();
        stock = snapshot.view();
        ensureSupply(spirits, stock, now);
        Map<UUID, Integer> held = board.assignedCounts();
        List<BoardTicket> claimable = board.claimable(stock, now);
        for (BoardTicket ticket : claimable) {
            SpiritEntity spirit = chooseSpirit(ticket, spirits, level, held);
            if (spirit == null) {
                LOGGER.debug("Задание {} ждёт свободного духа: все духи уровня заняты", ticket.id());
                return;
            }
            TicketTarget target = targets.getOrDefault(ticket.id(), TicketTarget.at(homeOf(spirits)));
            TaskNode plan;
            try {
                plan = TicketPlans.plan(ticket.boardCase(), target);
            } catch (RuntimeException failure) {
                LOGGER.error("Задание {} (кейс {}): план не собран: {}", ticket.id(), ticket.boardCase(),
                        failure.getMessage());
                continue;
            }
            try {
                spirit.startTask(plan);
            } catch (RuntimeException failure) {
                LOGGER.error("Задание {}: дух {} не принял задание: {}", ticket.id(), spirit.displayName(),
                        failure.getMessage());
                continue;
            }
            BoardOutcome outcome = board.claim(ticket.id(), spirit.getUUID(), now);
            if (outcome == BoardOutcome.CLAIMED) {
                claims++;
                held.merge(spirit.getUUID(), 1, Integer::sum);
                WorkReserveHub.reserve(spirit, new WorkTarget(WorkKind.CELL, target.target()));
                LOGGER.info("Доска выдала задание {} (кейс {}) духу {}", ticket.id(), ticket.boardCase(),
                        spirit.displayName());
            } else {
                LOGGER.error("Задание {} не выдано духу {}: {}", ticket.id(), spirit.displayName(), outcome);
            }
        }
    }

    /**
     * Центры чтения склада — цели заданий, ждущих ресурса: склад ищется там, где идёт работа,
     * и число мест обхода не растёт от числа духов. Если ждущих заявок нет, берём дом как
     * последнюю опору — иначе снимок не увидел бы даже собранного духами.
     */
    private List<BlockCoord> gateCenters(List<SpiritEntity> spirits) {
        List<BlockCoord> centers = new ArrayList<>();
        for (BoardTicket ticket : board.all()) {
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.PENDING && status != BoardStatus.WAITING_RESOURCES) {
                continue;
            }
            if (ticket.requiredItems().isEmpty()) {
                continue;
            }
            TicketTarget target = targets.get(ticket.id());
            BlockCoord center = target != null ? target.target() : homeOf(spirits);
            if (!centers.contains(center)) {
                centers.add(center);
            }
        }
        if (centers.isEmpty()) {
            centers.add(homeOf(spirits));
        }
        return centers;
    }

    private SpiritEntity chooseSpirit(BoardTicket ticket, List<SpiritEntity> spirits, ServerLevel level,
            Map<UUID, Integer> held) {
        if (ticket.assignee().isPresent()) {
            Entity entity = level.getEntity(ticket.assignee().orElseThrow());
            if (entity instanceof SpiritEntity spirit && free(spirit, held)) {
                return spirit;
            }
            return null;
        }
        return nearestFree(spirits, targets.get(ticket.id()), held);
    }

    private boolean free(SpiritEntity spirit, Map<UUID, Integer> held) {
        RunStatus status = spirit.taskStatus().orElse(null);
        if (status == RunStatus.RUNNING || spirit.currentTaskScript().isPresent()) {
            return false;
        }
        // Занятость — только RUNNING (идиома проекта, SpiritBotGateway): SUCCEEDED/FAILED
        // после закрытия задания духа не держат, иначе доска выдала бы духу одно задание навсегда.
        return held.getOrDefault(spirit.getUUID(), 0) < BoardTuning.maxActivePerSpirit();
    }

    private SpiritEntity nearestFree(List<SpiritEntity> spirits, TicketTarget target, Map<UUID, Integer> held) {
        SpiritEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SpiritEntity spirit : spirits) {
            if (!free(spirit, held)) {
                continue;
            }
            double distance = target == null ? 0
                    : spirit.distanceToSqr(target.target().x() + 0.5, target.target().y(),
                            target.target().z() + 0.5);
            if (distance < bestDistance || (distance == bestDistance && best != null
                    && spirit.getUUID().compareTo(best.getUUID()) < 0)) {
                best = spirit;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void ensureSupply(List<SpiritEntity> spirits, StockView stock, long now) {
        Map<String, Integer> missing = new HashMap<>();
        for (BoardTicket ticket : board.all()) {
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.PENDING && status != BoardStatus.WAITING_RESOURCES) {
                continue;
            }
            ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock).forEach((item, count) -> {
                if (!hasActiveSupply(item)) {
                    missing.merge(item, count, Integer::sum);
                }
            });
        }
        for (Map.Entry<String, Integer> entry : missing.entrySet()) {
            String item = entry.getKey();
            int count = entry.getValue();
            String id = "supply:" + item;
            if (board.statusOf(id).isPresent() || board.recentlyFailed(id, now)) {
                continue;
            }
            TicketTarget target = targets.values().stream()
                    .filter(supply -> item.equals(supply.supplyItem())).findFirst()
                    .orElse(TicketTarget.at(homeOf(spirits)));
            submit(new BoardTicket(id, BoardCase.SUPPLY_SITE, Optional.empty(),
                    BoardRules.priorityOf(BoardCase.SUPPLY_SITE), now, BoardTuning.DEFAULT_TTL_TICKS,
                    List.of(), Map.of()), target.withSupply(item, count));
        }
    }

    private boolean hasActiveSupply(String item) {
        for (BoardTicket ticket : board.all()) {
            if (ticket.boardCase() != BoardCase.SUPPLY_SITE || board.statusOf(ticket.id()).isEmpty()) {
                continue;
            }
            TicketTarget target = targets.get(ticket.id());
            if (target != null && item.equals(target.supplyItem())) {
                return true;
            }
        }
        return false;
    }

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        return spirits.stream().map(SpiritEntity::home).flatMap(Optional::stream).findFirst()
                .orElse(new BlockCoord(0, 64, 0));
    }

    private static List<SpiritEntity> census(ServerLevel level) {
        List<SpiritEntity> spirits = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SpiritEntity spirit && !spirit.isRemoved()) {
                spirits.add(spirit);
            }
        }
        return spirits;
    }
}
