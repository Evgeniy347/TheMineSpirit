package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MineLevelsGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int STEPS = 4;
    private static final int ENTRY_X = 3;
    private static final int ENTRY_Y = 5;
    private static final int ENTRY_Z = 4;
    private static final int CLEAR_HEIGHT = 16;

    private MineLevelsGameTests() {}

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper, CLEAR_HEIGHT);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 3; x <= 7; x++) {
            for (int y = 0; y <= 15; y++) {
                for (int z = 2; z <= 6; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(new BlockPos(ENTRY_X, ENTRY_Y, ENTRY_Z), Blocks.AIR);
        helper.setBlock(new BlockPos(ENTRY_X, ENTRY_Y + 1, ENTRY_Z), Blocks.AIR);
        helper.setBlock(new BlockPos(ENTRY_X, ENTRY_Y + 2, ENTRY_Z), Blocks.AIR);
    }

    private static SpiritEntity spawnWithKit(GameTestHelper helper) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(ENTRY_X, ENTRY_Y, ENTRY_Z));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE, 1));
        spirit.getInventory().setItem(1, new ItemStack(Items.TORCH, 16));
        spirit.getInventory().setItem(2, new ItemStack(Items.STONE_STAIRS, 16));
        return spirit;
    }

    private static void moveToEntrance(GameTestHelper helper, SpiritEntity spirit) {
        BlockPos absolute = helper.absolutePos(new BlockPos(ENTRY_X, ENTRY_Y, ENTRY_Z));
        spirit.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
    }

    // [флак] три задачи подряд (спуск, расширение 60+ клеток, 8 ступенек): подход к каждой клетке,
    // замер духа на порядок больше базового; запас 2× к ожидаемым тикам.
    @SceneStep(template = "mine_arena", timeoutTicks = 2400)
    public static void levels_DescentWidenStairs_BuildsThreeBySixShaftWithStairs(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWithKit(helper);
        int[] state = {0, 0};
        helper.onEachTick(() -> step(helper, spirit, state));
    }

    private static void step(GameTestHelper helper, SpiritEntity spirit, int[] state) {
        if (state[0] == 0) {
            if (spirit.tickCount >= SETTLE_TICKS) {
                state[1] = spirit.blockPosition().getY() - 4;
                spirit.startTask(new ScriptCall("dig_descent", Map.of("direction", "east", "y", state[1])));
                state[0] = 1;
            }
            return;
        }
        RunStatus status = spirit.taskStatus().orElse(null);
        if (status == RunStatus.FAILED) {
            helper.fail("фаза " + state[0] + " провалена: "
                    + spirit.taskFailureReason().orElse("нет"));
            return;
        }
        if (status != RunStatus.SUCCEEDED) {
            return;
        }
        switch (state[0]) {
            case 1 -> {
                moveToEntrance(helper, spirit);
                spirit.startTask(new ScriptCall("dig_descent_widen", Map.of("direction", "east", "y", state[1])));
                state[0] = 2;
            }
            case 2 -> {
                moveToEntrance(helper, spirit);
                spirit.startTask(new ScriptCall("place_descent_stairs", Map.of("direction", "east", "y", state[1])));
                state[0] = 3;
            }
            case 3 -> {
                assertShaft(helper);
                GameTestFixtures.finish(helper, CLEAR_HEIGHT);
                helper.succeed();
            }
            default -> {}
        }
    }

    private static void assertShaft(GameTestHelper helper) {
        for (int step = 0; step < STEPS; step++) {
            int floor = ENTRY_Y - 1 - step;
            int columnX = ENTRY_X + step;
            for (int lane = 0; lane <= 1; lane++) {
                int z = lane == 0 ? ENTRY_Z - 1 : ENTRY_Z + 1;
                BlockPos stair = new BlockPos(columnX, floor + 1, z);
                helper.assertTrue(
                        helper.getBlockState(stair).is(Blocks.STONE_STAIRS),
                        "на шаге " + step + " боковая полоса z=" + z + " обязана нести ступеньку, фактически "
                                + helper.getBlockState(stair));
                for (int above = 2; above <= 6; above++) {
                    BlockPos cell = new BlockPos(columnX, floor + above, z);
                    helper.assertTrue(
                            helper.getBlockState(cell).isAir(),
                            "боковая полоса z=" + z + " шага " + step + " на высоте floor+" + above
                                    + " обязана быть выкопана под 3×6, фактически " + helper.getBlockState(cell));
                }
            }
            helper.assertTrue(
                    !helper.getBlockState(new BlockPos(columnX, floor + 1, ENTRY_Z))
                            .is(Blocks.STONE_STAIRS),
                    "центральная полоса шага " + step + " обязана остаться свободной под будущие рельсы");
        }
    }
}
