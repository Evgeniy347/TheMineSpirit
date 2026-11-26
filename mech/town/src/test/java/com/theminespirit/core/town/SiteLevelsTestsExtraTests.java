package com.theminespirit.core.town;

import static com.theminespirit.core.town.SiteLevelsTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class SiteLevelsTestsExtraTests {

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
                        new PlotLeveling.Box(tall.x(), LEVEL + 2, tall.z(), tall.x(), LEVEL + 2, tall.z()),
                        new PlotLeveling.Box(tall.x(), LEVEL + 1, tall.z(), tall.x(), LEVEL + 1, tall.z()),
                        new PlotLeveling.Box(tall.x(), LEVEL, tall.z(), tall.x(), LEVEL, tall.z())),
                plan.cut(),
                "Выравнивание срезает всё лишнее выше уровня, слой за слоем"
                        + " Иначе тест leveling_MixedColumns_CutAboveFillBelow перестанет ловить эту поломку. При смене leveling_MixedColumns_CutAboveFillBelow проверь это утверждение.");
        assertEquals(
                List.of(
                        new PlotLeveling.Box(low.x(), LEVEL - 2, low.z(), low.x(), LEVEL - 2, low.z()),
                        new PlotLeveling.Box(low.x(), LEVEL - 1, low.z(), low.x(), LEVEL - 1, low.z())),
                plan.fill(),
                "Выравнивание подсыпает низину до уровня плана: весь план живёт на одном уровне"
                        + " Иначе тест leveling_MixedColumns_CutAboveFillBelow перестанет ловить эту поломку. При смене leveling_MixedColumns_CutAboveFillBelow проверь это утверждение.");
        assertTrue(
                plan.fill().stream().noneMatch(box -> box.x1() == deep.x() && box.z1() == deep.z()),
                "Низину глубже " + PlotLeveling.MAX_SHIFT + " блоков не засыпаем: это уже обрыв, а не яма"
                        + " Иначе тест leveling_MixedColumns_CutAboveFillBelow перестанет ловить эту поломку. При смене leveling_MixedColumns_CutAboveFillBelow проверь это утверждение.");
        assertEquals(
                List.of(),
                plan.voids(),
                "Выравнивание не ищет пустоты под участком: это работа расчистки, а не уровня"
                        + " Иначе тест leveling_MixedColumns_CutAboveFillBelow перестанет ловить эту поломку. При смене leveling_MixedColumns_CutAboveFillBelow проверь это утверждение.");
        assertTrue(
                SiteLevels.leveling(SITE, tops, at -> at.x() == low.x() && at.z() == low.z())
                        .fill()
                        .isEmpty(),
                "Клетку, где блок ставит сам план, подсыпка не дублирует"
                        + " Иначе тест leveling_MixedColumns_CutAboveFillBelow перестанет ловить эту поломку. При смене leveling_MixedColumns_CutAboveFillBelow проверь это утверждение.");
    }
}
