package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.BoardReserve;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.CraterProfiles;
import com.theminespirit.core.board.EatNeed;
import com.theminespirit.core.board.EvergreenRules;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketRun;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.TorchRules;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.task.SpiritReading;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import com.theminespirit.neoforge.inventory.SpiritSupplies;
import com.theminespirit.neoforge.inventory.StackMapper;
import com.theminespirit.neoforge.net.BuildFingerprint;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Доска одного уровня: пересборка раз в {@code BOARD_INTERVAL_TICKS}, опрос духов-владельцев,
 * выдача свободным, снимок запасов с бюджетом. Мир читает только здесь — ядро
 * ({@code Board}) остаётся чистым. Точки исполнения хранит отдельно от билета: мир из
 * ядра не читает, а план собирается из них при выдаче.
 */
public final class BoardService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIG_OUT = "dig_out";
    private static final long DIG_OUT_PAUSE_TICKS = 1200;

    private final Board board;
    private final HerdCycles animals;
    private final StockReader stockReader;
    private final BoardSources sources;
    private final JournalService journal;
    private final @Nullable BoardStateStore store;
    private final CraterProfiles craters = new CraterProfiles(64);
    private final Map<String, Capture> captures = new LinkedHashMap<>();
    private final Map<String, TicketTarget> targets = new HashMap<>();
    private final Map<UUID, Launched> launched = new HashMap<>();
    private final Map<UUID, Long> digOutAfter = new HashMap<>();
    private final Map<String, Long> issuedGenerations = new HashMap<>();
    // Духи, чьё задание снял сброс при смене сборки: их задача по снятому заданию (восстановлена из сохранения)
    // ещё идёт и держит духа «внешним занятием» — spirit-5 50+ мин копал dig_area бывшего mine_level_3 (INC-40).
    private final Map<UUID, String> orphans = new HashMap<>();
    private final CrafterMemory crafters = new CrafterMemory();
    private StockView stock = new StockView(Map.of());
    private long lastRebuild = -1;
    private int rebuilds;
    private int claims;
    private int retains;
    private long stockBlockReads;
    private long stockContainers;

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
        this.animals = Objects.requireNonNull(
                animals, "animals не может быть null: циклы животных по видам — составная часть доски");
        this.stockReader =
                Objects.requireNonNull(stockReader, "stockReader не может быть null: снимок запасов собирает адаптер");
        this.sources = Objects.requireNonNull(sources, "sources не может быть null: источники событий доски");
        this.journal =
                Objects.requireNonNull(journal, "journal не может быть null: без него переходы не попадут в журнал");
        this.store = store;
        if (store != null) {
            store.restoreInto(board);
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
        }
        journal.restore(board.ledger());
    }

    public static BoardService create(ServerLevel level) {
        LongSupplier now = level::getGameTime;
        JournalService journal = JournalService.inWorld(level.getServer());
        Board board = new Board(now, (event, ticket, holder, tick) -> journal.record(event, ticket, holder, tick));
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
        return stock;
    }

    public int rebuilds() {
        return rebuilds;
    }

    public int claims() {
        return claims;
    }

    public int retains() {
        return retains;
    }

    public int herdScans() {
        return sources.herdScans();
    }

    public long stockBlockReads() {
        return stockBlockReads;
    }

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

    public void rebuild(ServerLevel level, long now) {
        board.prune(now);
        Set<String> alive = new HashSet<>();
        for (BoardTicket ticket : board.all()) {
            alive.add(ticket.id());
        }
        targets.keySet().retainAll(alive);
        issuedGenerations.keySet().retainAll(alive);
        List<SpiritEntity> spirits = census(level);
        cancelOrphans(spirits);
        settleOwners(spirits);
        refreshStock(level, spirits);
        ensureSupply(spirits, stock, now);
        // Источники заданий видят запас за вычетом брони открытых заданий (решение 54b п.5): уголь для факелов не
        // пережигает брёвна, взятые под забор, и новый шаг не открывается на чужих материалах.
        StockView unreserved = BoardReserve.unreserved(stock, board);
        Set<String> liveCauses = sources.tick(level, this, spirits, unreserved, now);
        liveCauses.addAll(liveHerdCauses(level));
        liveCauses.addAll(liveDropCauses(level));
        board.retain(liveCauses, now);
        retains++;
        digOut(level, spirits);
        issue(level, spirits, now);
        reconcileActivities(spirits, true);
        flushBlockNotes();
        rebuilds++;
        lastRebuild = now;
        journal.flush(JournalService.FLUSH_BUDGET);
        persist();
    }

    public void close() {
        journal.flushAll();
        persist();
    }

    private void persist() {
        if (store != null) {
            store.update(board.snapshot());
        }
    }

    public JournalService journal() {
        return journal;
    }

    public CraterProfiles craters() {
        return craters;
    }

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

    private Set<String> liveDropCauses(ServerLevel level) {
        Set<String> live = new HashSet<>();
        for (BoardTicket ticket : board.all()) {
            if (ticket.boardCase() != BoardCase.COLLECT_DROPS || !ticket.cause().startsWith("drop:")) {
                continue;
            }
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.PENDING
                    && status != BoardStatus.WAITING_RESOURCES
                    && status != BoardStatus.ASSIGNED) {
                continue;
            }
            String[] parts = ticket.cause().split(":", 5);
            if (parts.length != 5) {
                continue;
            }
            int x;
            int y;
            int z;
            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (NumberFormatException broken) {
                continue;
            }
            String item = parts[4];
            boolean found = level.getEntitiesOfClass(
                                    ItemEntity.class,
                                    new AABB(x - 4, y - 4, z - 4, x + 5, y + 5, z + 5),
                                    drop -> !drop.isRemoved() && item.equals(StackMapper.itemId(drop.getItem())))
                            .isEmpty()
                    == false;
            if (found) {
                live.add(ticket.cause());
            }
        }
        return live;
    }

    public Set<UUID> capturedAnimals(Species species) {
        Set<UUID> animals = new HashSet<>();
        for (Capture capture : captures.values()) {
            if (capture.species() == species) {
                animals.add(capture.animal());
            }
        }
        return Set.copyOf(animals);
    }

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

    private record Capture(Species species, UUID animal, String cause, TicketTarget penTarget) {}

    public boolean reportDropped(SpiritEntity source, ItemEntity drop) {
        Objects.requireNonNull(source, "Дух, выкинувший вещь, не может быть null: по нему ищется поселение");
        Objects.requireNonNull(drop, "Выкинутая вещь не может быть null: без неё нечего забирать");
        if (!SpiritConfig.boardEnabled()
                || source.home().isEmpty()
                || drop.isRemoved()
                || !(drop.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos pos = drop.blockPosition();
        String item = StackMapper.itemId(drop.getItem());
        String id = "drop:" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ":" + item;
        if (board.statusOf(id).isPresent()) {
            return false;
        }
        String cause = "drop:" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ() + ":" + item;
        BlockCoord at = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        BoardOutcome outcome = submit(
                new BoardTicket(
                        id,
                        BoardCase.COLLECT_DROPS,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.COLLECT_DROPS),
                        level.getGameTime(),
                        1,
                        cause,
                        List.of(),
                        Map.of()),
                TicketTarget.at(at).withDrops(List.of(item)));
        if (outcome == BoardOutcome.ACCEPTED) {
            LOGGER.info("Журнал доски: постановка {} — забрать выкинутое в {}: {}", id, at, item);
        }
        journal.flush(JournalService.FLUSH_BUDGET);
        return outcome == BoardOutcome.ACCEPTED;
    }

    public BoardOutcome submit(BoardTicket ticket, TicketTarget target) {
        BoardOutcome outcome = board.submit(ticket);
        if (outcome == BoardOutcome.ACCEPTED) {
            targets.put(ticket.id(), target);
            journal.record(BoardLog.Event.POSTED, ticket, null, board.now());
            persist();
        } else if (outcome == BoardOutcome.DUPLICATE) {
            // Точки исполнения живут в памяти, а задания — в сохранении доски: после рестарта источник
            // возвращает точку уже стоящему заданию, иначе его план не собрать (INC-29).
            targets.putIfAbsent(ticket.id(), target);
        }
        return outcome;
    }

    public BoardOutcome submit(BoardTicket ticket) {
        BoardOutcome outcome = board.submit(ticket);
        if (outcome == BoardOutcome.ACCEPTED) {
            journal.record(BoardLog.Event.POSTED, ticket, null, board.now());
            persist();
        }
        return outcome;
    }

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
        persist();
        return true;
    }

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

    public void releaseSpirit(SpiritEntity spirit) {
        for (BoardTicket ticket : board.assignedTo(spirit.getUUID())) {
            issuedGenerations.remove(ticket.id());
            board.release(ticket.id());
        }
        board.vacate(spirit.getUUID());
        launched.remove(spirit.getUUID());
        WorkReserveHub.releaseAll(spirit);
    }

    public void offerActivities(List<ActivityOffer> offers, ActivityOffer.Launcher launcher, long now) {
        // Дух с заданием доски занятия не получает: задание выше любого занятия, его итог закроет settleOwners.
        Objects.requireNonNull(offers, "Предложения не могут быть null: пустой набор — пустой список");
        Objects.requireNonNull(launcher, "Запуск занятий не может быть null: занятие выдаёт доска через него");
        List<SpiritEntity> offered = new ArrayList<>();
        for (ActivityOffer offer : offers) {
            SpiritEntity spirit = offer.spirit();
            offered.add(spirit);
            TaskNode task = offer.task();
            if (spirit.isRemoved()
                    || task == null
                    || !board.assignedTo(spirit.getUUID()).isEmpty()) {
                continue;
            }
            if (launcher.start(spirit, offer.script(), task, now)) {
                launched.put(
                        spirit.getUUID(),
                        new Launched(BoardActivities.caseOf(offer.activity()), spirit.taskGeneration()));
            }
        }
        reconcileActivities(offered, false);
    }

    public boolean launch(
            SpiritEntity spirit,
            BoardCase activity,
            String script,
            TaskNode task,
            ActivityOffer.Launcher launcher,
            long now) {
        // Поход и рефлексы силы идут через доску: дух на них виден занятием, задание доски (если есть) остаётся
        // за ним и продолжается после (TicketRun).
        Objects.requireNonNull(spirit, "Дух занятия не может быть null: занятие адресовано конкретному духу");
        if (!BoardActivities.isActivity(activity)) {
            throw new IllegalArgumentException(
                    "Кейс " + activity + " — не занятие: через launch идут только ACTIVITY_*");
        }
        if (!launcher.start(spirit, script, task, now)) {
            return false;
        }
        launched.put(spirit.getUUID(), new Launched(activity, spirit.taskGeneration()));
        board.occupy(spirit.getUUID(), activity, board.now());
        return true;
    }

    private void reconcileActivities(List<SpiritEntity> spirits, boolean census) {
        Set<UUID> present = new HashSet<>();
        for (SpiritEntity spirit : spirits) {
            present.add(spirit.getUUID());
            Optional<BoardCase> occupation = occupationOf(spirit);
            if (occupation.isPresent()) {
                board.occupy(spirit.getUUID(), occupation.get(), board.now());
            } else {
                board.vacate(spirit.getUUID());
            }
        }
        if (!census) {
            return;
        }
        for (BoardTicket ticket : board.all()) {
            UUID holder = ticket.assignee().orElse(null);
            if (BoardActivities.isActivity(ticket.boardCase()) && holder != null && !present.contains(holder)) {
                board.vacate(holder);
                launched.remove(holder);
            }
        }
    }

    // Замурованный дух (воздуха к поверхности нет) сам копает лестницу вверх к дому: всю ночь стоял в каменном
    // кармане, а доска показывала «Бой» (INC-45, живой 24.10). Задание доски у такого духа всё равно бы «застряло».
    private void digOut(ServerLevel level, List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            boolean digging = DIG_OUT.equals(spirit.currentTaskScript().orElse(null))
                    && spirit.taskStatus().orElse(null) == RunStatus.RUNNING;
            long now = level.getGameTime();
            if (spirit.isRemoved()
                    || spirit.home().isEmpty()
                    || digging
                    || now < digOutAfter.getOrDefault(spirit.getUUID(), 0L)
                    || !DigOutScript.sealed(level, spirit.blockPosition())) {
                continue;
            }
            // Сорвавшийся выход не перезапускается каждую пересборку: пауза, чтобы доска успела выдать другое.
            digOutAfter.put(spirit.getUUID(), now + DIG_OUT_PAUSE_TICKS);
            BlockCoord home = spirit.home().orElseThrow();
            LOGGER.warn(
                    "[incident] дух {} замурован в {}: воздуха к поверхности нет — копает лестницу вверх к дому {}",
                    spirit.displayName(),
                    spirit.blockPosition().toShortString(),
                    home);
            board.assignedTo(spirit.getUUID()).forEach(ticket -> board.release(ticket.id()));
            spirit.startTask(new ScriptCall(DIG_OUT, Map.of("x", home.x(), "y", home.y(), "z", home.z())));
        }
    }

    private Optional<BoardCase> occupationOf(SpiritEntity spirit) {
        UUID id = spirit.getUUID();
        if (spirit.isRemoved()) {
            return Optional.empty();
        }
        if (DIG_OUT.equals(spirit.currentTaskScript().orElse(null))
                && spirit.taskStatus().orElse(null) == RunStatus.RUNNING) {
            return Optional.of(BoardCase.ACTIVITY_ESCAPE);
        }
        if (spirit.tactics().wantsControl()) {
            return Optional.of(BoardCase.ACTIVITY_COMBAT);
        }
        boolean running = spirit.taskStatus().orElse(null) == RunStatus.RUNNING;
        Launched own = launched.get(id);
        boolean ownActivity = own != null && own.generation() == spirit.taskGeneration();
        if (running && ownActivity) {
            return Optional.of(own.activity());
        }
        if (!board.assignedTo(id).isEmpty()) {
            return Optional.empty();
        }
        if (running) {
            return Optional.of(BoardCase.ACTIVITY_EXTERNAL);
        }
        if (spirit.isDefenceAssigned()) {
            return Optional.of(BoardCase.ACTIVITY_DEFEND);
        }
        return Optional.empty();
    }

    private record Launched(BoardCase activity, long generation) {}

    public void completeCapture(String captureId) {
        animals.cycle(speciesOfCapture(captureId)).captured(captureId);
    }

    public void completePen(String penId) {
        Species species = speciesOfId(penId);
        animals.cycle(species).penBuilt(penId);
        TicketTarget pen = targets.get(penId);
        if (pen != null) {
            targets.put(
                    cycleId(species, "lure"),
                    pen.withSpecies(species)
                            .withLureItem(FeedTable.feedFor(species).get(0)));
        }
    }

    public void completeLure(Species species) {
        animals.cycle(species).lured();
    }

    public void completeLure() {
        animals.cycle(firstCaptureSpecies()).lured();
    }

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
        String prefix = parts.length > 1 && "capture".equals(parts[0]) ? parts[1] : ticketId.split("[_:/]", 2)[0];
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

    private void cancelOrphans(List<SpiritEntity> spirits) {
        if (orphans.isEmpty()) {
            return;
        }
        for (SpiritEntity spirit : spirits) {
            String ticket = orphans.remove(spirit.getUUID());
            // Занятие (activity:<дух>, внешняя задача) — не задание: оно и есть след снятой задачи.
            boolean holdsTicket = board.assignedTo(spirit.getUUID()).stream()
                    .anyMatch(held -> !BoardActivities.isActivity(held.boardCase()));
            if (ticket == null || spirit.isRemoved() || holdsTicket) {
                continue;
            }
            if (spirit.taskStatus().orElse(null) == RunStatus.RUNNING) {
                spirit.cancelTask();
                LOGGER.warn(
                        "[incident] дух {}: задание {} снято сбросом при смене сборки — его задача {} отменена, дух "
                                + "свободен для доски",
                        spirit.displayName(),
                        ticket,
                        spirit.currentTaskScript().orElse("-"));
            }
        }
    }

    // Сброс заданий старых правил (смена сборки); держатели снятых заданий запоминаются — их задачи отменит
    // cancelOrphans при ближайшей пересборке. Публичен для GameTest: сброс без перезапуска сервера.
    public List<String> resetForNewRules() {
        Map<String, UUID> holders = new HashMap<>();
        for (BoardTicket ticket : board.all()) {
            board.holderOf(ticket.id()).ifPresent(holder -> holders.put(ticket.id(), holder));
        }
        List<String> reset = board.resetForNewRules();
        for (String id : reset) {
            UUID holder = holders.get(id);
            if (holder != null) {
                orphans.put(holder, id);
            }
        }
        return reset;
    }

    private void settleOwners(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()) {
                releaseSpirit(spirit);
                continue;
            }
            RunStatus status = spirit.taskStatus().orElse(null);
            List<BoardTicket> held = board.assignedTo(spirit.getUUID());
            if (held.isEmpty()) {
                continue;
            }
            // Дух исполняет одно задание за раз (MAX_ACTIVE_PER_SPIRIT): закрываем первое
            // по очереди, а остальные — следующими пересборками.
            BoardTicket ticket = held.get(0);
            TicketRun run = TicketRun.decide(
                    issuedGenerations.getOrDefault(ticket.id(), SpiritReading.UNKNOWN_GENERATION),
                    spirit.taskGeneration(),
                    status,
                    spirit.tactics().wantsControl());
            switch (run) {
                case SUCCEEDED, FAILED -> settle(spirit, ticket, run == TicketRun.SUCCEEDED);
                case RESUME -> resume(spirit, ticket, spirits);
                case RUNNING, DISPLACED -> {}
            }
        }
    }

    private void resume(SpiritEntity spirit, BoardTicket ticket, List<SpiritEntity> spirits) {
        TicketTarget target = targets.getOrDefault(ticket.id(), TicketTarget.at(homeOf(spirits)));
        TaskNode plan;
        try {
            plan = planOf(ticket, target);
            spirit.startTask(plan);
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "[incident] дух {} не продолжил задание {} после вытеснения: {}; задание возвращено доске",
                    spirit.displayName(),
                    ticket.id(),
                    failure.getMessage());
            issuedGenerations.remove(ticket.id());
            board.release(ticket.id());
            return;
        }
        issuedGenerations.put(ticket.id(), spirit.taskGeneration());
        LOGGER.info(
                "Дух {} продолжает задание {} (кейс {}): чужая задача его вытеснила, задание осталось за ним",
                spirit.displayName(),
                ticket.id(),
                ticket.boardCase());
    }

    private static TaskNode planOf(BoardTicket ticket, TicketTarget target) {
        return TicketPlans.EXTERNAL_CASES.contains(ticket.boardCase())
                ? new ScriptCall("eat", Map.of())
                : TicketPlans.plan(ticket.boardCase(), ticket.level(), target);
    }

    private void settle(SpiritEntity spirit, BoardTicket ticket, boolean success) {
        issuedGenerations.remove(ticket.id());
        if (success) {
            board.complete(ticket.id());
            switch (ticket.boardCase()) {
                case CAPTURE_ANIMAL -> animals.cycle(speciesFor(ticket)).captured(ticket.id());
                case BUILD_PEN -> completePen(ticket.id());
                case LURE_ANIMAL -> animals.cycle(speciesFor(ticket)).lured();
                case DISMANTLE_SHELTER -> {}
                default -> {}
            }
        } else {
            board.fail(ticket.id(), stock);
            LOGGER.warn(
                    "[incident] дух {} ({}) провалил задание доски {} (кейс {}): {}; статус {}; доска выдаёт "
                            + "следующее задание",
                    spirit.displayName(),
                    spirit.getUUID(),
                    ticket.id(),
                    ticket.boardCase(),
                    spirit.taskFailureReason().orElse("причина не записана"),
                    board.statusOf(ticket.id()).orElse(null));
            for (BoardLogEntry entry : board.drainDetachments()) {
                journal.recordEntry(entry);
            }
            flushBlockNotes();
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
        LOGGER.info(
                "Дух {} {} задание {}: статус закрыт доской",
                spirit.displayName(),
                success ? "завершил" : "провалил",
                ticket.id());
    }

    private void flushBlockNotes() {
        for (BoardLogEntry entry : board.drainBlocks()) {
            journal.recordEntry(entry);
        }
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
        crafters.retain(id -> board.statusOf(id).filter(TransferWatch::open).isPresent());
        // Дух ждёт своё задание, только если может его взять сейчас: держатель на паузе по кейсу (провалы)
        // или не годный к своему заданию иначе простаивал бы, не получая и чужих (INC-23).
        Set<UUID> awaited = new HashSet<>();
        for (BoardTicket ticket : claimable) {
            ticket.assignee()
                    .filter(holder ->
                            level.getEntity(holder) instanceof SpiritEntity own && free(own, held, ticket, now))
                    .ifPresent(awaited::add);
        }
        for (BoardTicket ticket : claimable) {
            SpiritEntity spirit = chooseSpirit(ticket, spirits, level, held, now, awaited);
            if (spirit == null) {
                LOGGER.debug(
                        "Задание {} ждёт свободного духа: {}",
                        ticket.id(),
                        ticket.assignee().isPresent() ? "свой дух занят" : "свободных нет или их ждут свои задания");
                continue;
            }
            TicketTarget target =
                    gatherOnTop(ticket, targets.getOrDefault(ticket.id(), TicketTarget.at(homeOf(spirits))), spirit);
            Optional<TicketTarget> fitted = fitTransfer(ticket, target, spirit);
            if (fitted.isEmpty()) {
                dropEmptyTransfer(ticket, target, spirit);
                continue;
            }
            target = fitted.orElseThrow();
            if (TransferWatch.ensureTransfers(level, this, spirits, stock, ticket, spirit, now)) {
                awaitTransfers(ticket, spirit, awaited);
                continue;
            }
            TaskNode plan;
            try {
                plan = planOf(ticket, target);
            } catch (RuntimeException failure) {
                LOGGER.error(
                        "Задание {} (кейс {}): план не собран: {}",
                        ticket.id(),
                        ticket.boardCase(),
                        failure.getMessage());
                continue;
            }
            if (spirit.runsBackgroundTask()) {
                LOGGER.info(
                        "Задание {} вытесняет фоновое занятие {} духа {}",
                        ticket.id(),
                        spirit.currentTaskScript().orElse("?"),
                        spirit.displayName());
            }
            try {
                spirit.startTask(plan);
            } catch (RuntimeException failure) {
                LOGGER.error(
                        "Задание {}: дух {} не принял задание: {}",
                        ticket.id(),
                        spirit.displayName(),
                        failure.getMessage());
                continue;
            }
            BoardOutcome outcome = board.claim(ticket.id(), spirit.getUUID(), now);
            if (outcome == BoardOutcome.CLAIMED) {
                crafters.forget(ticket.id());
                issuedGenerations.put(ticket.id(), spirit.taskGeneration());
                claims++;
                held.merge(spirit.getUUID(), 1, Integer::sum);
                WorkReserveHub.reserve(spirit, new WorkTarget(WorkKind.CELL, target.target()));
                LOGGER.info(
                        "Доска выдала задание {} (кейс {}) духу {}",
                        ticket.id(),
                        ticket.boardCase(),
                        spirit.displayName());
            } else {
                LOGGER.error("Задание {} не выдано духу {}: {}", ticket.id(), spirit.displayName(), outcome);
            }
        }
    }

    // acquire добывает «до N в инвентаре»: дух, у которого уже лежит N, завершал gather:jungle_planks ×4 сразу, запас
    // не рос, и STOCK_CHESTS ждал «не хватает jungle_planks=4» весь день (приёмка «джунгли»). Цель — сверх своего.
    private static TicketTarget gatherOnTop(BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        String item = target.supplyItem();
        if (ticket.boardCase() != BoardCase.GATHER_MATERIALS
                || item == null
                || TorchRules.COAL.equals(item)
                || "cobblestone".equals(item)) {
            return target;
        }
        int held = new SpiritSupplies(spirit, CatalogLimits.CONTAINER_RADIUS_DEFAULT).inInventory(item);
        return held <= 0
                ? target
                : target.withSupply(item, Math.min(CatalogLimits.COUNT_MAX, held + target.supplyCount()));
    }

    private void awaitTransfers(BoardTicket ticket, SpiritEntity crafter, Set<UUID> awaited) {
        crafters.remember(ticket.id(), crafter.getUUID());
        awaited.addAll(TransferWatch.donors(this, ticket.id()));
        LOGGER.info(
                "Задание {} ждёт передачу ингредиентов: дух {} пока не начинает крафт",
                ticket.id(),
                crafter.displayName());
    }

    private void dropEmptyTransfer(BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        board.cancel(ticket.id());
        targets.remove(ticket.id());
        persist();
        LOGGER.info(
                "Передача {} снята: у духа {} не осталось {}", ticket.id(), spirit.displayName(), target.supplyItem());
    }

    // Передача планируется по инвентарю на момент заказа, а исполняется позже: отдающий успевал израсходовать
    // часть, deliver падал «stick: нужно 16, в инвентаре 15», пауза кейса держала все его передачи, крафтеры
    // ждали их весь день (приёмка «джунгли» 24.10). Отдаём сколько есть; остаток крафт закажет заново.
    private static Optional<TicketTarget> fitTransfer(BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        if (ticket.boardCase() != BoardCase.TRANSFER_ITEM
                || target.transferFrom() == null
                || !target.transferFrom().equals(spirit.getUUID())
                || target.supplyItem() == null) {
            return Optional.of(target);
        }
        int held = new SpiritSupplies(spirit, CatalogLimits.CONTAINER_RADIUS_DEFAULT).inInventory(target.supplyItem());
        if (held <= 0) {
            return Optional.empty();
        }
        return Optional.of(
                held >= target.supplyCount()
                        ? target
                        : target.withTransfer(target.transferFrom(), target.transferTo(), target.supplyItem(), held));
    }

    TicketTarget targetOf(String ticketId, List<SpiritEntity> spirits) {
        return targets.getOrDefault(ticketId, TicketTarget.at(homeOf(spirits)));
    }

    BlockCoord targetPoint(String ticketId, List<SpiritEntity> spirits) {
        TicketTarget target = targets.get(ticketId);
        return target != null ? target.target() : homeOf(spirits);
    }

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

    private SpiritEntity chooseSpirit(
            BoardTicket ticket,
            List<SpiritEntity> spirits,
            ServerLevel level,
            Map<UUID, Integer> held,
            long now,
            Set<UUID> awaited) {
        if (ticket.assignee().isPresent()) {
            Entity entity = level.getEntity(ticket.assignee().orElseThrow());
            if (entity instanceof SpiritEntity spirit && free(spirit, held, ticket, now)) {
                return spirit;
            }
            return null;
        }
        if (crafters.remembered(ticket.id()).filter(awaited::contains).isPresent()) {
            // Крафтер сейчас отдаёт по своей передаче (секунды): иначе крафт ушёл бы другому и заказал бы
            // полученное обратно.
            return null;
        }
        List<SpiritEntity> unclaimed = spirits.stream()
                .filter(spirit -> !awaited.contains(spirit.getUUID()))
                .toList();
        return crafters.choose(
                ticket.id(),
                unclaimed,
                SpiritEntity::getUUID,
                spirit -> free(spirit, held, ticket, now),
                () -> nearestFree(unclaimed, targets.get(ticket.id()), held, ticket, now));
    }

    private boolean free(SpiritEntity spirit, Map<UUID, Integer> held, BoardTicket ticket, long now) {
        RunStatus status = spirit.taskStatus().orElse(null);
        boolean busy = status == RunStatus.RUNNING || spirit.currentTaskScript().isPresent();
        if (busy && !(spirit.runsBackgroundTask() && !spirit.tactics().wantsControl())) {
            return false;
        }
        // Занятость — только RUNNING (идиома проекта, SpiritBotGateway): SUCCEEDED/FAILED
        // после закрытия задания духа не держат, иначе доска выдала бы духу одно задание навсегда.
        return held.getOrDefault(spirit.getUUID(), 0) < BoardTuning.maxActivePerSpirit()
                && board.availableTo(spirit.getUUID(), ticket.boardCase(), now)
                && EvergreenRules.issuedTo(ticket, spirit.getUUID(), eatNeed(spirit), viewOf(spirit));
    }

    private static EatNeed eatNeed(SpiritEntity spirit) {
        return new EatNeed(
                spirit.hunger().foodLevel(), (int) Math.ceil(spirit.getHealth()), SpiritHunger.MAX_FOOD, (int)
                        Math.ceil(spirit.getMaxHealth()));
    }

    private static SpiritView viewOf(SpiritEntity spirit) {
        return new SpiritView(
                spirit.tactics().wantsControl(), false, spirit.taskStatus().orElse(null) == RunStatus.RUNNING);
    }

    private SpiritEntity nearestFree(
            List<SpiritEntity> spirits, TicketTarget target, Map<UUID, Integer> held, BoardTicket ticket, long now) {
        SpiritEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SpiritEntity spirit : spirits) {
            if (!free(spirit, held, ticket, now)) {
                continue;
            }
            double distance = target == null
                    ? 0
                    : spirit.distanceToSqr(
                            target.target().x() + 0.5,
                            target.target().y(),
                            target.target().z() + 0.5);
            if (distance < bestDistance
                    || (distance == bestDistance
                            && best != null
                            && spirit.getUUID().compareTo(best.getUUID()) < 0)) {
                best = spirit;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void ensureSupply(List<SpiritEntity> spirits, StockView stock, long now) {
        if (spirits.isEmpty()) {
            return;
        }
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
            if (active(id) || board.recentlyFailed(id, now)) {
                continue;
            }
            TicketTarget target = targets.values().stream()
                    .filter(supply -> item.equals(supply.supplyItem()))
                    .findFirst()
                    .orElse(TicketTarget.at(homeOf(spirits)));
            submit(
                    new BoardTicket(
                            id,
                            BoardCase.SUPPLY_SITE,
                            Optional.empty(),
                            BoardRules.priorityOf(BoardCase.SUPPLY_SITE),
                            now,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.of()),
                    target.withSupply(item, count));
        }
        permanentSupplies(spirits, stock, now);
    }

    private void permanentSupplies(List<SpiritEntity> spirits, StockView stock, long now) {
        EvergreenRules.supplyThresholds().forEach((item, threshold) -> {
            String shortItem = shortId(item);
            if (!EvergreenRules.supplyNeeded(item, board)) {
                return;
            }
            int have = stock.available().getOrDefault(shortItem, 0);
            if (have >= threshold || hasActiveSupply(shortItem)) {
                return;
            }
            String id = "supply:" + shortItem;
            if (active(id) || board.recentlyFailed(id, now)) {
                return;
            }
            submit(
                    new BoardTicket(
                            id,
                            BoardCase.SUPPLY_SITE,
                            Optional.empty(),
                            BoardPriorities.effective(BoardCase.ENCHANT_SHELVES, PriorityContext.none()),
                            now,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.of()),
                    TicketTarget.at(homeOf(spirits)).withSupply(shortItem, threshold - have));
        });
    }

    private boolean hasActiveSupply(String item) {
        for (BoardTicket ticket : board.all()) {
            if (ticket.boardCase() != BoardCase.SUPPLY_SITE || !active(ticket.id())) {
                continue;
            }
            TicketTarget target = targets.get(ticket.id());
            if (target != null && item.equals(target.supplyItem())) {
                return true;
            }
        }
        return false;
    }

    private boolean active(String id) {
        BoardStatus status = board.statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    private static String shortId(String item) {
        return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
    }

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        return spirits.stream()
                .map(SpiritEntity::home)
                .flatMap(Optional::stream)
                .findFirst()
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
