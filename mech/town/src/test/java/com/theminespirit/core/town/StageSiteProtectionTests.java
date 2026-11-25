package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.town.api.PlanBuilding;
import org.junit.jupiter.api.Test;

class StageSiteProtectionTests {
    @Test
    void protection_MatchesSeparateChecks_OnWholeSiteGrid() {
        StageSite site = TownFixtures.flatSite();
        int step = 3;
        int checked = 0;
        for (int x = site.zone().minX(); x < site.zone().minX() + site.zone().width(); x += step) {
            for (int z = site.zone().minZ();
                    z < site.zone().minZ() + site.zone().depth();
                    z += step) {
                for (int y = site.anchor().y() - 6; y <= site.anchor().y() + 9; y++) {
                    check(site, x, y, z);
                    checked++;
                }
            }
        }
        assertTrue(
                checked > 0,
                "Сетка проверки защиты пуста: тогда сравнение ничего не проверило, и тест зелёный впустую — без "
                        + "этого расхождение защиты пройдёт незамеченным. При смене TownFixtures.flatSite проверь "
                        + "границы zone() и шаг сетки этого теста");
    }

    private static void check(StageSite site, int x, int y, int z) {
        boolean cell = referenceCell(site, x, y, z);
        PlanBuilding building = referenceBuilding(site, x, y, z);
        SiteProtection protection = site.protection(x, y, z);
        String at = "клетка " + x + "," + y + "," + z + " сайта " + site.anchor() + ":";
        assertEquals(
                cell,
                protection.protectedCell(),
                at + " один проход защиты обязан совпадать с защищённостью клетки — иначе защита выпускает на "
                        + "снос то, что раньше держала, и духи ломают постройки поселения. При смене "
                        + "StageSite.protection проверь ClearBlocked.dropProtected и ProtectionDecide.plotCheck");
        assertEquals(
                building,
                protection.building(),
                at + " постройка защиты обязана совпадать с прежним разбором — иначе ремонт и снос принимают "
                        + "чужую клетку за свою. При смене StageSite.protection проверь PlotProtection и "
                        + "ProtectionDecide.plotCheck");
        assertEquals(
                cell ? building.area() : "",
                protection.area(),
                at + " место защиты обязано следовать за постройкой, а у незащищённой клетки — быть пустым — "
                        + "иначе журнал [plot-break] называет неверную постройку и разбор живого случая уводит "
                        + "в сторону. При смене StageSite.protection проверь PlotBreakLog и тексты отказов");
        if (!cell) {
            assertEquals(
                    SiteProtection.NONE,
                    protection,
                    at + " незащищённая клетка не несёт постройки — иначе расчистка пропускает обычный блок "
                            + "как чужую стену и не убирает его. При смене StageSite.protection проверь "
                            + "ClearSweepRun.pick и подсчёт пропусков в ClearAreaSession");
        }
    }

    private static boolean referenceCell(StageSite site, int x, int y, int z) {
        if (site.planCell(x, y, z) || site.orchardTree(x, y, z)) {
            return true;
        }
        if (site.streetColumn(x, z)) {
            return y >= site.anchor().y() - StageSite.STREET_DEPTH;
        }
        return site.zone().contains(x, z) || site.fenceStrip().contains(x, z);
    }

    private static PlanBuilding referenceBuilding(StageSite site, int x, int y, int z) {
        if (!referenceCell(site, x, y, z)) {
            return PlanBuilding.NONE;
        }
        PlanBuilding planned = site.planBuilding(x, y, z);
        if (planned != PlanBuilding.NONE) {
            return planned;
        }
        return site.orchardTree(x, y, z) ? PlanBuilding.ORCHARD : PlanBuilding.NONE;
    }
}
