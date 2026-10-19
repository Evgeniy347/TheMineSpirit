package com.theminespirit.neoforge;

import com.theminespirit.core.mining.OreView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class LevelOreView implements OreView {
    private final Level level;
    private final Set<Block> ores;
    private int reads;

    public LevelOreView(Level level, Set<Block> ores) {
        this.level = Objects.requireNonNull(level, "Уровень не может быть null: по нему читаются блоки руды");
        this.ores = Set.copyOf(
                Objects.requireNonNull(ores, "Множество руды не может быть null: без него непонятно, что искать"));
    }

    @Override
    public boolean isOre(BlockCoord coord) {
        Objects.requireNonNull(coord, "Координата не может быть null: неизвестно, какую клетку проверять");
        reads++;
        return ores.contains(level.getBlockState(new BlockPos(coord.x(), coord.y(), coord.z()))
                .getBlock());
    }

    public int reads() {
        return reads;
    }
}
