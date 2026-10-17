package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.CraterProfiles;
import com.theminespirit.core.board.EvergreenRules;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final HerdCycles animals;
    private final StockReader stockReader;
    private final BoardSources sources;
    private final JournalService journal;
    private final CraterProfiles craters = new CraterProfiles(64);
    private final Map<String, Capture> captures = new LinkedHashMap<>();
    private final Map<String, TicketTarget> targets = new HashMap<>();
    private StockView stock = new StockView(Map.of());
    private long lastRebuild = -1;
    private int rebuilds;
    private int claims;
    private long stockBlockReads;
    private long stockContainers;

    public BoardService(Board board, HerdCycles animals, StockReader stockReader, BoardSources sources,
            JournalService journal) {
        this.board = Objects.requireNonNull(board, "board не может быть null: очередь заданий живёт в ядре");
        this.animals = Objects.requireNonNull(animals,
                "animals не может быть null: циклы животных по видам — составная часть доски");
        this.stockReader = Objects.requireNonNull(stockReader,
                "stockReader не может быть null: снимок запасов собирает адаптер");
        this.sources = Objects.requireNonNull(sources,
                "sources не может быть null: источники событий доски");
        this.journal = Objects.requireNonNull(journal,
                "journal не может быть null: без него переходы не попадут в журнал");
        journal.restore(board.ledger());
    }

    public static BoardService create(ServerLevel level) {
        LongSupplier now = level::getGameTime;
        JournalService journal = JournalService.inConfigDirectory();
        Board board = new Board(now, (event, ticket, holder, tick) ->
                journal.record(event, ticket, holder, tick));
        return new BoardService(board, new HerdCycles(board),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                BoardSources.create(), journal);
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
        journal.flush(JournalService.FLUSH_BUDGET);
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
        refreshStock(level, spirits);
        ensureSupply(spirits, stock, now);
        Set<String> liveCauses = sources.tick(level, this, spirits, stock, now);
        liveCauses.addAll(liveHerdCauses(level));
        board.retain(liveCauses, now);
        issue(level, spirits, now);
        rebuilds++;
        lastRebuild = now;
        journal.flush(JournalService.FLUSH_BUDGET);
    }

    public void close() {
        journal.flushAll();
    }

    public JournalService journal() {
        return journal;
    }

    public CraterProfiles craters() {
        return craters;
    }

    public HerdCycles herds() {
        return animals;
    }

    /** Пойманное животное: помним вид и сущность, чтобы проверять «животное ещё живо?». */
    public void noteCapture(String captureId, Species species, UUID animal, TicketTarget penTarget) {
        Objects.requireNonNull(captureId, "captureId не может быть null: по нему идёт причина задания");
        Objects.requireNonNull(species, "Вид пойманного животного не может быть null: цикл идёт по виду");
        Objects.requireNonNull(animal, "UUID животного не может быть null: без него живость не проверить");
        captures.put(captureId, new Capture(species, animal, "animal:" + captureId, penTarget));
    }

    public void captureFailed(String captureId) {
        captures.remove(captureId);
    }

    public int capturedCount(Species species) {
        int count = 0;
        for (Capture capture : captures.values()) {
            if (capture.species() == species) {
                count++;
            }
        }
        return count;
    }

    /** Живые причины стада: пойманное животное живо. Гибель снимает «приманить» и ставит разбор. */
    public Set<String> liveHerdCauses(ServerLevel level) {
        Set<String> live = new HashSet<>();
        Iterator<Map.Entry<String, Capture>> iterator = captures.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Capture> entry = iterator.next();
            Entity entity = level.getEntity(entry.getValue().animal());
            if (entity != null && entity.isAlive() && !entity.isRemoved()) {
                live.add(entry.getValue().cause());
            } else {
                iterator.remove();
                animals.cycle(entry.getValue().species()).animalDied();
            }
        }
        return live;
    }

    private record Capture(Species species, UUID animal, String cause, TicketTarget penTarget) {
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
        animals.cycle(speciesOfCapture(captureId)).captured(captureId);
    }

    /**
     * Загон построен: следующее звено — «приманить» с гейтом по приманке. Пара к
     * {@link #completeCapture}: производство зовёт из учёта исполнителя, GameTest — напрямую.
     */
    public void completePen(String penId) {
        Species species = speciesOfId(penId);
        animals.cycle(species).penBuilt(penId);
        TicketTarget pen = targets.get(penId);
        if (pen != null) {
            targets.put(cycleId(species, "lure"), pen.withSpecies(species)
                    .withLureItem(FeedTable.feedFor(species).get(0)));
        }
    }

    /** Животное в загоне: ставится «разобрать укрытие». */
    public void completeLure(Species species) {
        animals.cycle(species).lured();
    }

    public void completeLure() {
        animals.cycle(firstCaptureSpecies()).lured();
    }

    /** Животное погибло: «приманить» снимается, ставится «разобрать укрытие». */
    public void notifyAnimalDied(Species species) {
        animals.cycle(species).animalDied();
    }

    public void notifyAnimalDied() {
        animals.cycle(firstCaptureSpecies()).animalDied();
    }

    private Species speciesOfCapture(String captureId) {
        Capture capture = captures.get(captureId);
        return capture == null ? speciesOfId(captureId) : capture.species();
    }

    private Species firstCaptureSpecies() {
        return captures.values().stream().findFirst().map(Capture::species).orElse(Species.COW);
    }

    private static Species speciesOfId(String ticketId) {
        String[] parts = ticketId.split(":", 3);
        String prefix = parts.length > 1 && "capture".equals(parts[0]) ? parts[1]
                : ticketId.split("[_:/]", 2)[0];
        String name = prefix.toLowerCase(Locale.ROOT);
        for (Species species : Species.all()) {
            if (species.name().toLowerCase(Locale.ROOT).equals(name)) {
                return species;
            }
        }
        return Species.COW;
    }

    private static String cycleId(Species species, String suffix) {
        return species.name().toLowerCase(Locale.ROOT) + "_" + suffix;
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
                case CAPTURE_ANIMAL -> animals.cycle(speciesFor(ticket)).captured(ticket.id());
                case BUILD_PEN -> completePen(ticket.id());
                case LURE_ANIMAL -> animals.cycle(speciesFor(ticket)).lured();
                case DISMANTLE_SHELTER -> {
                }
                default -> {
                }
            }
        } else {
            board.fail(ticket.id());
            for (BoardLogEntry entry : board.drainDetachments()) {
                journal.recordEntry(entry);
            }
            if (ticket.boardCase() == BoardCase.CAPTURE_ANIMAL) {
                captureFailed(ticket.id());
            }
            if (ticket.boardCase() == BoardCase.CAPTURE_ANIMAL
                    || ticket.boardCase() == BoardCase.BUILD_PEN
                    || ticket.boardCase() == BoardCase.LURE_ANIMAL) {
                animals.cycle(speciesFor(ticket)).animalDied();
            }
        }
        WorkReserveHub.releaseAll(spirit);
        LOGGER.info("Дух {} {} задание {}: статус закрыт доской", spirit.displayName(),
                success ? "завершил" : "провалил", ticket.id());
    }

    private Species speciesFor(BoardTicket ticket) {
        TicketTarget target = targets.get(ticket.id());
        if (target != null && target.species() != null) {
            return target.species();
        }
        Capture capture = captures.get(ticket.id());
        return capture == null ? speciesOfId(ticket.id()) : capture.species();
    }

    private void refreshStock(ServerLevel level, List<SpiritEntity> spirits) {
        StockReader.Snapshot snapshot = stockReader.collect(level, spirits, gateCenters(spirits));
        stockBlockReads += snapshot.blockReads();
        stockContainers += snapshot.containers();
        stock = snapshot.view();
    }

    private void issue(ServerLevel level, List<SpiritEntity> spirits, long now) {
        Map<UUID, Integer> held = board.assignedCounts();
        List<BoardTicket> claimable = board.claimable(stock, now);
        for (BoardTicket ticket : claimable) {
            SpiritEntity spirit = chooseSpirit(ticket, spirits, level, held, now);
            if (spirit == null) {
                LOGGER.debug("Задание {} ждёт свободного духа: все духи уровня заняты", ticket.id());
                return;
            }
            TicketTarget target = targets.getOrDefault(ticket.id(), TicketTarget.at(homeOf(spirits)));
            TaskNode plan;
            try {
                plan = TicketPlans.plan(ticket.boardCase(), ticket.level(), target);
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
            Map<UUID, Integer> held, long now) {
        if (ticket.assignee().isPresent()) {
            Entity entity = level.getEntity(ticket.assignee().orElseThrow());
            if (entity instanceof SpiritEntity spirit && free(spirit, held, ticket, now)) {
                return spirit;
            }
            return null;
        }
        return nearestFree(spirits, targets.get(ticket.id()), held, ticket, now);
    }

    private boolean free(SpiritEntity spirit, Map<UUID, Integer> held, BoardTicket ticket, long now) {
        RunStatus status = spirit.taskStatus().orElse(null);
        if (status == RunStatus.RUNNING || spirit.currentTaskScript().isPresent()) {
            return false;
        }
        // Занятость — только RUNNING (идиома проекта, SpiritBotGateway): SUCCEEDED/FAILED
        // после закрытия задания духа не держат, иначе доска выдала бы духу одно задание навсегда.
        return held.getOrDefault(spirit.getUUID(), 0) < BoardTuning.maxActivePerSpirit()
                && board.availableTo(spirit.getUUID(), ticket.boardCase(), now);
    }

    private SpiritEntity nearestFree(List<SpiritEntity> spirits, TicketTarget target, Map<UUID, Integer> held,
            BoardTicket ticket, long now) {
        SpiritEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SpiritEntity spirit : spirits) {
            if (!free(spirit, held, ticket, now)) {
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
                    BoardRules.priorityOf(BoardCase.SUPPLY_SITE), now, 1, BoardTicket.ALWAYS_CAUSE,
                    List.of(), Map.of()), target.withSupply(item, count));
        }
        permanentSupplies(spirits, stock, now);
    }

    /**
     * Постоянные запасы чаровальни (полки 32, книги 32, бумага 128): висят всегда и держат
     * склад. Приоритет полок ниже воронки — иначе запасы вытесняли бы срочную работу.
     */
    private void permanentSupplies(List<SpiritEntity> spirits, StockView stock, long now) {
        EvergreenRules.supplyThresholds().forEach((item, threshold) -> {
            String shortItem = shortId(item);
            int have = stock.available().getOrDefault(shortItem, 0);
            if (have >= threshold || hasActiveSupply(shortItem)) {
                return;
            }
            String id = "supply:" + shortItem;
            if (board.statusOf(id).isPresent() || board.recentlyFailed(id, now)) {
                return;
            }
            submit(new BoardTicket(id, BoardCase.SUPPLY_SITE, Optional.empty(),
                    BoardPriorities.effective(BoardCase.ENCHANT_SHELVES, PriorityContext.none()),
                    now, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of(shortItem, threshold - have)),
                    TicketTarget.at(homeOf(spirits)).withSupply(shortItem, threshold - have));
        });
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

    private static String shortId(String item) {
        return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
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
