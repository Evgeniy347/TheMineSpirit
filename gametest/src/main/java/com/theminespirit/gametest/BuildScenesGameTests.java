package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildScenesGameTests {
    private BuildScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_build_arena", timeoutTicks = 20403)
    public static void buildScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                PresetBuildGameTests.class,
                ShelterBuildGameTests.class,
                WarehouseGameTests.class);
    }
}
