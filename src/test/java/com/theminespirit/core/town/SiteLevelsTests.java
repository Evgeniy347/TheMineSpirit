package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.PlotLeveling;
import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class SiteLevelsTests {
    private static final StageSite SITE = TownFixtures.flatSite();
    private static final int LEVEL = TownFixtures.LEVEL;

    @Test
    void level_AnySite_EqualsAnchorY() {
        assertEquals(
                LEVEL,
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
                        new PlotLeveling.Box(cell.x(), LEVEL + 1, cell.z(), cell.x(), LEVEL + 1, cell.z()),
                        new PlotLeveling.Box(cell.x(), LEVEL + 2, cell.z(), cell.x(), LEVEL + 2, cell.z())),
                extra,
                "Лишним считается только то, что выше уровня: блок на уровне y — опора, его не срезают");
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
    void leveling_ColumnHigherThanLevel_HasCutWithoutFill() {
        ColumnMask.Cell cell = freeCell();
        PlotLeveling plan = SiteLevels.leveling(SITE, tops(cell, LEVEL + 3));

        assertEquals(2, plan.cut().size(), "Расчистка срезает всё лишнее выше y, слой за слоем");
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
        return SITE.level().cells().stream()
                .filter(cell -> !SITE.planCell(cell.x(), LEVEL, cell.z()))
                .filter(cell -> !SITE.ringPost(cell.x(), cell.z()))
                .findFirst()
                .orElseThrow();
    }
}
