package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TreeClearanceTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void treeClearance_FreeZoneCell_Allowed() {
        BlockCoord free = new BlockCoord(
                SITE.anchor().x(), SITE.anchor().y() + 40, SITE.anchor().z());

        assertTrue(
                SITE.inside(free.x(), free.z()),
                "Условие теста: выбранная клетка лежит в зоне поселения, иначе проверка послабления ничего не "
                        + "проверяет. При смене фикстуры проверь строку координаты ниже в тесте");
        assertFalse(
                SITE.planCell(free.x(), free.y(), free.z()) || SITE.orchardTree(free.x(), free.y(), free.z()),
                "Условие теста: клетка выше плана и вне колонок сада, иначе она не свободна. При смене высоты "
                        + "проверь строку координаты выше в тесте");

        assertTrue(
                TreeClearance.allowed(SITE, free),
                "Свободная клетка зоны доступна фоновой рубке: очистка обязана проходить по всей территории "
                        + "поселения, а не только по саду, иначе свободные деревья зоны остаются и заготовка "
                        + "встаёт. При смене TreeClearance проверь строку плана ниже в тесте");
        assertTrue(
                TreeClearance.allowed(Optional.empty(), free),
                "Без плана поселения (GameTest) ограничение не включено: сцены рубки вне поселения обязаны "
                        + "работать как прежде, иначе GameTest без плана перестанет находить лес. При смене "
                        + "Optional-перегрузки проверь строку выше в тесте");
    }

    @Test
    void treeClearance_PlannedCells_Denied() {
        BlockCoord hut =
                new BlockCoord(SITE.hut().x(), SITE.anchor().y(), SITE.hut().z());
        BlockCoord sapling = SITE.orchardSaplings().get(0);
        BlockCoord outside = new BlockCoord(
                SITE.anchor().x() + 1000, SITE.anchor().y(), SITE.anchor().z() + 1000);

        assertTrue(
                SITE.planCell(hut.x(), hut.y(), hut.z()),
                "Условие теста: угол дома входит в план, иначе запрет проверяет не ту клетку. При смене фикстуры "
                        + "проверь строку списка ниже в тесте");

        assertEquals(
                List.of(false, false, false),
                List.of(
                        TreeClearance.allowed(SITE, hut),
                        TreeClearance.allowed(SITE, sapling),
                        TreeClearance.allowed(SITE, outside)),
                "Дом, колонка дерева сада и клетка за зоной не рубятся: очистка не трогает план и не выходит за "
                        + "ограду, иначе рубка снесёт постройку или уйдёт в дикий лес. При смене правил проверь "
                        + "TreeClearance.allowed в ChopSearch");
        assertTrue(
                TreeClearance.planned(Optional.of(SITE), hut),
                "Клетка дома — плановая: групповая проверка обязана отбросить всю группу, задевшую постройку, "
                        + "иначе частичный разбор оставит стену висеть. При смене planned проверь строку выше "
                        + "в тесте");
    }
}
