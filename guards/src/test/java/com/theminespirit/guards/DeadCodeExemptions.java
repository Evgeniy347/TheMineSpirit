package com.theminespirit.guards;

import java.util.ArrayList;
import java.util.List;

final class DeadCodeExemptions {
    static final List<Exemption> OPEN_TASKS = List.of(
            new Exemption(
                    "core/board/graph/RecipeImport.java|class|<top>#RecipeImport",
                    "Генератор graph-recipes.json (export-задача): рецепты импортируются при генерации, "
                            + "в игре читается готовый файл; TestResults/task.md"),
            new Exemption(
                    "neoforge/crafting/RecipeKnowledge.java|method|RecipeKnowledge#indexBuilds",
                    "Заготовка под открытую задачу: todo.md → «Кузнечество»"),
            new Exemption(
                    "core/board/Board.java|method|Board#casePauseTicks",
                    "Заготовка под открытую задачу: todo.md → «Распределение работ и сбор в поселении»"),
            new Exemption(
                    "core/board/BreedingRules.java|method|BreedingRules#breedingAvailable",
                    "Заготовка под открытую задачу: todo.md → «контроль животных»"),
            new Exemption(
                    "core/board/BreedingRules.java|method|BreedingRules#cullNeeded",
                    "Заготовка под открытую задачу: todo.md → «контроль животных»"),
            new Exemption(
                    "core/board/BreedingRules.java|method|BreedingRules#stockFor",
                    "Заготовка под открытую задачу: todo.md → «контроль животных»"),
            new Exemption(
                    "core/board/HiveRules.java|method|HiveRules#placementReady",
                    "Заготовка под открытую задачу: todo.md → «контроль животных»"),
            new Exemption(
                    "core/building/PillarPlan.java|class|<top>#PillarPlan",
                    "Заготовка под открытую задачу: todo.md → «Строительство» (прототип столба/разбора)"),
            new Exemption(
                    "core/building/PresetLibrary.java|method|PresetLibrary#byStage",
                    "Заготовка под открытую задачу: todo.md → «Строительство»"),
            new Exemption(
                    "core/building/PresetLibrary.java|method|PresetLibrary#byTypeAndStage",
                    "Заготовка под открытую задачу: todo.md → «Строительство»"),
            new Exemption(
                    "core/building/WarehouseLayout.java|class|<top>#WarehouseLayout",
                    "Заготовка под открытую задачу: todo.md → «Строительство»; раскладки сундуков — docs/settlement/catalog.md"),
            new Exemption(
                    "core/coordinator/SettlementGroups.java|method|SettlementGroups#partition",
                    "Заготовка под открытую задачу: todo.md → «Строительство»"),
            new Exemption(
                    "core/crafting/Stations.java|method|Stations#preferredSmeltingStation",
                    "Заготовка под открытую задачу: todo.md → «Кузнечество»"),
            new Exemption(
                    "core/husbandry/PenBounds.java|method|PenBounds#escapedOf",
                    "Заготовка под открытую задачу: todo.md → «контроль животных»"),
            new Exemption(
                    "core/inventory/BaseKit.java|method|BaseKit#isSatisfiedBy",
                    "Заготовка под открытую задачу: todo.md → «Инвентарь, ценности и поход»"),
            new Exemption(
                    "core/inventory/ContainerSnapshot.java|method|ContainerSnapshot#hasFreeSlot",
                    "Заготовка под открытую задачу: todo.md → «Инвентарь, ценности и поход»"),
            new Exemption(
                    "core/inventory/OverflowTuning.java|method|OverflowTuning#effectiveStackValue",
                    "Заготовка под открытую задачу: todo.md → «Инвентарь, ценности и поход»"),
            new Exemption(
                    "core/board/pickup/PickupSpot.java|method|PickupSpot#forDrop",
                    "Заготовка под открытую задачу: todo.md → «Инвентарь, ценности и поход»"),
            new Exemption(
                    "core/social/TransferOrder.java|method|TransferOrder#fromWarehouse",
                    "Заготовка под открытую задачу: todo.md → «Экономика и логистика»"),
            new Exemption(
                    "core/navigation/NavCachedWorld.java|method|NavCachedWorld#cachedBytes",
                    "Счётчик памяти кэша для отчёта: docs/research/baritone-navigation.md"),
            new Exemption(
                    "core/mining/MineTunnel.java|field|MineTunnel#MAX_STEPS",
                    "Бюджет прохода шахты — доменное число: вход проверки кейса observe10; в игре передаётся из настроек"),
            new Exemption(
                    "core/coordinator/CoordinatorState.java|method|CoordinatorState#workers",
                    "Запрос работников — публичное чтение снимка поселения, проверяется свойствами; игра берёт defenders()"),
            new Exemption(
                    "core/intellect/FoodNorm.java|method|FoodNorm#perSoul",
                    "Норма еды на душу — публичная чистая функция, проверяется свойствами; в игре зовут demand()"),
            new Exemption(
                    "core/inventory/InventoryFill.java|method|InventoryFill#extraFraction",
                    "Доля лишнего в инвентаре — публичный запрос домена, проверяется нормами"),
            new Exemption(
                    "core/inventory/InventoryFill.java|method|InventoryFill#occupiedFraction",
                    "Доля занятых ячеек — публичный запрос домена, проверяется нормами"),
            new Exemption(
                    "core/processing/ProcessingStock.java|method|ProcessingStock#hasLogs",
                    "Признак запаса переработки — публичный запрос домена, проверяется таблицами"),
            new Exemption(
                    "core/processing/ProcessingStock.java|method|ProcessingStock#hasPlanks",
                    "Признак запаса переработки — публичный запрос домена, проверяется таблицами"),
            new Exemption(
                    "core/processing/ProcessingStock.java|method|ProcessingStock#hasRawFood",
                    "Признак запаса переработки — публичный запрос домена, проверяется таблицами"),
            new Exemption(
                    "core/processing/ProcessingStock.java|method|ProcessingStock#plankProduct",
                    "Выбор породы досок — публичный запрос домена, проверяется таблицами"),
            new Exemption(
                    "core/board/PriorityContext.java|method|PriorityContext#none",
                    "Пустой контекст приоритетов — публичная заготовка домена: в игре создаётся прямым конструктором, "
                            + "none() нужен тестам; удалить вместе с пересмотром приоритетов"),
            new Exemption(
                    "core/board/route/RouteResource.java|method|RouteResource#ofBlock",
                    "Разбор ресурса по блоку — публичный запрос домена, в игре ресурс берут по тегу/постройке"),
            new Exemption(
                    "core/intellect/Forage.java|method|Forage#hasRod",
                    "Признак удочки — публичный запрос домена: сканер поселения считает снасть своим способом"),
            new Exemption(
                    "core/tactics/Scorer.java|method|Scorer#combine",
                    "Комбинация оценок — публичная чистая функция, проверяется свойствами; в игре зовут другие методы"),
            new Exemption(
                    "core/board/graph/RecipeImport.java|method|Tags#groupOf",
                    "Часть генератора graph-recipes.json: рецепты импортируются при генерации, в игре читается файл"),
            new Exemption(
                    "core/board/TicketTarget.java|method|TicketTarget#withRescue",
                    "Заготовка под узел графа спасения: RESCUE_SPIRIT ставит граф, а не RescueWatch; "
                            + "TestResults/task.md B8"));

    static final List<Probe> SCALE_PROBES = List.of(
            new Probe(
                    "core/mining/api/GatherPolicy.java|method|GatherPolicy#forbiddenExactIds",
                    "check_ForbiddenTable_MatchesProduction",
                    "список точных запретов gather обязан существовать в реестре блоков: опечатка ослепляет политику"),
            new Probe(
                    "core/policy/BlockProtection.java|method|BlockProtection#denyPatterns",
                    "file_Loaded_ThresholdIsSixAndGatherPolicyIsNotDuplicated",
                    "deny не дублирует точные запреты GatherPolicy: списки не должны разъезжаться"),
            new Probe(
                    "core/rest/ServerThreadCall.java|method|ServerThreadCall#submittedCount",
                    "submittedCount_EachSubmit_CountsOneHop",
                    "счётчик переходов на серверный поток: один переход на запрос, отказы и чтения — без перехода"),
            new Probe(
                    "core/farming/FarmPlanner.java|field|FarmPlanner#MAX_SAFETY_READS_PER_TARGET",
                    "maxSafetyReadsPerTarget_SearchBoxVolume_Is324",
                    "бюджет чтений безопасности на цель: масштаб планирования фермы"),
            new Probe(
                    "core/navigation/NavAStarSession.java|method|NavAStarSession#returnableCacheSize",
                    "livePit_DeepSearch_ReturnableCacheStaysBounded",
                    "размер кэша обратимости: замер на живой яме, иначе предел не увидеть"));

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
