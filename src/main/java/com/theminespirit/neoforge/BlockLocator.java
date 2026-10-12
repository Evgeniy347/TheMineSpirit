package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.SectionCursor;
import com.theminespirit.core.observation.SectionSlice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class BlockLocator {
    private BlockLocator() {
    }

    public static Search search(Level level, BlockPos center, int radius, Set<Block> blocks) {
        return new Search(level, center, radius, blocks);
    }

    public static final class Search {
        private final Level level;
        private final BlockPos center;
        private final BlockCoord centerCoord;
        private final Set<Block> blocks;
        private final SectionCursor cursor;
        private final Set<Long> skippedChunks = new HashSet<>();
        private int examinedPositions;
        private long countedTick = Long.MIN_VALUE;
        private int slicesThisTick;
        private int maxSlicesPerTick;

        private Search(Level level, BlockPos center, int radius, Set<Block> blocks) {
            this.level = level;
            this.center = center;
            this.centerCoord = new BlockCoord(center.getX(), center.getY(), center.getZ());
            this.blocks = blocks;
            this.cursor = LoadedWorld.cursor(LoadedWorld.cube(level, centerCoord, radius), centerCoord);
        }

        public List<BlockPos> step(int maxSlices) {
            long tick = level.getGameTime();
            if (tick != countedTick) {
                countedTick = tick;
                slicesThisTick = 0;
            }
            List<BlockPos> found = new ArrayList<>();
            for (SectionSlice slice : cursor.next(maxSlices)) {
                slicesThisTick++;
                maxSlicesPerTick = Math.max(maxSlicesPerTick, slicesThisTick);
                List<BlockPos> inSlice = new ArrayList<>();
                scan(slice, inSlice);
                inSlice.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(center))
                        .thenComparing(Comparator.naturalOrder()));
                found.addAll(inSlice);
            }
            return found;
        }

        public boolean done() {
            return cursor.isDone();
        }

        public int maxSlicesPerTick() {
            return maxSlicesPerTick;
        }

        public int examinedPositions() {
            return examinedPositions;
        }

        public int skippedChunks() {
            return skippedChunks.size();
        }

        private void scan(SectionSlice slice, List<BlockPos> found) {
            LevelChunk chunk = LoadedWorld.chunk(level, slice.sectionX(), slice.sectionZ(), skippedChunks);
            if (chunk == null) {
                return;
            }
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(slice.sectionY()));
            if (section.hasOnlyAir() || !section.maybeHas(state -> blocks.contains(state.getBlock()))) {
                return;
            }
            for (int x = slice.bounds().min().x(); x <= slice.bounds().max().x(); x++) {
                for (int y = slice.bounds().min().y(); y <= slice.bounds().max().y(); y++) {
                    for (int z = slice.bounds().min().z(); z <= slice.bounds().max().z(); z++) {
                        examinedPositions++;
                        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                        if (blocks.contains(state.getBlock())) {
                            found.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
    }
}
