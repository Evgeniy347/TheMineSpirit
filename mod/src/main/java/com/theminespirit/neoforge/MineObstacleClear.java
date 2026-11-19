package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.api.BlockSight;
import com.theminespirit.core.mining.api.MineObstruction;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.api.Point;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class MineObstacleClear {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Entity spirit;
    private final Level level;
    private final BlockBreaker breaker;
    private @Nullable BlockCoord breaking;

    MineObstacleClear(Entity spirit, Level level, BlockBreaker breaker) {
        this.spirit = spirit;
        this.level = level;
        this.breaker = breaker;
    }

    boolean clear(BlockCoord cell) {
        Vec3 eye = spirit.getEyePosition();
        Optional<BlockCoord> blocker =
                BlockSight.firstBlocker(new Point(eye.x, eye.y, eye.z), cell, new LoadedTerrain(level), coord -> false);
        if (blocker.isEmpty()) {
            breaking = null;
            return false;
        }
        BlockCoord at = blocker.orElseThrow();
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        if (!MineObstruction.clearable(BlockStates.idOf(level.getBlockState(pos)))) {
            breaking = null;
            return false;
        }
        if (MineObstruction.underWork(breaking, at)) {
            return true;
        }
        if (breaker.beginExcavate(pos) instanceof BlockBreaker.Started) {
            breaking = at;
            LOGGER.info("Шахта: дух {} убирает помеху спуску {} перед клеткой {}", spirit.getStringUUID(), at, cell);
            return true;
        }
        return false;
    }
}
