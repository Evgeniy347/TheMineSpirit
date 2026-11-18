package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
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
    void fenceOrigin_DangerAboveSettlement_UsesFenceLevel() {
        BlockCoord roof = new BlockCoord(321, LEVEL + 10, -654);

        assertEquals(
                new BlockCoord(roof.x(), LEVEL, roof.z()),
                SiteLevels.fenceOrigin(SITE, roof),
                "Низ схемы забора — уровень ограды поселения, а не высота точки опасности: иначе забор встаёт на "
                        + "крышу склада (80), дух снизу считается «в яме» и копает вверх полным поиском пути");
    }

    @Test
    void fenceOrigin_PointOnFenceLevel_KeepsCoordinates() {
        BlockCoord at = new BlockCoord(321, LEVEL, -654);

        assertEquals(
                at,
                SiteLevels.fenceOrigin(SITE, at),
                "Точка уже на уровне ограды остаётся собой: правило не двигает забор по горизонтали");
    }

    @Test
    void fenceOrigin_PointBelowSettlement_RisesToFenceLevel() {
        BlockCoord below = new BlockCoord(321, LEVEL - 7, -654);

        assertEquals(
                new BlockCoord(below.x(), LEVEL, below.z()),
                SiteLevels.fenceOrigin(SITE, below),
                "Даже если точка опасности ниже площадки, забор ставится на уровень ограды: яма под поселением "
                        + "не становится основанием забора");
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
    void fenceReady_BuiltFenceAtLevel_IsReady() {
        ColumnMask.Cell post = SITE.ring().posts().get(0);

        assertTrue(
                SiteLevels.fenceReady(SITE, post, tops(post, LEVEL + 1), true),
                "Стоящий забор на уровне y не делает столб неготовым: иначе после постройки фрагмента повторная "
                        + "выдача видит пустой план и закрывается без работы");
        assertFalse(
                SiteLevels.fenceReady(SITE, post, tops(post, LEVEL + 1), false),
                "Посторонний блок на уровне y держит столб: его срезает расчистка, забором он не считается");
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
    void leveling_ForeignObjectAbovePlanColumn_IsCutBeforeItsSupport() {
        ColumnMask.Cell built = SITE.level().cells().stream()
                .filter(cell -> SITE.planCell(cell.x(), LEVEL + 2, cell.z()))
                .filter(cell -> !SITE.ringPost(cell.x(), cell.z()))
                .findFirst()
                .orElseThrow();
        Predicate<BlockCoord> plan = at -> at.equals(new BlockCoord(built.x(), LEVEL, built.z()));

        PlotLeveling cut = SiteLevels.leveling(SITE, tops(built, LEVEL + 3), plan);

        assertEquals(
                List.of(
                        new PlotLeveling.Box(built.x(), LEVEL + 1, built.z(), built.x(), LEVEL + 1, built.z()),
                        new PlotLeveling.Box(built.x(), LEVEL + 2, built.z(), built.x(), LEVEL + 2, built.z())),
                cut.cut(),
                "Схема плана защищает только свои блоки: чужой сундук над площадкой двора попадает в срезку, "
                        + "а не прячется за широкой колонкой постройки");
        assertEquals(
                LEVEL + 2,
                cut.cutTopDown().get(0).y1(),
                "Срезка идёт сверху вниз: чужой объект снимается до блока-опоры и не остаётся висеть");
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
    void leveling_MixedColumns_CutAboveFillBelow() {
        List<ColumnMask.Cell> cells = freeCells(3);
        ColumnMask.Cell tall = cells.get(0);
        ColumnMask.Cell low = cells.get(1);
        ColumnMask.Cell deep = cells.get(2);
        IntBinaryOperator tops = (x, z) -> {
            if (x == tall.x() && z == tall.z()) {
                return LEVEL + 3;
            }
            if (x == low.x() && z == low.z()) {
                return LEVEL - 2;
            }
            if (x == deep.x() && z == deep.z()) {
                return LEVEL - 11;
            }
            return LEVEL;
        };

        PlotLeveling plan = SiteLevels.leveling(SITE, tops);

        assertEquals(
                List.of(
                        new PlotLeveling.Box(tall.x(), LEVEL, tall.z(), tall.x(), LEVEL, tall.z()),
                        new PlotLeveling.Box(tall.x(), LEVEL + 1, tall.z(), tall.x(), LEVEL + 1, tall.z()),
                        new PlotLeveling.Box(tall.x(), LEVEL + 2, tall.z(), tall.x(), LEVEL + 2, tall.z())),
                plan.cut(),
                "Выравнивание срезает всё лишнее выше уровня, слой за слоем");
        assertEquals(
                List.of(
                        new PlotLeveling.Box(low.x(), LEVEL - 2, low.z(), low.x(), LEVEL - 2, low.z()),
                        new PlotLeveling.Box(low.x(), LEVEL - 1, low.z(), low.x(), LEVEL - 1, low.z())),
                plan.fill(),
                "Выравнивание подсыпает низину до уровня плана: весь план живёт на одном уровне");
        assertTrue(
                plan.fill().stream().noneMatch(box -> box.x1() == deep.x() && box.z1() == deep.z()),
                "Низину глубже " + PlotLeveling.MAX_SHIFT + " блоков не засыпаем: это уже обрыв, а не яма");
        assertEquals(
                List.of(),
                plan.voids(),
                "Выравнивание не ищет пустоты под участком: это работа расчистки, а не уровня");
        assertTrue(
                SiteLevels.leveling(SITE, tops, at -> at.x() == low.x() && at.z() == low.z())
                        .fill()
                        .isEmpty(),
                "Клетку, где блок ставит сам план, подсыпка не дублирует");
    }

    private static IntBinaryOperator tops(ColumnMask.Cell cell, int top) {
        return (x, z) -> x == cell.x() && z == cell.z() ? top : LEVEL;
    }

    private static List<ColumnMask.Cell> freeCells(int count) {
        BlockCoord chest = SiteLevels.chestSpot(SITE);
        List<ColumnMask.Cell> found = SITE.zone().cells().stream()
                .filter(cell -> !SITE.planCell(cell.x(), LEVEL, cell.z()))
                .filter(cell -> !SITE.ringPost(cell.x(), cell.z()))
                .filter(cell -> cell.x() != chest.x() || cell.z() != chest.z())
                .limit(count)
                .toList();
        assertEquals(count, found.size(), "На площадке мало свободных клеток для сцены выравнивания");
        return found;
    }

    private static ColumnMask.Cell freeCell() {
        BlockCoord chest = SiteLevels.chestSpot(SITE);
        return SITE.zone().cells().stream()
                .filter(cell -> !SITE.planCell(cell.x(), LEVEL, cell.z()))
                .filter(cell -> !SITE.ringPost(cell.x(), cell.z()))
                .filter(cell -> cell.x() != chest.x() || cell.z() != chest.z())
                .findFirst()
                .orElseThrow();
    }
}
