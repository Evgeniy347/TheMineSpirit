package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.RouteWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RouteScenesGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int RESOURCE_X = 7;
    private static final int RESOURCE_Z = 7;
    private static final int SAND_RADIUS = 4;

    private RouteScenesGameTests() {}

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void route_SandDig_LayersFlatSpiritBringsSand(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        for (int x = RESOURCE_X - SAND_RADIUS; x <= RESOURCE_X + SAND_RADIUS; x++) {
            for (int z = RESOURCE_Z - SAND_RADIUS; z <= RESOURCE_Z + SAND_RADIUS; z++) {
                int height = Math.abs(x - RESOURCE_X) <= 1 && Math.abs(z - RESOURCE_Z) <= 1 ? 3 : 2;
                for (int y = 1; y <= height; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.SAND);
                }
            }
        }
        BlockCoord point = coord(helper, new BlockPos(RESOURCE_X, 4, RESOURCE_Z));
        BlockCoord home = coord(helper, new BlockPos(1, 1, 1));
        PlotLeveling plan = RouteWorks.dig(level, point);
        SpiritEntity spirit = spawnDigger(helper);
        TaskNode task = TicketPlans.plan(
                BoardCase.GATHER_MATERIALS,
                TicketTarget.at(home).withRelocate(point, home).withLeveling(plan));
        SpiritRun run = SpiritRun.launch(helper, spirit, task, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Добыча с маршрута обязана завершиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.SAND) > 0,
                    "Дух приносит песок с места добычи, в инвентаре "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.SAND));
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int x = RESOURCE_X - SAND_RADIUS; x <= RESOURCE_X + SAND_RADIUS; x++) {
                for (int z = RESOURCE_Z - SAND_RADIUS; z <= RESOURCE_Z + SAND_RADIUS; z++) {
                    int top = top(helper, x, z);
                    min = Math.min(min, top);
                    max = Math.max(max, top);
                }
            }
            helper.assertTrue(
                    max - min <= 1,
                    "После добычи слой ровный: перепад не больше блока, ям в 2 блока нет (верх " + min + "…" + max
                            + ")");
            GameTestFixtures.finish(helper);
        });
    }

    private static SpiritEntity spawnDigger(GameTestHelper helper) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE_SHOVEL, 1));
        spirit.getInventory().setItem(1, new ItemStack(Items.DIRT, 64));
        return spirit;
    }

    private static int top(GameTestHelper helper, int x, int z) {
        for (int y = 12; y >= 0; y--) {
            if (!helper.getBlockState(new BlockPos(x, y, z)).isAir()) {
                return y;
            }
        }
        return 0;
    }

    private static BlockCoord coord(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }
}
