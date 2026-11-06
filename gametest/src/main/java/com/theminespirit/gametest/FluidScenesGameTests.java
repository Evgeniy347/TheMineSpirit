package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidScenesGameTests {
    private FluidScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_fluid_arena", timeoutTicks = 400)
    public static void fillWater_ArenaSource_WaterBucketInInventory(GameTestHelper helper) {
        FluidGameTests.fillWaterSource(helper);
    }

    @GameTest(template = "arena", batch = "scene_fluid_arena", timeoutTicks = 400)
    public static void fillLava_ArenaSource_LavaBucketInInventory(GameTestHelper helper) {
        FluidGameTests.fillLavaSource(helper);
    }

    @GameTest(template = "arena", batch = "scene_fluid_arena", timeoutTicks = 400)
    public static void pourWater_PlannedCell_WaterPlacedAndBucketReturned(GameTestHelper helper) {
        FluidGameTests.pourWaterCell(helper);
    }
}
