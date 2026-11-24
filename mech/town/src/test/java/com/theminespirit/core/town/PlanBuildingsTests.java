package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.api.PlanBuilding;
import org.junit.jupiter.api.Test;

class PlanBuildingsTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void planBuilding_HouseWall_IsHouse() {
        BlockCoord house = SITE.houseOrigin();
        assertEquals(
                PlanBuilding.HOUSE,
                SITE.planBuilding(house.x(), house.y(), house.z()),
                "Стена хижины принадлежит дому: иначе забор, идущий по линии ограды, считает её своей и разбирает "
                        + "(observe23: дом 100→94)"
                        + " При смене planBuilding_HouseWall_IsHouse проверь это утверждение.");
    }

    @Test
    void planBuilding_RingPost_IsFence() {
        ColumnMask.Cell post = SITE.ring().posts().get(0);
        assertEquals(
                PlanBuilding.FENCE,
                SITE.planBuilding(post.x(), SITE.anchor().y(), post.z()),
                "Столб ограды принадлежит забору: по нему забор отличает свою клетку от чужой"
                        + " Иначе тест planBuilding_RingPost_IsFence перестанет ловить эту поломку. При смене planBuilding_RingPost_IsFence проверь это утверждение.");
    }

    @Test
    void planBuilding_StreetColumn_IsStreet() {
        ColumnMask.Cell street = firstStreet();
        PlanBuilding owner = SITE.planBuilding(street.x(), SITE.anchor().y(), street.z());
        assertEquals(
                PlanBuilding.STREET,
                owner,
                "Клетка улицы " + street + " принадлежит улицам плана, а не " + owner
                        + ": стройка другой постройки не имеет права её разбирать"
                        + " Иначе тест planBuilding_StreetColumn_IsStreet перестанет ловить эту поломку. При смене planBuilding_StreetColumn_IsStreet проверь это утверждение.");
    }

    @Test
    void foreign_FenceOverHouse_True() {
        assertTrue(
                PlanBuildings.foreign(PlanBuilding.FENCE, PlanBuilding.HOUSE),
                "Забор не имеет права ломать клетку дома: это чужая постройка плана"
                        + " Иначе тест foreign_FenceOverHouse_True перестанет ловить эту поломку. При смене foreign_FenceOverHouse_True проверь это утверждение.");
        assertTrue(
                PlanBuildings.foreign(PlanBuilding.HOUSE, PlanBuilding.FENCE),
                "Дом не имеет права ломать клетку забора: правило работает в обе стороны"
                        + " Иначе тест foreign_FenceOverHouse_True перестанет ловить эту поломку. При смене foreign_FenceOverHouse_True проверь это утверждение.");
    }

    @Test
    void foreign_OwnPlanCell_False() {
        assertFalse(
                PlanBuildings.foreign(PlanBuilding.HOUSE, PlanBuilding.HOUSE),
                "Ремонт дома обязан трогать свои клетки плана: иначе повреждённую хижину не восстановить"
                        + " При смене foreign_OwnPlanCell_False проверь это утверждение.");
        assertFalse(
                PlanBuildings.foreign(PlanBuilding.NONE, PlanBuilding.NONE),
                "Вне плана постройки нет: обычная уборка не должна упираться в защиту плана"
                        + " Иначе тест foreign_OwnPlanCell_False перестанет ловить эту поломку. При смене foreign_OwnPlanCell_False проверь это утверждение.");
    }

    @Test
    void ofScheme_FenceScheme_IsFence() {
        assertEquals(
                PlanBuilding.FENCE,
                PlanBuildings.ofScheme("wall_3_straight"),
                "Фрагмент стены называет ограду: его стройка не должна примерять чужой дом"
                        + " Иначе тест ofScheme_FenceScheme_IsFence перестанет ловить эту поломку. При смене ofScheme_FenceScheme_IsFence проверь это утверждение.");
        assertEquals(
                PlanBuilding.FENCE,
                PlanBuildings.ofScheme("wall_3"),
                "Каменная стена — та же ограда"
                        + " Иначе тест ofScheme_FenceScheme_IsFence перестанет ловить эту поломку. При смене ofScheme_FenceScheme_IsFence проверь это утверждение.");
        assertEquals(
                PlanBuilding.HOUSE,
                PlanBuildings.ofScheme("stone_house"),
                "Схема дома называет дом: его ремонт — своя постройка"
                        + " Иначе тест ofScheme_FenceScheme_IsFence перестанет ловить эту поломку. При смене ofScheme_FenceScheme_IsFence проверь это утверждение.");
    }

    @Test
    void ofBuild_HouseOriginWithoutHouseScheme_IsHouse() {
        BlockCoord house = SITE.houseOrigin();
        assertEquals(
                PlanBuilding.HOUSE,
                PlanBuildings.ofBuild(SITE, "player_house_1", house),
                "Стройка дома по пресету у места хижины — своя постройка: без этого ремонт дома был бы запрещён"
                        + " При смене ofBuild_HouseOriginWithoutHouseScheme_IsHouse проверь это утверждение.");
    }

    private static ColumnMask.Cell firstStreet() {
        for (int x = SITE.enclosure().minX();
                x < SITE.enclosure().minX() + SITE.enclosure().width();
                x++) {
            for (int z = SITE.enclosure().minZ();
                    z < SITE.enclosure().minZ() + SITE.enclosure().depth();
                    z++) {
                if (SITE.streetColumn(x, z)
                        && !SITE.hut().covers(x, z)
                        && !SITE.yard().covers(x, z)
                        && !SITE.orchard().covers(x, z)
                        && !SITE.mine().covers(x, z)
                        && !nearMine(x, z)
                        && !SITE.fenceStrip().contains(x, z)
                        && !SITE.ringPost(x, z)) {
                    return new ColumnMask.Cell(x, z);
                }
            }
        }
        throw new IllegalStateException("В свежем плане нет ни одной клетки улицы: тест потерял опору");
    }

    private static boolean nearMine(int x, int z) {
        MinePlot mine = SITE.minePlot();
        return mine.covers(x, z)
                || mine.covers(x - 1, z)
                || mine.covers(x + 1, z)
                || mine.covers(x, z - 1)
                || mine.covers(x, z + 1);
    }
}
