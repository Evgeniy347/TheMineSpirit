package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.assertCount;
import static com.theminespirit.gametest.CraftTestSupport.assertFinished;
import static com.theminespirit.gametest.CraftTestSupport.cleanup;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.prepare;
import static com.theminespirit.gametest.CraftTestSupport.speedUpFurnaces;
import static com.theminespirit.gametest.CraftTestSupport.stack;
import static com.theminespirit.gametest.FullCycleScene.TABLE;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.PlanParser;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
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
    private static final int IRON_TICKS_LIMIT = 510;

    private FullCycleGameTests() {}

    // [флак] вся цепочка: добыча 8 булыжников по одному, крафт, плавка, сундук; замерено 339 тиков духа, запас 2×
    @SceneStep(template = "arena", timeoutTicks = 700)
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
}
