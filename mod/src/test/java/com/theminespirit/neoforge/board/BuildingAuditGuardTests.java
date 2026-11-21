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
                        + "постройка без факта выпадает из состояния графа молча: " + missing
                        + " Иначе тест planBuildings_EveryPlanId_HasAuditor перестанет ловить эту поломку. При смене planBuildings_EveryPlanId_HasAuditor проверь это утверждение.");
    }

    @Test
    void planIds_EveryPlanBuilding_MapToGraph() {
        assertEquals(
                "warehouse",
                BuildingAuditInputs.key("storage"),
                "склад плана — постройка графа warehouse"
                        + " Иначе тест planIds_EveryPlanBuilding_MapToGraph перестанет ловить эту поломку. При смене planIds_EveryPlanBuilding_MapToGraph проверь это утверждение.");
        assertEquals(
                "pen_cow",
                BuildingAuditInputs.key("pen"),
                "загон плана — постройка графа pen_cow"
                        + " Иначе тест planIds_EveryPlanBuilding_MapToGraph перестанет ловить эту поломку. При смене planIds_EveryPlanBuilding_MapToGraph проверь это утверждение.");
        assertEquals(
                "chests",
                BuildingAuditInputs.key("yard"),
                "двор плана считается по сундукам"
                        + " Иначе тест planIds_EveryPlanBuilding_MapToGraph перестанет ловить эту поломку. При смене planIds_EveryPlanBuilding_MapToGraph проверь это утверждение.");
        assertEquals(
                "lighting",
                BuildingAuditInputs.key("torches"),
                "свет плана — постройка lighting"
                        + " Иначе тест planIds_EveryPlanBuilding_MapToGraph перестанет ловить эту поломку. При смене planIds_EveryPlanBuilding_MapToGraph проверь это утверждение.");
        assertEquals(
                "orchard_oak",
                BuildingAuditInputs.key("orchard"),
                "сад плана — дубовый сад"
                        + " Иначе тест planIds_EveryPlanBuilding_MapToGraph перестанет ловить эту поломку. При смене planIds_EveryPlanBuilding_MapToGraph проверь это утверждение.");
        assertEquals(
                "field",
                BuildingAuditInputs.key("field_a"),
                "пашня плана — постройка field"
                        + " Иначе тест planIds_EveryPlanBuilding_MapToGraph перестанет ловить эту поломку. При смене planIds_EveryPlanBuilding_MapToGraph проверь это утверждение.");
        assertEquals(
                "field",
                BuildingAuditInputs.key("field_b"),
                "вторая пашня плана — та же постройка field"
                        + " Иначе тест planIds_EveryPlanBuilding_MapToGraph перестанет ловить эту поломку. При смене planIds_EveryPlanBuilding_MapToGraph проверь это утверждение.");
        assertEquals(
                "family_house_a",
                BuildingAuditInputs.key("family_house_a"),
                "дом семьи сохраняет своё имя"
                        + " Иначе тест planIds_EveryPlanBuilding_MapToGraph перестанет ловить эту поломку. При смене planIds_EveryPlanBuilding_MapToGraph проверь это утверждение.");
    }

    @Test
    void grouped_TwoFieldsAndOneFieldId_KeepsBothUnderOneGraphBuilding() {
        Map<String, List<Placement>> groups = BuildingAuditInputs.grouped(TownFixtures.flatPlan());

        assertEquals(
                List.of("field_a", "field_b"),
                groups.get("field").stream().map(Placement::id).toList(),
                "Пашни плана field_a и field_b обязаны аудироваться обе под graph-id field: иначе последняя "
                        + "перезаписывает первую и неготовое поле маскирует готовое"
                        + " При смене grouped_TwoFieldsAndOneFieldId_KeepsBothUnderOneGraphBuilding проверь это утверждение.");
    }

    @Test
    void sum_TwoFields_CountsBothAndLetsNeitherMaskTheOther() {
        BuildingAuditInputs.Audit built = new BuildingAuditInputs.Audit(20, 20, Map.of(), Map.of(), 0, 0);
        BuildingAuditInputs.Audit missing =
                new BuildingAuditInputs.Audit(20, 10, Map.of("minecraft:farmland", 10), Map.of(), 0, 0);

        BuildingAuditInputs.Audit sum = BuildingAuditInputs.sum(List.of(built, missing));

        assertEquals(
                40,
                sum.total(),
                "Обе пашни в аудите: иначе одна пашня маскирует другую"
                        + " При смене sum_TwoFields_CountsBothAndLetsNeitherMaskTheOther проверь это утверждение.");
        assertEquals(
                10,
                sum.missingCount(),
                "Недостача второй пашни видна графу: поле не считается готовым, пока не готовы обе"
                        + " Иначе тест sum_TwoFields_CountsBothAndLetsNeitherMaskTheOther перестанет ловить эту поломку. При смене sum_TwoFields_CountsBothAndLetsNeitherMaskTheOther проверь это утверждение.");
        assertEquals(
                Map.of("minecraft:farmland", 10),
                sum.missing(),
                "Недостача суммируется по обеим пашням, а не берётся от последней"
                        + " Иначе тест sum_TwoFields_CountsBothAndLetsNeitherMaskTheOther перестанет ловить эту поломку. При смене sum_TwoFields_CountsBothAndLetsNeitherMaskTheOther проверь это утверждение.");
    }

    @Test
    void unknownChunks_Unloaded_DoNotInventNumbers() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "hut",
                1,
                new GraphFacts.Position(1, 64, 2),
                new BuildingAuditInputs.Audit(20, 15, Map.of("minecraft:dirt", 5), Map.of("minecraft:stone", 2), 1, 1),
                true);

        assertTrue(
                fact.unknown(),
                "незагруженные чанки делают факт неизвестным"
                        + " Иначе тест unknownChunks_Unloaded_DoNotInventNumbers перестанет ловить эту поломку. При смене unknownChunks_Unloaded_DoNotInventNumbers проверь это утверждение.");
        assertEquals(
                0,
                fact.auditPercent(),
                "пока чанки не загружены, процент не выдумывается"
                        + " Иначе тест unknownChunks_Unloaded_DoNotInventNumbers перестанет ловить эту поломку. При смене unknownChunks_Unloaded_DoNotInventNumbers проверь это утверждение.");
        assertEquals(
                0,
                fact.damage(),
                "пока чанки не загружены, повреждения не выдумываются"
                        + " Иначе тест unknownChunks_Unloaded_DoNotInventNumbers перестанет ловить эту поломку. При смене unknownChunks_Unloaded_DoNotInventNumbers проверь это утверждение.");
        assertEquals(
                Map.of(),
                fact.missing(),
                "пока чанки не загружены, недостача не выдумывается"
                        + " Иначе тест unknownChunks_Unloaded_DoNotInventNumbers перестанет ловить эту поломку. При смене unknownChunks_Unloaded_DoNotInventNumbers проверь это утверждение.");
        assertEquals(
                Map.of(),
                fact.extra(),
                "пока чанки не загружены, лишнее не выдумывается"
                        + " Иначе тест unknownChunks_Unloaded_DoNotInventNumbers перестанет ловить эту поломку. При смене unknownChunks_Unloaded_DoNotInventNumbers проверь это утверждение.");
    }

    @Test
    void knownAudit_Loaded_GivesPercentMissingExtraAndDamage() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "hut",
                1,
                new GraphFacts.Position(1, 64, 2),
                new BuildingAuditInputs.Audit(20, 15, Map.of("minecraft:dirt", 5), Map.of("minecraft:stone", 2), 1, 0),
                true);

        assertFalse(
                fact.unknown(),
                "загруженные чанки дают известный аудит"
                        + " Иначе тест knownAudit_Loaded_GivesPercentMissingExtraAndDamage перестанет ловить эту поломку. При смене knownAudit_Loaded_GivesPercentMissingExtraAndDamage проверь это утверждение.");
        assertEquals(
                75,
                fact.auditPercent(),
                "процент — доля целых клеток схемы"
                        + " Иначе тест knownAudit_Loaded_GivesPercentMissingExtraAndDamage перестанет ловить эту поломку. При смене knownAudit_Loaded_GivesPercentMissingExtraAndDamage проверь это утверждение.");
        assertEquals(
                Map.of("minecraft:dirt", 5),
                fact.missing(),
                "недостача — блоки схемы без нужного блока"
                        + " Иначе тест knownAudit_Loaded_GivesPercentMissingExtraAndDamage перестанет ловить эту поломку. При смене knownAudit_Loaded_GivesPercentMissingExtraAndDamage проверь это утверждение.");
        assertEquals(
                Map.of("minecraft:stone", 2),
                fact.extra(),
                "лишнее — блоки в клетках, где по схеме воздух"
                        + " Иначе тест knownAudit_Loaded_GivesPercentMissingExtraAndDamage перестанет ловить эту поломку. При смене knownAudit_Loaded_GivesPercentMissingExtraAndDamage проверь это утверждение.");
        assertEquals(
                1,
                fact.wrong(),
                "чужой блок отличается от пустой клетки"
                        + " Иначе тест knownAudit_Loaded_GivesPercentMissingExtraAndDamage перестанет ловить эту поломку. При смене knownAudit_Loaded_GivesPercentMissingExtraAndDamage проверь это утверждение.");
        assertEquals(
                7,
                fact.damage(),
                "повреждения — недостача и лишнее вместе"
                        + " Иначе тест knownAudit_Loaded_GivesPercentMissingExtraAndDamage перестанет ловить эту поломку. При смене knownAudit_Loaded_GivesPercentMissingExtraAndDamage проверь это утверждение.");
    }

    @Test
    void everyPlanBuilding_Damaged_IsRepairedNotOnlyHouse() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "warehouse",
                1,
                new GraphFacts.Position(40, 64, 40),
                new BuildingAuditInputs.Audit(40, 33, Map.of("minecraft:oak_planks", 7), Map.of(), 0, 0),
                true);

        assertFalse(
                fact.unknown(),
                "склад плана аудируется схемой, как и дом"
                        + " Иначе тест everyPlanBuilding_Damaged_IsRepairedNotOnlyHouse перестанет ловить эту поломку. При смене everyPlanBuilding_Damaged_IsRepairedNotOnlyHouse проверь это утверждение.");
        assertEquals(
                7,
                fact.damage(),
                "повреждения считаются для любой постройки плана, а не только для дома"
                        + " Иначе тест everyPlanBuilding_Damaged_IsRepairedNotOnlyHouse перестанет ловить эту поломку. При смене everyPlanBuilding_Damaged_IsRepairedNotOnlyHouse проверь это утверждение.");
        assertEquals(
                Map.of("minecraft:oak_planks", 7),
                fact.missing(),
                "недостача склада видна графу"
                        + " Иначе тест everyPlanBuilding_Damaged_IsRepairedNotOnlyHouse перестанет ловить эту поломку. При смене everyPlanBuilding_Damaged_IsRepairedNotOnlyHouse проверь это утверждение.");
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
                "при перестройке состав блоков может быть пуст, но повреждение считается по числу недостающих клеток"
                        + " Иначе тест rebuild_NoComposition_StillDamaged перестанет ловить эту поломку. При смене rebuild_NoComposition_StillDamaged проверь это утверждение.");
    }

    @Test
    void unbuiltBuilding_Absent_IsKnownWithoutDamage() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "hut",
                0,
                new GraphFacts.Position(1, 64, 2),
                new BuildingAuditInputs.Audit(20, 0, Map.of("minecraft:dirt", 20), Map.of(), 0, 0),
                false);

        assertFalse(
                fact.unknown(),
                "отсутствие постройки известно и без чанков: её просто нет"
                        + " Иначе тест unbuiltBuilding_Absent_IsKnownWithoutDamage перестанет ловить эту поломку. При смене unbuiltBuilding_Absent_IsKnownWithoutDamage проверь это утверждение.");
        assertEquals(
                0,
                fact.auditPercent(),
                "непостроенная постройка — ноль процентов"
                        + " Иначе тест unbuiltBuilding_Absent_IsKnownWithoutDamage перестанет ловить эту поломку. При смене unbuiltBuilding_Absent_IsKnownWithoutDamage проверь это утверждение.");
        assertEquals(
                0,
                fact.damage(),
                "у непостроенной постройки нет повреждений: ремонт не нужен"
                        + " Иначе тест unbuiltBuilding_Absent_IsKnownWithoutDamage перестанет ловить эту поломку. При смене unbuiltBuilding_Absent_IsKnownWithoutDamage проверь это утверждение.");
        assertEquals(
                Map.of("minecraft:dirt", 20),
                fact.missing(),
                "недостача показывает, что ещё построить"
                        + " Иначе тест unbuiltBuilding_Absent_IsKnownWithoutDamage перестанет ловить эту поломку. При смене unbuiltBuilding_Absent_IsKnownWithoutDamage проверь это утверждение.");
    }

    @Test
    void partiallyBuiltMine_DamageReported_EvenWhenLedgerSaysPlanned() {
        GraphFacts.Building fact = BuildingAuditInputs.fact(
                "mine",
                0,
                new GraphFacts.Position(1, 64, 2),
                new BuildingAuditInputs.Audit(1512, 1125, Map.of("minecraft:cobblestone", 387), Map.of(), 0, 0),
                false);

        assertEquals(
                74,
                fact.auditPercent(),
                "шахта построена на 74%, это её аудит"
                        + " Иначе тест partiallyBuiltMine_DamageReported_EvenWhenLedgerSaysPlanned перестанет ловить эту поломку. При смене partiallyBuiltMine_DamageReported_EvenWhenLedgerSaysPlanned проверь это утверждение.");
        assertTrue(
                fact.damage() > 0,
                "начатая шахта повреждена на 387 клеток: без повреждения граф не ставит event:repair_mine, "
                        + "и шахта 75→74% стоит без ремонта"
                        + " Иначе тест partiallyBuiltMine_DamageReported_EvenWhenLedgerSaysPlanned перестанет ловить эту поломку. При смене partiallyBuiltMine_DamageReported_EvenWhenLedgerSaysPlanned проверь это утверждение.");
    }

    @Test
    void audit_Plus_SumsPartsAndUnknown() {
        BuildingAuditInputs.Audit sum = new BuildingAuditInputs.Audit(
                        10, 7, Map.of("minecraft:dirt", 3), Map.of("minecraft:stone", 1), 1, 0)
                .plus(new BuildingAuditInputs.Audit(5, 2, Map.of("minecraft:dirt", 3), Map.of(), 0, 2));

        assertEquals(
                new BuildingAuditInputs.Audit(15, 9, Map.of("minecraft:dirt", 6), Map.of("minecraft:stone", 1), 1, 2),
                sum,
                "части аудита складываются"
                        + " Иначе тест audit_Plus_SumsPartsAndUnknown перестанет ловить эту поломку. При смене audit_Plus_SumsPartsAndUnknown проверь это утверждение.");
        assertEquals(
                6,
                sum.missingCount(),
                "недостача суммы — сумма блоков обоих аудитов"
                        + " Иначе тест audit_Plus_SumsPartsAndUnknown перестанет ловить эту поломку. При смене audit_Plus_SumsPartsAndUnknown проверь это утверждение.");
    }
}
