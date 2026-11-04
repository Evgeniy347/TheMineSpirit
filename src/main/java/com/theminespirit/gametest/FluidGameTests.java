package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.assertCount;
import static com.theminespirit.gametest.CraftTestSupport.assertFinished;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.prepare;
import static com.theminespirit.gametest.CraftTestSupport.stack;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.PlanParser;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidGameTests {
    private static final int SETTLE_TICKS = 1;
    private static final BlockPos SOURCE = new BlockPos(6, 1, 6);

    private FluidGameTests() {}

    static void fillWaterSource(GameTestHelper helper) {
        field(helper);
        helper.setBlock(SOURCE, Blocks.WATER);
        SpiritEntity spirit = prepare(helper, stack(Items.BUCKET, 1));
        TaskNode plan = PlanParser.parse("""
                {"type":"script","script":"fill_bucket","params":{"fluid":"water","radius":8}}
                """, SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WATER_BUCKET, 1, "ведро воды");
            helper.assertTrue(
                    helper.getLevel().getBlockState(helper.absolutePos(SOURCE)).is(Blocks.WATER),
                    "источник остался на месте: дух только зачерпнул, мир не менял");
            GameTestFixtures.reset(helper);
        });
    }

    static void fillLavaSource(GameTestHelper helper) {
        field(helper);
        helper.setBlock(SOURCE, Blocks.LAVA);
        SpiritEntity spirit = prepare(helper, stack(Items.BUCKET, 1));
        TaskNode plan = PlanParser.parse("""
                {"type":"script","script":"fill_bucket","params":{"fluid":"lava","radius":8}}
                """, SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.LAVA_BUCKET, 1, "ведро лавы");
            helper.assertTrue(
                    helper.getLevel().getBlockState(helper.absolutePos(SOURCE)).is(Blocks.LAVA),
                    "источник лавы остался на месте");
            GameTestFixtures.reset(helper);
        });
    }

    static void pourWaterCell(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.WATER_BUCKET, 1));
        BlockPos absolute = helper.absolutePos(SOURCE);
        TaskNode plan = PlanParser.parse(
                """
                {"type":"script","script":"pour_bucket","params":{"fluid":"water","x":%d,"y":%d,"z":%d}}
                """.formatted(absolute.getX(), absolute.getY(), absolute.getZ()), SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            helper.assertTrue(
                    helper.getLevel().getBlockState(absolute).is(Blocks.WATER), "вода встала в клетку плана фермы");
            assertCount(helper, spirit, Items.BUCKET, 1, "пустое ведро вернулось");
            GameTestFixtures.reset(helper);
        });
    }
}
