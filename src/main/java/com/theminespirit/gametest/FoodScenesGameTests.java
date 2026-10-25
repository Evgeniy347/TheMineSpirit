package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Укрупнённые сцены механики «еда и рыбалка»: по сцене на шаблон, шаги — методы {@link SceneStep} классов механики. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoodScenesGameTests {
    private FoodScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_food_arena", timeoutTicks = 5316)
    public static void foodScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                FishingGameTests.class,
                FishingWatchGameTests.class,
                HungerGameTests.class,
                IntellectForageGameTests.class);
    }
}
