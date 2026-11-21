package com.theminespirit.core.town;

import static com.theminespirit.core.town.TownPlannerTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TownPlannerTestsExtraTests {

    @Test
    void plan_ExtensionGrowsFence_OldPlotsStayInside() {
        TownPlan base = plan((x, z) -> 70, (x, z) -> 0);
        TownPlan old = new TownPlan(
                base.anchor(),
                base.region(),
                base.streets(),
                base.placements().stream()
                        .filter(place -> !StageSites.ORCHARD_EXTRA.equals(place.id()))
                        .toList(),
                base.fence(),
                base.unplaced());
        StageSite before = StageSites.of(old, 1, old.anchor().y(), RULES);
        TownPlan grown =
                TownPlanner.plant(grid((x, z) -> 70, (x, z) -> 0), old, RULES, STAGES, List.of("orchard_jungle"), 1);
        StageSite after = StageSites.of(grown, 1, grown.anchor().y(), RULES);

        assertTrue(
                after.enclosure().count() >= before.enclosure().count(),
                "Контур ограды при росте плана не вырос: новый сад остался бы за забором"
                        + " Иначе тест plan_ExtensionGrowsFence_OldPlotsStayInside перестанет ловить эту поломку. При смене plan_ExtensionGrowsFence_OldPlotsStayInside проверь это утверждение.");
        assertTrue(
                StageSites.insideEnclosure(after),
                "После роста плана все старые и новые участки внутри ограды"
                        + " Иначе тест plan_ExtensionGrowsFence_OldPlotsStayInside перестанет ловить эту поломку. При смене plan_ExtensionGrowsFence_OldPlotsStayInside проверь это утверждение.");
        assertTrue(
                after.ring().posts().containsAll(before.ring().posts())
                        || after.enclosure().count() > before.enclosure().count(),
                "Ограда этапа не охватывает старый и новый контур: старые куски остались бы за забором"
                        + " Иначе тест plan_ExtensionGrowsFence_OldPlotsStayInside перестанет ловить эту поломку. При смене plan_ExtensionGrowsFence_OldPlotsStayInside проверь это утверждение.");
    }

    @Test
    void stages_SecondStagePlan_AllBuildingsRequired() {
        StageRules.Stage second = StageRules.load().stages().get(1);

        assertEquals(
                Set.copyOf(second.plan()),
                second.requires().keySet(),
                "Этап 2 закрывается всем составом плана: у каждой постройки плана есть требование и уровень"
                        + " Иначе тест stages_SecondStagePlan_AllBuildingsRequired перестанет ловить эту поломку. При смене stages_SecondStagePlan_AllBuildingsRequired проверь это утверждение.");
        assertTrue(
                second.requires().values().stream().allMatch(level -> level == 1),
                "Постройки плана этапа 2 стоят уровнем 1 из каталога: завышенный уровень держал бы этап навсегда"
                        + " Иначе тест stages_SecondStagePlan_AllBuildingsRequired перестанет ловить эту поломку. При смене stages_SecondStagePlan_AllBuildingsRequired проверь это утверждение.");
    }

    @Test
    void stages_EveryCatalogEntry_InExactlyOneStage() {
        Set<String> seen = new HashSet<>();
        for (List<String> stage : STAGES) {
            for (String id : stage) {
                assertTrue(
                        seen.add(id),
                        "Постройка '" + id + "' в двух этапах stages.json: место в плане одно, этап — один"
                                + " Иначе тест stages_EveryCatalogEntry_InExactlyOneStage перестанет ловить эту поломку. При смене stages_EveryCatalogEntry_InExactlyOneStage проверь это утверждение.");
            }
        }
        List<String> orphans = new ArrayList<>();
        RULES.buildings().stream()
                .map(TownEntry::id)
                .filter(id -> !seen.contains(id))
                .forEach(orphans::add);
        assertEquals(
                List.of(),
                orphans,
                "Постройки каталога town.json без этапа: план размечает все постройки всех этапов сразу"
                        + " Иначе тест stages_EveryCatalogEntry_InExactlyOneStage перестанет ловить эту поломку. При смене stages_EveryCatalogEntry_InExactlyOneStage проверь это утверждение.");
    }
}
