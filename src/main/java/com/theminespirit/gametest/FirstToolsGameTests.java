package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.assertCount;
import static com.theminespirit.gametest.CraftTestSupport.assertFinished;
import static com.theminespirit.gametest.CraftTestSupport.cleanup;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.prepare;
import static com.theminespirit.gametest.CraftTestSupport.speedUpFurnaces;
import static com.theminespirit.gametest.FullCycleScene.TREE;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.PlanParser;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FirstToolsGameTests {
    private static final int SETTLE_TICKS = 1;
    private static final int SCENE_HEIGHT = 8;
    private static final int CORNER_DISTANCE = 5;
    private static final int WOOD_TREE = 3;
    private static final int COBBLESTONE_ROW = 9;
    private static final int FURNACE_COBBLESTONE = 8;

    private FirstToolsGameTests() {
    }

    @BeforeBatch(batch = "first_tools_wood")
    public static void boardOffWood(ServerLevel level) {
        SpiritConfig.setBoardEnabled(false);
    }

    @AfterBatch(batch = "first_tools_wood")
    public static void boardOnWood(ServerLevel level) {
        SpiritConfig.setBoardEnabled(true);
    }

    @BeforeBatch(batch = "first_tools_stone")
    public static void boardOffStone(ServerLevel level) {
        SpiritConfig.setBoardEnabled(false);
    }

    @AfterBatch(batch = "first_tools_stone")
    public static void boardOnStone(ServerLevel level) {
        SpiritConfig.setBoardEnabled(true);
    }

    @BeforeBatch(batch = "first_tools_corner")
    public static void boardOffCorner(ServerLevel level) {
        SpiritConfig.setBoardEnabled(false);
    }

    @AfterBatch(batch = "first_tools_corner")
    public static void boardOnCorner(ServerLevel level) {
        SpiritConfig.setBoardEnabled(true);
    }

    // [флак] три acquire подряд (кирка, топор, лопата), запас 2× к замеру цепочки
    @GameTest(template = "arena", batch = "first_tools_wood", timeoutTicks = 1500)
    public static void firstTools_WoodOnly_PlacesTableAndCraftsPickaxeAxeShovel(GameTestHelper helper) {
        field(helper);
        buildWood(helper);
        SpiritEntity spirit = prepare(helper);
        TaskNode plan = sequence("""
                {"type":"script","script":"acquire","params":{"item":"wooden_pickaxe","count":1}},
                {"type":"script","script":"acquire","params":{"item":"wooden_axe","count":1}},
                {"type":"script","script":"acquire","params":{"item":"wooden_shovel","count":1}}
                """);
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1, "деревянная кирка");
            assertCount(helper, spirit, Items.WOODEN_AXE, 1, "деревянный топор");
            assertCount(helper, spirit, Items.WOODEN_SHOVEL, 1, "деревянная лопата");
            helper.assertTrue(FullCycleScene.countBlocks(helper, Blocks.CRAFTING_TABLE) == 1,
                    "Без верстака в сцене дух обязан скрафтить его сам и поставить в мир: в мире "
                            + FullCycleScene.countBlocks(helper, Blocks.CRAFTING_TABLE));
            helper.assertTrue(FullCycleScene.countBlocks(helper, Blocks.OAK_LOG) == 2,
                    "Цепочка трёх деревянных инструментов берёт ровно 7 брёвен из трёх деревьев по 3 "
                            + "(1 на верстак, 2 на кирку, по 2 на топор и лопату), остаток 2, а не "
                            + FullCycleScene.countBlocks(helper, Blocks.OAK_LOG));
            cleanup(helper, spirit);
        });
    }

    // [флак] четыре acquire подряд (кирка, топор, лопата, меч), запас 2× к замеру цепочки
    @GameTest(template = "arena", batch = "first_tools_stone", timeoutTicks = 1200)
    public static void firstTools_WoodAndStone_CraftsStonePickaxeAxeShovelSword(GameTestHelper helper) {
        field(helper);
        buildWood(helper);
        FullCycleScene.placeCobblestoneRow(helper, COBBLESTONE_ROW);
        SpiritEntity spirit = prepare(helper);
        TaskNode plan = sequence("""
                {"type":"script","script":"acquire","params":{"item":"stone_pickaxe","count":1}},
                {"type":"script","script":"acquire","params":{"item":"stone_axe","count":1}},
                {"type":"script","script":"acquire","params":{"item":"stone_shovel","count":1}},
                {"type":"script","script":"acquire","params":{"item":"stone_sword","count":1}}
                """);
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.STONE_PICKAXE, 1, "каменная кирка");
            assertCount(helper, spirit, Items.STONE_AXE, 1, "каменный топор");
            assertCount(helper, spirit, Items.STONE_SHOVEL, 1, "каменная лопата");
            assertCount(helper, spirit, Items.STONE_SWORD, 1, "каменный меч");
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1,
                    "деревянная кирка остаётся в инвентаре: каменные инструменты делаются после неё");
            helper.assertTrue(FullCycleScene.countBlocks(helper, Blocks.CRAFTING_TABLE) == 1,
                    "Верстак в сцене не поставлен — дух обязан получить и поставить один: в мире "
                            + FullCycleScene.countBlocks(helper, Blocks.CRAFTING_TABLE));
            cleanup(helper, spirit);
        });
    }

    // [флак] длинная цепочка: верстак, кирка, печь, плавка и факелы; запас 2× к замеру
    @GameTest(template = "arena", batch = "first_tools_corner", timeoutTicks = 1200)
    public static void firstTools_FromScratch_FurnaceBesideTableAndCharcoalTorches(GameTestHelper helper) {
        field(helper);
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
            helper.assertTrue(furnace != null, "дух обязан поставить печь: без неё не получить древесный уголь");
            helper.assertTrue(table.distSqr(furnace) <= CORNER_DISTANCE * CORNER_DISTANCE,
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
            helper.assertTrue(helper.getBlockState(chest).is(Blocks.CHEST),
                    "Сундук рабочего угла обязан остаться на месте: " + chest);
            helper.assertTrue(helper.absolutePos(chest).distSqr(table) <= CORNER_DISTANCE * CORNER_DISTANCE,
                    "Сундук рабочего угла обязан стоять рядом с верстаком (не дальше " + CORNER_DISTANCE
                            + " блоков): сундук " + chest + ", верстак " + table);
        }
    }

    private static void buildWood(GameTestHelper helper) {
        FullCycleScene.buildOakTree(helper, TREE, WOOD_TREE);
        FullCycleScene.buildOakTree(helper, new BlockPos(2, 1, 7), WOOD_TREE);
        FullCycleScene.buildOakTree(helper, new BlockPos(7, 1, 2), WOOD_TREE);
    }

    private static BlockPos findBlock(GameTestHelper helper, Block block) {
        for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
            for (int y = 1; y < SCENE_HEIGHT; y++) {
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
