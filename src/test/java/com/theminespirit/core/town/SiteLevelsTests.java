package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class SiteLevelsTests {
    private static final StageSite SITE = TownFixtures.flatSite();
    private static final int LEVEL = TownFixtures.LEVEL;

    @Test
    void level_AnySite_EqualsAnchorY() {
        assertEquals(
                LEVEL - 1,
                SiteLevels.level(SITE),
                "Уровень поселения один и берётся из якоря участка: иначе площадки, ограда и расчистка живут на "
                        + "разных высотах");
    }

    @Test
    void extra_ColumnHigherThanLevel_StartsOneAboveLevel() {
        ColumnMask.Cell cell = freeCell();
        List<PlotLeveling.Box> extra = SiteLevels.extra(SITE, tops(cell, LEVEL + 3));

        assertEquals(
                List.of(
                        new PlotLeveling.Box(cell.x(), LEVEL, cell.z(), cell.x(), LEVEL, cell.z()),
                        new PlotLeveling.Box(cell.x(), LEVEL + 1, cell.z(), cell.x(), LEVEL + 1, cell.z()),
                        new PlotLeveling.Box(cell.x(), LEVEL + 2, cell.z(), cell.x(), LEVEL + 2, cell.z())),
                extra,
                "Лишним считается только то, что выше уровня: блок на уровне y — опора, его не срезают");
    }

    @Test
    void fenceY_AnySite_OneAboveLevel() {
        assertEquals(
                LEVEL,
                SiteLevels.fenceY(SITE),
                "Вся ограда стоит на одном уровне y+1: столб над уровнем, а не по высоте своей земли");
    }

    @Test
    void extra_FencePostColumn_CutsBlocksAboveLevel() {
        ColumnMask.Cell post = SITE.ring().posts().get(0);

        assertTrue(
                !SiteLevels.extra(SITE, tops(post, LEVEL + 3)).isEmpty(),
                "Клетку столба ограды расчищают как все: дерево над ней не даёт поставить забор");
    }

    @Test
    void fenceReady_ColumnAtLevel_IsReady() {
        ColumnMask.Cell post = SITE.ring().posts().get(0);

        assertTrue(
                SiteLevels.fenceReady(SITE, post, tops(post, LEVEL)),
                "Клетка готова, когда выше y нет блоков: земля на уровне y столбу не мешает");
        assertFalse(
                SiteLevels.fenceReady(SITE, post, tops(post, LEVEL + 1)),
                "Блок над уровнем держит столб: его сначала срезает расчистка");
    }

    @Test
    void needsSupport_ColumnAtLevel_AddsOneDirt() {
        ColumnMask.Cell post = SITE.ring().posts().get(0);

        assertTrue(
                SiteLevels.needsSupport(SITE, post, tops(post, LEVEL - 1)),
                "Под столбом нет опоры на уровне y — подсыпают одну землю только под ним");
        assertFalse(
                SiteLevels.needsSupport(SITE, post, tops(post, LEVEL)),
                "Опора на уровне y уже есть — лишнюю землю под столб не кладут");
    }

    @Test
    void readyPosts_RingWithOneClearedColumn_ReturnsOnlyCleared() {
        ColumnMask.Cell first = SITE.ring().posts().get(0);
        IntBinaryOperator tops = (x, z) -> x == first.x() && z == first.z() ? LEVEL : LEVEL + 2;

        assertEquals(
                List.of(first),
                SiteLevels.readyPosts(SITE, tops),
                "В работу берут только те столбы, чьи клетки уже освободила расчистка: ограда и расчистка не ждут "
                        + "друг друга целиком");
    }

    @Test
    void extra_ColumnAtLevel_HasNothingExtra() {
        ColumnMask.Cell cell = SITE.level().cells().get(0);

        assertEquals(
                List.of(),
                SiteLevels.extra(SITE, tops(cell, LEVEL)),
                "Колонка, у которой всё выше y уже срезано, расчистку не держит");
    }

    @Test
    void extra_PitBelowLevel_HasNothingExtra() {
        ColumnMask.Cell cell = SITE.level().cells().get(0);

        assertEquals(
                List.of(),
                SiteLevels.extra(SITE, tops(cell, LEVEL - 3)),
                "Яма ниже y расчистку не держит: иначе ремонт уровня крутится вечно из-за нехватки земли");
    }

    @Test
    void extra_BuildingColumn_KeepsBlocksAboveLevel() {
        ColumnMask.Cell built = SITE.level().cells().stream()
                .filter(cell -> SITE.planCell(cell.x(), LEVEL + 2, cell.z()))
                .filter(cell -> !SITE.ringPost(cell.x(), cell.z()))
                .findFirst()
                .orElseThrow();

        assertEquals(
                List.of(),
                SiteLevels.extra(SITE, tops(built, LEVEL + 5)),
                "Блоки построек по плану не срезают: расчистка не ломает уже стоящую хижину или двор");
    }

    @Test
    void leveling_BuildingColumn_CutsStrayBlocksAboveLevel() {
        ColumnMask.Cell built = SITE.level().cells().stream()
                .filter(cell -> SITE.planCell(cell.x(), LEVEL + 2, cell.z()))
                .findFirst()
                .orElseThrow();

        assertFalse(
                SiteLevels.leveling(SITE, tops(built, LEVEL + 5)).cut().isEmpty(),
                "Расчистка срезает и дерево над площадкой постройки: постройки плана ещё не стоят, а их клетки — "
                        + "часть участка");
    }

    @Test
    void chestSpot_FlatSite_LiesOutsidePlan() {
        BlockCoord spot = SiteLevels.chestSpot(SITE);

        assertEquals(LEVEL, spot.y(), "Временный сундук стоит на уровне поселения: выше y его срежет расчистка");
        assertFalse(
                SITE.planCell(spot.x(), spot.y(), spot.z()),
                "Временный сундук не встаёт в клетку постройки плана: иначе он мешает хижине и двору");
        assertFalse(
                SITE.ringPost(spot.x(), spot.z()),
                "Временный сундук не встаёт в клетку столба ограды: столб и сундук делят одну клетку");
    }

    @Test
    void leveling_ColumnHigherThanLevel_HasCutWithoutFill() {
        ColumnMask.Cell cell = freeCell();
        PlotLeveling plan = SiteLevels.leveling(SITE, tops(cell, LEVEL + 3));

        assertEquals(3, plan.cut().size(), "Расчистка срезает всё лишнее выше y, слой за слоем");
        assertEquals(List.of(), plan.fill(), "Расчистка ничего не подсыпает: ямы ниже y её не держат");
        assertEquals(
                List.of(),
                plan.voids(),
                "Расчистка не ищет пустоты под участком: это работа выравнивания, а не расчистки");
    }

    private static IntBinaryOperator tops(ColumnMask.Cell cell, int top) {
        return (x, z) -> x == cell.x() && z == cell.z() ? top : LEVEL;
    }

    private static ColumnMask.Cell freeCell() {
        BlockCoord chest = SiteLevels.chestSpot(SITE);
        return SITE.level().cells().stream()
                .filter(cell -> !SITE.planCell(cell.x(), LEVEL, cell.z()))
                .filter(cell -> !SITE.ringPost(cell.x(), cell.z()))
                .filter(cell -> cell.x() != chest.x() || cell.z() != chest.z())
                .findFirst()
                .orElseThrow();
    }
}
