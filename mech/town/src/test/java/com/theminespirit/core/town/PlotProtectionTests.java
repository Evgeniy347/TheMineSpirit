package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlotProtectionTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void check_DeepMineCell_OrdinaryBreakRefused() {
        BlockCoord cell = mineCell(SITE.anchor().y() - 20);

        PlotProtection.Decision decision = decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE);

        PlotProtection.Refused refused = assertInstanceOf(
                PlotProtection.Refused.class,
                decision,
                "Блок облицовки шахты на глубине обязан быть защищён: сейчас защита видит только высоты у якоря, "
                        + "и дух добывает поставленную им же клетку шахты");
        assertEquals(PlanBuilding.MINE, refused.building(), "Отказ обязан называть шахту: по нему видно, чей блок");
    }

    @Test
    void check_StreetCell_OrdinaryBreakRefused() {
        ColumnMask.Cell street = SITE.streets().cells().iterator().next();
        BlockCoord cell = new BlockCoord(street.x(), SITE.anchor().y(), street.z());

        PlotProtection.Refused refused = assertInstanceOf(
                PlotProtection.Refused.class,
                decide(cell, PlotProtection.Action.DIG, PlotProtection.Ground.NONE),
                "Тропа плана — такая же постройка, как дом: копать её без основания нельзя");
        assertEquals(PlanBuilding.STREET, refused.building(), "Отказ обязан называть улицу, а не безымянный план");
    }

    @Test
    void check_FencePost_OrdinaryBreakRefused() {
        ColumnMask.Cell post = SITE.ring().posts().iterator().next();
        BlockCoord cell = new BlockCoord(post.x(), SITE.anchor().y(), post.z());

        PlotProtection.Refused refused = assertInstanceOf(
                PlotProtection.Refused.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE),
                "Столб ограды защищён на любой высоте: иначе уборка принимает его за лишний столб");
        assertEquals(PlanBuilding.FENCE, refused.building(), "Отказ обязан называть ограду");
    }

    @Test
    void check_OrchardCrownAboveBuildingHeight_Refused() {
        BlockCoord sapling = SITE.allOrchardSaplings().get(0);
        BlockCoord crown = new BlockCoord(sapling.x(), SITE.anchor().y() + 12, sapling.z());

        assertInstanceOf(
                PlotProtection.Refused.class,
                decide(crown, PlotProtection.Action.CHOP, PlotProtection.Ground.NONE),
                "Крона сада выше семи блоков тоже план: иначе рубка снимает верх дерева и сад теряет форму");
    }

    @Test
    void check_OutsidePlan_AllowedWithoutBasis() {
        BlockCoord cell = new BlockCoord(
                SITE.anchor().x() + 500, SITE.anchor().y(), SITE.anchor().z());

        PlotProtection.Allowed allowed = assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE),
                "Природный блок вне участка ломается штатно: защита не должна запрещать всю добычу");
        assertEquals(PlotProtection.Ground.NONE, allowed.basis(), "Вне плана основание не нужно");
    }

    @Test
    void check_DemolishOwnHouse_Allowed() {
        BlockCoord cell = SITE.houseOrigin();

        PlotProtection.Allowed allowed = assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAN_DEMOLISH, PlanBuilding.HOUSE),
                "Снос своей постройки по плану — законное основание: без него дом не перестроить и не обновить");
        assertEquals(PlotProtection.Ground.PLAN_DEMOLISH, allowed.basis(), "Журнал обязан назвать основание");
    }

    @Test
    void check_DemolishForeignFence_Refused() {
        ColumnMask.Cell post = SITE.ring().posts().iterator().next();
        BlockCoord cell = new BlockCoord(post.x(), SITE.anchor().y(), post.z());

        assertInstanceOf(
                PlotProtection.Refused.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAN_DEMOLISH, PlanBuilding.HOUSE),
                "Дом не имеет права разбирать ограду: чужая постройка плана остаётся защищённой");
    }

    @Test
    void check_DemolishWithoutOwnBuilding_Refused() {
        BlockCoord cell = SITE.houseOrigin();

        assertInstanceOf(
                PlotProtection.Refused.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAN_DEMOLISH, PlanBuilding.NONE),
                "Снос без названной постройки — это уборка, а не план: она не имеет права снимать дом");
    }

    @Test
    void check_RepairHouse_Allowed() {
        BlockCoord cell = SITE.houseOrigin();

        assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.REPAIR, PlanBuilding.HOUSE),
                "Ремонт к схеме заменяет неверный блок своей постройки: это законное основание");
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
                "Стройка по плану ставит блоки в клетки плана: без этого основания дом не собрать");
    }

    @Test
    void check_SelfRescueAndPlayerCommand_Allowed() {
        BlockCoord deep = mineCell(SITE.anchor().y() - 12);

        assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(deep, PlotProtection.Action.BREAK, PlotProtection.Ground.SELF_RESCUE, PlanBuilding.NONE),
                "Замурованный дух обязан выбраться из блока постройки: жизнь сильнее сохранности клетки");
        assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(deep, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAYER_COMMAND, PlanBuilding.NONE),
                "Команда игрока снимает защиту явно: это решение владельца, а не самовольная ломка");
    }

    @Test
    void check_MineExcavationOnlyInMine_Allowed() {
        BlockCoord mine = mineCell(SITE.anchor().y() - 8);
        BlockCoord house = SITE.houseOrigin();

        assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(mine, PlotProtection.Action.DIG, PlotProtection.Ground.PLAN_EXCAVATE, PlanBuilding.MINE),
                "Прокоп шахты по плану — законная работа: иначе шахту не выкопать");
        assertInstanceOf(
                PlotProtection.Refused.class,
                decide(house, PlotProtection.Action.DIG, PlotProtection.Ground.PLAN_EXCAVATE, PlanBuilding.MINE),
                "Прокоп не имеет права лезть в дом под видом шахты");
    }

    @Test
    void basisText_EveryGround_IsNamed() {
        for (PlotProtection.Ground ground : PlotProtection.Ground.values()) {
            String text = PlotProtection.basisText(ground);
            assertEquals(
                    ground == PlotProtection.Ground.NONE,
                    text.equals("без основания"),
                    "Текст основания обязан отличать отсутствие основания от законной причины: " + ground);
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
