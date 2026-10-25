package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestScenesGameTests {
    private RestScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_rest_arena", timeoutTicks = 780443)
    public static void restScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena", "empty"),
                RestEventsGameTests.class,
                RestGameTests.class,
                RestLifecycleGameTests.class,
                RestPlanGameTests.class,
                RestScaleGameTests.class,
                RestTaskCancelGameTests.class,
                RestTaskGameTests.class,
                RestTaskScaleGameTests.class);
    }
}
