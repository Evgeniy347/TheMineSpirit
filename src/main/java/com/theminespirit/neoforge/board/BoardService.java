package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.AbsentOwners;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardReserve;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.CraterProfiles;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.SupplyGate;
import com.theminespirit.core.board.TaskStall;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketRun;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolCraft;
import com.theminespirit.core.board.ToolLossRelease;
import com.theminespirit.core.board.TransferCarriers;
import com.theminespirit.core.board.TransferTicket;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.movement.PitEscape;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.task.SpiritReading;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.IncidentSnapshots;
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

public final class BoardService {
    private static final long EARLY_DISPATCH_TICKS = 1;
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
    private final TaskStall stall = new TaskStall();
    private final Map<UUID, Long> digOutChecked = new HashMap<>();
    private final Map<String, Long> issuedGenerations = new HashMap<>();
    private final Map<String, Long> supplyClosed = new HashMap<>();
    private final AbsentOwners absentOwners = new AbsentOwners();
    private final StageProgressWatch progress = new StageProgressWatch();
    private final Map<UUID, String> orphans = new HashMap<>();
    private final CrafterMemory crafters = new CrafterMemory();
    private StockView stock = new StockView(Map.of());
    private long lastRebuild = -1;
    private int rebuilds;
    private boolean freshTickets;
    private long lastDispatch;
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
        long since = now - lastRebuild;
        if (since >= BoardTuning.boardIntervalTicks()) {
            rebuild(level, now);
            return;
        }
        if (now - lastDispatch >= EARLY_DISPATCH_TICKS && (freshTickets || holderFreed(level))) {
            lastDispatch = now;
            freshTickets = false;
            issue(level, census(level), now);
        }
    }

    private boolean holderFreed(ServerLevel level) {
        for (UUID holder : board.assignedCounts().keySet()) {
            if (level.getEntity(holder) instanceof SpiritEntity spirit
                    && spirit.taskStatus().orElse(null) != RunStatus.RUNNING) {
                return true;
            }
        }
        return false;
    }

    public void rebuild(ServerLevel level, long now) {
        freshTickets = false;
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
        StockView unreserved = BoardReserve.unreserved(stock, board);
        Set<String> liveCauses = sources.tick(level, this, spirits, unreserved, now);
        liveCauses.addAll(liveHerdCauses(level));
        liveCauses.addAll(liveDropCauses(level));
        if (!sources.failedRound()) {
            board.retain(liveCauses, now);
            retains++;
        }
        digOut(level, spirits);
        issue(level, spirits, now);
        reconcileActivities(spirits, true);
        releaseDeadHolders(level, spirits, now);
        dropAbsentOwners(spirits, now);
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
            freshTickets = true;
            targets.put(ticket.id(), target);
            journal.record(BoardLog.Event.POSTED, ticket, null, board.now());
            persist();
        } else if (outcome == BoardOutcome.DUPLICATE) {
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

    public boolean reopen(String ticketId) {
        boolean reopened = board.reopen(ticketId);
        if (reopened) {
            targets.remove(ticketId);
            persist();
        }
        return reopened;
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
        Objects.requireNonNull(spirit, "Дух занятия не может быть null: занятие адресовано конкретному духу");
        if (!BoardActivities.isActivity(activity)) {
            throw new IllegalArgumentException(
                    "Кейс " + activity + " — не занятие: через launch идут только ACTIVITY_*");
        }
        if (!launcher.start(spirit, script, task, now)) {
            return false;
        }
        launched.put(spirit.getUUID(), new Launched(activity, spirit.taskGeneration()));
        board.occupy(spirit.getUUID(), activity, script, board.now());
        return true;
    }

    private void reconcileActivities(List<SpiritEntity> spirits, boolean census) {
        for (SpiritEntity spirit : spirits) {
            Optional<BoardCase> occupation = occupationOf(spirit);
            if (occupation.isPresent()) {
                board.occupy(
                        spirit.getUUID(),
                        occupation.get(),
                        spirit.currentPlan().map(BoardActivities::scriptOf).orElse(null),
                        board.now());
            } else {
                board.vacate(spirit.getUUID());
            }
        }
        if (!census) {
            return;
        }
    }

    private void dropAbsentOwners(List<SpiritEntity> spirits, long now) {
        Set<UUID> live = new HashSet<>();
        spirits.forEach(spirit -> live.add(spirit.getUUID()));
        for (String id : absentOwners.sweep(board, live, now)) {
            LOGGER.info(
                    "[board] задание {} снято: его дух удалён или не появлялся {} тиков", id, AbsentOwners.GRACE_TICKS);
        }
    }

    private void releaseDeadHolders(ServerLevel level, List<SpiritEntity> spirits, long now) {
        Set<UUID> live = new HashSet<>();
        for (SpiritEntity spirit : spirits) {
            live.add(spirit.getUUID());
        }
        for (BoardTicket ticket : board.all()) {
            if (board.statusOf(ticket.id()).orElse(null) != BoardStatus.ASSIGNED
                    || BoardActivities.isActivity(ticket.boardCase())) {
                continue;
            }
            UUID holder = board.holderOf(ticket.id()).orElse(null);
            if (holder == null || live.contains(holder) || !repairGoalDone(level, ticket)) {
                continue;
            }
            board.complete(ticket.id());
        }
        for (Board.HolderRelease freed : board.releaseDeadHolders(live)) {
            if (freed.activity()) {
                launched.remove(freed.holder());
            }
            LOGGER.info(
                    "[board] задание {} освобождено: держатель {} погиб или удалён", freed.ticketId(), freed.holder());
            journal.recordEntry(BoardLog.note(
                    freed.ticketId(),
                    freed.boardCase(),
                    now,
                    List.of("держатель " + freed.holder() + " погиб/удалён"),
                    freed.holder().toString(),
                    ConfigStamp.current()));
        }
    }

    private boolean repairGoalDone(ServerLevel level, BoardTicket ticket) {
        if (ticket.boardCase() != BoardCase.REPAIR_BUILDING || !ticket.id().startsWith(RepairPlan.PREFIX)) {
            return false;
        }
        TicketTarget target = targets.get(ticket.id());
        StageWork work = target == null ? null : target.work();
        String wood = target == null ? null : target.wood();
        if (work == null || work.repairs().isEmpty() || wood == null) {
            return false;
        }
        FirstStepsRunner.Placed placed =
                FirstStepsRunner.audit(level, BoardCase.valueOf(work.repairs()), work.site(), wood);
        return placed.total() > 0 && placed.done() >= placed.total();
    }

    private void digOut(ServerLevel level, List<SpiritEntity> spirits) {
        long now = level.getGameTime();
        for (SpiritEntity spirit : spirits) {
            BlockPos feet = spirit.blockPosition();
            if (spirit.isRemoved()) {
                continue;
            }
            boolean digging = DIG_OUT.equals(spirit.currentTaskScript().orElse(null))
                    && spirit.taskStatus().orElse(null) == RunStatus.RUNNING;
            boolean paused = now < digOutAfter.getOrDefault(spirit.getUUID(), 0L);
            if (DigOutScript.open(level, feet)) {
                if (!digging && !paused && idleOrActivity(spirit)) {
                    climbOut(spirit, now);
                }
                continue;
            }
            boolean sealed = !digging && !paused && DigOutScript.sealed(level, feet);
            BlockCoord home = spirit.home().orElseGet(() -> homeOf(spirits));
            if (now >= digOutChecked.getOrDefault(spirit.getUUID(), 0L)) {
                digOutChecked.put(spirit.getUUID(), now + DIG_OUT_PAUSE_TICKS);
                LOGGER.info(
                        "[dig_out] проверка: дух {} в {} без неба над головой — замурован {}, уже копает {}, пауза {}, задача {}"
                                + ", дом {}",
                        spirit.displayName(),
                        feet.toShortString(),
                        sealed,
                        digging,
                        paused,
                        spirit.currentTaskScript().orElse("-"),
                        spirit.home().isPresent() ? home : "нет (дом поселения " + home + ")");
            }
            if (!sealed) {
                continue;
            }
            digOutAfter.put(spirit.getUUID(), now + DIG_OUT_PAUSE_TICKS);
            LOGGER.warn(
                    "[incident] дух {} замурован в {}: воздуха к поверхности нет — копает лестницу вверх к дому {}",
                    spirit.displayName(),
                    feet.toShortString(),
                    home);
            board.assignedTo(spirit.getUUID()).forEach(ticket -> board.release(ticket.id()));
            spirit.startTask(new ScriptCall(DIG_OUT, Map.of("x", home.x(), "y", home.y(), "z", home.z())));
        }
    }

    private boolean idleOrActivity(SpiritEntity spirit) {
        if (spirit.taskStatus().orElse(null) != RunStatus.RUNNING) {
            return true;
        }
        Launched own = launched.get(spirit.getUUID());
        return own != null && own.generation() == spirit.taskGeneration();
    }

    private void climbOut(SpiritEntity spirit, long now) {
        Optional<PitEscape.Exit> pit = PitRescue.trapped(spirit);
        if (pit.isEmpty()) {
            return;
        }
        digOutAfter.put(spirit.getUUID(), now + DIG_OUT_PAUSE_TICKS);
        PitEscape.Exit exit = pit.orElseThrow();
        LOGGER.warn(
                "[incident] дух {} в яме {}: пути наверх нет — поднимается на {} у ({}, {}) к краю",
                spirit.displayName(),
                spirit.blockPosition().toShortString(),
                exit.rise(),
                exit.dx(),
                exit.dz());
        board.assignedTo(spirit.getUUID()).forEach(ticket -> board.release(ticket.id()));
        launched.remove(spirit.getUUID());
        spirit.startTask(PitRescue.plan(spirit, exit));
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
        if (spirit.tactics().wantsControl() && board.assignedTo(id).isEmpty()) {
            Optional<BoardCase> tactic = BoardActivities.caseOf(spirit.tactics().action());
            if (tactic.isPresent()) {
                return tactic;
            }
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
            dropUntooled(spirit);
            List<BoardTicket> held = board.assignedTo(spirit.getUUID());
            if (held.isEmpty()) {
                continue;
            }
            BoardTicket ticket = held.get(0);
            TicketRun run = TicketRun.decide(
                    issuedGenerations.getOrDefault(ticket.id(), SpiritReading.UNKNOWN_GENERATION),
                    spirit.taskGeneration(),
                    status,
                    spirit.tactics().wantsControl());
            switch (run) {
                case SUCCEEDED, FAILED -> {
                    if (run == TicketRun.SUCCEEDED
                            && !ToolCraft.fulfilled(ticket.boardCase(), SpiritItems.ids(spirit))) {
                        ToolWait.release(board, issuedGenerations, spirit, ticket);
                        break;
                    }
                    String empty = run == TicketRun.SUCCEEDED && spirit.level() instanceof ServerLevel level
                            ? progress.emptyResult(level, ticket, targets.get(ticket.id()))
                            : null;
                    settle(spirit, ticket, run == TicketRun.SUCCEEDED && empty == null, empty);
                }
                case RESUME -> resume(spirit, ticket, spirits);
                case RUNNING -> checkStall(spirit, ticket);
                case DISPLACED -> {}
            }
        }
    }

    private void dropUntooled(SpiritEntity spirit) {
        boolean walledIn = DIG_OUT.equals(spirit.currentTaskScript().orElse(null));
        for (ToolLossRelease.Released freed :
                ToolLossRelease.apply(board, spirit.getUUID(), SpiritItems.ids(spirit), walledIn, this::supplyOf)) {
            Long issued = issuedGenerations.remove(freed.ticketId());
            if (issued != null
                    && issued == spirit.taskGeneration()
                    && spirit.taskStatus().orElse(null) == RunStatus.RUNNING) {
                spirit.cancelTask();
            }
            LOGGER.info("[tool] дух {}: {}", spirit.displayName(), freed.reason());
            journal.recordEntry(BoardLog.note(
                    freed.ticketId(),
                    freed.boardCase(),
                    board.now(),
                    List.of(freed.reason()),
                    spirit.getUUID().toString(),
                    ConfigStamp.current()));
        }
    }

    private @Nullable String supplyOf(BoardTicket ticket) {
        TicketTarget target = targets.get(ticket.id());
        return target == null ? null : target.supplyItem();
    }

    private void checkStall(SpiritEntity spirit, BoardTicket ticket) {
        BlockPos feet = spirit.blockPosition();
        String script = spirit.currentTaskScript().orElse(null);
        boolean standing = stall.stalled(
                spirit.getUUID(), script, new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), board.now());
        String key = spirit.taskProgress()
                .map(p -> p.step() + "/" + p.currentScript() + "/" + p.done())
                .orElse("-");
        boolean idle = stall.noProgress(spirit.getUUID(), key, board.now());
        if (!standing && !idle) {
            return;
        }
        String reason = (standing
                        ? "задание стоит " + TaskStall.STALL_TICKS + " тиков: "
                                + (script == null ? "скрипт не стартовал" : "подход не двигается")
                        : "нет прогресса " + TaskStall.NO_PROGRESS_TICKS + " тиков (" + script + ", " + key + ")")
                + " в " + feet.toShortString()
                + (spirit.tactics().wantsControl() ? ", тактика держит духа" : "")
                + spirit.taskSuspendReason().map(why -> ", пауза: " + why).orElse("");
        LOGGER.warn(
                "[incident] дух {}: {} {}; задание провалено; {}",
                spirit.displayName(),
                ticket.id(),
                reason,
                incident(spirit, ticket, reason));
        stall.forget(spirit.getUUID());
        issuedGenerations.remove(ticket.id());
        spirit.cancelTask();
        board.fail(ticket.id(), stock, reason);
    }

    private String incident(SpiritEntity spirit, BoardTicket ticket, String reason) {
        TicketTarget target = targets.get(ticket.id());
        String aim = target == null ? "-" : target.target() + " часть " + target.count();
        String what = ticket.id() + " (" + ticket.boardCase() + ", цель " + aim + "): " + reason;
        return spirit.level() instanceof ServerLevel level ? IncidentSnapshots.record(level, spirit, what) : what;
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

    private void settle(SpiritEntity spirit, BoardTicket ticket, boolean success, @Nullable String verdict) {
        issuedGenerations.remove(ticket.id());
        if (ticket.boardCase() == BoardCase.SUPPLY_SITE) {
            supplyClosed.put(ticket.id(), spirit.level().getGameTime());
        }
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
            String reason =
                    verdict != null ? verdict : spirit.taskFailureReason().orElse("причина не записана");
            board.fail(
                    ticket.id(),
                    stock,
                    verdict != null ? verdict : spirit.taskFailureReason().orElse(null));
            LOGGER.warn(
                    "[incident] дух {} ({}) провалил задание доски {} (кейс {}): {}; статус {}; доска выдаёт "
                            + "следующее задание; {}",
                    spirit.displayName(),
                    spirit.getUUID(),
                    ticket.id(),
                    ticket.boardCase(),
                    reason,
                    board.statusOf(ticket.id()).orElse(null),
                    incident(spirit, ticket, reason));
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
        List<BoardTicket> claimable = DispatchRound.order(board.claimable(stock, now));
        crafters.retain(id -> board.statusOf(id).filter(TransferWatch::open).isPresent());
        DispatchRound round = new DispatchRound(board, now);
        DispatchPick pick = new DispatchPick(round, spirits);
        for (BoardTicket ticket : claimable) {
            SpiritEntity spirit = pick.choose(ticket, targets.get(ticket.id()), crafters);
            if (spirit == null) {
                String why = pick.why(ticket, targets.get(ticket.id()));
                board.noteWait(ticket.id(), why);
                LOGGER.debug("Задание {} ждёт свободного духа: {}", ticket.id(), why);
                continue;
            }
            board.noteWait(ticket.id(), null);
            TicketTarget target = gatherOnTop(
                    ticket,
                    TransferTicket.restore(ticket, targets.getOrDefault(ticket.id(), TicketTarget.at(homeOf(spirits)))),
                    spirit);
            Optional<TicketTarget> fitted = fitTransfer(ticket, target, spirit);
            if (fitted.isEmpty()) {
                dropEmptyTransfer(ticket, target, spirit);
                continue;
            }
            target = fitted.orElseThrow();
            if (TransferWatch.ensureTransfers(level, this, spirits, stock, ticket, spirit, now)) {
                awaitTransfers(ticket, spirit, spirits, round);
                continue;
            }
            TaskNode plan;
            try {
                plan = planOf(ticket, target);
            } catch (RuntimeException failure) {
                if (RepairPlan.goalless(ticket, targets.get(ticket.id()))) {
                    dropGoallessRepair(ticket);
                    continue;
                }
                LOGGER.error(
                        "Задание {} (кейс {}): план не собран: {}",
                        ticket.id(),
                        ticket.boardCase(),
                        failure.getMessage());
                dropUnplannedTransfer(ticket, round);
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
                progress.issued(level, ticket, target);
                claims++;
                round.take(spirit.getUUID());
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

    private static TicketTarget gatherOnTop(BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        String item = target.supplyItem();
        if (ticket.boardCase() != BoardCase.GATHER_MATERIALS
                || item == null
                || "coal".equals(item)
                || "cobblestone".equals(item)) {
            return target;
        }
        int held = new SpiritSupplies(spirit, CatalogLimits.CONTAINER_RADIUS_DEFAULT).inInventory(item);
        return held <= 0
                ? target
                : target.withSupply(item, Math.min(CatalogLimits.COUNT_MAX, held + target.supplyCount()));
    }

    private void awaitTransfers(
            BoardTicket ticket, SpiritEntity crafter, List<SpiritEntity> spirits, DispatchRound round) {
        crafters.remember(ticket.id(), crafter.getUUID());
        Set<UUID> running = new HashSet<>();
        for (SpiritEntity each : spirits) {
            if (each.taskStatus().orElse(null) == RunStatus.RUNNING) {
                running.add(each.getUUID());
            }
        }
        for (UUID donor : TransferCarriers.carrying(board, ticket.id(), running)) {
            round.await(donor, "несёт ингредиенты для " + ticket.id());
        }
        LOGGER.info(
                "Задание {} ждёт передачу ингредиентов: дух {} пока не начинает крафт",
                ticket.id(),
                crafter.displayName());
    }

    private void dropGoallessRepair(BoardTicket ticket) {
        board.cancel(ticket.id());
        targets.remove(ticket.id());
        persist();
        LOGGER.info(
                "[board] ремонт {} снят: цели нет (план сменился или сервер перезапущен), аудит этапа поставит заново",
                ticket.id());
    }

    private void dropUnplannedTransfer(BoardTicket ticket, DispatchRound round) {
        if (ticket.boardCase() != BoardCase.TRANSFER_ITEM) {
            return;
        }
        TransferCarriers.dropUnplanned(board, round, ticket);
        targets.remove(ticket.id());
        LOGGER.warn(
                "[incident] передача {} снята: план не собран, держатель {} свободен, доска поставит её заново",
                ticket.id(),
                ticket.assignee().map(UUID::toString).orElse("-"));
        persist();
    }

    private void dropEmptyTransfer(BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        board.cancel(ticket.id());
        targets.remove(ticket.id());
        persist();
        LOGGER.info(
                "Передача {} снята: у духа {} не осталось {}", ticket.id(), spirit.displayName(), target.supplyItem());
    }

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

    public TicketTarget targetOf(String ticketId, List<SpiritEntity> spirits) {
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
        SupplyGate.Decision decision = SupplyGate.decide(
                missing,
                stock.available(),
                DataTables.INSTANCE.leveling().current().chestKeep(),
                supplyClosed,
                now,
                BoardTuning.failBackoffTicks());
        BlockCoord home = homeOf(spirits);
        for (SupplyGate.Request request : decision.supply()) {
            SupplyTickets.issue(this, targets, home, request, BoardCase.SUPPLY_SITE, "supply:", now);
        }
        for (SupplyGate.Request request : decision.gather()) {
            SupplyTickets.issue(this, targets, home, request, BoardCase.GATHER_MATERIALS, "supply_gather:", now);
        }
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
            if (entity instanceof SpiritEntity spirit && !spirit.isRemoved() && spirit.isAlive() && !spirit.isDemo()) {
                spirits.add(spirit);
            }
        }
        return spirits;
    }
}
