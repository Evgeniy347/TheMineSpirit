package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MineScenesGameTests {
    private MineScenesGameTests() {}

    @GameTest(template = "mine_arena", batch = "scene_mine_mine_arena", timeoutTicks = 8805)
    public static void mineScene_MineArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("mine_arena"),
                DigOutGameTests.class,
                MineBuildGameTests.class,
                MineLevelsGameTests.class,
                SafeMineGameTests.class);
    }
}
