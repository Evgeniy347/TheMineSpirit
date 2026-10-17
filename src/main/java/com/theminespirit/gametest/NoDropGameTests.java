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
public final class NoDropGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(4, 1, 4);
    private static final int SETTLE_TICKS = 5;

    private NoDropGameTests() {
    }

    @GameTest(template = "arena", batch = "no_drop_almost_full", timeoutTicks = 400)
    public static void gather_AlmostFullInventory_MinesWithoutDropping(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(2, 1, 2, Blocks.COBBLESTONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        for (int slot = 1; slot <= 34; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        spirit.getInventory().setItem(35, new ItemStack(Items.COBBLESTONE, 63));
        int before = GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE);

        SpiritRun.launch(helper, spirit,
                new ScriptCall("gather", Map.of("block", "cobblestone", "count", 1, "radius", 8)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "gather одного булыжника при почти полном инвентаре обязан завершиться успехом: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == before + 1,
                    "в инвентаре обязан появиться добытый булыжник: было " + before + ", стало "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE).isEmpty(),
                    "на земле не должно быть выброшенных булыжников: политика «не выкидывать предметы»");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "no_drop_full", timeoutTicks = 400)
    public static void gather_FullInventory_FailsWithoutDropping(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(2, 1, 2, Blocks.COBBLESTONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        for (int slot = 1; slot <= 35; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        int before = GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE);

        SpiritRun.launch(helper, spirit,
                new ScriptCall("gather", Map.of("block", "cobblestone", "count", 1, "radius", 8)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "gather при полном инвентаре обязан завершиться отказом: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskFailureReason().orElse("").contains("инвентарь полон"),
                    "причина отказа обязана называть полный инвентарь: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == before,
                    "инвентарь не должен измениться при отказе: было " + before + ", стало "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE).isEmpty(),
                    "на земле не должно быть выброшенных булыжников при отказе");
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.COBBLESTONE),
                    "блок при отказе не должен быть сломан");
            GameTestFixtures.finish(helper);
        });
    }
}
