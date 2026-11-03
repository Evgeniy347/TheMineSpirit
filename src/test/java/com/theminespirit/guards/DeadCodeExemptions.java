package com.theminespirit.guards;

import java.util.ArrayList;
import java.util.List;

final class DeadCodeExemptions {
    static final List<Exemption> OPEN_TASKS = List.of(
            new Exemption(
                    "src/main/java/com/theminespirit/core/scene/SceneGenerator.java|method|SceneGenerator#generate",
                    "Генератор сцены по сиду — чистая функция core, вход только из GameTest first_stage"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/board/graph/RecipeImport.java|method|RecipeImport#importRecipes",
                    "Генератор graph-recipes.json (export-задача): рецепты импортируются при генерации, "
                            + "в игре читается готовый файл; TestResults/task.md"),
            new Exemption(
                    "src/main/java/com/theminespirit/neoforge/SpiritEntity.java|method|SpiritEntity#setChild",
                    "Заготовка под открытую задачу: todo.md → «Дети»"),
            new Exemption(
                    "src/main/java/com/theminespirit/neoforge/board/BoardService.java|method|BoardService#completeCapture",
                    "Заготовка под открытую задачу: todo.md → «Распределение работ и сбор в поселении»"),
            new Exemption(
                    "src/main/java/com/theminespirit/neoforge/board/BoardService.java|method|BoardService#completeLure",
                    "Заготовка под открытую задачу: todo.md → «Распределение работ и сбор в поселении»"),
            new Exemption(
                    "src/main/java/com/theminespirit/neoforge/board/BoardService.java|method|BoardService#completeLure",
                    "Заготовка под открытую задачу: todo.md → «Распределение работ и сбор в поселении»"),
            new Exemption(
                    "src/main/java/com/theminespirit/neoforge/board/BoardService.java|method|BoardService#notifyAnimalDied",
                    "Заготовка под открытую задачу: todo.md → «Распределение работ и сбор в поселении»"),
            new Exemption(
                    "src/main/java/com/theminespirit/neoforge/board/BoardService.java|method|BoardService#notifyAnimalDied",
                    "Заготовка под открытую задачу: todo.md → «Распределение работ и сбор в поселении»"),
            new Exemption(
                    "src/main/java/com/theminespirit/neoforge/crafting/RecipeKnowledge.java|method|RecipeKnowledge#indexBuilds",
                    "Заготовка под открытую задачу: todo.md → «Кузнечество»"),
            new Exemption(
                    "src/main/java/com/theminespirit/neoforge/intellect/SettlementIntellect.java|method|SettlementIntellect#taskFailures",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/neoforge/intellect/SettlementIntellect.java|method|SettlementIntellect#taskStarts",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/neoforge/tactics/TacticsService.java|method|TacticsService#lastOutcome",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/neoforge/tactics/TacticsService.java|method|TacticsService#retreatExecutor",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/board/Board.java|method|Board#casePauseTicks",
                    "Заготовка под открытую задачу: todo.md → «Распределение работ и сбор в поселении»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/board/Board.java|method|Board#pendingFor",
                    "Заготовка под открытую задачу: todo.md → «Распределение работ и сбор в поселении»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/board/BreedingRules.java|method|BreedingRules#breedingAvailable",
                    "Заготовка под открытую задачу: todo.md → «контроль животных»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/board/BreedingRules.java|method|BreedingRules#cullNeeded",
                    "Заготовка под открытую задачу: todo.md → «контроль животных»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/board/BreedingRules.java|method|BreedingRules#stockFor",
                    "Заготовка под открытую задачу: todo.md → «контроль животных»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/board/HiveRules.java|method|HiveRules#placementReady",
                    "Заготовка под открытую задачу: todo.md → «контроль животных»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/building/PillarPlan.java|method|PillarPlan#dismantle",
                    "Заготовка под открытую задачу: todo.md → «Строительство»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/building/PresetLibrary.java|method|PresetLibrary#byStage",
                    "Заготовка под открытую задачу: todo.md → «Строительство»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/building/PresetLibrary.java|method|PresetLibrary#byTypeAndStage",
                    "Заготовка под открытую задачу: todo.md → «Строительство»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/building/WarehouseLayout.java|method|WarehouseLayout#stacksWithoutSign",
                    "Заготовка под открытую задачу: todo.md → «Строительство»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/coordinator/SettlementGroups.java|method|SettlementGroups#partition",
                    "Заготовка под открытую задачу: todo.md → «Строительство»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/crafting/Stations.java|method|Stations#preferredSmeltingStation",
                    "Заготовка под открытую задачу: todo.md → «Кузнечество»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/farming/Hydration.java|field|Hydration#MAX_LOOKUPS",
                    "Заготовка под открытую задачу: todo.md → «Фермерство»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/forestry/TreeCandidates.java|method|TreeCandidates#isKnown",
                    "Заготовка под открытую задачу: todo.md → «Лесничество»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/husbandry/PenBounds.java|method|PenBounds#escapedOf",
                    "Заготовка под открытую задачу: todo.md → «контроль животных»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/intellect/ActivityCatalog.java|method|ActivityCatalog#forChild",
                    "Заготовка под открытую задачу: todo.md → «Дети»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/intellect/Forage.java|method|Forage#hasWater",
                    "Заготовка под открытую задачу: todo.md → «Рыболовство»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/inventory/BaseKit.java|method|BaseKit#isSatisfiedBy",
                    "Заготовка под открытую задачу: todo.md → «Инвентарь, ценности и поход»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/inventory/ContainerSnapshot.java|method|ContainerSnapshot#hasFreeSlot",
                    "Заготовка под открытую задачу: todo.md → «Инвентарь, ценности и поход»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/inventory/OverflowTuning.java|method|OverflowTuning#effectiveStackValue",
                    "Заготовка под открытую задачу: todo.md → «Инвентарь, ценности и поход»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/perception/HazardFacts.java|method|HazardFacts#hasDrop",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/perception/HazardFacts.java|method|HazardFacts#hasLava",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/perception/HazardFacts.java|method|HazardFacts#hasWater",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/pickup/PickupSpot.java|method|PickupSpot#forDrop",
                    "Заготовка под открытую задачу: todo.md → «Инвентарь, ценности и поход»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/social/TransferOrder.java|method|TransferOrder#fromWarehouse",
                    "Заготовка под открытую задачу: todo.md → «Экономика и логистика»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/tactics/LayerOrder.java|field|LayerOrder#INTERRUPTED_LAYER_RESUMES",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/tactics/SafePointChoice.java|field|SafePointChoice#NO_CAMP",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/tactics/SpiritTargets.java|method|SpiritTargets#attacksSpirits",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/tactics/TacticsEngine.java|method|TacticsEngine#totalRules",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/tactics/adapter/CoreFactCollector.java|method|CoreFactCollector#withDefaultSettings",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/tactics/adapter/RetreatTracker.java|method|RetreatTracker#progressTicks",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/tactics/adapter/TacticsSchedule.java|method|TacticsSchedule#everEvaluated",
                    "Заготовка под открытую задачу: todo.md → «Тактический интеллект»"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/movement/network/TrailTrack.java|class|<top>#TrailTrack",
                    "Прототип графа внешних троп для разведки дальности пути: docs/research/pathfinding-range.md"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/movement/network/BreadcrumbTrail.java|method|BreadcrumbTrail#backtrack",
                    "Прототип обратного маршрута по крошкам: docs/research/pathfinding-range.md"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/navigation/NavCachedWorld.java|method|NavCachedWorld#cachedBytes",
                    "Счётчик памяти кэша для отчёта: docs/research/baritone-navigation.md"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/navigation/NavPathSplicer.java|method|NavPathSplicer#splice",
                    "Склейка сегментов Baritone: используется при встраивании, docs/research/baritone-navigation.md"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/navigation/NavSegmentedPlanner.java|method|NavSegmentedPlanner#follow",
                    "Сегментированный дальний путь Baritone: используется при встраивании, docs/research/baritone-navigation.md"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/route/RouteBreakAudit.java|method|RouteBreakAudit#firstVeto",
                    "Старый аудит ломки RoutePlanner: навигация ходит без ломки, docs/ARCHITECTURE.md"),
            new Exemption(
                    "src/main/java/com/theminespirit/core/board/TicketTarget.java|method|TicketTarget#withRescue",
                    "Заготовка под узел графа спасения: RESCUE_SPIRIT ставит граф, а не RescueWatch; "
                            + "TestResults/task.md B8"));

    static final List<Probe> SCALE_PROBES = List.of(
            new Probe(
                    "src/main/java/com/theminespirit/core/mining/GatherPolicy.java|method|GatherPolicy#forbiddenExactIds",
                    "check_ForbiddenTable_MatchesProduction",
                    "список точных запретов gather обязан существовать в реестре блоков: опечатка ослепляет политику"),
            new Probe(
                    "src/main/java/com/theminespirit/core/policy/BlockProtection.java|method|BlockProtection#denyPatterns",
                    "file_Loaded_ThresholdIsSixAndGatherPolicyIsNotDuplicated",
                    "deny не дублирует точные запреты GatherPolicy: списки не должны разъезжаться"),
            new Probe(
                    "src/main/java/com/theminespirit/core/rest/ServerThreadCall.java|method|ServerThreadCall#submittedCount",
                    "submittedCount_EachSubmit_CountsOneHop",
                    "счётчик переходов на серверный поток: один переход на запрос, отказы и чтения — без перехода"),
            new Probe(
                    "src/main/java/com/theminespirit/core/farming/FarmPlanner.java|field|FarmPlanner#MAX_SAFETY_READS_PER_TARGET",
                    "maxSafetyReadsPerTarget_SearchBoxVolume_Is324",
                    "бюджет чтений безопасности на цель: масштаб планирования фермы"));

    private DeadCodeExemptions() {}

    static List<Exemption> all() {
        List<Exemption> all = new ArrayList<>(OPEN_TASKS);
        for (Probe probe : SCALE_PROBES) {
            all.add(new Exemption(probe.key(), "Проба теста " + probe.test() + ": " + probe.reason()));
        }
        return List.copyOf(all);
    }

    record Probe(String key, String test, String reason) {}

    record Exemption(String key, String reason) {}
}
