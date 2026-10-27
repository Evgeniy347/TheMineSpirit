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

    @GameTest(template = "arena", batch = "scene_gather_arena", timeoutTicks = 38837)
    public static void gatherScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                AcquireBrainGameTests.class,
                AcquireGameTests.class,
                BlockBreakerGameTests.class,
                CaptureGameTests.class,
                ClearGameTests.class,
                CollectDropsGameTests.class,
                DigGameTests.class,
                FillPitGameTests.class,
                GatherIronOreGameTests.class,
                NoDropGameTests.class,
                UseBlockGameTests.class);
    }

    @GameTest(template = "mine_arena", batch = "scene_gather_mine_arena", timeoutTicks = 5203)
    public static void gatherScene_MineArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("mine_arena"), DigOutGameTests.class);
    }
}
