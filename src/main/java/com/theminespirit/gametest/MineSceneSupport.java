package com.theminespirit.gametest;

import static com.theminespirit.gametest.MineSceneSupport.RADIUS;
import static com.theminespirit.gametest.MineSceneSupport.SHELL_MAX;
import static com.theminespirit.gametest.MineSceneSupport.SHELL_MIN;
import static com.theminespirit.gametest.MineSceneSupport.SHELL_TOP;
import static com.theminespirit.gametest.MineSceneSupport.TOOL_SLOT;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class MineSceneSupport {
    static final int SHELL_MIN = -2;
    static final int SHELL_MAX = 6;
    static final int SHELL_TOP = 4;
    static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    static final int TOOL_SLOT = 0;
    static final int SETTLE_TICKS = 5;
    static final int RADIUS = 16;
    static final int MAX_DROP_LINES = 12;

    private MineSceneSupport() {}

    static SpiritEntity reload(GameTestHelper helper, SpiritEntity original, CompoundTag tag) {
        original.discard();
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        boolean added = helper.getLevel().addFreshEntity(reloaded);
        if (!added) {
            throw new IllegalStateException("перезагруженный дух не добавлен в мир: uuid " + reloaded.getUUID());
        }
        return reloaded;
    }

    static ScriptCall mineCall(String ore, int count) {
        return new ScriptCall("mine_vein", Map.of("ore", ore, "count", count, "radius", RADIUS));
    }

    static ScriptCall gatherCall(String block, int count) {
        return gatherCall(block, count, RADIUS);
    }

    static ScriptCall gatherCall(String block, int count, int radius) {
        return new ScriptCall("gather", Map.of("block", block, "count", count, "radius", radius));
    }

    static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, Item tool) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(tool));
        return spirit;
    }

    static int countBlock(GameTestHelper helper, Block block) {
        return countBlock(helper, block, 0);
    }

    static int countBlock(GameTestHelper helper, Block block, int shift) {
        int total = 0;
        for (int x = SHELL_MIN + shift; x <= SHELL_MAX + shift; x++) {
            for (int y = 0; y <= SHELL_TOP; y++) {
                for (int z = SHELL_MIN + shift; z <= SHELL_MAX + shift; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        total++;
                    }
                }
            }
        }
        return total;
    }
}
