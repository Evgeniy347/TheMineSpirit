package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Укрупнённые сцены механики «ферма»: по сцене на шаблон, шаги — методы {@link SceneStep} классов механики. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarmScenesGameTests {
    private FarmScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_farm_arena", timeoutTicks = 14447)
    public static void farmScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("arena", "empty"), FarmGameTests.class);
    }
}
