package com.theminespirit.gametest;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.MinePlot;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.FirstStepsRunner;
import com.theminespirit.neoforge.board.GraphTargets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

final class CraftStationCheck {
    private CraftStationCheck() {}

    static void unreachableStationSkipsToAnother(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockCoord anchor = new BlockCoord(origin.getX() + 2, origin.getY() + 1, origin.getZ() + 2);
        int x = anchor.x();
        int z = anchor.z();
        StageSite site = new StageSite(
                1,
                anchor,
                new Placement("hut", "hut", 1, x, z, 5, 6, Placement.Facing.SOUTH),
                new Placement("yard", "yard", 1, x + 9, z, 3, 3, Placement.Facing.SOUTH),
                new Placement("mine", "mine", 1, x, z + 9, 3, 3, Placement.Facing.SOUTH),
                new MinePlot(anchor, anchor, x, z + 9, x + 3, z + 12),
                new Placement("orchard", "orchard", 1, x + 14, z, 5, 5, Placement.Facing.SOUTH),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ColumnMask.rect(x, z, x + 20, z + 20),
                ColumnMask.rect(x, z, x + 20, z + 20),
                ColumnMask.rect(x, z, x, z),
                ColumnMask.rect(x, z, x + 20, z + 20),
                ColumnMask.rect(x, z, x + 20, z + 20),
                new FenceLine(List.of(), List.of()));
        SettlementFacts facts = new SettlementFacts(
                true,
                true,
                true,
                1,
                true,
                true,
                Set.of(),
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                Set.of(BoardCase.STOCK_CHESTS),
                Set.of());
        FirstStepsRunner.StageScan scan = new FirstStepsRunner.StageScan(site, "oak", anchor, facts, false);
        BoardService service = BoardRegistry.get(helper.getLevel());
        service.sources.restoreGraphFailures(List.of());
        SupplyGraph graph = DataTables.INSTANCE.graph().current();
        helper.setBlock(relative(helper, anchor), Blocks.CRAFTING_TABLE);
        helper.setBlock(relative(helper, site.workYard()), Blocks.CRAFTING_TABLE);
        long now = helper.getLevel().getGameTime();
        BlockCoord preferred = GraphTargets.craftPoint(helper.getLevel(), scan, service, graph, now);
        try {
            service.sources.graphFailure(
                    new BoardTicket(
                            "graph:chop_jungle_log",
                            BoardCase.GATHER_MATERIALS,
                            Optional.empty(),
                            1,
                            now,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.of()),
                    TicketTarget.at(preferred),
                    new GraphBlock.Path(preferred.x() + "," + preferred.y() + "," + preferred.z()),
                    now);

            BlockCoord chosen = GraphTargets.craftPoint(helper.getLevel(), scan, service, graph, now);

            helper.assertTrue(
                    !preferred.equals(chosen),
                    "Недостижимая стоянка ремесла не выбирается снова: работа переходит к другой стоянке, "
                            + "а не повторяет мёртвую точку; была " + preferred + ", выбрано " + chosen);
        } finally {
            service.sources.restoreGraphFailures(List.of());
        }
    }

    private static BlockPos relative(GameTestHelper helper, BlockCoord at) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new BlockPos(at.x() - origin.getX(), at.y() - origin.getY(), at.z() - origin.getZ());
    }
}
