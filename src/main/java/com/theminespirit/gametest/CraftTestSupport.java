package com.theminespirit.gametest;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

final class CraftTestSupport {
    static final String FIELD = "arena";
    static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    static final int RADIUS = 16;
    static final int TIMEOUT = 200;
    private static final int SETTLE_TICKS = 1;
    private static final int SERVER_TICKS_PER_ITEM = 200;

    private CraftTestSupport() {
    }

    static void field(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    static SpiritEntity prepare(GameTestHelper helper, ItemStack... stacks) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        for (ItemStack stack : stacks) {
            int pieces = stack.getMaxStackSize() == 1 ? stack.getCount() : 1;
            for (int piece = 0; piece < pieces; piece++) {
                spirit.getInventory().addItem(pieces == 1 ? stack.copy() : stack.copyWithCount(1));
            }
        }
        return spirit;
    }

    static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }

    static SpiritRun launch(GameTestHelper helper, SpiritEntity spirit, String script, String item, int count) {
        ScriptCall call = new ScriptCall(script, Map.of("item", item, "count", count, "radius", RADIUS));
        return SpiritRun.launch(helper, spirit, call, SETTLE_TICKS);
    }

    static void assertFinished(GameTestHelper helper, SpiritEntity spirit, SpiritRun run, RunStatus expected) {
        run.assertStarted();
        RunStatus actual = spirit.taskStatus().orElse(null);
        helper.assertTrue(actual == expected, "задача обязана завершиться статусом " + expected + ", статус: "
                + actual + ", причина: " + spirit.taskFailureReason().orElse("нет"));
    }

    static void assertReason(GameTestHelper helper, SpiritEntity spirit, String fragment) {
        String reason = spirit.taskFailureReason().orElse("");
        helper.assertTrue(reason.contains(fragment),
                "причина провала обязана содержать '" + fragment + "', фактическая: '" + reason + "'");
    }

    static void assertCount(GameTestHelper helper, SpiritEntity spirit, Item item, int expected, String what) {
        int actual = GameTestFixtures.countInInventory(spirit.getInventory(), item);
        helper.assertTrue(actual == expected, what + ": ожидалось " + expected + ", в инвентаре " + actual);
    }

    static void speedUpFurnaces(GameTestHelper helper) {
        helper.onEachTick(() -> {
            for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
                for (int z = 0; z < GameTestFixtures.FLOOR_SIZE; z++) {
                    BlockPos cell = helper.absolutePos(new BlockPos(x, 1, z));
                    BlockEntity entity = helper.getLevel().getBlockEntity(cell);
                    if (entity instanceof AbstractFurnaceBlockEntity furnace) {
                        for (int i = 0; i < SERVER_TICKS_PER_ITEM; i++) {
                            AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), cell,
                                    helper.getLevel().getBlockState(cell), furnace);
                        }
                    }
                }
            }
        });
    }

    static SpiritEntity reload(GameTestHelper helper, SpiritEntity original) {
        CompoundTag tag = new CompoundTag();
        original.saveWithoutId(tag);
        original.discard();
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        helper.getLevel().addFreshEntity(reloaded);
        return reloaded;
    }

    static void cleanup(GameTestHelper helper, SpiritEntity spirit) {
        spirit.discard();
        GameTestFixtures.reset(helper);
    }
}
