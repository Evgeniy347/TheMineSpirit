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
