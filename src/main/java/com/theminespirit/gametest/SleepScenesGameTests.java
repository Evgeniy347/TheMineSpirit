package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SleepScenesGameTests {
    private SleepScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_sleep_arena", timeoutTicks = 2311)
    public static void sleepScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("arena"), CampGameTests.class, SleepGameTests.class);
    }
}
