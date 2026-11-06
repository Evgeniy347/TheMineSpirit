package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopSceneSupport.buildOakTree;
import static com.theminespirit.gametest.ChopSceneSupport.buildTree;
import static com.theminespirit.gametest.ChopSceneSupport.clearField;
import static com.theminespirit.gametest.ChopSceneSupport.prepareField;
import static com.theminespirit.gametest.ChopTestSupport.FIELD;
import static com.theminespirit.gametest.ChopTestSupport.PLATFORM;
import static com.theminespirit.gametest.ChopTestSupport.chopParams;
import static com.theminespirit.gametest.ChopTestSupport.countBlocksInWorld;
import static com.theminespirit.gametest.ChopTestSupport.driveScript;
import static com.theminespirit.gametest.ChopTestSupport.spawnChopper;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.neoforge.ChopTreeScript;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class ChopReplantGameTests {
    private ChopReplantGameTests() {}

    // [флак] обновление участка: шесть саженцев — ствол и досадка сеткой, каждую клетку дух обходит;
    // потолок с запасом на флаки полного прогона
    @SceneStep(template = FIELD, timeoutTicks = 800)
    public static void chopTree_ReplantOakWithExtraSaplings_RenewsAreaAwayFromWall(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        for (int z = 0; z < PLATFORM; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(8, y, z, Blocks.COBBLESTONE);
            }
        }
        SpiritEntity spirit = spawnChopper(helper);
        spirit.getInventory().setItem(1, new ItemStack(Items.OAK_SAPLING, 6));
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("oak", 1, true));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        for (int y = 8; y <= 10; y++) {
            helper.setBlock(1, y, 8, Blocks.BIRCH_LOG);
        }
        leaf(helper, 2, 9, 8);
        leaf(helper, 0, 9, 8);
        leaf(helper, 1, 11, 8);
        buildTree(helper, 1, 1, 3, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        buildTree(helper, 3, 1, 3, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        buildTree(helper, 1, 5, 5, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        helper.setBlock(2, 4, 5, Blocks.BIRCH_LOG);
        leaf(helper, 2, 5, 5);
        helper.setBlock(5, 1, 8, Blocks.GRASS_BLOCK);
        for (int y = 2; y <= 5; y++) {
            helper.setBlock(5, y, 8, Blocks.BIRCH_LOG);
        }
        leaf(helper, 4, 5, 8);
        leaf(helper, 6, 5, 8);
        leaf(helper, 5, 6, 8);
        helper.setBlock(7, 1, 8, Blocks.WATER);
        helper.setBlock(6, 1, 8, Blocks.STONE);
        helper.setBlock(7, 1, 7, Blocks.STONE);
        helper.setBlock(7, 1, 9, Blocks.STONE);
        SpiritEntity birches = spawnChopper(helper);
        ChopTreeScript shapes = new ChopTreeScript(birches, chopParams("birch", 5));
        ScriptStatus[] shapeStatus = driveScript(helper, birches, shapes);

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    status[0] == ScriptStatus.SUCCEEDED,
                    "рубка с обновлением участка обязана завершиться успехом, статус " + status[0] + ", причина: "
                            + script.failureReason() + ", итог: " + script.summary());
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.OAK_SAPLING),
                    "на месте ствола обязан стоять саженец дуба, а стоит "
                            + helper.getBlockState(new BlockPos(4, 1, 4)).getBlock());
            int saplings = countBlocksInWorld(helper, Blocks.OAK_SAPLING);
            helper.assertTrue(
                    saplings >= 2,
                    "шесть саженцев при одном на ствол дают досадку участка: всего посажено " + saplings);
            for (int x = 0; x < PLATFORM; x++) {
                for (int z = 0; z < PLATFORM; z++) {
                    if (!helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.OAK_SAPLING)) {
                        continue;
                    }
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int y = 1; y <= 2; y++) {
                                helper.assertTrue(
                                        !helper.getBlockState(new BlockPos(x + dx, y, z + dz))
                                                .is(Blocks.COBBLESTONE),
                                        "саженец в клетке " + new BlockPos(x, 1, z) + " стоит вплотную к стене "
                                                + "игрока: зазор buildingGap=2 не соблюдён");
                            }
                        }
                    }
                }
            }
            helper.assertTrue(
                    shapeStatus[0] == ScriptStatus.SUCCEEDED,
                    "рубка висящей верхушки, склона, веток и двух деревьев рядом обязана завершиться, статус "
                            + shapeStatus[0] + ", причина: " + shapes.failureReason());
            helper.assertTrue(
                    countBlocksInWorld(helper, Blocks.BIRCH_LOG) == 0,
                    "все формы срублены целиком: висящая верхушка, склон у воды, ветки и два дерева рядом — "
                            + "брёвен не осталось");
            clearField(helper);
        });
    }

    private static void leaf(GameTestHelper helper, int x, int y, int z) {
        helper.setBlock(x, y, z, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
    }
}
