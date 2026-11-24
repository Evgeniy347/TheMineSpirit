package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardScenesGameTests {
    private BoardScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_board_arena", timeoutTicks = 3203)
    public static void boardScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("arena"), BoardGameTests.class, BoardV2GameTests.class);
    }

    @GameTest(template = "arena", batch = "scene_dig_open_pit", timeoutTicks = 400)
    public static void digOut_OpenPitUnderPlateau_StepsOutBeforeBackfill(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                for (int y = 1; y <= 2; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        BlockPos feet = new BlockPos(4, 1, 4);
        helper.setBlock(feet, Blocks.AIR);
        helper.setBlock(feet.above(), Blocks.AIR);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), feet);
        spirit.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        spirit.getInventory().addItem(new ItemStack(Items.DIRT, 8));
        BlockPos home = helper.absolutePos(new BlockPos(6, 3, 4));
        BlockCoord goal = new BlockCoord(home.getX(), home.getY(), home.getZ());
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("dig_out", Map.of("x", goal.x(), "y", goal.y(), "z", goal.z(), "rescue", true)),
                1);

        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            helper.assertTrue(
                    spirit.blockPosition().getY() >= home.getY(),
                    "дух обязан выйти из ямы на площадку до возврата spoil: стоит в " + spirit.blockPosition()
                            + ", цель " + home + ", иначе dig_out закрывается, заделывает ступень и оставляет "
                            + "духа в той же яме (живой случай spirit-1 -195,65,-497)");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(5, 2, 4)).is(Blocks.STONE),
                    "ступень, по которой дух вышел, возвращается на место после выхода: в (5,2,4) сейчас "
                            + helper.getBlockState(new BlockPos(5, 2, 4)));
            spirit.discard();
            GameTestFixtures.finish(helper);
        });
    }
}
