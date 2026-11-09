package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.mining.SoilLayer;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class SoilQuarry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StateChangeLog CHANGES = new StateChangeLog();
    private static final int RADIUS = 64;
    private static final int STEP = 2;

    private SoilQuarry() {}

    record Result(@Nullable BlockCoord at, int distance, int inside, int notTurf, int closed) {
        Optional<BlockCoord> point() {
            return Optional.ofNullable(at);
        }
    }

    static Result find(ServerLevel level, BlockCoord home) {
        StageSite site = SettlementPlotStore.liveSite(level).orElse(null);
        BlockCoord best = null;
        int bestDistance = Integer.MAX_VALUE;
        int inside = 0;
        int notTurf = 0;
        int closed = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx += STEP) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += STEP) {
                int squared = dx * dx + dz * dz;
                if (squared >= bestDistance) {
                    continue;
                }
                int x = home.x() + dx;
                int z = home.z() + dz;
                if (site != null && site.inside(x, z)) {
                    inside++;
                    continue;
                }
                BlockPos probe = new BlockPos(x, home.y(), z);
                if (!level.isLoaded(probe)) {
                    closed++;
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos pos = new BlockPos(x, y, z);
                if (!takeable(level, pos)) {
                    notTurf++;
                    continue;
                }
                if (site != null
                        && (site.protectionCell(x, y, z) || site.gatherForbidden(new BlockCoord(x, y, z), 0))) {
                    inside++;
                    continue;
                }
                best = new BlockCoord(x, y, z);
                bestDistance = squared;
            }
        }
        Result result =
                new Result(best, best == null ? 0 : (int) Math.round(Math.sqrt(bestDistance)), inside, notTurf, closed);
        if (best == null && CHANGES.changed("soil", result.toString())) {
            LOGGER.info(
                    "[soil] источника земли нет: дёрна вне участка не найдено (в участке {}, не дёрн {},"
                            + " чанк не загружен {})",
                    inside,
                    notTurf,
                    closed);
        }
        return result;
    }

    private static boolean takeable(ServerLevel level, BlockPos pos) {
        SoilLayer.Ground ground = ground(level.getBlockState(pos));
        if (ground != SoilLayer.Ground.TURF) {
            return false;
        }
        return SoilLayer.takeable(new SoilLayer.Cell(
                pos.getY(),
                ground,
                ground(level.getBlockState(pos.above())),
                ground(level.getBlockState(pos.below())),
                OptionalInt.empty()));
    }

    private static SoilLayer.Ground ground(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
            return SoilLayer.Ground.TURF;
        }
        if (state.is(BlockTags.DIRT)) {
            return SoilLayer.Ground.SOIL;
        }
        if (state.isAir() || state.canBeReplaced()) {
            return SoilLayer.Ground.OPEN;
        }
        return SoilLayer.Ground.OTHER;
    }
}
