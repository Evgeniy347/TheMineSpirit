package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoordinatorScenesGameTests {
    private CoordinatorScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_coordinator_arena", timeoutTicks = 2914)
    public static void coordinatorScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("arena"), CoordinatorGameTests.class, CoordinatorScaleGameTests.class);
    }
}
