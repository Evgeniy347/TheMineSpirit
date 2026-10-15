package com.theminespirit.gametest;

import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.function.BooleanSupplier;
import net.minecraft.gametest.framework.GameTestHelper;

final class SpiritRun {
    private final GameTestHelper helper;
    private final SpiritEntity spirit;
    private final TaskNode call;
    private final int settleTicks;
    private final BooleanSupplier ready;
    private int startTickCount = -1;

    private SpiritRun(GameTestHelper helper, SpiritEntity spirit, TaskNode call, int settleTicks,
            BooleanSupplier ready) {
        this.helper = helper;
        this.spirit = spirit;
        this.call = call;
        this.settleTicks = settleTicks;
        this.ready = ready;
    }

    static SpiritRun launch(GameTestHelper helper, SpiritEntity spirit, TaskNode call, int settleTicks) {
        return launch(helper, spirit, call, settleTicks, () -> true);
    }

    // Условие ready — для состояния мира, которое готовится не за тики, а в другом потоке (свет): число тиков
    // ожидания там не гарантия, а гонка.
    static SpiritRun launch(GameTestHelper helper, SpiritEntity spirit, TaskNode call, int settleTicks,
            BooleanSupplier ready) {
        SpiritRun run = new SpiritRun(helper, spirit, call, settleTicks, ready);
        helper.onEachTick(run::startWhenSettled);
        return run;
    }

    boolean started() {
        return startTickCount >= 0;
    }

    void assertStarted() {
        helper.assertTrue(startTickCount >= 0,
                "дух ещё не начал тикать (чанк площадки не стал активным) или не выполнено условие запуска, "
                        + "задача не запущена: тик духа " + spirit.tickCount);
    }

    int spiritTicks() {
        return spirit.tickCount - startTickCount;
    }

    private void startWhenSettled() {
        if (startTickCount < 0 && spirit.tickCount >= settleTicks && ready.getAsBoolean()) {
            startTickCount = spirit.tickCount;
            spirit.startTask(call);
        }
    }
}
