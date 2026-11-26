package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownFixtures;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class SettlementGroundTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void area_CurrentStage_IncludesStreetsAndPriorityPlotsOnly() {
        ColumnMask area = SettlementGround.area(SITE, 1);
        ColumnMask.Cell street = SITE.streets().cells().getFirst();
        Placement field = SITE.fields().stream()
                .filter(place -> "wheat_field".equals(place.id()))
                .findFirst()
                .orElseThrow();

        assertTrue(
                area.contains(street.x(), street.z()),
                "Расчистка этапа включает тропинки: иначе постройки останутся без прохода. При смене "
                        + "area_CurrentStage_IncludesStreetsAndPriorityPlotsOnly проверь SettlementGround.area.");
        assertTrue(
                area.contains(field.centerX(), field.centerZ()),
                "Расчистка этапа включает пашню: иначе поле будет ждать уборки вне текущего плана. При смене "
                        + "area_CurrentStage_IncludesStreetsAndPriorityPlotsOnly проверь StageRules.plans.");
        assertTrue(
                SITE.zone().cells().stream().anyMatch(cell -> !area.contains(cell.x(), cell.z())),
                "Дикая территория вне улиц и участков этапа не входит в уборку: иначе clearing снова станет "
                        + "тысячами лишних боксов. При смене area_CurrentStage_IncludesStreetsAndPriorityPlotsOnly "
                        + "проверь SiteLevels.leveling и живой first:clear_grounds.");
    }

    @Test
    void leveling_RequestedArea_IgnoresHighBlocksOutside() {
        ColumnMask area = SettlementGround.area(SITE, 1);
        ColumnMask.Cell outside = SITE.zone().cells().stream()
                .filter(cell -> !area.contains(cell.x(), cell.z()))
                .findFirst()
                .orElseThrow();
        int level = SITE.anchor().y();
        IntBinaryOperator tops = (x, z) -> x == outside.x() && z == outside.z() ? level + 8 : level;

        PlotLeveling plan = SiteLevels.leveling(SITE, tops, at -> false, area);

        assertFalse(
                plan.cut().stream()
                        .anyMatch(box -> box.x1() <= outside.x()
                                && box.x2() >= outside.x()
                                && box.z1() <= outside.z()
                                && box.z2() >= outside.z()),
                "Высокий блок вне выбранной площади не создаёт срезку: иначе граф снова расчищает весь "
                        + " wilderness вместо улиц и текущих участков. При смене leveling_RequestedArea_IgnoresHighBlocksOutside "
                        + "проверь SiteLevels.extra и SettlementGround.leveling.");
    }
}
