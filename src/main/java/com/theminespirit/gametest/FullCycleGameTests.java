package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.assertCount;
import static com.theminespirit.gametest.CraftTestSupport.assertFinished;
import static com.theminespirit.gametest.CraftTestSupport.cleanup;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.prepare;
import static com.theminespirit.gametest.CraftTestSupport.speedUpFurnaces;
import static com.theminespirit.gametest.CraftTestSupport.stack;
import static com.theminespirit.gametest.FullCycleScene.TABLE;
import static com.theminespirit.gametest.FullCycleScene.TREE;
import static com.theminespirit.gametest.FullCycleScene.TREE_LOGS;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.PlanParser;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.ResourceResolver;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import java.util.Collections;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FullCycleGameTests {
    private static final int SETTLE_TICKS = 1;
    private static final int COBBLESTONE_FOR_FURNACE = 8;
    private static final int WOOD_TICKS_LIMIT = 190;
    private static final int IRON_TICKS_LIMIT = 510;
    private static final int RESOLVER_TICKS_LIMIT = 405;

    private FullCycleGameTests() {}

    @GameTest(template = "arena", batch = "full_cycle_wood", timeoutTicks = 400)
    public static void fullCycle_ChopCraftPickaxeDeposit_PickaxeInSortedChestNoLogsLeft(GameTestHelper helper) {
        field(helper);
        FullCycleScene.buildOakTree(helper, TREE, TREE_LOGS);
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        FullCycleScene.placeUnsortedChest(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.IRON_AXE, 1));
        TaskNode plan = PlanParser.parse("""
                {"type":"sequence","steps":[
                  {"type":"script","script":"chop_tree","params":{"species":"oak","count":1}},
                  {"type":"script","script":"craft","params":{"item":"oak_planks","count":8}},
                  {"type":"script","script":"craft","params":{"item":"stick","count":4}},
                  {"type":"script","script":"craft","params":{"item":"wooden_pickaxe","count":1}},
                  {"type":"script","script":"deposit","params":{"container":"%s","item":"wooden_pickaxe","count":1}}
                ]}""".formatted(FullCycleScene.chestTarget(helper)), SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            FullCycleScene.assertChestHolds(helper, Items.WOODEN_PICKAXE, 1);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 0, "кирка у духа после deposit");
            helper.assertTrue(
                    FullCycleScene.countBlocks(helper, Blocks.OAK_LOG) == 0,
                    "в мире не должно остаться дубовых брёвен: chop_tree срубил дерево целиком");
            helper.assertTrue(
                    GameTestFixtures.countInWorld(helper, Items.OAK_LOG) == 0,
                    "выпавшие брёвна подобраны, на земле не лежит ни одного");
            helper.assertTrue(
                    helper.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE),
                    "верстак цел: крафт кирки шёл у существующего, а не расходовал его");
            FullCycleScene.assertChestSortedAfterDeposit(helper, Items.WOODEN_PICKAXE);
            helper.assertTrue(
                    run.spiritTicks() <= WOOD_TICKS_LIMIT,
                    "цепочка «рубка, крафт, сундук» обязана уложиться в " + WOOD_TICKS_LIMIT
                            + " тиков духа (замерено 123, порог ≈1.5×), заняла " + run.spiritTicks());
            cleanup(helper, spirit);
        });
    }

    // [флак] вся цепочка: добыча 8 булыжников по одному, крафт, плавка, сундук; замерено 339 тиков духа, запас 2×
    @GameTest(template = "arena", batch = "full_cycle_iron", timeoutTicks = 700)
    public static void fullCycle_GatherCobbleCraftFurnaceSmeltDeposit_IngotInChestFurnaceOnce(GameTestHelper helper) {
        field(helper);
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        FullCycleScene.placeCobblestoneRow(helper, COBBLESTONE_FOR_FURNACE);
        FullCycleScene.placeUnsortedChest(helper);
        SpiritEntity spirit =
                prepare(helper, stack(Items.IRON_PICKAXE, 1), stack(Items.RAW_IRON, 1), stack(Items.COAL, 1));
        speedUpFurnaces(helper);
        TaskNode plan = PlanParser.parse(
                """
                {"type":"sequence","steps":[
                  {"type":"script","script":"gather","params":{"block":"cobblestone","count":%d}},
                  {"type":"script","script":"craft","params":{"item":"furnace","count":1}},
                  {"type":"script","script":"smelt","params":{"item":"iron_ingot","count":1}},
                  {"type":"script","script":"deposit","params":{"container":"%s","item":"iron_ingot","count":1}}
                ]}""".formatted(COBBLESTONE_FOR_FURNACE, FullCycleScene.chestTarget(helper)), SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            FullCycleScene.assertChestHolds(helper, Items.IRON_INGOT, FullCycleScene.INGOTS_BEFORE + 1);
            assertCount(helper, spirit, Items.IRON_INGOT, 0, "слиток у духа после deposit");
            assertCount(helper, spirit, Items.COBBLESTONE, 0, "булыжник: все 8 ушли в печь");
            assertCount(helper, spirit, Items.FURNACE, 0, "печь в инвентаре: smelt поставил её в мир");
            helper.assertTrue(
                    FullCycleScene.countBlocks(helper, Blocks.FURNACE) == 1,
                    "в мире обязана стоять ровно одна печь (скрафченная и поставленная), фактически "
                            + FullCycleScene.countBlocks(helper, Blocks.FURNACE));
            helper.assertTrue(
                    FullCycleScene.countBlocks(helper, Blocks.COBBLESTONE) == 0, "ряд булыжника разобран целиком");
            FullCycleScene.assertChestSortedAfterDeposit(helper, Items.IRON_INGOT);
            helper.assertTrue(
                    run.spiritTicks() <= IRON_TICKS_LIMIT,
                    "цепочка «булыжник, печь, плавка, сундук» обязана уложиться в " + IRON_TICKS_LIMIT
                            + " тиков духа (замерено 339, порог ≈1.5×), заняла " + run.spiritTicks());
            cleanup(helper, spirit);
        });
    }

    // [флак] три добычи брёвен голыми руками и пять крафтов подряд; замерено 270 тиков духа, запас 2×
    @GameTest(template = "arena", batch = "full_cycle_resolver", timeoutTicks = 540)
    public static void fullCycle_ResolverPlanForWoodenPickaxe_PickaxeInInventory(GameTestHelper helper) {
        field(helper);
        FullCycleScene.buildOakTree(helper, TREE, TREE_LOGS + 1);
        SpiritEntity spirit = prepare(helper);
        TaskNode plan = ResourceResolver.resolve("wooden_pickaxe", 1, RecipeKnowledge.of(helper.getLevel()));
        List<String> steps = FullCycleScene.scriptsOf(plan);
        helper.assertTrue(
                steps.contains("gather:oak_log") && steps.indexOf("gather:oak_log") < steps.indexOf("craft:oak_planks"),
                "план резолвера начинается с добычи брёвен, до первых досок: " + steps);
        helper.assertTrue(
                Collections.frequency(steps, "craft:crafting_table") == 1,
                "верстак в плане ровно один (станция на план, а не на каждый крафт): " + steps);
        helper.assertTrue(
                "craft:wooden_pickaxe".equals(steps.get(steps.size() - 1)),
                "план резолвера заканчивается крафтом кирки: " + steps);
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1, "деревянная кирка из плана резолвера");
            helper.assertTrue(
                    FullCycleScene.countBlocks(helper, Blocks.CRAFTING_TABLE) == 1,
                    "дух обязан скрафтить верстак и поставить его в мир: кирка 3×3 без станции не делается");
            helper.assertTrue(
                    FullCycleScene.countBlocks(helper, Blocks.OAK_LOG) == 1,
                    "из 4 брёвен дерева план берёт ровно 3 (доски на кирку, палки и верстак), в мире осталось "
                            + FullCycleScene.countBlocks(helper, Blocks.OAK_LOG));
            helper.assertTrue(
                    run.spiritTicks() <= RESOLVER_TICKS_LIMIT,
                    "цепочка по плану резолвера обязана уложиться в " + RESOLVER_TICKS_LIMIT
                            + " тиков духа (замерено 270, порог ≈1.5×), заняла " + run.spiritTicks());
            cleanup(helper, spirit);
        });
    }
}
