package com.theminespirit.gametest;

import com.theminespirit.neoforge.SpiritEntity;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTestHelper;

final class PolicyScene {
    private final GameTestHelper helper;
    private final SpiritEntity spirit;
    private final int settleTicks;
    private final Runnable onStart;
    private final Function<SpiritEntity, String> invariant;
    private int startTick = -1;
    private float minHealth = Float.MAX_VALUE;

    private PolicyScene(
            GameTestHelper helper,
            SpiritEntity spirit,
            int settleTicks,
            Runnable onStart,
            Function<SpiritEntity, String> invariant) {
        this.helper = helper;
        this.spirit = spirit;
        this.settleTicks = settleTicks;
        this.onStart = onStart;
        this.invariant = invariant;
    }

    static PolicyScene launch(GameTestHelper helper, SpiritEntity spirit, int settleTicks, Runnable onStart) {
        return launch(helper, spirit, settleTicks, onStart, ignored -> null);
    }

    static PolicyScene launch(
            GameTestHelper helper,
            SpiritEntity spirit,
            int settleTicks,
            Runnable onStart,
            Function<SpiritEntity, String> invariant) {
        PolicyScene scene = new PolicyScene(helper, spirit, settleTicks, onStart, invariant);
        helper.onEachTick(scene::tick);
        return scene;
    }

    void assertStarted() {
        helper.assertTrue(startTick >= 0, "дух ещё не начал тикать, сцена не запущена: тик духа " + spirit.tickCount);
    }

    boolean started() {
        return startTick >= 0;
    }

    int ticks() {
        return spirit.tickCount - startTick;
    }

    float minHealth() {
        return minHealth;
    }

    private void tick() {
        if (startTick < 0 && spirit.tickCount >= settleTicks) {
            startTick = spirit.tickCount;
            onStart.run();
        }
        if (startTick < 0) {
            return;
        }
        minHealth = Math.min(minHealth, spirit.getHealth());
        String violation = invariant.apply(spirit);
        if (violation != null) {
            helper.fail(violation + " (тик духа " + ticks() + ")");
        }
    }
}
