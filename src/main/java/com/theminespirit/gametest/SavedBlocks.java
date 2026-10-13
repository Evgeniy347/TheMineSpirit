package com.theminespirit.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class SavedBlocks {
    private final GameTestHelper helper;
    private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();

    SavedBlocks(GameTestHelper helper) {
        this.helper = helper;
    }

    void set(BlockPos relative, Block block) {
        before.putIfAbsent(relative, helper.getBlockState(relative));
        helper.setBlock(relative, block);
    }

    void restore() {
        before.forEach((relative, state) -> helper.setBlock(relative, state));
        before.clear();
    }
}
