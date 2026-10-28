package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopTestSupport.FIELD_HEIGHT;
import static com.theminespirit.gametest.ChopTestSupport.PLATFORM;
import static com.theminespirit.gametest.ChopTestSupport.SEARCH_RADIUS;
import static com.theminespirit.gametest.ChopTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.ChopTestSupport.SPIRIT_START;
import static com.theminespirit.gametest.ChopTestSupport.TOOL_SLOT;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.ChopTreeParams;
import com.theminespirit.neoforge.ChopTreeScript;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class ChopTestSupport {
    static final String FIELD = "arena";
    static final int PLATFORM = 11;
    static final int PIT_DEPTH = 4;
    static final int FIELD_HEIGHT = 24;
    static final int TALL_FIELD_HEIGHT = 16;
    static final int SEARCH_RADIUS = 8;
    static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    static final int SETTLE_TICKS = 1;
    static final int TOOL_SLOT = 0;
    static final int HUT_POST_LOGS = 12;
    static final int TALL_TREE_LOGS = 60;
    static final long MEASURED_TREE_READS = 559;
    static final long MAX_TREE_READS = 838;
    static final int SECTION_POSITIONS = 16 * 16 * 16;
    static final Logger LOGGER = LogUtils.getLogger();
    static final int BIG_BUILDING_LOGS = 7 * 7 * 7;

    private ChopTestSupport() {}

    static ChopTreeParams chopParams(String species, int count) {
        return chopParams(species, count, false);
    }

    static ChopTreeParams chopParams(String species, int count, boolean replant) {
        return ChopTreeParams.from(
                Map.of("species", species, "count", count, "radius", SEARCH_RADIUS, "replant", replant));
    }

    static ScriptStatus[] driveScript(GameTestHelper helper, SpiritEntity spirit, ChopTreeScript script) {
        ScriptStatus[] status = {ScriptStatus.RUNNING};
        helper.onEachTick(() -> {
            if (status[0] == ScriptStatus.RUNNING && spirit.tickCount >= SETTLE_TICKS) {
                status[0] = script.tick();
            }
        });
        return status;
    }

    static SpiritEntity spawnChopper(GameTestHelper helper) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));
        return spirit;
    }

    static ScriptCall chopCall(String species, int count) {
        return chopCall(species, count, false);
    }

    static ScriptCall chopCall(String species, int count, boolean replant) {
        return new ScriptCall(
                "chop_tree", Map.of("species", species, "count", count, "radius", SEARCH_RADIUS, "replant", replant));
    }

    static int positionsInSectionsWithLogs(Level level, BlockPos center, int radius, Block log) {
        int minY = Math.max(center.getY() - radius, level.getMinBuildHeight());
        int maxY = Math.min(center.getY() + radius, level.getMaxBuildHeight() - 1);
        Map<String, Boolean> sectionHasLog = new HashMap<>();
        int count = 0;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
                    int sectionY = y >> 4;
                    if (!(level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) instanceof LevelChunk)) {
                        continue;
                    }
                    String key = chunkX + "," + sectionY + "," + chunkZ;
                    if (sectionHasLog.computeIfAbsent(
                            key, k -> sectionContains(level, chunkX, sectionY, chunkZ, log))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    static boolean sectionContains(Level level, int chunkX, int sectionY, int chunkZ, Block block) {
        for (int dx = 0; dx < 16; dx++) {
            for (int dy = 0; dy < 16; dy++) {
                for (int dz = 0; dz < 16; dz++) {
                    BlockPos pos = new BlockPos(chunkX * 16 + dx, sectionY * 16 + dy, chunkZ * 16 + dz);
                    if (level.getBlockState(pos).is(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static int countBlocksInWorld(GameTestHelper helper, Block block) {
        int count = 0;
        for (int x = -2; x < PLATFORM + 2; x++) {
            for (int y = 0; y < FIELD_HEIGHT; y++) {
                for (int z = -2; z < PLATFORM + 2; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
