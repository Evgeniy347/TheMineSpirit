package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.api.PlanBuilding;
import org.junit.jupiter.api.Test;

class PlotProtectionDismantleTests {
    private static final StageSite SITE = TownFixtures.flatSite();

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
    void check_DirtAboveHutFootprintNotInScheme_NotNamedHouse() {
        Placement hut = SITE.hut();
        BlockCoord mud = new BlockCoord(hut.x() + hut.width() / 2, SITE.anchor().y() + 4, hut.z() + 1);

        PlotProtection.Decision notStructure = PlotProtection.check(
                SITE, mud, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAN_DEMOLISH, PlanBuilding.NONE, false);

        assertInstanceOf(
                PlotProtection.Allowed.class,
                notStructure,
                "Глина над footprint'ом дома — не блок схемы дома: расчистка сносит её без своей постройки. "
                        + "Живой сервер 14.11: 102 отказа «дом» у -177,-509 — дух 20 минут не мог срезать "
                        + "бугор кучки, защита называла «домом» землю и джунгловые брёвна вне схемы");

        PlotProtection.Decision structure = PlotProtection.check(
                SITE, mud, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAN_DEMOLISH, PlanBuilding.NONE, true);

        assertInstanceOf(
                PlotProtection.Refused.class,
                structure,
                "Если клетка всё же отмечена как блок схемы — снос без своей постройки по-прежнему запрещён: "
                        + "пометка структура/не-структура решает, а не footprint");
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

    private static PlotProtection.Decision decide(
            BlockCoord cell, PlotProtection.Action action, PlotProtection.Ground ground, PlanBuilding own) {
        return PlotProtection.check(SITE, cell, action, ground, own);
    }
}
