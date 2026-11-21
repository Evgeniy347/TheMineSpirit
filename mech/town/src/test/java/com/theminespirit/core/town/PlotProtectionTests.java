package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.api.PlanBuilding;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlotProtectionTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void protectionCell_FieldCell_True() {
        Placement field = TownFixtures.flatPlan()
                .placement("wheat_field")
                .orElseThrow(() -> new IllegalStateException("В плане этапа 1 нет домашней грядки"));
        int y = SITE.anchor().y();
        int x = field.centerX();
        int z = field.centerZ();

        assertTrue(
                SITE.protectionCell(x, y, z),
                "Клетка поля — постройка плана: без этого пахота и сбор урожая выкапывают грядку, "
                        + "а защита видит поле только там, где случайно попала улица или площадка"
                        + " При смене protectionCell_FieldCell_True проверь это утверждение.");
        assertEquals(
                PlanBuilding.FIELD,
                SITE.protectionBuilding(x, y, z),
                "Защита обязана называть поле полем"
                        + " Иначе тест protectionCell_FieldCell_True перестанет ловить эту поломку. При смене protectionCell_FieldCell_True проверь это утверждение.");
        assertEquals(
                "поле",
                SITE.protectionArea(x, y, z),
                "Журнал отказа называет участок «поле»"
                        + " Иначе тест protectionCell_FieldCell_True перестанет ловить эту поломку. При смене protectionCell_FieldCell_True проверь это утверждение.");
    }

    @Test
    void fields_LaterStage_KeepEarlierFieldsAndAddOwn() {
        StageSite second = StageSites.of(TownFixtures.flatPlan(), 2, TownFixtures.LEVEL, TownRules.load());
        List<String> ids = second.fields().stream().map(Placement::id).toList();

        assertTrue(
                ids.contains("wheat_field"),
                "Грядка этапа 1 остаётся постоянной площадкой на этапе 2: " + ids
                        + " Иначе тест fields_LaterStage_KeepEarlierFieldsAndAddOwn перестанет ловить эту поломку. При смене fields_LaterStage_KeepEarlierFieldsAndAddOwn проверь это утверждение.");
        assertTrue(
                ids.contains("field_a") && ids.contains("field_b"),
                "Пашни этапа 2 входят в поля: " + ids
                        + " Иначе тест fields_LaterStage_KeepEarlierFieldsAndAddOwn перестанет ловить эту поломку. При смене fields_LaterStage_KeepEarlierFieldsAndAddOwn проверь это утверждение.");
    }

    @Test
    void check_DeepMineCell_OrdinaryBreakRefused() {
        BlockCoord cell = mineCell(SITE.anchor().y() - 20);

        PlotProtection.Decision decision = decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE);

        PlotProtection.Refused refused = assertInstanceOf(
                PlotProtection.Refused.class,
                decision,
                "Блок облицовки шахты на глубине обязан быть защищён: сейчас защита видит только высоты у якоря, "
                        + "и дух добывает поставленную им же клетку шахты"
                        + " Иначе тест check_DeepMineCell_OrdinaryBreakRefused перестанет ловить эту поломку. При смене check_DeepMineCell_OrdinaryBreakRefused проверь это утверждение.");
        assertEquals(
                PlanBuilding.MINE,
                refused.building(),
                "Отказ обязан называть шахту: по нему видно, чей блок"
                        + " Иначе тест check_DeepMineCell_OrdinaryBreakRefused перестанет ловить эту поломку. При смене check_DeepMineCell_OrdinaryBreakRefused проверь это утверждение.");
    }

    @Test
    void check_StreetCell_OrdinaryBreakRefused() {
        ColumnMask.Cell street = SITE.streets().cells().iterator().next();
        BlockCoord cell = new BlockCoord(street.x(), SITE.anchor().y(), street.z());

        PlotProtection.Refused refused = assertInstanceOf(
                PlotProtection.Refused.class,
                decide(cell, PlotProtection.Action.DIG, PlotProtection.Ground.NONE),
                "Тропа плана — такая же постройка, как дом: копать её без основания нельзя"
                        + " Иначе тест check_StreetCell_OrdinaryBreakRefused перестанет ловить эту поломку. При смене check_StreetCell_OrdinaryBreakRefused проверь это утверждение.");
        assertEquals(
                PlanBuilding.STREET,
                refused.building(),
                "Отказ обязан называть улицу, а не безымянный план"
                        + " Иначе тест check_StreetCell_OrdinaryBreakRefused перестанет ловить эту поломку. При смене check_StreetCell_OrdinaryBreakRefused проверь это утверждение.");
    }

    @Test
    void check_StreetColumnBelowRoad_DeepCellsNotStreet() {
        ColumnMask.Cell street = SITE.streets().cells().iterator().next();
        int deepY = SITE.anchor().y() - StageSite.STREET_DEPTH - 1;
        BlockCoord deepCell = new BlockCoord(street.x(), deepY, street.z());

        assertEquals(
                PlanBuilding.NONE,
                SITE.protectionBuilding(deepCell.x(), deepCell.y(), deepCell.z()),
                "Колонна улицы ниже полотна дороги (" + StageSite.STREET_DEPTH + " блоков от уровня) — обычный "
                        + "камень: шахта по плану копает под улицей, иначе спуск «Спуск в шахту» получает отказ "
                        + "«STREET» на глубине и стоит (живой сервер 14.11: 81 отказ на z=-527, y62-66 у "
                        + "входа шахты)"
                        + " При смене check_StreetColumnBelowRoad_DeepCellsNotStreet проверь это утверждение.");
        assertTrue(
                SITE.protectionCell(street.x(), SITE.anchor().y(), street.z()),
                "Полотно дороги сверху защищено как раньше: улицу не раскапывают, правило копаем-только-в-шахте "
                        + "на поверхности действует"
                        + " Иначе тест check_StreetColumnBelowRoad_DeepCellsNotStreet перестанет ловить эту поломку. При смене check_StreetColumnBelowRoad_DeepCellsNotStreet проверь это утверждение.");
    }

    @Test
    void check_FencePost_OrdinaryBreakRefused() {
        ColumnMask.Cell post = SITE.ring().posts().iterator().next();
        BlockCoord cell = new BlockCoord(post.x(), SITE.anchor().y(), post.z());

        PlotProtection.Refused refused = assertInstanceOf(
                PlotProtection.Refused.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE),
                "Столб ограды защищён на любой высоте: иначе уборка принимает его за лишний столб"
                        + " При смене check_FencePost_OrdinaryBreakRefused проверь это утверждение.");
        assertEquals(
                PlanBuilding.FENCE,
                refused.building(),
                "Отказ обязан называть ограду"
                        + " Иначе тест check_FencePost_OrdinaryBreakRefused перестанет ловить эту поломку. При смене check_FencePost_OrdinaryBreakRefused проверь это утверждение.");
    }

    @Test
    void check_OrchardCrownAboveBuildingHeight_Refused() {
        BlockCoord sapling = SITE.allOrchardSaplings().get(0);
        BlockCoord crown = new BlockCoord(sapling.x(), SITE.anchor().y() + 12, sapling.z());

        assertInstanceOf(
                PlotProtection.Refused.class,
                decide(crown, PlotProtection.Action.CHOP, PlotProtection.Ground.NONE),
                "Крона сада выше семи блоков тоже план: иначе рубка снимает верх дерева и сад теряет форму"
                        + " При смене check_OrchardCrownAboveBuildingHeight_Refused проверь это утверждение.");
    }

    @Test
    void check_OutsidePlan_AllowedWithoutBasis() {
        BlockCoord cell = new BlockCoord(
                SITE.anchor().x() + 500, SITE.anchor().y(), SITE.anchor().z());

        PlotProtection.Allowed allowed = assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE),
                "Природный блок вне участка ломается штатно: защита не должна запрещать всю добычу"
                        + " При смене check_OutsidePlan_AllowedWithoutBasis проверь это утверждение.");
        assertEquals(
                PlotProtection.Ground.NONE,
                allowed.basis(),
                "Вне плана основание не нужно"
                        + " Иначе тест check_OutsidePlan_AllowedWithoutBasis перестанет ловить эту поломку. При смене check_OutsidePlan_AllowedWithoutBasis проверь это утверждение.");
    }

    @Test
    void check_NaturalGroundInPlan_LevelingBreakAllowedButHarvestRefused() {
        BlockCoord ground = new BlockCoord(
                SITE.houseOrigin().x(), SITE.anchor().y(), SITE.houseOrigin().z());

        assertInstanceOf(
                PlotProtection.Allowed.class,
                PlotProtection.check(
                        SITE,
                        ground,
                        PlotProtection.Action.BREAK,
                        PlotProtection.Ground.NONE,
                        PlanBuilding.NONE,
                        false),
                "Выравнивание площадки снимает природный грунт в клетке плана: иначе участок не расчистить"
                        + " При смене check_NaturalGroundInPlan_LevelingBreakAllowedButHarvestRefused проверь это утверждение.");
        assertInstanceOf(
                PlotProtection.Refused.class,
                PlotProtection.check(
                        SITE,
                        ground,
                        PlotProtection.Action.HARVEST,
                        PlotProtection.Ground.NONE,
                        PlanBuilding.NONE,
                        false),
                "Сбор грунта не имеет права брать даже природную клетку плана: под домом появляется яма"
                        + " Иначе тест check_NaturalGroundInPlan_LevelingBreakAllowedButHarvestRefused перестанет ловить эту поломку. При смене check_NaturalGroundInPlan_LevelingBreakAllowedButHarvestRefused проверь это утверждение.");
        assertInstanceOf(
                PlotProtection.Refused.class,
                PlotProtection.check(
                        SITE, ground, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE, PlanBuilding.NONE, true),
                "Поставленный блок постройки выравнивание не снимает: это уже стена, а не грунт"
                        + " Иначе тест check_NaturalGroundInPlan_LevelingBreakAllowedButHarvestRefused перестанет ловить эту поломку. При смене check_NaturalGroundInPlan_LevelingBreakAllowedButHarvestRefused проверь это утверждение.");
    }

    @Test
    void check_PlanBuildPlacement_Allowed() {
        assertInstanceOf(
                PlotProtection.Allowed.class,
                PlotProtection.check(
                        SITE,
                        SITE.houseOrigin(),
                        PlotProtection.Action.PLACE,
                        PlotProtection.Ground.PLAN_BUILD,
                        PlanBuilding.NONE),
                "Стройка по плану ставит блоки в клетки плана: без этого основания дом не собрать"
                        + " При смене check_PlanBuildPlacement_Allowed проверь это утверждение.");
    }

    @Test
    void check_SelfRescueAndPlayerCommand_Allowed() {
        BlockCoord deep = mineCell(SITE.anchor().y() - 12);

        assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(deep, PlotProtection.Action.BREAK, PlotProtection.Ground.SELF_RESCUE, PlanBuilding.NONE),
                "Замурованный дух обязан выбраться из блока постройки: жизнь сильнее сохранности клетки"
                        + " Иначе тест check_SelfRescueAndPlayerCommand_Allowed перестанет ловить эту поломку. При смене check_SelfRescueAndPlayerCommand_Allowed проверь это утверждение.");
        assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(deep, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAYER_COMMAND, PlanBuilding.NONE),
                "Команда игрока снимает защиту явно: это решение владельца, а не самовольная ломка"
                        + " Иначе тест check_SelfRescueAndPlayerCommand_Allowed перестанет ловить эту поломку. При смене check_SelfRescueAndPlayerCommand_Allowed проверь это утверждение.");
    }

    @Test
    void check_MineExcavationOnlyInMine_Allowed() {
        BlockCoord mine = mineCell(SITE.anchor().y() - 8);
        BlockCoord house = SITE.houseOrigin();

        assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(mine, PlotProtection.Action.DIG, PlotProtection.Ground.PLAN_EXCAVATE, PlanBuilding.MINE),
                "Прокоп шахты по плану — законная работа: иначе шахту не выкопать"
                        + " При смене check_MineExcavationOnlyInMine_Allowed проверь это утверждение.");
        assertInstanceOf(
                PlotProtection.Refused.class,
                decide(house, PlotProtection.Action.DIG, PlotProtection.Ground.PLAN_EXCAVATE, PlanBuilding.MINE),
                "Прокоп не имеет права лезть в дом под видом шахты"
                        + " Иначе тест check_MineExcavationOnlyInMine_Allowed перестанет ловить эту поломку. При смене check_MineExcavationOnlyInMine_Allowed проверь это утверждение.");
    }

    @Test
    void check_TemporaryMovement_AllowedOnPlanCell() {
        BlockCoord cell = SITE.houseOrigin();

        assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(cell, PlotProtection.Action.PLACE, PlotProtection.Ground.TEMPORARY_MOVEMENT, PlanBuilding.NONE),
                "Временный проход (столб, мост, пол ступени) обязан ставить опору и на клетке плана: "
                        + "иначе дух не выберется с крыши и уборка застревает на улице"
                        + " При смене check_TemporaryMovement_AllowedOnPlanCell проверь это утверждение.");
    }

    @Test
    void basisText_EveryGround_IsNamed() {
        for (PlotProtection.Ground ground : PlotProtection.Ground.values()) {
            String text = PlotProtection.basisText(ground);
            assertEquals(
                    ground == PlotProtection.Ground.NONE,
                    "без основания".equals(text),
                    "Текст основания обязан отличать отсутствие основания от законной причины: " + ground
                            + " Иначе тест basisText_EveryGround_IsNamed перестанет ловить эту поломку. При смене basisText_EveryGround_IsNamed проверь это утверждение.");
        }
    }

    private static PlotProtection.Decision decide(
            BlockCoord cell, PlotProtection.Action action, PlotProtection.Ground ground) {
        return decide(cell, action, ground, PlanBuilding.NONE);
    }

    private static PlotProtection.Decision decide(
            BlockCoord cell, PlotProtection.Action action, PlotProtection.Ground ground, PlanBuilding own) {
        return PlotProtection.check(SITE, cell, action, ground, own);
    }

    private static BlockCoord mineCell(int y) {
        List<ColumnMask.Cell> columns = SITE.mine().mask().cells().stream()
                .filter(cell -> SITE.mineColumn(cell.x(), cell.z()))
                .toList();
        ColumnMask.Cell column = columns.get(0);
        return new BlockCoord(column.x(), y, column.z());
    }
}
