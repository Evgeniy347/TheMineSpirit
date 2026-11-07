package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DemolitionKey;
import com.theminespirit.core.board.DemolitionScope;
import com.theminespirit.core.mining.UnreachableCells;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.FloatingCleanup;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TreeSupport;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SettlementChestStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import org.slf4j.Logger;

final class DemolitionCollect {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SHOWN = 4;
    private static final int PROTECTED_SHOWN = 10;

    private DemolitionCollect() {}

    record Ctx(
            ServerLevel level,
            BoardService service,
            StageSite site,
            PlotPlanBlocks.Plan plan,
            boolean yardReady,
            List<FloatingBlocks.Finding> findings,
            UnreachableCells unreachable) {}

    private record Strays(int protectedSkipped, int unreachableSkipped) {}

    private static final class Found {
        private final List<BlockCoord> remove;
        private final List<String> notes;
        private int excluded;
        private int reachableShown;
        private int protectedSkipped;
        private int unreachableSkipped;
        private DemolitionTickets.Placement placement;
        private List<BlockCoord> treeCells = List.of();

        private Found(List<BlockCoord> remove, List<String> notes, int protectedSkipped, int unreachableSkipped) {
            this.remove = remove;
            this.notes = notes;
            this.protectedSkipped = protectedSkipped;
            this.unreachableSkipped = unreachableSkipped;
        }
    }

    static PlanDemolition.Collected collect(Ctx ctx, DemolitionScope scope) {
        List<BlockCoord> remove = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int protectedSkipped = 0;
        int unreachableSkipped = 0;
        if (scope != DemolitionScope.FLOATING) {
            Strays strays = strays(ctx, remove, notes);
            protectedSkipped = strays.protectedSkipped();
            unreachableSkipped = strays.unreachableSkipped();
        }
        Found found = new Found(remove, notes, protectedSkipped, unreachableSkipped);
        if (scope != DemolitionScope.STRAYS) {
            findings(ctx, found);
        }
        return new PlanDemolition.Collected(
                found.remove,
                List.copyOf(found.notes),
                found.excluded,
                found.unreachableSkipped,
                found.placement == null ? null : found.placement.stand(),
                found.placement == null ? null : found.placement.top(),
                found.placement == null ? null : found.placement.lowest(),
                found.treeCells);
    }

    private static void findings(Ctx ctx, Found found) {
        ServerLevel level = ctx.level();
        for (FloatingBlocks.Finding finding : ctx.findings()) {
            BlockCoord at = finding.at();
            boolean blocked = ctx.unreachable().excluded(at);
            BlockCoord treeStand = finding.kind() == FloatingBlocks.Kind.TREE
                    ? FloatingScan.standBeside(level, finding.cells())
                    : null;
            boolean reachable = !blocked
                    && (finding.kind() == FloatingBlocks.Kind.TREE
                            ? treeStand != null
                            : FloatingScan.reachable(level, at));
            FloatingCleanup.Action action = FloatingCleanup.decide(
                    finding, rooms(level, ctx.plan(), ctx.site(), ctx.yardReady(), finding, at, reachable));
            switch (action) {
                case SKIP_UNREACHABLE -> {
                    if (blocked) {
                        found.excluded++;
                    }
                    if (found.unreachableSkipped++ < SHOWN) {
                        found.notes.add(
                                at + " — " + (blocked ? "исключён: " : "рядом нет стоянки: ") + finding.reason());
                    }
                }
                case SKIP_PLANNED -> {
                    if (found.notes.size() < SHOWN) {
                        found.notes.add(at + " — забор плана ограды, не убираем");
                    }
                }
                case SKIP_PROTECTED -> {
                    if (found.protectedSkipped++ < PROTECTED_SHOWN) {
                        found.notes.add(at + " — постройка игрока, не трогаем: " + finding.reason());
                    }
                }
                case DRAIN_CHEST -> {
                    if (track(level, at)) {
                        found.notes.add("сундук " + at + " вне плана не пуст — содержимое уйдёт в сундуки двора");
                    }
                }
                case RELOCATE_STATION -> {
                    reopen(ctx.service(), finding.blockId());
                    found.notes.add("станция " + at + " нужна плану — снимаем и ставим заново по схеме двора");
                    found.remove.add(at);
                }
                case REMOVE -> {
                    if (finding.kind() == FloatingBlocks.Kind.TREE && found.placement != null) {
                        if (!DemolitionKey.withinStand(found.placement.stand(), finding.cells())) {
                            continue;
                        }
                        found.treeCells = merged(found.treeCells, finding.cells());
                        found.placement = DemolitionTickets.extend(found.placement, finding);
                    }
                    if (found.reachableShown++ < SHOWN) {
                        found.notes.add(at + " — " + finding.reason() + ": стоянка рядом есть — в уборку");
                    }
                    found.remove.addAll(finding.cells());
                    DemolitionGuard.claimForCleanup(level, ctx.plan(), finding);
                    if (finding.kind() == FloatingBlocks.Kind.TREE && found.placement == null) {
                        found.placement = DemolitionTickets.treePlacement(treeStand, finding);
                        found.treeCells = List.copyOf(finding.cells());
                    }
                }
                case SKIP_REGISTERED, SKIP_WAIT -> {}
            }
        }
    }

    private static Strays strays(Ctx ctx, List<BlockCoord> remove, List<String> notes) {
        Set<BlockCoord> floatingCells = new HashSet<>();
        ctx.findings().forEach(finding -> floatingCells.addAll(finding.cells()));
        PlanStrays.Result strays = PlanStrays.collect(ctx.level(), ctx.unreachable(), SHOWN);
        for (BlockCoord at : strays.remove()) {
            if (!floatingCells.contains(at)) {
                remove.add(at);
            }
        }
        notes.addAll(strays.notes());
        return new Strays(strays.protectedCount(), strays.unreachable());
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

    private static List<BlockCoord> merged(List<BlockCoord> current, List<BlockCoord> next) {
        Set<BlockCoord> cells = new HashSet<>(current);
        cells.addAll(next);
        return List.copyOf(cells);
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
