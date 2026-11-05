package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
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
public final class PathScenesGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos FLAT_ORIGIN = new BlockPos(1, 1, 1);
    private static final BlockPos LOW_ORIGIN = new BlockPos(2, 1, 2);

    private PathScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_path_arena", timeoutTicks = 400)
    public static void pathScene_FlatAndUnevenGround_PathLiesInSurface(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 16);
        for (int x = -1; x <= 10; x++) {
            for (int z = -1; z <= 10; z++) {
                helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.setBlock(LOW_ORIGIN.getX(), 0, LOW_ORIGIN.getZ(), Blocks.AIR);
        helper.setBlock(LOW_ORIGIN.getX(), -1, LOW_ORIGIN.getZ(), Blocks.DIRT);
        helper.setBlock(FLAT_ORIGIN.getX(), FLAT_ORIGIN.getY(), FLAT_ORIGIN.getZ(), Blocks.DIRT_PATH);

        SpiritEntity flat = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(8, 1, 8));
        SpiritEntity low = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(7, 1, 7));
        flat.getInventory().setItem(0, new ItemStack(Items.IRON_SHOVEL));
        low.getInventory().setItem(0, new ItemStack(Items.IRON_SHOVEL));
        SpiritRun flatRun = SpiritRun.launch(helper, flat, build(helper, FLAT_ORIGIN), SETTLE_TICKS);
        SpiritRun lowRun = SpiritRun.launch(helper, low, build(helper, LOW_ORIGIN), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            flatRun.assertStarted();
            lowRun.assertStarted();
            helper.assertTrue(
                    flat.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "тропа на ровной земле обязана встать целиком, статус "
                            + flat.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + flat.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    low.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "тропа на низкой колонке обязана встать целиком, статус "
                            + low.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + low.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(FLAT_ORIGIN.getX(), 0, FLAT_ORIGIN.getZ()))
                            .is(Blocks.DIRT_PATH),
                    "тропа на ровной земле заменяет верхний блок земли: её быть на уровне поверхности");
            helper.assertTrue(
                    helper.getBlockState(FLAT_ORIGIN).isAir(),
                    "старая тропа блоком над поверхностью снята, а не осталась висеть над новой");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(LOW_ORIGIN.getX(), -1, LOW_ORIGIN.getZ()))
                            .is(Blocks.DIRT_PATH),
                    "на неровной земле тропа ложится в поверхность колонки, а не висит на уровне схемы");
            helper.assertTrue(
                    helper.getBlockState(LOW_ORIGIN).isAir(),
                    "клетка схемы над низкой колонкой остаётся воздухом: тропа не ставится блоком над землёй");
            helper.setBlock(LOW_ORIGIN.getX(), -1, LOW_ORIGIN.getZ(), Blocks.AIR);
            GameTestFixtures.finish(helper, 16);
        });
    }

    private static ScriptCall build(GameTestHelper helper, BlockPos origin) {
        BlockPos absolute = helper.absolutePos(origin);
        return new ScriptCall(
                "build_schematic",
                Map.of(
                        "file", "street_patch",
                        "x", absolute.getX(),
                        "y", absolute.getY(),
                        "z", absolute.getZ()));
    }
}
