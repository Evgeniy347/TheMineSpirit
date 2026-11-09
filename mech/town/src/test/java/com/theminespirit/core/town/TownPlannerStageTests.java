package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownPlannerStageTests {
    private static final TownRules RULES = TownRules.load();
    private static final BlockCoord ANCHOR = new BlockCoord(200, 70, -300);

    @Test
    void plan_AllStageRequirements_PlacedInPlan() {
        TownPlan plan = TownFixtures.plan((x, z) -> 70, (x, z) -> 0);
        List<String> missing = new ArrayList<>();
        for (StageRules.Stage stage : StageRules.load().stages()) {
            List<String> ids = new ArrayList<>(stage.requires().keySet());
            ids.addAll(stage.optional().keySet());
            for (String id : ids) {
                if (plan.placement(id).isEmpty()) {
                    missing.add("этап " + stage.stage() + ": " + id);
                }
            }
        }

        assertEquals(
                List.of(),
                missing,
                "Каждая обязательная и желательная постройка этапов 1–4 обязана иметь своё место в плане: без "
                        + "участка аудит не закроет этап и постройка не начнётся; не поместилось " + missing);
    }

    @Test
    void plan_HillyTerrain_PlacesAllStageRequirements() {
        TownPlan plan = TownFixtures.hillyPlan(7L);
        List<String> missing = new ArrayList<>();
        for (StageRules.Stage stage : StageRules.load().stages()) {
            for (String id : stage.requires().keySet()) {
                if (plan.placement(id).isEmpty()) {
                    missing.add("этап " + stage.stage() + ": " + id);
                }
            }
        }

        assertEquals(
                List.of(),
                missing,
                "На холмистом рельефе обязательные постройки этапов 1–4 получают лоты: иначе этап держится "
                        + "навсегда. Не поместилось " + missing + ", непоместившиеся " + plan.unplaced());
    }

    @Test
    void plan_StrayBlockAboveAnchor_DoesNotCutContourToItsColumn() {
        TownPlan plan = TownFixtures.plan((x, z) -> x == ANCHOR.x() && z == ANCHOR.z() ? 80 : 70, (x, z) -> 0);

        assertTrue(
                plan.area() > RULES.ground().area() / 2,
                "Одиночный блок над якорем не отрезает участок от соседей: высота якоря — медиана суши вокруг, "
                        + "иначе план не растёт и ни одна постройка не получает лота. Площадь " + plan.area());
        assertTrue(
                plan.placement("hut").isPresent(),
                "Хижина получает лот даже когда ровно над якорем стоит чужой блок: не поместилось " + plan.unplaced());
    }
}
