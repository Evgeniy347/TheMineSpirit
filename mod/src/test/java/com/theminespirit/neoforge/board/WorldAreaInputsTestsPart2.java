package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.graph.GraphFacts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldAreaInputsTestsPart2 {
    @Test
    void buildings_WrongHeight_MissingAndExtraKept() {
        Map<String, GraphFacts.Building> base = new LinkedHashMap<>();
        base.put(
                "hut",
                new GraphFacts.Building(
                        "hut",
                        1,
                        75,
                        5,
                        Map.of("minecraft:dirt", 3),
                        Map.of("minecraft:stone", 2),
                        1,
                        new GraphFacts.Position(0, 64, 0),
                        false,
                        true));

        Map<String, GraphFacts.Building> facts = WorldAreaInputs.buildings(base, null);

        assertEquals(
                Map.of("minecraft:dirt", 3),
                facts.get("hut").missing(),
                "Недостача на единой высоте остаётся фактом: по ней ставится стройка"
                        + " Иначе тест buildings_WrongHeight_MissingAndExtraKept перестанет ловить эту поломку. При смене buildings_WrongHeight_MissingAndExtraKept проверь это утверждение.");
        assertEquals(
                Map.of("minecraft:stone", 2),
                facts.get("hut").extra(),
                "Блоки не на единой высоте остаются extra: по ним ставится снос/восстановление"
                        + " Иначе тест buildings_WrongHeight_MissingAndExtraKept перестанет ловить эту поломку. При смене buildings_WrongHeight_MissingAndExtraKept проверь это утверждение.");
        assertEquals(
                1,
                facts.get("hut").wrong(),
                "Чужая клетка плана не теряется при сборке факта"
                        + " Иначе тест buildings_WrongHeight_MissingAndExtraKept перестанет ловить эту поломку. При смене buildings_WrongHeight_MissingAndExtraKept проверь это утверждение.");
    }

    @Test
    void buildings_UnknownAudit_IsNotReady() {
        Map<String, GraphFacts.Building> base = new LinkedHashMap<>();
        base.put(
                "hut",
                new GraphFacts.Building(
                        "hut", 1, 100, 0, Map.of(), Map.of(), 0, new GraphFacts.Position(0, 64, 0), false, true));

        Map<String, GraphFacts.Building> facts =
                WorldAreaInputs.buildings(base, new RepairAudit.Report(20, 5, false, List.of(), 2));

        assertTrue(
                facts.get("hut").unknown(),
                "Незагруженные чанки делают аудит неизвестным"
                        + " Иначе тест buildings_UnknownAudit_IsNotReady перестанет ловить эту поломку. При смене buildings_UnknownAudit_IsNotReady проверь это утверждение.");
        assertEquals(
                0,
                facts.get("hut").damage(),
                "Пока аудит не готов, повреждений не выдумываем"
                        + " Иначе тест buildings_UnknownAudit_IsNotReady перестанет ловить эту поломку. При смене buildings_UnknownAudit_IsNotReady проверь это утверждение.");
        assertEquals(
                0,
                facts.get("hut").auditPercent(),
                "Пока аудит не готов, процент не выдумываем"
                        + " Иначе тест buildings_UnknownAudit_IsNotReady перестанет ловить эту поломку. При смене buildings_UnknownAudit_IsNotReady проверь это утверждение.");
    }
}
