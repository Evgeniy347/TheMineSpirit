package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.assertCount;
import static com.theminespirit.gametest.CraftTestSupport.assertFinished;
import static com.theminespirit.gametest.CraftTestSupport.cleanup;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.prepare;
import static com.theminespirit.gametest.CraftTestSupport.speedUpFurnaces;
import static com.theminespirit.gametest.CraftTestSupport.stationsNear;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.PlanParser;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FirstToolsGameTests {
    private static final int SETTLE_TICKS = 1;
    private static final long DAY_TIME = 6000;
    private static final int SCENE_HEIGHT = 8;
    private static final int CORNER_DISTANCE = 5;
    private static final int WOOD_TREE = 3;
    private static final int FURNACE_COBBLESTONE = 8;

    private FirstToolsGameTests() {}

    // [флак] длинная цепочка: верстак, кирка, печь, плавка и факелы; запас 2× к замеру. Деревья стоят
    // кучно у рабочего угла намеренно: стол и печь ставятся рядом с духом, и работа у угла держит их
    // компактно (проверка CORNER_DISTANCE). Разнести деревья нельзя — печь уезжает за духом.
    @SceneStep(template = "arena", timeoutTicks = 1200, calmMobs = true)
    public static void firstTools_FromScratch_FurnaceBesideTableAndCharcoalTorches(GameTestHelper helper) {
        field(helper);
        GameTestFixtures.holdDayTime(helper, DAY_TIME);
        FullCycleScene.buildOakTree(helper, new BlockPos(4, 1, 4), WOOD_TREE);
        FullCycleScene.buildOakTree(helper, new BlockPos(5, 1, 4), WOOD_TREE);
        FullCycleScene.buildOakTree(helper, new BlockPos(4, 1, 5), WOOD_TREE);
        FullCycleScene.placeCobblestoneRow(helper, FURNACE_COBBLESTONE + 1);
        helper.setBlock(6, 1, 6, Blocks.COAL_ORE);
        helper.setBlock(3, 1, 3, Blocks.CHEST);
        helper.setBlock(5, 1, 3, Blocks.CHEST);
        SpiritEntity spirit = prepare(helper);
        speedUpFurnaces(helper);
        TaskNode plan = sequence("""
                {"type":"script","script":"acquire","params":{"item":"wooden_pickaxe","count":1}},
                {"type":"script","script":"acquire","params":{"item":"furnace","count":1}},
                {"type":"script","script":"acquire","params":{"item":"oak_log","count":2}},
                {"type":"script","script":"acquire","params":{"item":"coal","count":1}},
                {"type":"script","script":"smelt","params":{"item":"charcoal","count":1}},
                {"type":"script","script":"acquire","params":{"item":"stick","count":2}},
                {"type":"script","script":"craft","params":{"item":"torch","count":4}}
                """);
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.TORCH, 4, "факелы из древесного угля");
            assertCount(helper, spirit, Items.CHARCOAL, 0, "древесный уголь израсходован на факелы");
            assertCount(helper, spirit, Items.COAL, 0, "каменный уголь не остался: он был только топливом печи");
            BlockPos table = findBlock(helper, Blocks.CRAFTING_TABLE);
            BlockPos furnace = findBlock(helper, Blocks.FURNACE);
            helper.assertTrue(table != null, "дух обязан поставить верстак: без него не сделать кирку и печь");
            helper.assertTrue(
                    furnace != null,
                    "дух обязан поставить печь: без неё не получить древесный уголь; "
                            + "печей в сцене " + FullCycleScene.countBlocks(helper, Blocks.FURNACE)
                            + "; станции рядом: " + stationsNear(helper, spirit));
            helper.assertTrue(
                    table.distSqr(furnace) <= CORNER_DISTANCE * CORNER_DISTANCE,
                    "Рабочий угол: печь обязана стоять рядом с верстаком (не дальше " + CORNER_DISTANCE
                            + " блоков), а стоит " + table.distSqr(furnace) + "² от него: верстак " + table
                            + ", печь " + furnace);
            assertCornerChests(helper, table);
            cleanup(helper, spirit);
        });
    }

    private static void assertCornerChests(GameTestHelper helper, BlockPos table) {
        BlockPos[] chests = {new BlockPos(3, 1, 3), new BlockPos(5, 1, 3)};
        for (BlockPos chest : chests) {
            helper.assertTrue(
                    helper.getBlockState(chest).is(Blocks.CHEST),
                    "Сундук рабочего угла обязан остаться на месте: " + chest);
            helper.assertTrue(
                    helper.absolutePos(chest).distSqr(table) <= CORNER_DISTANCE * CORNER_DISTANCE,
                    "Сундук рабочего угла обязан стоять рядом с верстаком (не дальше " + CORNER_DISTANCE
                            + " блоков): сундук " + chest + ", верстак " + table);
        }
    }

    private static BlockPos findBlock(GameTestHelper helper, Block block) {
        for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
            for (int y = 0; y < SCENE_HEIGHT; y++) {
                for (int z = 0; z < GameTestFixtures.FLOOR_SIZE; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(block)) {
                        return helper.absolutePos(pos);
                    }
                }
            }
        }
        return null;
    }

    private static TaskNode sequence(String steps) {
        return PlanParser.parse("{\"type\":\"sequence\",\"steps\":[" + steps + "]}", SpiritScripts.catalog());
    }
}
