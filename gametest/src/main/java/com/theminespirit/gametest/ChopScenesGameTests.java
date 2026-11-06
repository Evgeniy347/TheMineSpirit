package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChopScenesGameTests {
    private ChopScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_chop_arena", timeoutTicks = 801)
    public static void chopScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("arena"), ChopReplantGameTests.class);
    }

    @GameTest(template = "mine_arena", batch = "scene_chop_mine_arena", timeoutTicks = 1801)
    public static void chopScene_MineArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("mine_arena"), ChopBigTreesGameTests.class);
    }
}
