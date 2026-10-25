package com.theminespirit.gametest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.scene.SceneBlock;
import com.theminespirit.core.scene.SceneGenerator;
import com.theminespirit.core.scene.ScenePlan;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class GeneratedScene {
    static final int CENTER = 64;
    static final int SURFACE = 7;
    private static final int DIRT_DEPTH = 3;
    private static final String SEED_PROPERTY = "theminespirit.gametest.seed";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SplittableRandom FRESH = new SplittableRandom();

    private GeneratedScene() {}

    static long seed() {
        String fixed = System.getProperty(SEED_PROPERTY, "").trim();
        if (!fixed.isEmpty()) {
            return Long.parseLong(fixed);
        }
        synchronized (FRESH) {
            return FRESH.nextLong();
        }
    }

    static ScenePlan place(GameTestHelper helper, long seed, String batch) {
        ScenePlan plan = SceneGenerator.generate(seed);
        LOGGER.info(
                "[scene] сид {}: порода {}, повтор — scripts/test.sh gametest --seed {} {}",
                seed,
                plan.wood(),
                seed,
                batch);
        int radius = plan.radius();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                column(helper, plan, x, z);
            }
        }
        ServerLevel level = helper.getLevel();
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        for (SceneBlock block : plan.blocks()) {
            BlockPos pos = at(block.x(), block.y(), block.z());
            helper.setBlock(pos, state(block.block()));
            if (block.settlement()) {
                placed.markSpirit(helper.absolutePos(pos));
            }
        }
        return plan;
    }

    static BlockPos at(int x, int y, int z) {
        return new BlockPos(CENTER + x, SURFACE + y, CENTER + z);
    }

    private static void column(GameTestHelper helper, ScenePlan plan, int x, int z) {
        int top = plan.height(x, z);
        for (int y = plan.bottom(); y <= top; y++) {
            Block block = y == top ? Blocks.GRASS_BLOCK : y > top - DIRT_DEPTH ? Blocks.DIRT : Blocks.STONE;
            helper.setBlock(at(x, y, z), block);
        }
        for (int y = top + 1; y <= SceneGenerator.RELIEF + 1; y++) {
            helper.setBlock(at(x, y, z), Blocks.AIR);
        }
    }

    private static BlockState state(String id) {
        ResourceLocation key = ResourceLocation.withDefaultNamespace(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            throw new IllegalArgumentException("Блок сцены " + id + " не найден в реестре: план сцены устарел");
        }
        return BuiltInRegistries.BLOCK.get(key).defaultBlockState();
    }
}
