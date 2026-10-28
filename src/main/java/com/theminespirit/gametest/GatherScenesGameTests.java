package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GatherScenesGameTests {
    private GatherScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_gather_arena", timeoutTicks = 2003)
    public static void gatherScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                AcquireGameTests.class,
                FullCycleGameTests.class,
                MineVeinDurabilityGameTests.class);
    }
}
