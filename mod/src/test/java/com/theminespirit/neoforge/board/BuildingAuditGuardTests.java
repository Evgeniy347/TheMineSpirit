package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownFixtures;
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
        assertEquals("field", BuildingAuditInputs.key("field_b"), "вторая пашня плана — та же постройка field");
        assertEquals("family_house_a", BuildingAuditInputs.key("family_house_a"), "дом семьи сохраняет своё имя");
    }

    @Test
    void grouped_TwoFieldsAndOneFieldId_KeepsBothUnderOneGraphBuilding() {
        Map<String, List<Placement>> groups = BuildingAuditInputs.grouped(TownFixtures.flatPlan());

        assertEquals(
                List.of("field_a", "field_b"),
                groups.get("field").stream().map(Placement::id).toList(),
                "Пашни плана field_a и field_b обязаны аудироваться обе под graph-id field: иначе последняя "
                        + "перезаписывает первую и неготовое поле маскирует готовое");
    }

    @Test
    void sum_TwoFields_CountsBothAndLetsNeitherMaskTheOther() {
        BuildingAuditInputs.Audit built = new BuildingAuditInputs.Audit(20, 20, Map.of(), Map.of(), 0, 0);
        BuildingAuditInputs.Audit missing =
                new BuildingAuditInputs.Audit(20, 10, Map.of("minecraft:farmland", 10), Map.of(), 0, 0);

        BuildingAuditInputs.Audit sum = BuildingAuditInputs.sum(List.of(built, missing));

        assertEquals(40, sum.total(), "Обе пашни в аудите: иначе одна пашня маскирует другую");
        assertEquals(
                10,
                sum.missingCount(),
                "Недостача второй пашни видна графу: поле не считается готовым, пока не готовы обе");
        assertEquals(
                Map.of("minecraft:farmland", 10),
                sum.missing(),
                "Недостача суммируется по обеим пашням, а не берётся от последней");
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
    void everyPlanBuilding_Damaged_IsRepairedNotOnlyHouse() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "warehouse",
                1,
                new GraphFacts.Position(40, 64, 40),
                new BuildingAuditInputs.Audit(40, 33, Map.of("minecraft:oak_planks", 7), Map.of(), 0, 0),
                true);

        assertFalse(fact.unknown(), "склад плана аудируется схемой, как и дом");
        assertEquals(7, fact.damage(), "повреждения считаются для любой постройки плана, а не только для дома");
        assertEquals(Map.of("minecraft:oak_planks", 7), fact.missing(), "недостача склада видна графу");
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
    void partiallyBuiltMine_DamageReported_EvenWhenLedgerSaysPlanned() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "mine",
                0,
                new GraphFacts.Position(1, 64, 2),
                new BuildingAuditInputs.Audit(1512, 1125, Map.of("minecraft:cobblestone", 387), Map.of(), 0, 0),
                false);

        assertEquals(74, fact.auditPercent(), "шахта построена на 74%, это её аудит");
        assertTrue(
                fact.damage() > 0,
                "начатая шахта повреждена на 387 клеток: без повреждения граф не ставит event:repair_mine, "
                        + "и шахта 75→74% стоит без ремонта");
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
