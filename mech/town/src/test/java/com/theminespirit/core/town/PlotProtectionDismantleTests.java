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
                "Снос своей постройки по плану — законное основание: без него дом не перестроить и не обновить"
                        + " Иначе тест check_DemolishOwnHouse_Allowed перестанет ловить эту поломку. При смене check_DemolishOwnHouse_Allowed проверь это утверждение.");
        assertEquals(
                PlotProtection.Ground.PLAN_DEMOLISH,
                allowed.basis(),
                "Журнал обязан назвать основание"
                        + " Иначе тест check_DemolishOwnHouse_Allowed перестанет ловить эту поломку. При смене check_DemolishOwnHouse_Allowed проверь это утверждение.");
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
                        + "бугор кучки, защита называла «домом» землю и джунгловые брёвна вне схемы"
                        + " Иначе тест check_DirtAboveHutFootprintNotInScheme_NotNamedHouse перестанет ловить эту поломку. При смене check_DirtAboveHutFootprintNotInScheme_NotNamedHouse проверь это утверждение.");

        PlotProtection.Decision structure = PlotProtection.check(
                SITE, mud, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAN_DEMOLISH, PlanBuilding.NONE, true);

        assertInstanceOf(
                PlotProtection.Refused.class,
                structure,
                "Если клетка всё же отмечена как блок схемы — снос без своей постройки по-прежнему запрещён: "
                        + "пометка структура/не-структура решает, а не footprint"
                        + " Иначе тест check_DirtAboveHutFootprintNotInScheme_NotNamedHouse перестанет ловить эту поломку. При смене check_DirtAboveHutFootprintNotInScheme_NotNamedHouse проверь это утверждение.");
    }

    @Test
    void check_DemolishForeignFence_Refused() {
        ColumnMask.Cell post = SITE.ring().posts().iterator().next();
        BlockCoord cell = new BlockCoord(post.x(), SITE.anchor().y(), post.z());

        assertInstanceOf(
                PlotProtection.Refused.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAN_DEMOLISH, PlanBuilding.HOUSE),
                "Дом не имеет права разбирать ограду: чужая постройка плана остаётся защищённой"
                        + " Иначе тест check_DemolishForeignFence_Refused перестанет ловить эту поломку. При смене check_DemolishForeignFence_Refused проверь это утверждение.");
    }

    @Test
    void check_DemolishWithoutOwnBuilding_Refused() {
        BlockCoord cell = SITE.houseOrigin();

        assertInstanceOf(
                PlotProtection.Refused.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAN_DEMOLISH, PlanBuilding.NONE),
                "Снос без названной постройки — это уборка, а не план: она не имеет права снимать дом"
                        + " Иначе тест check_DemolishWithoutOwnBuilding_Refused перестанет ловить эту поломку. При смене check_DemolishWithoutOwnBuilding_Refused проверь это утверждение.");
    }

    @Test
    void check_RepairHouse_Allowed() {
        BlockCoord cell = SITE.houseOrigin();

        assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.REPAIR, PlanBuilding.HOUSE),
                "Ремонт к схеме заменяет неверный блок своей постройки: это законное основание"
                        + " Иначе тест check_RepairHouse_Allowed перестанет ловить эту поломку. При смене check_RepairHouse_Allowed проверь это утверждение.");
    }

    @Test
    void check_ChestSalvageOnStreet_Allowed() {
        BlockCoord cell = firstCell(SITE.streets());

        PlotProtection.Allowed allowed = assertInstanceOf(
                PlotProtection.Allowed.class,
                decide(cell, PlotProtection.Action.BREAK, PlotProtection.Ground.CHEST_SALVAGE, PlanBuilding.NONE),
                "Сундук с заданием переноса вещей снимают и на улице: без этого основания защита отвергает снос, "
                        + "ремонт улицы падает каждый цикл, а содержимое сундука остаётся недоступным поселению; "
                        + "при смене проверки сноса сундука проверь задание demolish_chest");
        assertEquals(
                PlotProtection.Ground.CHEST_SALVAGE,
                allowed.basis(),
                "Журнал обязан назвать основание «снос сундука с переносом вещей», иначе по логу не отличить "
                        + "законный снос от разбора чужой постройки; при смене проверки сноса сундука проверь "
                        + "задание demolish_chest");
    }

    private static BlockCoord firstCell(ColumnMask mask) {
        for (int dx = 0; dx < mask.width(); dx++) {
            for (int dz = 0; dz < mask.depth(); dz++) {
                int x = mask.minX() + dx;
                int z = mask.minZ() + dz;
                if (mask.contains(x, z)) {
                    return new BlockCoord(x, SITE.anchor().y(), z);
                }
            }
        }
        throw new IllegalStateException("у площадки нет клетки улицы: проверка сноса сундука не на чем идти");
    }

    private static PlotProtection.Decision decide(
            BlockCoord cell, PlotProtection.Action action, PlotProtection.Ground ground, PlanBuilding own) {
        return PlotProtection.check(SITE, cell, action, ground, own);
    }
}
