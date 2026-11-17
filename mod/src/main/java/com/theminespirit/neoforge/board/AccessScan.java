package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BlockedCells;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.Walkways;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.PathBlocked;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class AccessScan {
    static final String EVENT = "access_blocked";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CACHE_TICKS = 600;
    private static final List<String> KEEP = List.of(
            "chest",
            "barrel",
            "crafting_table",
            "furnace",
            "smoker",
            "_door",
            "_bed",
            "anvil",
            "fence",
            "_gate",
            "torch",
            "lantern",
            "ladder",
            "shulker");
    private static final Predicate<String> KEPT = id -> KEEP.stream().anyMatch(id::contains);
    private static final Set<String> SOFT_GROUND = Set.of(
            "minecraft:dirt",
            "minecraft:grass_block",
            "minecraft:coarse_dirt",
            "minecraft:rooted_dirt",
            "minecraft:podzol",
            "minecraft:mycelium",
            "minecraft:dirt_path",
            "minecraft:farmland",
            "minecraft:sand",
            "minecraft:red_sand",
            "minecraft:gravel",
            "minecraft:clay",
            "minecraft:snow_block",
            "minecraft:moss_block");
    private static final Map<ServerLevel, Cached> LAST = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    record Cut(List<BlockedCells.Mark> chest, List<BlockedCells.Mark> path) {
        List<BlockedCells.Mark> cells() {
            List<BlockedCells.Mark> all = new ArrayList<>(chest.size() + path.size());
            all.addAll(chest);
            all.addAll(path);
            return List.copyOf(all);
        }

        boolean chestBlocked() {
            return !chest.isEmpty();
        }
    }

    private record Cached(long bucket, Cut cut) {}

    private AccessScan() {}

    static Cut cut(ServerLevel level, BoardService service, StageSite site, BlockCoord home) {
        synchronized (LOCK) {
            long bucket = level.getGameTime() / CACHE_TICKS;
            Cached before = LAST.get(level);
            if (before != null && before.bucket() == bucket) {
                return before.cut();
            }
            Cut cut = scan(level, service, site, home);
            LAST.put(level, new Cached(bucket, cut));
            return cut;
        }
    }

    static boolean blocked(ServerLevel level, BoardService service, StageSite site, BlockCoord home) {
        return !cut(level, service, site, home).cells().isEmpty();
    }

    private static Cut scan(ServerLevel level, BoardService service, StageSite site, BlockCoord home) {
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        if (town.isEmpty()) {
            return new Cut(List.of(), List.of());
        }
        long started = System.nanoTime();
        List<BlockedCells.Mark> chest = new ArrayList<>();
        for (BlockedCells.Mark mark : ChestOpenScan.marks(level)) {
            if (!kept(level, mark.cell()) && demolishersMayBreak(level, site, mark.cell())) {
                chest.add(mark);
            }
        }
        List<BlockedCells.Mark> path = new ArrayList<>();
        for (BlockCoord cell : clearance(level, service, site, town.orElseThrow(), home)) {
            if (demolishersMayBreak(level, site, cell)) {
                path.add(new BlockedCells.Mark(cell, 0));
            }
        }
        for (BlockedCells.Mark mark : PathBlocked.marks(level)) {
            if (soft(level, mark.cell()) && demolishersMayBreak(level, site, mark.cell())) {
                path.add(mark);
            }
        }
        Set<BlockCoord> seen = new HashSet<>();
        List<BlockedCells.Mark> chestMarks = unique(chest, seen);
        List<BlockedCells.Mark> pathMarks = unique(path, seen);
        if (!chestMarks.isEmpty()) {
            LOGGER.info(
                    "[access] склад: сундук закрыт сверху, на снос {} блоков: {}",
                    chestMarks.size(),
                    chestMarks.get(0).cell());
        }
        if (!pathMarks.isEmpty()) {
            LOGGER.info(
                    "[access] проход: срезать {} клеток за {} мс — тропы плана и преграды от духов",
                    pathMarks.size(),
                    (System.nanoTime() - started) / 1_000_000L);
        }
        return new Cut(chestMarks, pathMarks);
    }

    private static List<BlockCoord> clearance(
            ServerLevel level, BoardService service, StageSite site, TownPlan town, BlockCoord home) {
        return Walkways.clearance(
                        WorldAreaScan.view(level, site),
                        KEPT,
                        Walkways.of(town, SettlementPlotStore.currentStage(service)),
                        town.anchor().y())
                .stream()
                .sorted(Comparator.comparingInt(
                                (BlockCoord cell) -> Math.abs(cell.x() - home.x()) + Math.abs(cell.z() - home.z()))
                        .thenComparing(Comparator.comparingInt(BlockCoord::y).reversed()))
                .toList();
    }

    private static List<BlockedCells.Mark> unique(List<BlockedCells.Mark> marks, Set<BlockCoord> seen) {
        List<BlockedCells.Mark> kept = new ArrayList<>(marks.size());
        for (BlockedCells.Mark mark : marks) {
            if (seen.add(mark.cell())) {
                kept.add(mark);
            }
        }
        return List.copyOf(kept);
    }

    private static boolean demolishersMayBreak(ServerLevel level, StageSite site, BlockCoord cell) {
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        return PlotProtection.demolishersMayBreak(site, cell, PlotCellStructure.isStructure(level, pos));
    }

    private static boolean kept(ServerLevel level, BlockCoord cell) {
        BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
        return state.isAir() || KEPT.test(BlockStates.idOf(state));
    }

    private static boolean soft(ServerLevel level, BlockCoord cell) {
        BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
        return !state.isAir() && SOFT_GROUND.contains(BlockStates.idOf(state));
    }
}
