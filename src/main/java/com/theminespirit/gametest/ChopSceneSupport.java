package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopTestSupport.FIELD_HEIGHT;
import static com.theminespirit.gametest.ChopTestSupport.PIT_DEPTH;
import static com.theminespirit.gametest.ChopTestSupport.PLATFORM;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class ChopSceneSupport {
    private ChopSceneSupport() {}

    static BlockCoord coordOf(GameTestHelper helper, Map<BlockState, Boolean> table, Block block) {
        int index = 0;
        for (BlockState state : table.keySet()) {
            if (state.is(block)) {
                BlockPos absolute = helper.absolutePos(new BlockPos(index % PLATFORM, 1, index / PLATFORM));
                return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
            }
            index++;
        }
        throw new IllegalStateException("блока " + block + " нет в таблице теста");
    }

    static void prepareField(GameTestHelper helper) {
        clearField(helper);
        for (int x = 0; x < PLATFORM; x++) {
            for (int z = 0; z < PLATFORM; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
    }

    static void clearField(GameTestHelper helper) {
        clearField(helper, FIELD_HEIGHT);
    }

    static void clearField(GameTestHelper helper, int height) {
        GameTestFixtures.reset(helper, height);
        for (int x = 0; x < PLATFORM; x++) {
            for (int z = 0; z < PLATFORM; z++) {
                for (int y = 1 - PIT_DEPTH; y < 0; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
            }
        }
    }

    static void buildOakTree(GameTestHelper helper, int x, int z, int height) {
        buildTree(helper, x, z, height, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
    }

    static void buildSquaredTree(GameTestHelper helper, int x, int z, int height, int crownY, Block log, Block leaves) {
        for (int y = 1; y <= height; y++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    helper.setBlock(x + dx, y, z + dz, log);
                }
            }
        }
        var leavesState = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                boolean ring = dx == -1 || dx == 2 || dz == -1 || dz == 2;
                if (ring
                        && helper.getBlockState(new BlockPos(x + dx, crownY, z + dz))
                                .isAir()) {
                    helper.setBlock(x + dx, crownY, z + dz, leavesState);
                }
            }
        }
    }

    static void buildTree(GameTestHelper helper, int x, int z, int height, Block log, Block leaves) {
        for (int y = 1; y <= height; y++) {
            helper.setBlock(x, y, z, log);
        }
        addLeavesCrown(helper, x, z, height, leaves);
    }

    static void addLeavesCrown(GameTestHelper helper, int x, int z, int trunkHeight, Block leavesBlock) {
        int top = trunkHeight;
        var leavesState = leavesBlock.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        helper.setBlock(x, top + 1, z, leavesState);
        helper.setBlock(x + 1, top, z, leavesState);
        helper.setBlock(x - 1, top, z, leavesState);
        helper.setBlock(x, top, z + 1, leavesState);
        helper.setBlock(x, top, z - 1, leavesState);
        helper.setBlock(x + 1, top + 1, z, leavesState);
        helper.setBlock(x - 1, top + 1, z, leavesState);
        helper.setBlock(x, top + 1, z + 1, leavesState);
        helper.setBlock(x, top + 1, z - 1, leavesState);
        helper.setBlock(x + 1, top + 1, z + 1, leavesState);
        helper.setBlock(x - 1, top + 1, z - 1, leavesState);
    }
}
