package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardScenesGameTests {
    private BoardScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_board_arena", timeoutTicks = 11380)
    public static void boardScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena", "empty"),
                BoardAnimalGameTests.class,
                BoardCraterGameTests.class,
                BoardDispatchGameTests.class,
                BoardFenceGameTests.class,
                BoardGameTests.class,
                BoardLeftOreGameTests.class,
                BoardPreemptGameTests.class,
                BoardResetGameTests.class,
                BoardScaleGameTests.class,
                BoardSupplyGateGameTests.class,
                BoardToolStockGameTests.class,
                BoardV2GameTests.class,
                BoardV2ScaleGameTests.class,
                BoardViewGameTests.class,
                FirstStepsRemainderGameTests.class,
                OverflowBoardGameTests.class);
    }
}
