package com.theminespirit.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

final class GameTestChunks {
    private GameTestChunks() {}

    static void hold(GameTestHelper helper, BlockPos center, int radius) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(center);
        ChunkPos from = new ChunkPos(absolute.offset(-radius, 0, -radius));
        ChunkPos to = new ChunkPos(absolute.offset(radius, 0, radius));
        List<Long> forced = new ArrayList<>();
        for (int cx = from.x; cx <= to.x; cx++) {
            for (int cz = from.z; cz <= to.z; cz++) {
                level.getChunk(cx, cz);
                if (level.setChunkForced(cx, cz, true)) {
                    forced.add(ChunkPos.asLong(cx, cz));
                }
            }
        }
        helper.testInfo.addListener(new Release(level, new ChunkHold(forced)));
    }

    // mc: слушатель пачки в GameTestRunner стоит раньше слушателей сцены и на последнем тесте пачки сразу
    // ставит следующую пачку, загружая её чанки; отпустить их здесь — следующая сцена ждёт чанки вечно
    static Set<Long> liveTestChunks(GameTestRunner runner) {
        Set<Long> chunks = new HashSet<>();
        for (GameTestInfo other : runner.getTestInfos()) {
            if (!other.isDone() && other.getStructureBlockPos() != null) {
                StructureUtils.getStructureBoundingBox(other.getStructureBlockEntity())
                        .intersectingChunks()
                        .forEach(chunk -> chunks.add(chunk.toLong()));
            }
        }
        return chunks;
    }

    private record Release(ServerLevel level, ChunkHold hold) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo testInfo) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            release(runner);
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            release(runner);
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {}

        private void release(GameTestRunner runner) {
            for (long chunk : hold.releasable(liveTestChunks(runner))) {
                level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            }
        }
    }
}
