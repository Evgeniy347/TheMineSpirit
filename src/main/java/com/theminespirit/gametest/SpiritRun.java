package com.theminespirit.gametest;

import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.gametest.framework.GameTestHelper;

final class SpiritRun {
    private final GameTestHelper helper;
    private final SpiritEntity spirit;
    private final TaskNode call;
    private final int settleTicks;
    private int startTickCount = -1;

    private SpiritRun(GameTestHelper helper, SpiritEntity spirit, TaskNode call, int settleTicks) {
        this.helper = helper;
        this.spirit = spirit;
        this.call = call;
        this.settleTicks = settleTicks;
    }

    static SpiritRun launch(GameTestHelper helper, SpiritEntity spirit, TaskNode call, int settleTicks) {
        SpiritRun run = new SpiritRun(helper, spirit, call, settleTicks);
        helper.onEachTick(run::startWhenSettled);
        return run;
    }

    void assertStarted() {
        helper.assertTrue(startTickCount >= 0,
                "дух ещё не начал тикать (чанк площадки не стал активным), задача не запущена: тик духа "
                        + spirit.tickCount);
    }

    int spiritTicks() {
        return spirit.tickCount - startTickCount;
    }

    private void startWhenSettled() {
        if (startTickCount < 0 && spirit.tickCount >= settleTicks) {
            startTickCount = spirit.tickCount;
            spirit.startTask(call);
        }
    }
}
