package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BlockSight;
import com.theminespirit.core.mining.MineObstruction;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.Point;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class MineObstacleClear {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final Level level;
    private final BlockBreaker breaker;
    private final Set<BlockCoord> tried = new HashSet<>();

    MineObstacleClear(SpiritEntity spirit, Level level, BlockBreaker breaker) {
        this.spirit = spirit;
        this.level = level;
        this.breaker = breaker;
    }

    boolean clear(BlockCoord cell) {
        Vec3 eye = spirit.getEyePosition();
        Optional<BlockCoord> blocker =
                BlockSight.firstBlocker(new Point(eye.x, eye.y, eye.z), cell, new LoadedTerrain(level), coord -> false);
        if (blocker.isEmpty()) {
            return false;
        }
        BlockCoord at = blocker.orElseThrow();
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        if (!MineObstruction.clearable(BlockStates.idOf(level.getBlockState(pos))) || !tried.add(at)) {
            return false;
        }
        LOGGER.info("Шахта: дух {} убирает помеху спуску {} перед клеткой {}", spirit.getStringUUID(), at, cell);
        return breaker.beginExcavate(pos) instanceof BlockBreaker.Started;
    }
}
