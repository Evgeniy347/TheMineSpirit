package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.board.FirstStepsRunner;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Повтор проваленной стройки первых шагов просит только непоставленное: после провала ограды на 91/94 доска
 * снова заказывала забор ×72 на всю схему — 1800 тиков лишней добычи (приёмка «джунгли» 24.10).
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FirstStepsRemainderGameTests {
    private static final String BATCH = "first_steps_remainder";
    private static final String FENCE_ITEM = "jungle_fence";
    private static final int PLACED = 3;
    private static final int NEED = 72;

    private FirstStepsRemainderGameTests() {}

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 40)
    public static void remainder_FencePartlyBuilt_NeedsOnlyUnplaced(GameTestHelper helper) {
        BlockPos anchorPos = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockCoord anchor = new BlockCoord(anchorPos.getX(), anchorPos.getY(), anchorPos.getZ());
        BlockCoord origin = SettlementLayout.fenceOrigin(anchor);
        int placed = 0;
        for (Schematic.Entry entry : SchematicResources.load("fence").blocks()) {
            if (placed < PLACED
                    && !entry.optional()
                    && "oak_fence".equals(bare(SchematicBlocks.itemOf(entry.block())))) {
                helper.getLevel()
                        .setBlockAndUpdate(
                                new BlockPos(origin.x() + entry.dx(), origin.y() + entry.dy(), origin.z() + entry.dz()),
                                Blocks.JUNGLE_FENCE.defaultBlockState());
                placed++;
            }
        }
        helper.assertTrue(placed == PLACED, "В схеме ограды не нашлось " + PLACED + " клеток забора: " + placed);
        Map<String, Integer> need = new HashMap<>(Map.of(FENCE_ITEM, NEED));
        FirstStepsRunner.minusPlaced(
                helper.getLevel(),
                TicketPlans.plan(
                        BoardCase.BUILD_FENCE, 1, TicketTarget.at(anchor).withWood("jungle")),
                "jungle",
                need);
        helper.assertTrue(
                need.getOrDefault(FENCE_ITEM, 0) == NEED - PLACED,
                "Нужда ограды — без уже поставленного забора: ждали " + (NEED - PLACED) + ", получили " + need);
        helper.succeed();
    }

    private static String bare(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
