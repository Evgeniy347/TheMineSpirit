package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.graph.GraphFacts;
import java.util.List;
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
                "hut", 1, new GraphFacts.Position(1, 64, 2), new BuildingAuditInputs.Audit(20, 15, 2, 1), true);

        assertTrue(fact.unknown(), "незагруженные чанки делают факт неизвестным");
        assertEquals(0, fact.auditPercent(), "пока чанки не загружены, процент не выдумывается");
        assertEquals(0, fact.damage(), "пока чанки не загружены, повреждения не выдумываются");
        assertEquals(0, fact.missing(), "пока чанки не загружены, недостача не выдумывается");
        assertEquals(0, fact.extra(), "пока чанки не загружены, лишнее не выдумывается");
    }

    @Test
    void knownAudit_Loaded_GivesPercentMissingExtraAndDamage() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "hut", 1, new GraphFacts.Position(1, 64, 2), new BuildingAuditInputs.Audit(20, 15, 2, 0), true);

        assertFalse(fact.unknown(), "загруженные чанки дают известный аудит");
        assertEquals(75, fact.auditPercent(), "процент — доля целых клеток схемы");
        assertEquals(5, fact.missing(), "недостача — клетки схемы без нужного блока");
        assertEquals(2, fact.extra(), "лишнее — блоки в клетках, где по схеме воздух");
        assertEquals(7, fact.damage(), "повреждения — недостача и лишнее вместе");
    }

    @Test
    void unbuiltBuilding_Absent_IsKnownWithoutDamage() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "hut", 0, new GraphFacts.Position(1, 64, 2), new BuildingAuditInputs.Audit(20, 0, 0, 0), false);

        assertFalse(fact.unknown(), "отсутствие постройки известно и без чанков: её просто нет");
        assertEquals(0, fact.auditPercent(), "непостроенная постройка — ноль процентов");
        assertEquals(0, fact.damage(), "у непостроенной постройки нет повреждений: ремонт не нужен");
        assertEquals(20, fact.missing(), "недостача показывает, сколько блоков ещё построить");
    }

    @Test
    void audit_Plus_SumsPartsAndUnknown() {
        BuildingAuditInputs.Audit sum =
                new BuildingAuditInputs.Audit(10, 7, 1, 0).plus(new BuildingAuditInputs.Audit(5, 2, 0, 2));

        assertEquals(new BuildingAuditInputs.Audit(15, 9, 1, 2), sum, "части аудита складываются");
        assertEquals(6, sum.missing(), "недостача суммы — разница итога и сделанного");
    }
}
