package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MoveScenesGameTests {
    private MoveScenesGameTests() {}

    @GameTest(template = "big_arena", batch = "scene_move_big_arena", timeoutTicks = 2401)
    public static void moveScene_BigArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("big_arena"), HouseMovementGameTests.class);
    }
}
