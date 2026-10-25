package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerceptionScenesGameTests {
    private PerceptionScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_perception_arena", timeoutTicks = 7337)
    public static void perceptionScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena", "empty"),
                BlockLocatorGameTests.class,
                MemoryGameTests.class,
                PerceptionGameTests.class,
                PerceptionLocateGameTests.class,
                PerceptionLookGameTests.class,
                PerceptionScaleGameTests.class,
                ScanGameTests.class);
    }
}
