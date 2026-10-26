package com.theminespirit.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

final class GameTestChunks {
    private GameTestChunks() {}

    static void hold(GameTestHelper helper, BlockPos center, int radius) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(center);
        ChunkPos from = new ChunkPos(absolute.offset(-radius, 0, -radius));
        ChunkPos to = new ChunkPos(absolute.offset(radius, 0, radius));
        List<ChunkPos> forced = new ArrayList<>();
        for (int cx = from.x; cx <= to.x; cx++) {
            for (int cz = from.z; cz <= to.z; cz++) {
                level.getChunk(cx, cz);
                if (level.setChunkForced(cx, cz, true)) {
                    forced.add(new ChunkPos(cx, cz));
                }
            }
        }
        helper.testInfo.addListener(new Release(level, List.copyOf(forced)));
    }

    private record Release(ServerLevel level, List<ChunkPos> forced) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo testInfo) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            release();
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            release();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {}

        private void release() {
            forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        }
    }
}
