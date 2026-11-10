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
public final class AcquireGameTests {
    private static final int SETTLE_TICKS = 1;
    private static final int IRON_ORE_COUNT = 3;

    private AcquireGameTests() {}

    // [флак] добыча железа с нуля без дерева: верстак и печь в мире, каменная кирка снабжается
    // заранее (резолвер не планирует крафт инструментов — RequiredTier вне ResourceKnowledge),
    // остальное дух добывает, крафтит и плавит сам; замерено ~340 тиков, запас 2×.
    @SceneStep(template = "arena", timeoutTicks = 700)
    public static void acquireIronIngot_FromScratch_IngotInInventory(GameTestHelper helper) {
        GraphForageGameTests.surroundings(helper);
        field(helper);
        placeIronOreRow(helper);
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        helper.setBlock(5, 1, 5, Blocks.FURNACE);
        helper.setBlock(3, 1, 4, Blocks.COAL_ORE);
        helper.setBlock(4, 1, 4, Blocks.COAL_ORE);
        SpiritEntity spirit = prepare(helper, stack(Items.STONE_PICKAXE, 1));
        speedUpFurnaces(helper);
        TaskNode plan = PlanParser.parse("""
                {"type":"script","script":"acquire","params":{"item":"iron_ingot","count":1}}
                """, SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.IRON_INGOT, 1, "железный слиток");
            helper.assertTrue(
                    FullCycleScene.countBlocks(helper, Blocks.IRON_ORE) == IRON_ORE_COUNT - 1,
                    "план добывает ровно одну руду под слиток — лишняя не нужна, осталось "
                            + FullCycleScene.countBlocks(helper, Blocks.IRON_ORE));
            cleanup(helper, spirit);
        });
    }

    private static void placeIronOreRow(GameTestHelper helper) {
        for (int x = 0; x < IRON_ORE_COUNT; x++) {
            helper.setBlock(3 + x, 1, 3, Blocks.IRON_ORE);
        }
    }
}
