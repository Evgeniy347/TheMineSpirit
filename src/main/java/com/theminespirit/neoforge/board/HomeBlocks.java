package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Есть ли у точки (дом, место задания) нужный блок — станция, на которой скрипт будет работать. Гейт до выдачи:
 * задание без станции рядом невыполнимо (INC-24, INC-26). Обход — круг радиуса по горизонтали и полоса по высоте,
 * только загруженные чанки; вызывается редко (когда гейт вообще нужен), не каждый тик.
 */
final class HomeBlocks {
    // Станции стоят на уровне дома; ниже и выше — шахта и крыши.
    static final int STATION_DY = 4;

    private HomeBlocks() {}

    static boolean near(ServerLevel level, BlockCoord center, int radius, Predicate<BlockState> match) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                for (int dy = -STATION_DY; dy <= STATION_DY; dy++) {
                    BlockPos check = new BlockPos(center.x() + dx, center.y() + dy, center.z() + dz);
                    if (level.isLoaded(check) && match.test(level.getBlockState(check))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
