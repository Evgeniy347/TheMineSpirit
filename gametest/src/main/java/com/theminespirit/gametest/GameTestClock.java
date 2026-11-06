package com.theminespirit.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;

final class GameTestClock {
    private static final long PERIOD = 24_000;

    private GameTestClock() {}

    static void align(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (!(level.getLevelData() instanceof ServerLevelData data)) {
            throw new IllegalStateException("Данные уровня " + level.dimension().location()
                    + " не серверные: время игры не выровнять, а с ним и фазы периодических проверок сцены");
        }
        long now = level.getGameTime();
        long aligned = (now / PERIOD + 1) * PERIOD;
        helper.testInfo.startTick += aligned - now;
        data.setGameTime(aligned);
    }
}
