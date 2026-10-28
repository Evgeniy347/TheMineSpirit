package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class CollectDropsSupport {
    static final BlockPos SPIRIT_START = new BlockPos(4, 1, 4);
    static final int PILLAR_HEIGHT = 6;
    static final int SETTLE_TICKS = 1;
    static final int PILLAR_SETTLE_TICKS = 10;
    static final int ENDLESS_PICKUP_DELAY = 32767;
    static final int DIAMOND_HEAPS = 20;
    static final int DIAMONDS_PER_HEAP = 64;

    private CollectDropsSupport() {}

    static void dropDiamondRing(GameTestHelper helper) {
        int[][] ring = {
            {2, 2}, {2, 1}, {2, 0}, {2, -1}, {2, -2}, {1, -2}, {0, -2}, {-1, -2}, {-2, -2}, {-2, -1}, {-2, 0}, {-2, 1},
            {-2, 2}, {-1, 2}, {0, 2}, {1, 2}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        for (int[] cell : ring) {
            GameTestFixtures.dropAt(
                    helper,
                    Items.DIAMOND,
                    DIAMONDS_PER_HEAP,
                    SPIRIT_START.getX() + cell[0] + 0.5,
                    1,
                    SPIRIT_START.getZ() + cell[1] + 0.5);
        }
    }

    static void spawnCobblestoneHeaps(GameTestHelper helper) {
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 5, 1.5, 1, 2.5);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 3, 7.5, 1, 2.5);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 1, 4.5, 1, 7.5);
    }

    static int totalItems(GameTestHelper helper, SpiritEntity spirit) {
        int total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            total += spirit.getInventory().getItem(slot).getCount();
        }
        return total + GameTestFixtures.countInWorld(helper, Items.COBBLESTONE);
    }
}
