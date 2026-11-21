package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatherDemandTests {
    private static final BlockCoord AT = new BlockCoord(0, 64, 0);

    private static GatherDemand.Need need(String id, String item, int count, int priority) {
        return new GatherDemand.Need(id, item, id, count, priority, AT);
    }

    @Test
    void merge_ThreeCausesSameLog_OneRequestWithTotal() {
        GatherDemand.Need top = need("graph:make:jungle_log", "jungle_log", 24, 90);
        List<GatherDemand.Need> merged = GatherDemand.merge(List.of(
                need("graph:chop_jungle_log", "jungle_log", 24, 80),
                top,
                need("graph:event:more2_stage_prep:more2_prepare_stage", "jungle_log", 16, 40)));

        assertEquals(
                1,
                merged.size(),
                "Три причины одного бревна — одна заявка, а не три: " + merged
                        + " Иначе тест merge_ThreeCausesSameLog_OneRequestWithTotal перестанет ловить эту поломку. При смене merge_ThreeCausesSameLog_OneRequestWithTotal проверь это утверждение.");
        assertEquals(
                64,
                merged.get(0).count(),
                "Количество заявки — сумма всех причин (24+24+16): " + merged
                        + " Иначе тест merge_ThreeCausesSameLog_OneRequestWithTotal перестанет ловить эту поломку. При смене merge_ThreeCausesSameLog_OneRequestWithTotal проверь это утверждение.");
        assertEquals(
                "graph:chop_jungle_log",
                merged.get(0).id(),
                "Заявка держит графовый путь рубки: " + merged
                        + " Иначе тест merge_ThreeCausesSameLog_OneRequestWithTotal перестанет ловить эту поломку. При смене merge_ThreeCausesSameLog_OneRequestWithTotal проверь это утверждение.");
        assertEquals(
                top.priority(),
                merged.get(0).priority(),
                "Приоритет общей заявки — самый высокий из причин: " + merged
                        + " Иначе тест merge_ThreeCausesSameLog_OneRequestWithTotal перестанет ловить эту поломку. При смене merge_ThreeCausesSameLog_OneRequestWithTotal проверь это утверждение.");
    }

    @Test
    void merge_DifferentItems_StayApart() {
        List<GatherDemand.Need> merged = GatherDemand.merge(List.of(
                need("graph:chop_jungle_log", "jungle_log", 64, 80), need("graph:chop_oak_log", "oak_log", 32, 70)));

        assertEquals(
                2,
                merged.size(),
                "Дуб и джунгль — разные предметы, их заявки не сливаются: " + merged
                        + " Иначе тест merge_DifferentItems_StayApart перестанет ловить эту поломку. При смене merge_DifferentItems_StayApart проверь это утверждение.");
    }

    @Test
    void onePerWood_LogAndSaplingsSameSpecies_WaitForOneRequest() {
        List<GatherDemand.Need> merged = GatherDemand.onePerWood(List.of(
                need("graph:chop_jungle_log", "jungle_log", 64, 80),
                need("graph:gather_jungle_sapling", "jungle_sapling", 6, 90)));

        assertEquals(
                1,
                merged.size(),
                "Лес одной породы — одна заявка: саженцы ждут, пока рубят брёвна: " + merged
                        + " Иначе тест onePerWood_LogAndSaplingsSameSpecies_WaitForOneRequest перестанет ловить эту поломку. При смене onePerWood_LogAndSaplingsSameSpecies_WaitForOneRequest проверь это утверждение.");
        assertEquals(
                "jungle_log",
                merged.get(0).item(),
                "Побеждает бревно, даже если саженцы важнее: " + merged
                        + " Иначе тест onePerWood_LogAndSaplingsSameSpecies_WaitForOneRequest перестанет ловить эту поломку. При смене onePerWood_LogAndSaplingsSameSpecies_WaitForOneRequest проверь это утверждение.");
    }

    @Test
    void onePerWood_PlanksSameSpecies_StaySeparateCraft() {
        List<GatherDemand.Need> merged = GatherDemand.onePerWood(List.of(
                need("graph:chop_jungle_log", "jungle_log", 64, 80),
                need("graph:craft_jungle_planks", "jungle_planks", 8, 90)));

        assertEquals(
                2,
                merged.size(),
                "Доски — крафт из брёвен, а не вторая рубка: заявка на доски остаётся, иначе стройка ждёт: " + merged
                        + " При смене onePerWood_PlanksSameSpecies_StaySeparateCraft проверь это утверждение.");
    }

    @Test
    void onePerWood_DifferentSpecies_WorkInParallel() {
        List<GatherDemand.Need> merged = GatherDemand.onePerWood(List.of(
                need("graph:chop_jungle_log", "jungle_log", 64, 80), need("graph:chop_oak_log", "oak_log", 32, 70)));

        assertEquals(
                2,
                merged.size(),
                "Разные породы — разные заявки: " + merged
                        + " Иначе тест onePerWood_DifferentSpecies_WorkInParallel перестанет ловить эту поломку. При смене onePerWood_DifferentSpecies_WorkInParallel проверь это утверждение.");
    }

    @Test
    void onePerWood_StrippedLogAndPlainLog_PlainLogWins() {
        List<GatherDemand.Need> merged = GatherDemand.onePerWood(List.of(
                need("graph:strip_jungle_log", "stripped_jungle_log", 32, 90),
                need("graph:chop_jungle_log", "jungle_log", 64, 80)));

        assertEquals(
                1,
                merged.size(),
                "Ошкуренное бревно и обычное — одна порода и одна рубка: " + merged
                        + " Иначе тест onePerWood_StrippedLogAndPlainLog_PlainLogWins перестанет ловить эту поломку. При смене onePerWood_StrippedLogAndPlainLog_PlainLogWins проверь это утверждение.");
        assertEquals(
                "jungle_log",
                merged.get(0).item(),
                "Побеждает обычное бревно: ошкуривание — не повод держать вторую лесную заявку: " + merged
                        + " Иначе тест onePerWood_StrippedLogAndPlainLog_PlainLogWins перестанет ловить эту поломку. При смене onePerWood_StrippedLogAndPlainLog_PlainLogWins проверь это утверждение.");
    }

    @Test
    void demandItem_FuelPlanks_ResolvesSpeciesFromScan() {
        assertEquals(
                "jungle_planks",
                GatherDemand.demandItem("fuel_planks", "jungle"),
                "Топливные доски превращаются в доски выбранной сканом породы, а не остаются fuel_planks"
                        + " Иначе тест demandItem_FuelPlanks_ResolvesSpeciesFromScan перестанет ловить эту поломку. При смене demandItem_FuelPlanks_ResolvesSpeciesFromScan проверь это утверждение.");
        assertEquals(
                "oak_log",
                GatherDemand.demandItem("oak_log", "jungle"),
                "Обычный предмет не переименовывается: порода уже в его имени"
                        + " Иначе тест demandItem_FuelPlanks_ResolvesSpeciesFromScan перестанет ловить эту поломку. При смене demandItem_FuelPlanks_ResolvesSpeciesFromScan проверь это утверждение.");
    }

    @Test
    void onePerWood_FuelPlanksResolved_StayApartFromSameSpeciesLog() {
        List<GatherDemand.Need> merged = GatherDemand.onePerWood(List.of(
                need("graph:chop_jungle_log", "jungle_log", 64, 80),
                need("graph:craft_fuel_planks", GatherDemand.demandItem("fuel_planks", "jungle"), 8, 90)));

        assertEquals(
                2,
                merged.size(),
                "Доски из топлива — крафт, а не вторая рубка бревна той же породы: заявки не сливаются: " + merged
                        + " Иначе тест onePerWood_FuelPlanksResolved_StayApartFromSameSpeciesLog перестанет ловить эту поломку. При смене onePerWood_FuelPlanksResolved_StayApartFromSameSpeciesLog проверь это утверждение.");
    }

    @Test
    void wood_LogPlanksSapling_GivesSpecies() {
        assertEquals(
                "jungle",
                GatherDemand.wood("jungle_log"),
                "Бревно называет породу"
                        + " Иначе тест wood_LogPlanksSapling_GivesSpecies перестанет ловить эту поломку. При смене wood_LogPlanksSapling_GivesSpecies проверь это утверждение.");
        assertEquals(
                "jungle",
                GatherDemand.wood("jungle_planks"),
                "Доски — та же порода"
                        + " Иначе тест wood_LogPlanksSapling_GivesSpecies перестанет ловить эту поломку. При смене wood_LogPlanksSapling_GivesSpecies проверь это утверждение.");
        assertEquals(
                "jungle",
                GatherDemand.wood("jungle_sapling"),
                "Саженец — та же порода"
                        + " Иначе тест wood_LogPlanksSapling_GivesSpecies перестанет ловить эту поломку. При смене wood_LogPlanksSapling_GivesSpecies проверь это утверждение.");
        assertEquals(
                "oak",
                GatherDemand.wood("stripped_oak_log"),
                "Ошкуренное бревно — та же порода"
                        + " Иначе тест wood_LogPlanksSapling_GivesSpecies перестанет ловить эту поломку. При смене wood_LogPlanksSapling_GivesSpecies проверь это утверждение.");
        assertNull(
                GatherDemand.wood("cobblestone"),
                "Камень не порода дерева"
                        + " Иначе тест wood_LogPlanksSapling_GivesSpecies перестанет ловить эту поломку. При смене wood_LogPlanksSapling_GivesSpecies проверь это утверждение.");
    }
}
