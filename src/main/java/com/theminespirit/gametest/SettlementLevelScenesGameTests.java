package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettlementLevelScenesGameTests {
    private SettlementLevelScenesGameTests() {}

    @GameTest(template = "arena", timeoutTicks = 40)
    public static void settlementPlot_Settle_HeightCountedOnce(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SettlementPlotStore store = SettlementPlotStore.get(helper.getLevel());
        store.clear();
        TownPlan plan = plan(helper, 90);
        store.fixTown(plan);
        int x = plan.anchor().x();
        int z = plan.anchor().z();

        helper.assertFalse(store.levelMarked(), "Сохранение старого мира без отметки обязано пересчитать высоту");
        store.settle(new SettlementPlot(new BlockCoord(x, 90, z), 88, 92, 9, "oak"));

        helper.assertTrue(store.levelMarked(), "После обмера высота помечена правилом SettlementLevel");
        helper.assertTrue(
                store.town().orElseThrow().anchor().y() == 90,
                "Высота поселения попадает в план: второго источника (медианы домов) не остаётся");
        store.settle(new SettlementPlot(new BlockCoord(x, 94, z), 92, 96, 9, "oak"));
        helper.assertTrue(
                store.town().orElseThrow().anchor().y() == 90,
                "Высота считается один раз: повторный обмер её не переносит");
        store.clear();
        helper.assertFalse(store.levelMarked(), "Новый мир начинает без отметки высоты");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static TownPlan plan(GameTestHelper helper, int level) {
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        BlockPos max = helper.absolutePos(new BlockPos(10, 0, 10));
        int x1 = Math.min(min.getX(), max.getX());
        int z1 = Math.min(min.getZ(), max.getZ());
        int x2 = Math.max(min.getX(), max.getX());
        int z2 = Math.max(min.getZ(), max.getZ());
        ColumnMask empty = new ColumnMask(x1, z1, x2 - x1 + 1, z2 - z1 + 1, new BitSet());
        return new TownPlan(
                new BlockCoord(x1 + 5, level, z1 + 5),
                ColumnMask.rect(x1, z1, x2, z2),
                new TownStreets(ColumnMask.rect(x1, z1, x2, z1), empty, empty),
                List.of(),
                new FenceLine(List.of(), List.of()),
                List.of());
    }
}
