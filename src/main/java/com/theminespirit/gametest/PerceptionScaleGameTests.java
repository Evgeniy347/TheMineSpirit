package com.theminespirit.gametest;

import static com.theminespirit.gametest.PerceptionTestSupport.spawnSpirit;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.perception.PerceptionQuery;
import com.theminespirit.core.script.PerceptionParams;
import com.theminespirit.neoforge.ScanScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerceptionScaleGameTests {
    private static final int SPIRITS = 20;
    private static final int DRIVEN_TICKS = 100;
    private static final int SETTLE_TICKS = 1;
    private static final int POSITIONS_PER_SLICE = 4096;
    private static final int SLAB_REACH = 40;
    private static final int SCAN_RADIUS = 32;

    private PerceptionScaleGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void scanBlocks_TwentySpiritsHundredTicks_StaysWithinPortionBudgets(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        List<BlockPos> slab = buildSlab(helper);
        PerceptionQuery query =
                PerceptionParams.blocks(Map.of("radius", SCAN_RADIUS, "filter", List.of("minecraft:stone")));
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int i = 0; i < SPIRITS; i++) {
            spirits.add(spawnSpirit(helper, 1 + i % 8, 1, 1 + i / 8));
        }
        List<ScanScript> current = new ArrayList<>();
        long[] totals = new long[3];
        int[] maxReadsPerStep = {0};
        int[] driven = {0};
        helper.onEachTick(() -> {
            if (driven[0] >= DRIVEN_TICKS || !spirits.stream().allMatch(spirit -> spirit.tickCount >= SETTLE_TICKS)) {
                return;
            }
            driven[0]++;
            for (int i = 0; i < SPIRITS; i++) {
                if (current.size() <= i) {
                    current.add(new ScanScript(spirits.get(i), query));
                }
                ScanScript script = current.get(i);
                int examinedBefore = script.examinedPositions();
                ScriptStatus status = script.tick();
                maxReadsPerStep[0] = Math.max(maxReadsPerStep[0], script.examinedPositions() - examinedBefore);
                if (status != ScriptStatus.RUNNING) {
                    totals[0] += script.examinedPositions();
                    totals[1] += script.stepCalls();
                    totals[2] += script.entityQueries();
                    current.set(i, new ScanScript(spirits.get(i), query));
                }
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    driven[0] >= DRIVEN_TICKS,
                    "наблюдение идёт: отработано тиков " + driven[0] + " из " + DRIVEN_TICKS);
            long examined = totals[0]
                    + current.stream().mapToLong(ScanScript::examinedPositions).sum();
            long steps = totals[1]
                    + current.stream().mapToLong(ScanScript::stepCalls).sum();
            long entityQueries = totals[2]
                    + current.stream().mapToLong(ScanScript::entityQueries).sum();
            helper.assertTrue(examined > 0, "тест не доказывает бюджет: плита из камня не прочитана ни одним духом");
            helper.assertTrue(
                    maxReadsPerStep[0] <= ScanTuning.SLICES_PER_TICK * POSITIONS_PER_SLICE,
                    "один дух за один тик обязан читать не больше " + ScanTuning.SLICES_PER_TICK + " срезов по "
                            + POSITIONS_PER_SLICE + " позиций, а прочитал " + maxReadsPerStep[0]
                            + ": порция скана снята, куб читается разом");
            helper.assertTrue(
                    examined <= (long) SPIRITS * DRIVEN_TICKS * ScanTuning.SLICES_PER_TICK * POSITIONS_PER_SLICE,
                    "за " + DRIVEN_TICKS + " тиков " + SPIRITS + " духов обязаны прочитать не больше "
                            + (long) SPIRITS * DRIVEN_TICKS * ScanTuning.SLICES_PER_TICK * POSITIONS_PER_SLICE
                            + " позиций, а прочитали " + examined);
            helper.assertTrue(
                    steps <= (long) SPIRITS * DRIVEN_TICKS,
                    "скан делает не больше одного шага порции за тик на духа: шагов " + steps + " при пределе "
                            + (long) SPIRITS * DRIVEN_TICKS);
            helper.assertTrue(
                    entityQueries <= (long) SPIRITS * DRIVEN_TICKS,
                    "запросов сущностей не больше, чем завершённых сканов, то есть не больше одного на тик на духа: "
                            + entityQueries + " при пределе " + (long) SPIRITS * DRIVEN_TICKS);
            clearSlab(helper, slab);
            GameTestFixtures.finish(helper);
        });
    }

    private static List<BlockPos> buildSlab(GameTestHelper helper) {
        List<BlockPos> placed = new ArrayList<>();
        for (int x = -SLAB_REACH; x <= SLAB_REACH; x++) {
            for (int z = -SLAB_REACH; z <= SLAB_REACH; z++) {
                BlockPos absolute = helper.absolutePos(new BlockPos(x, 0, z));
                if (helper.getLevel().hasChunk(absolute.getX() >> 4, absolute.getZ() >> 4)
                        && helper.getLevel().getBlockState(absolute).isAir()) {
                    helper.getLevel().setBlock(absolute, Blocks.STONE.defaultBlockState(), 2);
                    placed.add(absolute);
                }
            }
        }
        return placed;
    }

    private static void clearSlab(GameTestHelper helper, List<BlockPos> slab) {
        for (BlockPos absolute : slab) {
            helper.getLevel().setBlock(absolute, Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
