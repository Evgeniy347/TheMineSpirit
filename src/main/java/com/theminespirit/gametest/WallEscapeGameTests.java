package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WallEscapeGameTests {
    private static final BlockPos FEET = new BlockPos(4, 1, 4);

    private WallEscapeGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 40)
    public static void wallEscape_HeadInStone_StepsToFreeCell(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), FEET);
        helper.setBlock(FEET, Blocks.STONE);
        helper.setBlock(FEET.above(), Blocks.STONE);

        helper.succeedWhen(() -> {
            helper.assertFalse(
                    spirit.isInWall(),
                    "дух с головой в камне обязан выйти в свободную клетку, а не задохнуться в стене (INC-61)");
            helper.assertTrue(
                    !helper.relativePos(spirit.blockPosition()).equals(FEET),
                    "дух ушёл из клетки камня, стоит в " + helper.relativePos(spirit.blockPosition()));
            GameTestFixtures.finish(helper);
        });
    }
}
