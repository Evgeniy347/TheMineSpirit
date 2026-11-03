package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.graph.GraphFacts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildingAuditGuardTests {
    @Test
    void planBuildings_EveryPlanId_HasAuditor() {
        List<String> missing = StageRules.load().stages().stream()
                .flatMap(stage -> stage.plan().stream())
                .distinct()
                .filter(id -> !BuildingAuditInputs.auditable(id))
                .sorted()
                .toList();

        assertEquals(
                List.of(),
                missing,
                "у каждой постройки плана обязан быть источник факта: аудитор схемы или особый аудитор этапа; "
                        + "постройка без факта выпадает из состояния графа молча: " + missing);
    }

    @Test
    void planIds_EveryPlanBuilding_MapToGraph() {
        assertEquals("warehouse", BuildingAuditInputs.key("storage"), "склад плана — постройка графа warehouse");
        assertEquals("pen_cow", BuildingAuditInputs.key("pen"), "загон плана — постройка графа pen_cow");
        assertEquals("chests", BuildingAuditInputs.key("yard"), "двор плана считается по сундукам");
        assertEquals("lighting", BuildingAuditInputs.key("torches"), "свет плана — постройка lighting");
        assertEquals("orchard_oak", BuildingAuditInputs.key("orchard"), "сад плана — дубовый сад");
        assertEquals("field", BuildingAuditInputs.key("field_a"), "пашня плана — постройка field");
        assertEquals("family_house_a", BuildingAuditInputs.key("family_house_a"), "дом семьи сохраняет своё имя");
    }

    @Test
    void unknownChunks_Unloaded_DoNotInventNumbers() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "hut",
                1,
                new GraphFacts.Position(1, 64, 2),
                new BuildingAuditInputs.Audit(20, 15, Map.of("minecraft:dirt", 5), Map.of("minecraft:stone", 2), 1, 1),
                true);

        assertTrue(fact.unknown(), "незагруженные чанки делают факт неизвестным");
        assertEquals(0, fact.auditPercent(), "пока чанки не загружены, процент не выдумывается");
        assertEquals(0, fact.damage(), "пока чанки не загружены, повреждения не выдумываются");
        assertEquals(Map.of(), fact.missing(), "пока чанки не загружены, недостача не выдумывается");
        assertEquals(Map.of(), fact.extra(), "пока чанки не загружены, лишнее не выдумывается");
    }

    @Test
    void knownAudit_Loaded_GivesPercentMissingExtraAndDamage() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "hut",
                1,
                new GraphFacts.Position(1, 64, 2),
                new BuildingAuditInputs.Audit(20, 15, Map.of("minecraft:dirt", 5), Map.of("minecraft:stone", 2), 1, 0),
                true);

        assertFalse(fact.unknown(), "загруженные чанки дают известный аудит");
        assertEquals(75, fact.auditPercent(), "процент — доля целых клеток схемы");
        assertEquals(Map.of("minecraft:dirt", 5), fact.missing(), "недостача — блоки схемы без нужного блока");
        assertEquals(Map.of("minecraft:stone", 2), fact.extra(), "лишнее — блоки в клетках, где по схеме воздух");
        assertEquals(1, fact.wrong(), "чужой блок отличается от пустой клетки");
        assertEquals(7, fact.damage(), "повреждения — недостача и лишнее вместе");
    }

    @Test
    void rebuild_NoComposition_StillDamaged() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "hut",
                1,
                new GraphFacts.Position(1, 64, 2),
                new BuildingAuditInputs.Audit(20, 10, Map.of(), Map.of(), 0, 0),
                true);

        assertEquals(
                10,
                fact.damage(),
                "при перестройке состав блоков может быть пуст, но повреждение считается по числу недостающих клеток");
    }

    @Test
    void unbuiltBuilding_Absent_IsKnownWithoutDamage() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "hut",
                0,
                new GraphFacts.Position(1, 64, 2),
                new BuildingAuditInputs.Audit(20, 0, Map.of("minecraft:dirt", 20), Map.of(), 0, 0),
                false);

        assertFalse(fact.unknown(), "отсутствие постройки известно и без чанков: её просто нет");
        assertEquals(0, fact.auditPercent(), "непостроенная постройка — ноль процентов");
        assertEquals(0, fact.damage(), "у непостроенной постройки нет повреждений: ремонт не нужен");
        assertEquals(Map.of("minecraft:dirt", 20), fact.missing(), "недостача показывает, что ещё построить");
    }

    @Test
    void audit_Plus_SumsPartsAndUnknown() {
        BuildingAuditInputs.Audit sum = new BuildingAuditInputs.Audit(
                        10, 7, Map.of("minecraft:dirt", 3), Map.of("minecraft:stone", 1), 1, 0)
                .plus(new BuildingAuditInputs.Audit(5, 2, Map.of("minecraft:dirt", 3), Map.of(), 0, 2));

        assertEquals(
                new BuildingAuditInputs.Audit(15, 9, Map.of("minecraft:dirt", 6), Map.of("minecraft:stone", 1), 1, 2),
                sum,
                "части аудита складываются");
        assertEquals(6, sum.missingCount(), "недостача суммы — сумма блоков обоих аудитов");
    }
}
