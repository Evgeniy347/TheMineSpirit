package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.assertFinished;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.prepare;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.PlanParser;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

final class CrossWaterGameTests {
    private static final int SETTLE_TICKS = 2;
    private static final int WATER_MIN_X = 3;
    private static final int WATER_MAX_X = 5;
    private static final int ISLAND_X = 4;
    private static final int POND_MIN_Z = -2;
    private static final int POND_MAX_Z = 10;
    private static final BlockPos TARGET = new BlockPos(7, 1, 1);

    private CrossWaterGameTests() {}

    static void islandInPond_ReentersWaterAndFinishes(GameTestHelper helper) {
        field(helper);
        for (int x = WATER_MIN_X; x <= WATER_MAX_X; x++) {
            for (int z = POND_MIN_Z; z <= POND_MAX_Z; z++) {
                boolean island = x == ISLAND_X;
                helper.setBlock(new BlockPos(x, -2, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, -1, z), island ? Blocks.SAND : Blocks.WATER);
                helper.setBlock(new BlockPos(x, 0, z), island ? Blocks.SAND : Blocks.WATER);
            }
        }
        SpiritEntity spirit = prepare(helper);
        double surfaceY = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
        helper.onEachTick(() -> {
            if (spirit.isInWater() && spirit.getY() < surfaceY - 1.5) {
                helper.fail("дух тонет ниже зеркала пруда: y=" + spirit.getY());
            }
        });
        BlockPos absolute = helper.absolutePos(TARGET);
        TaskNode plan = PlanParser.parse(
                """
                {"type":"script","script":"cross_water","params":{"x":%d,"y":%d,"z":%d}}
                """.formatted(absolute.getX(), absolute.getY(), absolute.getZ()), SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.WATER, new BlockPos(WATER_MIN_X, 0, 1));
            helper.assertBlockPresent(Blocks.WATER, new BlockPos(WATER_MIN_X, -1, 1));
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            helper.assertTrue(
                    !spirit.isInWater(), "дух вышел из воды на дальний берег, а не остался тонуть: y=" + spirit.getY());
            int farX = helper.absolutePos(new BlockPos(WATER_MAX_X, 1, 1)).getX();
            helper.assertTrue(
                    spirit.blockPosition().getX() >= farX,
                    "дух на дальнем берегу после отмели: x="
                            + spirit.blockPosition().getX() + " < " + farX);
            GameTestFixtures.reset(helper);
        });
    }
}
