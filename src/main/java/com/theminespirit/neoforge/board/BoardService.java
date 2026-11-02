package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.AbsentOwners;
import com.theminespirit.core.board.ActivityCadence;
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
import com.theminespirit.core.board.HolderRelease;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockParts;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.SupplyGate;
import com.theminespirit.core.board.TaskStall;
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
import com.theminespirit.core.husbandry.SpeciesIds;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.task.SpiritReading;
import com.theminespirit.core.town.StageWork;
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
    private final Board board;
    private final HerdCycles animals;
    private final StockReader stockReader;
    private final BoardSources sources;
    private final JournalService journal;
    private final @Nullable BoardStateStore store;
    private final CraterProfiles craters = new CraterProfiles(64);
    private final Map<String, Capture> captures = new LinkedHashMap<>();
    private final Map<String, TicketTarget> targets = new HashMap<>();
    private final ActivityCards cards = new ActivityCards();
    private final DigOutWatch digOut;
    private final TaskStall stall = new TaskStall();
    private long lastActivity;
    private final Map<String, Long> issuedGenerations = new HashMap<>();
    private final Map<String, Long> supplyClosed = new HashMap<>();
    private final AbsentOwners absentOwners = new AbsentOwners();
    private final StageProgressWatch progress = new StageProgressWatch();
    private final BoardNotices notices = new BoardNotices();
    private final BoardIncidents incidents = new BoardIncidents();
    private final Map<UUID, String> orphans = new HashMap<>();
    private final CrafterMemory crafters = new CrafterMemory();
    private final TransferWait transferWait = new TransferWait();
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
        this.animals = Objects.requireNonNull(animals, "animals не может быть null: циклы животных по видам");
        this.stockReader =
                Objects.requireNonNull(stockReader, "stockReader не может быть null: снимок запасов собирает адаптер");
        this.sources = Objects.requireNonNull(sources, "sources не может быть null: источники событий доски");
        this.journal =
                Objects.requireNonNull(journal, "journal не может быть null: без него переходы не попадут в журнал");
        this.store = store;
        if (store != null) {
            store.restoreInto(board);
            RescueWatch.cancelRestored(board);
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
        this.digOut = new DigOutWatch(this);
    }

    public static BoardService create(ServerLevel level) {
        LongSupplier now = level::getGameTime;
        JournalService journal = JournalService.inWorld(level.getServer());
        Board board = TransitionEvents.create(now, journal);
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
        if (ActivityCadence.due(lastActivity, now)) {
            lastActivity = now;
            reconcileActivities(SpiritCensus.all(level), false);
        }
        if (now - lastDispatch >= EARLY_DISPATCH_TICKS && (freshTickets || holderFreed(level))) {
            lastDispatch = now;
            freshTickets = false;
            issue(level, SpiritCensus.all(level), now);
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
        List<SpiritEntity> spirits = SpiritCensus.all(level);
        cancelOrphans(level, spirits);
        settleOwners(spirits);
        StockParts parts = refreshStock(level, spirits);
        ensureSupply(level, spirits, stock, now);
        StockView unreserved = parts.free(BoardReserve.items(board));
        Set<String> liveCauses = sources.tick(level, this, spirits, unreserved, now);
        liveCauses.addAll(liveHerdCauses(level));
        liveCauses.addAll(liveDropCauses(level));
        if (!sources.failedRound()) {
            board.retain(liveCauses, now);
            retains++;
        }
        digOut.tick(level, spirits, now);
        ForceloadGuard.tick(level, spirits, board, targets);
        issue(level, spirits, now);
        notices.watchPending(board, stock, now);
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
        cards.forget(spirit.getUUID());
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
                cards.remember(
                        spirit.getUUID(),
                        BoardActivities.caseOf(offer.activity()),
                        spirit.taskGeneration(),
                        offer.script(),
                        BoardActivities.targetOf(task));
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
        cards.remember(spirit.getUUID(), activity, spirit.taskGeneration(), script, BoardActivities.targetOf(task));
        board.occupy(spirit.getUUID(), activity, script, BoardActivities.targetOf(task), board.now());
        return true;
    }

    private void reconcileActivities(List<SpiritEntity> spirits, boolean census) {
        for (SpiritEntity spirit : spirits) {
            Optional<BoardOccupant.Occupation> occupation = occupationOf(spirit);
            if (occupation.isPresent()) {
                BoardOccupant.Occupation occ = occupation.get();
                board.occupy(spirit.getUUID(), occ.boardCase(), occ.script(), occ.target(), board.now());
            } else {
                board.vacate(spirit.getUUID());
            }
        }
        WaitRefresh.tick(board, spirits, targets, stock, crafters, board.now());
        if (!census) return;
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
        for (HolderRelease freed : board.releaseDeadHolders(live)) {
            if (freed.activity()) {
                cards.forget(freed.holder());
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
        return placed.confident() && placed.total() > 0 && placed.done() >= placed.total();
    }

    boolean idleOrActivity(SpiritEntity spirit) {
        return cards.idleOrActivity(
                spirit.getUUID(), spirit.taskGeneration(), spirit.taskStatus().orElse(null));
    }

    void releaseAssigned(SpiritEntity spirit) {
        board.assignedTo(spirit.getUUID()).forEach(ticket -> board.release(ticket.id()));
    }

    void forgetLaunch(SpiritEntity spirit) {
        cards.forget(spirit.getUUID());
    }

    private Optional<BoardOccupant.Occupation> occupationOf(SpiritEntity spirit) {
        return cards.occupation(spirit, !board.assignedTo(spirit.getUUID()).isEmpty());
    }

    public void completeCapture(String captureId) {
        animals.cycle(speciesOfCapture(captureId)).captured(captureId);
    }

    public void completePen(String penId) {
        Species species = SpeciesIds.of(penId);
        animals.cycle(species).penBuilt(penId);
        TicketTarget pen = targets.get(penId);
        if (pen != null) {
            targets.put(
                    HerdCycles.cycleId(species, "lure"),
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
        return capture == null ? SpeciesIds.of(captureId) : capture.species();
    }

    private Species firstCaptureSpecies() {
        return captures.values().stream().findFirst().map(Capture::species).orElse(Species.COW);
    }

    private void cancelOrphans(ServerLevel level, List<SpiritEntity> spirits) {
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
                                + "свободен для доски; {}",
                        spirit.displayName(),
                        ticket,
                        spirit.currentTaskScript().orElse("-"),
                        IncidentSnapshots.record(level, spirit, "сброс сборки снял задание " + ticket));
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
                    spirit.tactics().wantsControl(),
                    targets.containsKey(ticket.id()));
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
                case DISPLACED -> DisplacedClaims.release(spirit, ticket, board, issuedGenerations);
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
        boolean paused =
                spirit.tactics().wantsControl() || spirit.taskSuspendReason().isPresent();
        BlockCoord at = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        boolean standing = stall.stalled(spirit.getUUID(), script, at, board.now(), paused);
        String key = spirit.taskProgress()
                .map(p -> p.step() + "/" + p.currentScript() + "/" + p.done())
                .orElse("-");
        boolean idle = stall.noProgress(spirit.getUUID(), key, at, board.now(), paused);
        if (!standing && !idle) {
            return;
        }
        String reason = StallText.reason(
                standing,
                script,
                key,
                feet.toShortString(),
                spirit.tactics().wantsControl(),
                spirit.taskSuspendReason().orElse(null));
        incidents.stalled(spirit, ticket, targets.get(ticket.id()), reason, board.now());
        stall.forget(spirit.getUUID());
        issuedGenerations.remove(ticket.id());
        spirit.cancelTask();
        board.fail(ticket.id(), stock, reason);
    }

    private void resume(SpiritEntity spirit, BoardTicket ticket, List<SpiritEntity> spirits) {
        TicketTarget target = targets.getOrDefault(ticket.id(), TicketTarget.at(SettlementHome.of(spirits)));
        TaskNode plan;
        try {
            plan = JobLaunch.plan(ticket, target);
            JobLaunch.start(board, cards, spirit, ticket.id(), plan);
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

    private void settle(SpiritEntity spirit, BoardTicket ticket, boolean success, @Nullable String verdict) {
        issuedGenerations.remove(ticket.id());
        SettleNotes.note(spirit, ticket, success, supplyClosed, spirit.level().getGameTime());
        String why = verdict != null ? verdict : spirit.taskFailureReason().orElse("причина не записана");
        if (success) {
            PlanCompletion.complete(board, ticket, targets.get(ticket.id()));
            if (ticket.boardCase() == BoardCase.TRANSFER_ITEM) {
                TransferNote.delivered(spirit, ticket, targets.get(ticket.id()));
            }
            switch (ticket.boardCase()) {
                case CAPTURE_ANIMAL -> animals.cycle(speciesFor(ticket)).captured(ticket.id());
                case BUILD_PEN -> completePen(ticket.id());
                case LURE_ANIMAL -> animals.cycle(speciesFor(ticket)).lured();
                case RESCUE_SPIRIT -> incidents.rescued(spirit, ticket);
                default -> {}
            }
        } else {
            board.fail(
                    ticket.id(),
                    stock,
                    verdict != null ? verdict : spirit.taskFailureReason().orElse(null));
            incidents.failed(spirit, ticket, targets.get(ticket.id()), why, board.now());
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
    }

    private void flushBlockNotes() {
        notices.blocks(board, journal, stock, board.now());
    }

    private Species speciesFor(BoardTicket ticket) {
        TicketTarget target = targets.get(ticket.id());
        if (target != null && target.species() != null) {
            return target.species();
        }
        Capture capture = captures.get(ticket.id());
        return capture == null ? SpeciesIds.of(ticket.id()) : capture.species();
    }

    private StockParts refreshStock(ServerLevel level, List<SpiritEntity> spirits) {
        StockReader.Snapshot snapshot = stockReader.collect(level, spirits, gateCenters(spirits));
        stockBlockReads += snapshot.blockReads();
        stockContainers += snapshot.containers();
        stock = snapshot.parts().total();
        return snapshot.parts();
    }

    private void issue(ServerLevel level, List<SpiritEntity> spirits, long now) {
        List<BoardTicket> claimable = DispatchRound.order(board.claimable(stock, now));
        transferWait.retain(board);
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
                    TransferTicket.restore(
                            ticket, targets.getOrDefault(ticket.id(), TicketTarget.at(SettlementHome.of(spirits)))),
                    spirit);
            Optional<TicketTarget> fitted = fitTransfer(ticket, target, spirit);
            if (fitted.isEmpty()) {
                dropEmptyTransfer(ticket, target, spirit);
                continue;
            }
            target = fitted.orElseThrow();
            if (TransferWatch.ensureTransfers(level, this, spirits, stock, ticket, spirit, now)) {
                transferWait.await(board, crafters, ticket, spirit, spirits, round);
                continue;
            }
            TaskNode plan;
            try {
                plan = JobLaunch.plan(ticket, target);
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
                JobLaunch.start(board, cards, spirit, ticket.id(), plan);
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
                notices.assigned(board, ticket, target, pick.whyChosen(ticket, spirit, crafters));
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
        return targets.getOrDefault(ticketId, TicketTarget.at(SettlementHome.of(spirits)));
    }

    boolean hasTarget(String ticketId) {
        return targets.containsKey(ticketId);
    }

    BlockCoord targetPoint(String ticketId, List<SpiritEntity> spirits) {
        TicketTarget target = targets.get(ticketId);
        return target != null ? target.target() : SettlementHome.of(spirits);
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
            BlockCoord center = target != null ? target.target() : SettlementHome.of(spirits);
            if (!centers.contains(center)) {
                centers.add(center);
            }
        }
        if (centers.isEmpty()) {
            centers.add(SettlementHome.of(spirits));
        }
        return centers;
    }

    private void ensureSupply(ServerLevel level, List<SpiritEntity> spirits, StockView stock, long now) {
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
        BlockCoord home = SettlementHome.of(spirits);
        for (SupplyGate.Request request : decision.supply()) {
            SupplyTickets.issue(level, this, targets, home, request, BoardCase.SUPPLY_SITE, now);
        }
        for (SupplyGate.Request request : decision.gather()) {
            SupplyTickets.issue(level, this, targets, home, request, BoardCase.GATHER_MATERIALS, now);
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
}
