package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Укрупнённые сцены механики «животные»: по сцене на шаблон, шаги — методы {@link SceneStep} классов механики. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimalScenesGameTests {
    private AnimalScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_animal_arena", timeoutTicks = 8825)
    public static void animalScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                HiveGameTests.class,
                HusbandryCullGameTests.class,
                HusbandryGameTests.class,
                HusbandryProduceGameTests.class,
                HusbandryScaleGameTests.class);
    }
}
