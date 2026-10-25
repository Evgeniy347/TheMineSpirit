package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Укрупнённые сцены механики «стройка»: по сцене на шаблон, шаги — методы {@link SceneStep} классов механики. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildScenesGameTests {
    private BuildScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_build_arena", timeoutTicks = 74153)
    public static void buildScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                BuildAuditGameTests.class,
                BuildFarmGameTests.class,
                BuildFenceShelterGameTests.class,
                BuildPaceGameTests.class,
                BuildRestartGameTests.class,
                BuildResultGameTests.class,
                BuildRetreatGameTests.class,
                BuildScaleGameTests.class,
                BuildingGameTests.class,
                LightAreaGameTests.class,
                PresetBuildGameTests.class,
                SchematicGameTests.class,
                ShelterBuildGameTests.class,
                WoolBedsGameTests.class);
    }

    @GameTest(template = "demo_arena", batch = "scene_build_demo_arena", timeoutTicks = 54610)
    public static void buildScene_DemoArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("demo_arena"),
                DemoBuildGameTests.class,
                DemoCityGameTests.class,
                DemoLayoutGameTests.class,
                DemoSpiritGameTests.class);
    }
}
