package com.theminespirit.core.town;

import static com.theminespirit.core.town.StageSiteTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.town.api.PlanBuilding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StageSiteTestsExtraTests {

    @Test
    void planCell_RingPostColumn_OnlyAtFenceHeight() {
        ColumnMask.Cell post = SITE.ring().posts().iterator().next();
        int above = SITE.anchor().y() + StageSite.BUILDING_HEIGHT + 10;

        assertTrue(
                SITE.planCell(post.x(), SITE.anchor().y(), post.z()),
                "Столб ограды у земли — клетка плана: его нельзя выломать случайной уборкой"
                        + " Иначе тест planCell_RingPostColumn_OnlyAtFenceHeight перестанет ловить эту поломку. При смене planCell_RingPostColumn_OnlyAtFenceHeight проверь это утверждение.");
        assertFalse(
                SITE.planCell(post.x(), above, post.z()),
                "Столб ограды не занимает бесконечную колонку: дерево над ним рубится, а не считается планом"
                        + " Иначе тест planCell_RingPostColumn_OnlyAtFenceHeight перестанет ловить эту поломку. При смене planCell_RingPostColumn_OnlyAtFenceHeight проверь это утверждение.");
        assertFalse(
                SITE.planCell(post.x(), SITE.anchor().y() - 10, post.z()),
                "Под землёй столба ограды нет: глубокую клетку той же колонки план не защищает"
                        + " Иначе тест planCell_RingPostColumn_OnlyAtFenceHeight перестанет ловить эту поломку. При смене planCell_RingPostColumn_OnlyAtFenceHeight проверь это утверждение.");
        assertEquals(
                PlanBuilding.NONE,
                SITE.planBuilding(post.x(), above, post.z()),
                "Имя постройки над оградой не выдаётся: иначе защита считает дерево частью забора"
                        + " При смене planCell_RingPostColumn_OnlyAtFenceHeight проверь это утверждение.");
    }

    @Test
    void planCell_LiveObserve24TreeAboveMine_NotPlan() throws IOException {
        String text = Files.readString(Path.of("src/test/resources/live/observe24/town-plan.txt"))
                .replace("\r\n", "\n");
        TownPlan plan = TownPlanCodec.decode(text);
        StageSite site = StageSites.of(plan, 1, plan.anchor().y(), TownRules.load());

        assertTrue(
                site.mine().covers(-163, -507),
                "Живой план observe24: дерево (-163,85,-507) стоит над лотом шахты " + site.mine()
                        + " Иначе тест planCell_LiveObserve24TreeAboveMine_NotPlan перестанет ловить эту поломку. При смене planCell_LiveObserve24TreeAboveMine_NotPlan проверь это утверждение.");
        assertFalse(
                site.planCell(-163, 97, -507),
                "Живой случай observe24: бревно y=97 над лотом шахты — не план, дерево рубится целиком"
                        + " Иначе тест planCell_LiveObserve24TreeAboveMine_NotPlan перестанет ловить эту поломку. При смене planCell_LiveObserve24TreeAboveMine_NotPlan проверь это утверждение.");
        assertTrue(
                site.planCell(-163, plan.anchor().y(), -507),
                "Предусловие: у земли та же колонка остаётся планом шахты"
                        + " Иначе тест planCell_LiveObserve24TreeAboveMine_NotPlan перестанет ловить эту поломку. При смене planCell_LiveObserve24TreeAboveMine_NotPlan проверь это утверждение.");
    }
}
