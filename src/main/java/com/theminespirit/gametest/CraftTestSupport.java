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
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class CraftTestSupport {
    static final String FIELD = "arena";
    static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    static final int RADIUS = 16;
    static final int TIMEOUT = 200;
    private static final int SETTLE_TICKS = 1;
    private static final int SERVER_TICKS_PER_ITEM = 200;

    private CraftTestSupport() {}

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
        if (actual != expected) {
            helper.assertTrue(
                    false,
                    "задача обязана завершиться статусом " + expected + ", статус: "
                            + actual + ", причина: "
                            + spirit.taskFailureReason().orElse("нет")
                            + "; станции рядом: " + stationsNear(helper, spirit));
        }
    }

    static String stationsNear(GameTestHelper helper, SpiritEntity spirit) {
        BlockPos center = spirit.blockPosition();
        StringBuilder report = new StringBuilder();
        for (int dx = -17; dx <= 17; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -17; dz <= 17; dz++) {
                    BlockPos cell = center.offset(dx, dy, dz);
                    BlockState state = helper.getLevel().getBlockState(cell);
                    if (!state.is(Blocks.FURNACE)
                            && !state.is(Blocks.SMOKER)
                            && !state.is(Blocks.BLAST_FURNACE)
                            && !state.is(Blocks.CAMPFIRE)) {
                        continue;
                    }
                    report.append(state.getBlock()).append('@').append(cell.toShortString());
                    BlockEntity entity = helper.getLevel().getBlockEntity(cell);
                    if (entity instanceof AbstractFurnaceBlockEntity furnace) {
                        report.append("[in=")
                                .append(furnace.getItem(0))
                                .append(" fuel=")
                                .append(furnace.getItem(1))
                                .append(" out=")
                                .append(furnace.getItem(2))
                                .append(']');
                    } else if (entity instanceof CampfireBlockEntity campfire) {
                        report.append(campfire.getItems());
                    }
                    report.append(' ');
                }
            }
        }
        return report.length() == 0 ? "нет" : report.toString();
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
                            AbstractFurnaceBlockEntity.serverTick(
                                    helper.getLevel(), cell, helper.getLevel().getBlockState(cell), furnace);
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
