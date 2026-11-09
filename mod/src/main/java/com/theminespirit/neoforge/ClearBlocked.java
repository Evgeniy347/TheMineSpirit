package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.mining.UnreachableCells;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class ClearBlocked {
    record Sweep(
            List<ClearSummary.Leftover> leftover, int unsafe, int noStand, int unreachable, int excluded, int planned) {
        int skipped() {
            return unsafe + noStand + unreachable + excluded + planned;
        }
    }

    private final Level level;
    private final UnreachableCells cells;
    private final String area;

    ClearBlocked(Level level, BuildBox box) {
        this.level = level;
        this.cells = UnreachableCellsStore.of(level);
        this.area = box.minX() + "," + box.minY() + "," + box.minZ() + ".." + box.maxX() + "," + box.maxY() + ","
                + box.maxZ();
    }

    void reached(BlockCoord cell) {
        cells.reached(cell);
    }

    Sweep dropExcluded(List<BlockCoord> remaining) {
        List<ClearSummary.Leftover> leftover = new ArrayList<>();
        int excluded = 0;
        for (int index = remaining.size() - 1; index >= 0; index--) {
            BlockCoord cell = remaining.get(index);
            if (!cells.excluded(cell)) {
                continue;
            }
            excluded++;
            leftover.add(new ClearSummary.Leftover(
                    cell, blockAt(cell), BreakSkipTally.Reason.NO_STAND.title(), cells.failureCount(cell)));
            remaining.remove(index);
        }
        return new Sweep(leftover, 0, 0, 0, excluded, 0);
    }

    Sweep dropPlanned(List<BlockCoord> remaining) {
        List<ClearSummary.Leftover> leftover = new ArrayList<>();
        int planned = 0;
        for (int index = remaining.size() - 1; index >= 0; index--) {
            BlockCoord cell = remaining.get(index);
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            BlockState state = level.getBlockState(pos);
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (!plannedFence(id, onFenceStrip(pos))) {
                continue;
            }
            planned++;
            leftover.add(new ClearSummary.Leftover(cell, blockAt(cell), "забор плана ограды", 0));
            remaining.remove(index);
        }
        return new Sweep(leftover, 0, 0, 0, 0, planned);
    }

    static boolean plannedFence(String blockId, boolean onStrip) {
        if (!onStrip) {
            return false;
        }
        String path = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
        return "fence".equals(path)
                || "fence_gate".equals(path)
                || path.endsWith("_fence")
                || path.endsWith("_fence_gate");
    }

    private boolean onFenceStrip(BlockPos pos) {
        return level instanceof ServerLevel server
                && SettlementPlotStore.liveSite(server)
                        .filter(site -> site.fenceStrip().contains(pos.getX(), pos.getZ()))
                        .isPresent();
    }

    Sweep exhausted(List<BlockCoord> remaining, LevelSafetyView safety, BlockCoord from) {
        List<ClearSummary.Leftover> leftover = new ArrayList<>();
        int unsafe = 0;
        int noStand = 0;
        int unreachable = 0;
        for (BlockCoord cell : remaining) {
            String reason;
            if (!(MiningSafety.canBreak(cell, safety) instanceof BreakSafety.Safe)) {
                unsafe++;
                reason = BreakSkipTally.Reason.UNBREAKABLE.title();
            } else {
                List<BlockCoord> stands = StandSpots.of(cell, safety, from);
                if (stands.isEmpty()) {
                    noStand++;
                    reason = BreakSkipTally.Reason.NO_STAND.title();
                } else {
                    unreachable++;
                    reason = unreachableReason(stands, from);
                }
            }
            leftover.add(new ClearSummary.Leftover(cell, blockAt(cell), reason, cells.noteFailure(cell)));
        }
        return new Sweep(leftover, unsafe, noStand, unreachable, 0, 0);
    }

    Sweep approachFailure(BlockCoord target, BlockCoord stand, String refusal) {
        String reason = "срыв подхода к стоянке " + stand + ":" + (refusal == null ? " без причины" : " " + refusal);
        ClearSummary.Leftover step =
                new ClearSummary.Leftover(target, blockAt(target), reason, cells.noteFailure(target));
        return new Sweep(List.of(step), 0, 0, 1, 0, 0);
    }

    private static String unreachableReason(List<BlockCoord> stands, BlockCoord from) {
        for (BlockCoord stand : stands) {
            if (!StandSpots.walkable(stand, from)) {
                return "стоянка " + stand + " на " + (stand.y() - from.y()) + " выше духа — не подняться";
            }
        }
        return "недостижимо";
    }

    void escalate(Logger logger, SpiritEntity spirit, String reason) {
        int attempts = cells.noteAttempt(area);
        if (cells.escalating(area)) {
            logger.warn(
                    "[clear] {}: участок {} не расчищается — попыток {}, причина: {}",
                    spirit.getName().getString(),
                    area,
                    attempts,
                    reason);
        }
    }

    void warnExcluded(Logger logger, SpiritEntity spirit, int excluded, List<ClearSummary.Leftover> leftover) {
        if (excluded == 0) {
            return;
        }
        logger.warn(
                "[clear] {}: участок {} не расчищается — исключено недостижимых {}: {}",
                spirit.getName().getString(),
                area,
                excluded,
                leftover);
    }

    String blockAt(BlockCoord cell) {
        return blockAt(level, cell);
    }

    static String blockAt(Level level, BlockCoord cell) {
        return BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                        .getBlock())
                .getPath();
    }
}
