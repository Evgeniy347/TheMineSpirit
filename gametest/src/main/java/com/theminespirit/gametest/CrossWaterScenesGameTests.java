package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrossWaterScenesGameTests {
    private CrossWaterScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_cross_water_arena", timeoutTicks = 400)
    public static void islandInPond_Reentry_ReachesFarShore(GameTestHelper helper) {
        CrossWaterGameTests.islandInPond_ReentersWaterAndFinishes(helper);
    }
}
