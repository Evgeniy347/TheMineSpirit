package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.ui.MineGeometry;
import com.theminespirit.core.ui.TownMapView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MinePlotTests {
    private static final TownPlan PLAN = TownFixtures.flatPlan();
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void of_PlanMine_SameOriginForBuildAndMineTab() {
        MinePlot plot = SITE.minePlot();
        Placement mine = SITE.mine();
        assertEquals(
                new BlockCoord(mine.x() + StageSite.MINE_SHIFT, SITE.anchor().y() - 1, mine.z() + 1),
                plot.origin(),
                "Начало спуска берётся из места шахты в плане: обе вкладки и стройка обязаны начать один и тот же спуск"
                        + " Иначе тест of_PlanMine_SameOriginForBuildAndMineTab перестанет ловить эту поломку. При смене of_PlanMine_SameOriginForBuildAndMineTab проверь это утверждение.");
        assertEquals(
                plot.origin(),
                SITE.mineEntrance(),
                "Вкладка «Шахта» рисует спуск от начала в плане"
                        + " Иначе тест of_PlanMine_SameOriginForBuildAndMineTab перестанет ловить эту поломку. При смене of_PlanMine_SameOriginForBuildAndMineTab проверь это утверждение.");
        TicketTarget target = TicketTarget.at(SITE.workYard()).withWork(new StageWork(SITE, List.of()));
        assertEquals(
                plot.origin(),
                TicketPlans.mineDescentOrigin(target),
                "Стройка ставит спуск от того же начала"
                        + " Иначе тест of_PlanMine_SameOriginForBuildAndMineTab перестанет ловить эту поломку. При смене of_PlanMine_SameOriginForBuildAndMineTab проверь это утверждение.");

        MineLayout layout = layout();
        assertEquals(
                plot.entrance(),
                layout.entrance(),
                "Вход схемы считается поворотом от того же начала"
                        + " Иначе тест of_PlanMine_SameOriginForBuildAndMineTab перестанет ловить эту поломку. При смене of_PlanMine_SameOriginForBuildAndMineTab проверь это утверждение.");
        for (BlockCoord cell : layout.air()) {
            assertTrue(
                    plot.covers(cell.x(), cell.z()),
                    "Прямоугольник шахты " + plot + " не покрывает клетку спуска " + cell
                            + " Иначе тест of_PlanMine_SameOriginForBuildAndMineTab перестанет ловить эту поломку. При смене of_PlanMine_SameOriginForBuildAndMineTab проверь это утверждение.");
        }
        TownMapView.Bounds descent =
                MineGeometry.of(layout, TicketPlans.mineTunnelSize()).descentBounds();
        assertEquals(
                new TownMapView.Bounds(plot.x1(), plot.z1(), plot.x2(), plot.z2()),
                descent,
                "Вкладка «Шахта» рисует «Спуск» по тем же углам, что и «План»"
                        + " Иначе тест of_PlanMine_SameOriginForBuildAndMineTab перестанет ловить эту поломку. При смене of_PlanMine_SameOriginForBuildAndMineTab проверь это утверждение.");
    }

    @Test
    void of_RotatedDescent_ExtendsAlongPlanDepth() {
        MinePlot plot = SITE.minePlot();
        Placement mine = SITE.mine();
        assertTrue(
                plot.width() < mine.width() && plot.depth() > mine.depth(),
                "Спуск повёрнут на 90°: он уже площадки в плане и уходит вдоль её глубины дальше ("
                        + plot.width() + "×" + plot.depth() + " против "
                        + mine.width() + "×" + mine.depth() + ")"
                        + " Иначе тест of_RotatedDescent_ExtendsAlongPlanDepth перестанет ловить эту поломку. При смене of_RotatedDescent_ExtendsAlongPlanDepth проверь это утверждение.");
        assertTrue(
                plot.x1() > mine.x() && plot.z1() > mine.z(),
                "Спуск начинается внутри места шахты, а не по его углу: прямоугольник " + plot
                        + " Иначе тест of_RotatedDescent_ExtendsAlongPlanDepth перестанет ловить эту поломку. При смене of_RotatedDescent_ExtendsAlongPlanDepth проверь это утверждение.");
    }

    @Test
    void snapshot_MineLot_MatchesPlacement() {
        TownSnapshot snapshot = TownSnapshot.of(PLAN, SITE, Map.of(), List.of());
        TownSnapshot.Lot lot = snapshot.lots().stream()
                .filter(found -> StageSites.MINE.equals(found.id()))
                .findFirst()
                .orElseThrow();
        Placement mine = SITE.mine();
        assertEquals(
                List.of(mine.x(), mine.z(), mine.width(), mine.depth()),
                List.of(lot.x(), lot.z(), lot.width(), lot.depth()),
                "На «Плане» участок шахты — площадка 16×16, а не подземный спуск: иначе вместо участка рисуется "
                        + "полоса тоннеля и площадку не видно; получено " + lot
                        + " Иначе тест snapshot_MineLot_MatchesPlacement перестанет ловить эту поломку. При смене "
                        + "snapshot_MineLot_MatchesPlacement проверь прямоугольник участка.");
    }

    private static MineLayout layout() {
        Preset preset = DataTables.INSTANCE
                .presets()
                .current()
                .byId(TicketPlans.MINE_DESCENT_SCHEME)
                .orElseThrow();
        return MineLayout.place(
                preset.schematic(),
                preset.entry(),
                SITE.mineEntrance(),
                BuildRotation.parse(TicketPlans.MINE_DESCENT_ROTATION),
                MineLayout.Extent.NONE);
    }
}
