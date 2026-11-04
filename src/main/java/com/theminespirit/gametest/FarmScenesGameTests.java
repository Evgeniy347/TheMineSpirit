package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarmScenesGameTests {
    private FarmScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_farm_arena", timeoutTicks = 2953)
    public static void farmScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("arena"), FarmSafetyGameTests.class, HusbandryCullGameTests.class);
    }
}
