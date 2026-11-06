package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.DemolitionKey;
import com.theminespirit.core.board.DemolitionRetry;
import com.theminespirit.core.board.StagePlans;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.board.SubmitGate;
import com.theminespirit.core.mining.UnreachableCells;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.FloatingCleanup;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TreeSupport;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.UnreachableCellsStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import org.slf4j.Logger;

final class PlanDemolition {
    static final String PREFIX = DemolitionKey.PREFIX;
    static final long COOLDOWN = 600;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SHOWN = 4;
    private static final int PROTECTED_SHOWN = 10;
    private static final SubmitGate SUBMITS = new SubmitGate();
    private static final StateChangeLog CHANGES = new StateChangeLog();
    private static final DemolitionRetry RETRIES = new DemolitionRetry();

    record Collected(
            List<BlockCoord> remove,
            List<String> notes,
            int excluded,
            int unreachable,
            BlockCoord stand,
            BlockCoord top,
            BlockCoord lowest,
            List<BlockCoord> treeCells) {}

    private PlanDemolition() {}

    static void tick(
            ServerLevel level,
            BoardService service,
            StageSite site,
            TownPlan town,
            long now,
            GraphPost post,
            String cause) {
        if (!service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1)) {
            return;
        }
        List<BoardTicket> open = DemolitionHistory.open(service);
        boolean lost = open.stream().anyMatch(ticket -> !service.hasTarget(ticket.id()));
        if (!lost && DemolitionHistory.busy(service, now)) {
            return;
        }
        String world = level.dimension().location().toString();
        boolean yardReady = service.board().ledger().has(BoardCase.STOCK_CHESTS, 1)
                && service.board().ledger().has(BoardCase.CRAFT_FURNACE, 1);
        PlotPlanBlocks.Plan plan = PlotPlanBlocks.of(site);
        List<FloatingBlocks.Finding> findings = FloatingScanCycle.find(level, site, town.region());
        Collected base = collect(level, service, site, plan, yardReady, findings, UnreachableCellsStore.of(level));
        Collected collected = FarmStray.withFarm(base, level, site, town, plan);
        List<BlockCoord> candidates = collected.treeCells().isEmpty() ? collected.remove() : collected.treeCells();
        List<BlockCoord> stray = DemolitionKey.piece(candidates.stream()
                .distinct()
                .sorted(Comparator.comparingInt(BlockCoord::y).reversed().thenComparing(Comparator.naturalOrder()))
                .toList());
        List<PlannedBlock> blocks = stray.stream()
                .map(cell -> new PlannedBlock(cell, StagePlans.AIR))
                .toList();
        DemolitionTickets.Placement placement =
                new DemolitionTickets.Placement(collected.stand(), collected.top(), collected.lowest());
        if (lost
                && DemolitionRestore.restoreLost(
                        level, service, post, site, open, new DemolitionTickets.Batch(stray, blocks, placement))) {
            return;
        }
        if (stray.isEmpty()) {
            DemolitionTickets.warnExcluded(world, collected.excluded(), collected.notes(), now);
            return;
        }
        String id = DemolitionTickets.idFor(stray);
        if (waiting(world, id, collected, now)) {
            return;
        }
        if (!SUBMITS.allow("demolish:" + world, RETRIES.signature(id))) {
            DemolitionTickets.warnExcluded(world, collected.excluded(), collected.notes(), now);
            if (CHANGES.changed("tidy-wait:" + world, id)) {
                LOGGER.info(
                        "[tidy] {}: набор уборки не изменился — повторно не выдаём (первый {}); {}",
                        id,
                        stray.get(0),
                        collected.notes());
            }
            return;
        }
        String previous = DemolitionHistory.previous(service);
        if (!DemolitionTickets.submit(post, site, blocks, id, placement, cause)) {
            RETRIES.submitted(id, now, COOLDOWN);
            return;
        }
        RETRIES.submitted(id, now, COOLDOWN);
        DemolitionHistory.supersede(level, service, id);
        if (CHANGES.changed("tidy:" + world, id)) {
            DemolitionTickets.logTidy(id, now, cause, stray, findings, collected, previous);
        }
        service.journal()
                .recordEntry(BoardLog.note(
                        id,
                        BoardCase.DISMANTLE_SHELTER,
                        now,
                        List.of("blocks=" + stray.size(), "first=" + stray.get(0)),
                        null,
                        ConfigStamp.current()));
    }

    static int shown() {
        return SHOWN;
    }

    private static boolean waiting(String world, String id, Collected collected, long now) {
        if (!RETRIES.waiting(id, now)) {
            return false;
        }
        DemolitionTickets.warnExcluded(world, collected.excluded(), collected.notes(), now);
        if (CHANGES.changed("tidy-retry:" + world, id + "@" + RETRIES.until(id))) {
            LOGGER.info(
                    "[tidy] {}: набор уборки ждёт повтора до тика {} — прошлая уборка закрылась без факта "
                            + "мира; {}",
                    id,
                    RETRIES.until(id),
                    collected.notes());
        }
        return true;
    }

    private static Collected collect(
            ServerLevel level,
            BoardService service,
            StageSite site,
            PlotPlanBlocks.Plan plan,
            boolean yardReady,
            List<FloatingBlocks.Finding> findings,
            UnreachableCells unreachableCells) {
        List<BlockCoord> remove = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int excluded = 0;
        int reachableShown = 0;
        DemolitionTickets.Placement placement = null;
        List<BlockCoord> treeCells = List.of();
        PlanStrays.Result strays = PlanStrays.collect(level, unreachableCells, SHOWN);
        remove.addAll(strays.remove());
        notes.addAll(strays.notes());
        int protectedSkipped = strays.protectedCount();
        int unreachableSkipped = strays.unreachable();
        for (FloatingBlocks.Finding finding : findings) {
            BlockCoord at = finding.at();
            boolean blocked = unreachableCells.excluded(at);
            BlockCoord treeStand = finding.kind() == FloatingBlocks.Kind.TREE
                    ? FloatingScan.standBeside(level, finding.cells())
                    : null;
            boolean reachable = !blocked
                    && (finding.kind() == FloatingBlocks.Kind.TREE
                            ? treeStand != null
                            : FloatingScan.reachable(level, at));
            FloatingCleanup.Action action =
                    FloatingCleanup.decide(finding, rooms(level, plan, site, yardReady, finding, at, reachable));
            switch (action) {
                case SKIP_UNREACHABLE -> {
                    if (blocked) {
                        excluded++;
                    }
                    if (unreachableSkipped++ < SHOWN) {
                        notes.add(at + " — " + (blocked ? "исключён: " : "рядом нет стоянки: ") + finding.reason());
                    }
                }
                case SKIP_PLANNED -> {
                    if (notes.size() < SHOWN) {
                        notes.add(at + " — забор плана ограды, не убираем");
                    }
                }
                case SKIP_PROTECTED -> {
                    if (protectedSkipped++ < PROTECTED_SHOWN) {
                        notes.add(at + " — постройка игрока, не трогаем: " + finding.reason());
                    }
                }
                case DRAIN_CHEST -> {
                    if (track(level, at)) {
                        notes.add("сундук " + at + " вне плана не пуст — содержимое уйдёт в сундуки двора");
                    }
                }
                case RELOCATE_STATION -> {
                    reopen(service, finding.blockId());
                    notes.add("станция " + at + " нужна плану — снимаем и ставим заново по схеме двора");
                    remove.add(at);
                }
                case REMOVE -> {
                    if (finding.kind() == FloatingBlocks.Kind.TREE && placement != null) {
                        continue;
                    }
                    if (reachableShown++ < SHOWN) {
                        notes.add(at + " — " + finding.reason() + ": стоянка рядом есть — в уборку");
                    }
                    remove.addAll(finding.cells());
                    DemolitionGuard.claimForCleanup(level, plan, finding);
                    if (finding.kind() == FloatingBlocks.Kind.TREE) {
                        placement = DemolitionTickets.treePlacement(treeStand, finding);
                        treeCells = List.copyOf(finding.cells());
                    }
                }
                case SKIP_REGISTERED, SKIP_WAIT -> {}
            }
        }
        return new Collected(
                remove,
                List.copyOf(notes),
                excluded,
                unreachableSkipped,
                placement == null ? null : placement.stand(),
                placement == null ? null : placement.top(),
                placement == null ? null : placement.lowest(),
                treeCells);
    }

    private static FloatingCleanup.Rooms rooms(
            ServerLevel level,
            PlotPlanBlocks.Plan plan,
            StageSite site,
            boolean yardReady,
            FloatingBlocks.Finding finding,
            BlockCoord at,
            boolean reachable) {
        boolean plannedFence =
                finding.kind() == FloatingBlocks.Kind.FENCE && site.fenceStrip().contains(at.x(), at.z());
        boolean needed = finding.kind() == FloatingBlocks.Kind.STATION && needed(level, plan, finding.blockId());
        boolean stump = finding.kind() == FloatingBlocks.Kind.STUMP
                && TreeSupport.isolatedLog(
                        at, cell -> level.getBlockState(pos(cell)).is(BlockTags.LOGS));
        return new FloatingCleanup.Rooms(
                yardReady,
                DemolitionGuard.protectedForDemolish(level, at, finding.blockId(), plan, stump),
                empty(level, at),
                needed,
                reachable,
                plannedFence);
    }

    private static boolean empty(ServerLevel level, BlockCoord at) {
        return !(level.getBlockEntity(pos(at)) instanceof Container container) || container.isEmpty();
    }

    private static boolean track(ServerLevel level, BlockCoord at) {
        return SettlementChestStore.get(level).track(pos(at));
    }

    private static boolean needed(ServerLevel level, PlotPlanBlocks.Plan plan, String blockId) {
        List<BlockCoord> expected = plan.expected(blockId);
        if (expected.isEmpty()) {
            return false;
        }
        String path = FloatingBlocks.pathOf(blockId);
        return expected.stream()
                .noneMatch(at -> path.equals(FloatingBlocks.pathOf(BlockStates.idOf(level.getBlockState(pos(at))))));
    }

    private static void reopen(BoardService service, String blockId) {
        BoardCase wanted =
                "furnace".equals(FloatingBlocks.pathOf(blockId)) ? BoardCase.CRAFT_FURNACE : BoardCase.STOCK_CHESTS;
        for (BoardTicket ticket : List.copyOf(service.board().all())) {
            if (ticket.boardCase() != wanted) {
                continue;
            }
            if (service.board().statusOf(ticket.id()).orElse(null) == BoardStatus.DONE) {
                service.reopen(ticket.id());
                LOGGER.info("[tidy] задание {} возвращено в доску: станция встанет по плану", ticket.id());
            }
        }
    }

    private static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
