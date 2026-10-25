package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FullCycleScenesGameTests {
    private FullCycleScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_fullcycle_arena", timeoutTicks = 1643)
    public static void fullCycleScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("arena"), FullCycleGameTests.class);
    }
}
