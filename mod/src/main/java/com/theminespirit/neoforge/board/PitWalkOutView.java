package com.theminespirit.neoforge.board;

import com.theminespirit.core.movement.BlockKind;
import com.theminespirit.core.movement.PitExitPlanner;
import com.theminespirit.core.movement.PitWalkOut;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public final class PitWalkOutView implements PitWalkOut.World {
    private final Level level;
    private final PitExitPlanner.World cells;

    public PitWalkOutView(Level level) {
        this.level = Objects.requireNonNull(level, "Мир не может быть null: по нему ищется пеший выход");
        this.cells = new PitWorldView(level);
    }

    @Override
    public OptionalInt groundY(int x, int z, int from) {
        return PitExitPlanner.groundY(x, z, from, cells);
    }

    @Override
    public boolean clear(int x, int y, int z) {
        BlockKind kind = cells.at(new BlockCoord(x, y, z));
        return kind == BlockKind.AIR || kind == BlockKind.WATER;
    }

    @Override
    public int surfaceY(int x, int z) {
        BlockPos pos = new BlockPos(x, 0, z);
        if (!level.isLoaded(pos)) {
            return Integer.MAX_VALUE;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }
}
