package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopTestSupport.FIELD_HEIGHT;
import static com.theminespirit.gametest.ChopTestSupport.PIT_DEPTH;
import static com.theminespirit.gametest.ChopTestSupport.PLATFORM;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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

    static void digPitAround(GameTestHelper helper, int centerX, int centerZ, int width) {
        for (int x = 0; x < PLATFORM; x++) {
            for (int z = 0; z < PLATFORM; z++) {
                int distance = Math.max(Math.abs(x - centerX), Math.abs(z - centerZ));
                if (distance > 0 && distance <= width) {
                    for (int y = 1 - PIT_DEPTH; y <= 0; y++) {
                        helper.setBlock(x, y, z, Blocks.AIR);
                    }
                }
            }
        }
    }

    static List<String> snapshotFieldWithoutLeaves(GameTestHelper helper) {
        List<String> cells = new ArrayList<>();
        for (int x = 0; x < PLATFORM; x++) {
            for (int y = 1; y < FIELD_HEIGHT; y++) {
                for (int z = 0; z < PLATFORM; z++) {
                    BlockState state = helper.getBlockState(new BlockPos(x, y, z));
                    if (!state.isAir() && !(state.getBlock() instanceof LeavesBlock)) {
                        cells.add(x + "," + y + "," + z + "=" + state);
                    }
                }
            }
        }
        return cells;
    }

    static void buildOakTree(GameTestHelper helper, int x, int z, int height) {
        buildTree(helper, x, z, height, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
    }

    static void buildDarkOakTree(GameTestHelper helper, int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    helper.setBlock(x + dx, y, z + dz, Blocks.DARK_OAK_LOG);
                }
            }
        }
        var leaves = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                helper.setBlock(x + dx, height + 1, z + dz, leaves);
            }
        }
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

    static void buildBirchTree(GameTestHelper helper, int x, int z, int height) {
        buildTree(helper, x, z, height, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
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

    static void buildPlankHutOnLedge(GameTestHelper helper, int x, int z) {
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                helper.setBlock(x + dx, 1, z + dz, Blocks.DIRT);
            }
        }
        for (int y = 2; y <= 4; y++) {
            for (int dx = 0; dx <= 2; dx += 2) {
                for (int dz = 0; dz <= 2; dz += 2) {
                    helper.setBlock(x + dx, y, z + dz, Blocks.OAK_LOG);
                }
            }
        }
        for (int y = 2; y <= 3; y++) {
            helper.setBlock(x + 1, y, z, Blocks.OAK_PLANKS);
            helper.setBlock(x, y, z + 1, Blocks.OAK_PLANKS);
            helper.setBlock(x + 2, y, z + 1, Blocks.OAK_PLANKS);
        }
        helper.setBlock(x + 1, 2, z + 2, Blocks.OAK_PLANKS);
        helper.setBlock(x + 1, 3, z + 2, Blocks.GLASS);
        helper.setBlock(x + 1, 2, z, Blocks.OAK_DOOR);
        helper.setBlock(
                x + 1, 3, z, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(x + 1, 2, z + 1, Blocks.TORCH);
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                helper.setBlock(x + dx, 5, z + dz, Blocks.OAK_PLANKS);
            }
        }
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                boolean ring = dx == -1 || dx == 3 || dz == -1 || dz == 3;
                if (ring) {
                    helper.setBlock(x + dx, 4, z + dz, leaves);
                }
            }
        }
    }

    static void buildLogHut(GameTestHelper helper, int x, int z, int size) {
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                for (int y = 1; y <= size; y++) {
                    helper.setBlock(x + dx, y, z + dz, Blocks.OAK_LOG);
                }
            }
        }
    }

    static void buildLogPillar(GameTestHelper helper, int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            helper.setBlock(x, y, z, Blocks.OAK_LOG);
        }
    }
}
