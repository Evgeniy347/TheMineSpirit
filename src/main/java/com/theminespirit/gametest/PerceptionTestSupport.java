package com.theminespirit.gametest;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

final class PerceptionTestSupport {
    static final int SETTLE_TICKS = 1;
    static final int TEST_TIMEOUT_TICKS = 200;

    private PerceptionTestSupport() {}

    static SpiritEntity spawnSpirit(GameTestHelper helper, int x, int y, int z) {
        return helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(x, y, z));
    }

    static SpiritRun launch(GameTestHelper helper, SpiritEntity spirit, String script, Map<String, Object> params) {
        return SpiritRun.launch(helper, spirit, new ScriptCall(script, params), SETTLE_TICKS);
    }

    static SpiritRun launch(GameTestHelper helper, SpiritEntity spirit, TaskNode plan) {
        return SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);
    }

    static BlockCoord coordOf(GameTestHelper helper, int x, int y, int z) {
        BlockPos absolute = helper.absolutePos(new BlockPos(x, y, z));
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }

    static void assertSucceeded(GameTestHelper helper, SpiritEntity spirit, SpiritRun run, String script) {
        run.assertStarted();
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                script + " обязан завершиться успехом, статус: "
                        + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", тиков духа "
                        + run.spiritTicks() + ", причина: "
                        + spirit.taskFailureReason().orElse("нет"));
    }
}
