package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftScenesGameTests {
    private CraftScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_craft_arena", timeoutTicks = 11613)
    public static void craftScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                CraftGameTests.class,
                CraftScaleGameTests.class,
                FirstToolsGameTests.class,
                RecipeKnowledgeGameTests.class,
                RepairGameTests.class,
                StationGameTests.class);
    }
}
