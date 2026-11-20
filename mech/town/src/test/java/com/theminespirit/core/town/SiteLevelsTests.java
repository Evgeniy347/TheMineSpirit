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
    static final StageSite SITE = TownFixtures.flatSite();
    static final int LEVEL = TownFixtures.LEVEL;

    @Test
    void level_AnySite_EqualsAnchorY() {
        assertEquals(
                LEVEL - 1,
                SiteLevels.level(SITE),
                "Уровень поселения один и берётся из якоря участка: иначе площадки, ограда и расчистка живут на "
                        + "разных высотах" + " При смене level_AnySite_EqualsAnchorY проверь это утверждение.");
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
                "Лишним считается только то, что выше уровня: блок на уровне y — опора, его не срезают"
                        + " Иначе тест extra_ColumnHigherThanLevel_StartsOneAboveLevel перестанет ловить эту поломку. При смене extra_ColumnHigherThanLevel_StartsOneAboveLevel проверь это утверждение.");
    }

    @Test
    void fenceY_AnySite_OneAboveLevel() {
        assertEquals(
                LEVEL,
                SiteLevels.fenceY(SITE),
                "Вся ограда стоит на одном уровне y+1: столб над уровнем, а не по высоте своей земли"
                        + " Иначе тест fenceY_AnySite_OneAboveLevel перестанет ловить эту поломку. При смене fenceY_AnySite_OneAboveLevel проверь это утверждение.");
    }

    @Test
    void fenceOrigin_DangerAboveSettlement_UsesFenceLevel() {
        BlockCoord roof = new BlockCoord(321, LEVEL + 10, -654);

        assertEquals(
                new BlockCoord(roof.x(), LEVEL, roof.z()),
                SiteLevels.fenceOrigin(SITE, roof),
                "Низ схемы забора — уровень ограды поселения, а не высота точки опасности: иначе забор встаёт на "
                        + "крышу склада (80), дух снизу считается «в яме» и копает вверх полным поиском пути"
                        + " При смене fenceOrigin_DangerAboveSettlement_UsesFenceLevel проверь это утверждение.");
    }

    @Test
    void fenceOrigin_PointOnFenceLevel_KeepsCoordinates() {
        BlockCoord at = new BlockCoord(321, LEVEL, -654);

        assertEquals(
                at,
                SiteLevels.fenceOrigin(SITE, at),
                "Точка уже на уровне ограды остаётся собой: правило не двигает забор по горизонтали"
                        + " Иначе тест fenceOrigin_PointOnFenceLevel_KeepsCoordinates перестанет ловить эту поломку. При смене fenceOrigin_PointOnFenceLevel_KeepsCoordinates проверь это утверждение.");
    }

    @Test
    void fenceOrigin_PointBelowSettlement_RisesToFenceLevel() {
        BlockCoord below = new BlockCoord(321, LEVEL - 7, -654);

        assertEquals(
                new BlockCoord(below.x(), LEVEL, below.z()),
                SiteLevels.fenceOrigin(SITE, below),
                "Даже если точка опасности ниже площадки, забор ставится на уровень ограды: яма под поселением "
                        + "не становится основанием забора"
                        + " Иначе тест fenceOrigin_PointBelowSettlement_RisesToFenceLevel перестанет ловить эту поломку. При смене fenceOrigin_PointBelowSettlement_RisesToFenceLevel проверь это утверждение.");
    }

    @Test
    void extra_FencePostColumn_CutsBlocksAboveLevel() {
        ColumnMask.Cell post = SITE.ring().posts().get(0);

        assertTrue(
                !SiteLevels.extra(SITE, tops(post, LEVEL + 3)).isEmpty(),
                "Клетку столба ограды расчищают как все: дерево над ней не даёт поставить забор"
                        + " Иначе тест extra_FencePostColumn_CutsBlocksAboveLevel перестанет ловить эту поломку. При смене extra_FencePostColumn_CutsBlocksAboveLevel проверь это утверждение.");
    }

    @Test
    void fenceReady_ColumnAtLevel_IsReady() {
        ColumnMask.Cell post = SITE.ring().posts().get(0);

        assertTrue(
                SiteLevels.fenceReady(SITE, post, tops(post, LEVEL)),
                "Клетка готова, когда выше y нет блоков: земля на уровне y столбу не мешает"
                        + " Иначе тест fenceReady_ColumnAtLevel_IsReady перестанет ловить эту поломку. При смене fenceReady_ColumnAtLevel_IsReady проверь это утверждение.");
        assertFalse(
                SiteLevels.fenceReady(SITE, post, tops(post, LEVEL + 1)),
                "Блок над уровнем держит столб: его сначала срезает расчистка"
                        + " Иначе тест fenceReady_ColumnAtLevel_IsReady перестанет ловить эту поломку. При смене fenceReady_ColumnAtLevel_IsReady проверь это утверждение.");
    }

    @Test
    void fenceReady_BuiltFenceAtLevel_IsReady() {
        ColumnMask.Cell post = SITE.ring().posts().get(0);

        assertTrue(
                SiteLevels.fenceReady(SITE, post, tops(post, LEVEL + 1), true),
                "Стоящий забор на уровне y не делает столб неготовым: иначе после постройки фрагмента повторная "
                        + "выдача видит пустой план и закрывается без работы"
                        + " При смене fenceReady_BuiltFenceAtLevel_IsReady проверь это утверждение.");
        assertFalse(
                SiteLevels.fenceReady(SITE, post, tops(post, LEVEL + 1), false),
                "Посторонний блок на уровне y держит столб: его срезает расчистка, забором он не считается"
                        + " Иначе тест fenceReady_BuiltFenceAtLevel_IsReady перестанет ловить эту поломку. При смене fenceReady_BuiltFenceAtLevel_IsReady проверь это утверждение.");
    }

    @Test
    void needsSupport_ColumnAtLevel_AddsOneDirt() {
        ColumnMask.Cell post = SITE.ring().posts().get(0);

        assertTrue(
                SiteLevels.needsSupport(SITE, post, tops(post, LEVEL - 1)),
                "Под столбом нет опоры на уровне y — подсыпают одну землю только под ним"
                        + " Иначе тест needsSupport_ColumnAtLevel_AddsOneDirt перестанет ловить эту поломку. При смене needsSupport_ColumnAtLevel_AddsOneDirt проверь это утверждение.");
        assertFalse(
                SiteLevels.needsSupport(SITE, post, tops(post, LEVEL)),
                "Опора на уровне y уже есть — лишнюю землю под столб не кладут"
                        + " Иначе тест needsSupport_ColumnAtLevel_AddsOneDirt перестанет ловить эту поломку. При смене needsSupport_ColumnAtLevel_AddsOneDirt проверь это утверждение.");
    }

    @Test
    void readyPosts_RingWithOneClearedColumn_ReturnsOnlyCleared() {
        ColumnMask.Cell first = SITE.ring().posts().get(0);
        IntBinaryOperator tops = (x, z) -> x == first.x() && z == first.z() ? LEVEL : LEVEL + 2;

        assertEquals(
                List.of(first),
                SiteLevels.readyPosts(SITE, tops),
                "В работу берут только те столбы, чьи клетки уже освободила расчистка: ограда и расчистка не ждут "
                        + "друг друга целиком"
                        + " Иначе тест readyPosts_RingWithOneClearedColumn_ReturnsOnlyCleared перестанет ловить эту поломку. При смене readyPosts_RingWithOneClearedColumn_ReturnsOnlyCleared проверь это утверждение.");
    }

    @Test
    void extra_ColumnAtLevel_HasNothingExtra() {
        ColumnMask.Cell cell = SITE.level().cells().get(0);

        assertEquals(
                List.of(),
                SiteLevels.extra(SITE, tops(cell, LEVEL)),
                "Колонка, у которой всё выше y уже срезано, расчистку не держит"
                        + " Иначе тест extra_ColumnAtLevel_HasNothingExtra перестанет ловить эту поломку. При смене extra_ColumnAtLevel_HasNothingExtra проверь это утверждение.");
    }

    @Test
    void extra_PitBelowLevel_HasNothingExtra() {
        ColumnMask.Cell cell = SITE.level().cells().get(0);

        assertEquals(
                List.of(),
                SiteLevels.extra(SITE, tops(cell, LEVEL - 3)),
                "Яма ниже y расчистку не держит: иначе ремонт уровня крутится вечно из-за нехватки земли"
                        + " При смене extra_PitBelowLevel_HasNothingExtra проверь это утверждение.");
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
                "Блоки построек по плану не срезают: расчистка не ломает уже стоящую хижину или двор"
                        + " При смене extra_BuildingColumn_KeepsBlocksAboveLevel проверь это утверждение.");
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
                        + "а не прячется за широкой колонкой постройки"
                        + " Иначе тест leveling_ForeignObjectAbovePlanColumn_IsCutBeforeItsSupport перестанет ловить эту поломку. При смене leveling_ForeignObjectAbovePlanColumn_IsCutBeforeItsSupport проверь это утверждение.");
        assertEquals(
                LEVEL + 2,
                cut.cutTopDown().get(0).y1(),
                "Срезка идёт сверху вниз: чужой объект снимается до блока-опоры и не остаётся висеть"
                        + " Иначе тест leveling_ForeignObjectAbovePlanColumn_IsCutBeforeItsSupport перестанет ловить эту поломку. При смене leveling_ForeignObjectAbovePlanColumn_IsCutBeforeItsSupport проверь это утверждение.");
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
                        + "часть участка"
                        + " Иначе тест leveling_BuildingColumn_CutsStrayBlocksAboveLevel перестанет ловить эту поломку. При смене leveling_BuildingColumn_CutsStrayBlocksAboveLevel проверь это утверждение.");
    }

    static IntBinaryOperator tops(ColumnMask.Cell cell, int top) {
        return (x, z) -> x == cell.x() && z == cell.z() ? top : LEVEL;
    }

    static List<ColumnMask.Cell> freeCells(int count) {
        List<ColumnMask.Cell> found = SITE.zone().cells().stream()
                .filter(cell -> !SITE.planCell(cell.x(), LEVEL, cell.z()))
                .filter(cell -> !SITE.ringPost(cell.x(), cell.z()))
                .limit(count)
                .toList();
        assertEquals(
                count,
                found.size(),
                "На площадке мало свободных клеток для сцены выравнивания"
                        + " Иначе тест leveling_MixedColumns_CutAboveFillBelow перестанет ловить эту поломку. При смене leveling_MixedColumns_CutAboveFillBelow проверь это утверждение.");
        return found;
    }

    static ColumnMask.Cell freeCell() {
        return SITE.zone().cells().stream()
                .filter(cell -> !SITE.planCell(cell.x(), LEVEL, cell.z()))
                .filter(cell -> !SITE.ringPost(cell.x(), cell.z()))
                .findFirst()
                .orElseThrow();
    }
}
