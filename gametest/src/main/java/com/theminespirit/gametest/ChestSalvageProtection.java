package com.theminespirit.gametest;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.ChestDemolition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

final class ChestSalvageProtection {
    private ChestSalvageProtection() {}

    static void check(GameTestHelper helper, SpiritEntity spirit, BlockPos left, BlockPos right) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(
                ChestDemolition.removable(level, coord(left)),
                "Метка игрока на второй половине не защищает пару от сноса: сундук уходит в задание переноса вещей, "
                        + "иначе ремонт плановой клетки падает каждый цикл, а содержимое остаётся запертым");
    }

    static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
