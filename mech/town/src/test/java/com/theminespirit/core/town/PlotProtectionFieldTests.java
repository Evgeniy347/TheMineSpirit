package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.api.PlanBuilding;
import org.junit.jupiter.api.Test;

class PlotProtectionFieldTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void check_FieldCropHarvest_AllowedButBreakRefused() {
        Placement field = TownFixtures.flatPlan()
                .placement("wheat_field")
                .orElseThrow(() -> new IllegalStateException("В плане этапа 1 нет домашней грядки"));
        BlockCoord crop = new BlockCoord(field.centerX(), SITE.anchor().y() + 1, field.centerZ());

        PlotProtection.Allowed allowed = assertInstanceOf(
                PlotProtection.Allowed.class,
                PlotProtection.check(
                        SITE, crop, PlotProtection.Action.HARVEST, PlotProtection.Ground.NONE, PlanBuilding.NONE, true),
                "Сбор урожая на поле по схеме разрешён: иначе граф видит зрелые колосья, но защита отбрасывает "
                        + "их до сбора и задание вечно падает с нулевым прогрессом"
                        + " При смене check_FieldCropHarvest_AllowedButBreakRefused проверь PlotProtection.check "
                        + "и FarmTargets.allowed.");
        assertEquals(
                PlanBuilding.FIELD,
                allowed.building(),
                "Разрешение сбору относится только к полю: соседние постройки остаются защищены"
                        + " Иначе тест check_FieldCropHarvest_AllowedButBreakRefused перестанет ловить эту поломку. "
                        + "При смене check_FieldCropHarvest_AllowedButBreakRefused проверь это утверждение.");
        assertInstanceOf(
                PlotProtection.Refused.class,
                PlotProtection.check(
                        SITE, crop, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE, PlanBuilding.NONE, true),
                "Сбор урожая не разрешает обычную ломку клетки поля: иначе защита схемы потеряет смысл"
                        + " Иначе тест check_FieldCropHarvest_AllowedButBreakRefused перестанет ловить эту поломку. "
                        + "При смене check_FieldCropHarvest_AllowedButBreakRefused проверь это утверждение.");
    }
}
