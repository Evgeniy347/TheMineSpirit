package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DigGameTests {
    private static final int SETTLE_TICKS = 5;

    private DigGameTests() {}

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static SpiritEntity spawnWithPick(GameTestHelper helper, BlockPos relative) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE_PICKAXE, 1));
        return spirit;
    }

    private static ScriptCall digCall(GameTestHelper helper, BlockPos corner, int size) {
        BlockPos absolute = helper.absolutePos(corner);
        return new ScriptCall(
                "dig_area", Map.of("x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ(), "size", size));
    }

    @GameTest(template = "arena", batch = "dig", timeoutTicks = 400)
    public static void dig_StoneCube_DigsToBottomWithoutTunnels(GameTestHelper helper) {
        prepare(helper);
        for (int x = 3; x <= 6; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 3; z <= 6; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.assertTrue(
                helper.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.STONE),
                "масса обязана встать в (4,1,4): без неё копать нечего");
        helper.assertTrue(
                helper.getBlockState(new BlockPos(4, 2, 4)).is(Blocks.STONE),
                "масса обязана встать в (4,2,4): без неё копать нечего");
        helper.assertTrue(
                helper.getBlockState(new BlockPos(4, 3, 4)).isAir(),
                "над массой в (4,3,4) обязан быть воздух: иначе стоянок сверху нет");
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(3, 3, 3));
        SpiritRun run = SpiritRun.launch(helper, spirit, digCall(helper, new BlockPos(4, 1, 4), 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "куб камня 2×2×2 обязан выкопаться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            for (int x = 4; x <= 5; x++) {
                for (int y = 1; y <= 2; y++) {
                    for (int z = 4; z <= 5; z++) {
                        helper.assertTrue(
                                helper.getBlockState(new BlockPos(x, y, z)).isAir(),
                                "клетка куба (" + x + "," + y + "," + z + ") обязана быть выкопана");
                    }
                }
            }
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(3, 1, 4)).is(Blocks.STONE)
                            && helper.getBlockState(new BlockPos(6, 1, 5)).is(Blocks.STONE)
                            && helper.getBlockState(new BlockPos(4, 1, 3)).is(Blocks.STONE)
                            && helper.getBlockState(new BlockPos(5, 1, 6)).is(Blocks.STONE)
                            && helper.getBlockState(new BlockPos(4, 0, 4)).is(Blocks.STONE),
                    "стенки и дно вокруг куба обязаны остаться камнем: туннелей за границу области нет");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 8,
                    "камень даёт дроп булыжника: все 8 обязаны подобраться, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "dig", timeoutTicks = 200)
    public static void dig_SizeOutOfBounds_RejectedWithNumbersKeepsWorld(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        SpiritRun first = SpiritRun.launch(helper, spirit, digCall(helper, new BlockPos(4, 1, 4), 0), SETTLE_TICKS);
        AtomicBoolean secondLaunched = new AtomicBoolean(false);

        helper.succeedWhen(() -> {
            first.assertStarted();
            if (!secondLaunched.get()) {
                helper.assertTrue(
                        spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                        "куб со стороной 0 обязан отвергнуться, статус "
                                + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
                String reason = spirit.taskFailureReason().orElse("");
                helper.assertTrue(
                        reason.contains("0") && reason.contains("16"),
                        "причина обязана называть сторону 0 и границу 16, фактически: " + reason);
                secondLaunched.set(true);
                SpiritRun.launch(helper, spirit, digCall(helper, new BlockPos(4, 1, 4), 17), 0);
            } else if (spirit.taskStatus().orElse(null) == RunStatus.FAILED
                    && spirit.taskFailureReason().orElse("").contains("17")) {
                helper.assertTrue(
                        helper.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.STONE),
                        "мир обязан остаться нетронутым: отказ идёт до первого блока");
                GameTestFixtures.finish(helper);
            }
        });
    }
}
