package com.theminespirit.gametest;

import static com.theminespirit.gametest.MineSceneSupport.MAX_DROP_LINES;
import static com.theminespirit.gametest.MineSceneSupport.RADIUS;
import static com.theminespirit.gametest.MineSceneSupport.SHELL_MAX;
import static com.theminespirit.gametest.MineSceneSupport.SHELL_MIN;
import static com.theminespirit.gametest.MineSceneSupport.SHELL_TOP;
import static com.theminespirit.gametest.MineSceneSupport.TOOL_SLOT;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class MineSceneSupport {
    static final int SHELL_MIN = -2;
    static final int SHELL_MAX = 6;
    static final int SHELL_TOP = 4;
    static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    static final BlockPos SPIRIT_CORNER = new BlockPos(0, 1, 0);
    static final BlockPos ARENA_START = new BlockPos(4, 1, 4);
    static final int TOOL_SLOT = 0;
    static final int SETTLE_TICKS = 5;
    static final int RADIUS = 16;
    static final int DIRT_HEAP_TIMEOUT_TICKS = 1000;
    static final int REDSTONE_TIMEOUT_TICKS = 400;
    static final int UNPICKABLE_DELAY = 200;
    static final int UNPICKABLE_TICKS = 80;
    static final int MAX_DROP_LINES = 12;
    static final int LAVA_TOTAL = 1;
    static final int CAVE_SHIFT_INSIDE_ARENA = -SHELL_MIN;

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

    static String dropSummary(GameTestHelper helper, Item item) {
        List<ItemEntity> drops = GameTestFixtures.dropsInArea(helper, item);
        StringBuilder text = new StringBuilder("капель: " + drops.size());
        int shown = 0;
        for (ItemEntity drop : drops) {
            if (shown >= MAX_DROP_LINES) {
                text.append(" ...обрезано");
                break;
            }
            text.append(" [")
                    .append(drop.getItem().getCount())
                    .append("× на ")
                    .append(drop.blockPosition().toShortString())
                    .append("]");
            shown++;
        }
        return text.toString();
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

    static void buildCave(GameTestHelper helper, int shift) {
        for (int x = SHELL_MIN + shift; x <= SHELL_MAX + shift; x++) {
            for (int y = 0; y <= SHELL_TOP; y++) {
                for (int z = SHELL_MIN + shift; z <= SHELL_MAX + shift; z++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        for (int x = shift; x <= 4 + shift; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = shift; z <= 4 + shift; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        for (int x = SHELL_MIN + shift; x <= SHELL_MAX + shift; x++) {
            for (int z = SHELL_MIN + shift; z <= SHELL_MAX + shift; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        for (int x = SHELL_MIN + shift; x <= SHELL_MAX + shift; x++) {
            for (int z = SHELL_MIN + shift; z <= SHELL_MAX + shift; z++) {
                helper.setBlock(x, -1, z, Blocks.DIRT);
            }
        }
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

    static String caveSnapshot(GameTestHelper helper, int shift) {
        StringBuilder snapshot = new StringBuilder();
        for (int x = SHELL_MIN + shift; x <= SHELL_MAX + shift; x++) {
            for (int y = 0; y <= SHELL_TOP; y++) {
                for (int z = SHELL_MIN + shift; z <= SHELL_MAX + shift; z++) {
                    snapshot.append(x)
                            .append(',')
                            .append(y)
                            .append(',')
                            .append(z)
                            .append('=')
                            .append(helper.getBlockState(new BlockPos(x, y, z)))
                            .append(';');
                }
            }
        }
        return snapshot.toString();
    }
}
