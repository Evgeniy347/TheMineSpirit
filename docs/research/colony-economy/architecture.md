## Архитектура систем (пакеты/классы, пути файлов)

### 1. Jobs — работы и профессии

- Корневой интерфейс: `api/colony/jobs/IJob.java` (докблоки по каждой обязанности работы).
- База: `core/colony/jobs/AbstractJob.java` — хранит `JobEntry`, счётчик `actionsDone`,
  асинхронные запросы (`Set<IToken<?>> asyncRequests`), привязку к зданию (`workBuilding`) и модулю
  работы (`IAssignsJob workModule`). `assignTo(module)` меняет место работы и снимает со старого.
- Реализации: 52 класса в `core/colony/jobs/` — `JobBuilder`, `JobFarmer`, `JobLumberjack`, `JobMiner`,
  `JobQuarrier`, `JobFisherman`, `JobCook/Chef/Baker`, `JobBlacksmith/Smelter/Stonemason/Sawmill`,
  `JobAlchemist`, `JobDeliveryman`, `JobCowboy/Shepherd/SwineHerder/ChickenHerder/RabbitHerder/Beekeeper/
  Stablemaster`, `JobComposter`, `JobUndertaker`, `JobNetherWorker`, guard-классы (`core/colony/jobs/
  guard/`: `JobKnight`, `JobRanger`, `JobMarksman`, `JobDruid`, `JobHuscarl`, `JobCavalry`).
- Реестр: `api/colony/jobs/registry/JobEntry.java` (builder `jobProducer`, перевод, view), `IJobRegistry`.
- **AI работы** живёт не в Job, а в отдельном классе: `job.createAI()` → `generateAI()` → сущность через
  `CitizenJobHandler`. Пример: `core/entity/ai/workers/production/agriculture/EntityAIWorkFarmer.java`.
- **Назначение на работу (кто «ищет работу»):** не работник ищет, а **здание** тянет безработного.
  `core/colony/buildings/modules/WorkerBuildingModule.java` → `onColonyTick()`: если слот свободен и
  `canAutoHire(...)`, берёт `colony.getCitizenManager().getJoblessCitizen()`
  (`core/colony/managers/CitizenManager.java:410`) и `assignCitizen()`.
  - `assignCitizen()` создаёт Job через `createJob(citizen)`, зовёт `job.assignTo(this)`.
  - Слоты и лимит: `core/colony/buildings/modules/AbstractAssignedCitizenModule.java` (`isFull`,
    `getModuleMax`), интерфейс `api/colony/buildings/modules/IAssignsJob.java`.
  - Режимы найма: `api/colony/buildings/HiringMode.java` = `DEFAULT/AUTO/MANUAL/LOCKED`; `canAutoHire`.
  - Смена профессии = `AbstractJob.assignTo(newModule)` + `AbstractJob.onRemoval()` (снимает экипировку,
    чистит слоты). `CitizenData.setJob()` (`core/colony/CitizenData.java:996`) поднимает событие
    `CitizenJobChangedModEvent`.

### 2. Requests — снабжение, приоритеты, резервирование (ядро экономики)

Это, по сути, встроенная библиотека «внутреннего рынка колонии». API — `api/colony/requestsystem/**`,
реализация — `core/colony/requestsystem/**`.

- Менеджер: `core/colony/requestsystem/management/manager/StandardRequestManager.java` — держит
  DataStore-ы (идентичности запросов, назначения, провайдеры), `createRequest`, `assignRequest`,
  `reassignRequest`, `updateRequestState`. Тики: `getRetryingRequestResolver().tick()`.
- Жизненный цикл запроса: `api/colony/requestsystem/request/RequestState.java` =
  `CREATED → REPORTED → ASSIGNING → ASSIGNED → IN_PROGRESS → RESOLVED → FOLLOWUP_IN_PROGRESS →
  COMPLETED/RECEIVED`, плюс `OVERRULED/CANCELLED/FAILED/FINALIZING`.
- Запрос: `api/colony/requestsystem/request/IRequest.java` — дерево (`parent`/`children`), `getRequest()`
  (что просят), `getResult()`, `getDeliveries()`, `getStrategy()`.
- **Алгоритм назначения решателя:** `core/colony/requestsystem/management/handlers/RequestHandler.java`
  (`assignRequestDefault`, стр. 123): берёт решатели подходящего типа, сортирует по `getPriority()`
  (выше первым), затем по порядку супертипов запроса; для решателей одного приоритета выбирает лучший по
  `getSuitabilityMetric()` (у склада это «расстояние/10 + длина очереди запросов»); если запрос не
  удаётся решить целиком, решатель создаёт **дочерний** запрос на недостачу — рекурсивный спрос.
- Стратегии: `api/colony/requestsystem/manager/AssigningStrategy.java` = `PRIORITY_BASED`,
  `FASTEST_FIRST`.
- «Повторитель» нерешённых: `core/colony/requestsystem/resolvers/StandardRetryingRequestResolver.java`.
- Решатели (`core/colony/requestsystem/resolvers/`):
  - склад: `WarehouseRequestResolver`, `WarehouseConcreteRequestResolver`, база
    `resolvers/core/AbstractWarehouseRequestResolver.java`; умеет отдавать из складских инвентарей и
    создавать `Delivery`-запросы на доставку к месту просителя;
  - крафт: `PublicWorkerCraftingRequestResolver` / `PrivateWorkerCraftingRequestResolver` и
    production-варианты, база `resolvers/core/AbstractCraftingRequestResolver.java` — ищет рецепт в
    здании-крафтере, считает число прогонов, вычитает уже имеющееся, создаёт дочерние запросы на
    ингредиенты; защита `createsCraftingCycle(...)`;
  - доставка: `DeliveryRequestResolver`, `PickupRequestResolver`, `DeliverymenRequestResolver`;
  - игрок: `StandardPlayerRequestResolver` (игрок вручную закрывает запрос, «почтовый ящик»).
- Типы запросов (что именно «заказывают»): `api/colony/requestsystem/requestable/`
  — `Stack.java` (N предметов, `matches`, `copyWithCount`, `canBeResolvedByBuilding`), `Tool.java`
  (инструмент по типу/уровню), `Food.java` (еда с минимальной питательностью), `Burnable.java`,
  `MinimumStack.java` («держать не меньше N»), `deliveryman/Delivery.java` / `Pickup.java`,
  `crafting/PublicCrafting.java` / `PrivateCrafting.java`, `RequestTag.java`.
- Готовые типы-обёртки: `core/colony/requestsystem/requests/StandardRequests.java`
  (`ItemStackRequest`, `FoodRequest`, `ToolRequest`, `DeliveryRequest`, `MinStackRequest`, …).

### 3. Warehouse — склад и снабжение

- Здание: `core/colony/buildings/workerbuildings/BuildingWareHouse.java`, `BlockHutWareHouse.java`.
- Хранилище: `core/tileentities/TileEntityWareHouse.java` — `getMatchingItemStacksInWarehouse(predicate)`
  возвращает пары `(ItemStack, BlockPos)` из всех несгораемых инвентарей склада; `tryToFinalizeDelivery`.
- Модули: `core/colony/buildings/modules/WarehouseModule.java` (апгрейд ёмкости),
  `WarehouseRequestQueueModule.java` (очередь задач склада, она же в метрике пригодности),
  `WarehouseOptionsModuleView.java` (опции).
- Решатели: см. выше `WarehouseRequestResolver` / `WarehouseConcreteRequestResolver`; база
  `AbstractWarehouseRequestResolver` умеет: игнорировать собственные запросы, проверять наличие с учётом
  `INonExhaustiveDeliverable.getLeftOver()` (не разорять склад до нуля), суммировать несколько складов,
  и на `getFollowupRequestForCompletion` порождать `Delivery` до просителя.
- Доставка = отдельная профессия: `core/entity/ai/workers/service/EntityAIWorkDeliveryman.java`
  (states `PICKUP → DUMPING → PREPARE_DELIVERY → DELIVERY`), `JobDeliveryman`. Курьер носит предметы
  между зданиями/складом; ёмкость растёт с уровнем здания.
- **Резервирование:** явного «лок-менеджера» нет; роль резерва играют состояния запроса + `parent/child`
  (дочерний запрос на недостачу) + `MinimumStack` (не отдавать ниже минимума).

### 4. Building — строительство, здания, работа

- Иерархия: `api/colony/buildings/IBuilding.java`, `core/colony/buildings/AbstractBuilding.java`;
  строители — `core/colony/buildings/AbstractBuildingStructureBuilder.java` (стадии прогресса, bucket
  ресурсов, workorder), конкретные `core/colony/buildings/workerbuildings/*` (`BuildingBuilder`,
  `BuildingFarmer`, `BuildingWareHouse`, …).
- **Декомпозиция стройки** идёт через work orders: `core/colony/workorders/` — `AbstractWorkOrder.java`
  (пакет/путь blueprints, `location`, `rotation`, `targetLevel`, `iteratorType`, `cleared`, `requested`,
  `stage`, оценочная рамка `AABB`), `WorkManager.java` (реестр, `onColonyTick` сортирует по claimed/
  приоритету и `tryAssignWorkOrder` раздаёт свободным строителям), `WorkOrderBuilding.java`,
  `WorkOrderDecoration.java`, `WorkOrderMiner.java`.
- **Стадии постройки:** `core/entity/ai/workers/util/BuildingProgressStage.java` =
  `CLEAR, BUILD_SOLID, CLEAR_WATER, CLEAR_NON_SOLIDS, DECORATE, SPAWN, REMOVE, REMOVE_WATER, WEAK_SOLID`.
  Итерация блока-за-блоком: `core/entity/ai/workers/util/LayerBlueprintIterator.java` (слой за слоем,
  стратегии обхода `inwardcircle/hilbert/default`), `AbstractEntityAIStructure`,
  `AbstractEntityAIStructureWithWorkOrder`.
- **Ресурсы стройки:** `core/colony/buildings/modules/BuildingResourcesModule.java` — карта
  `neededResources` (`BuildingBuilderResource`) и очередь `Deque<BuilderBucket>` по стадиям;
  `core/colony/buildings/utils/BuildingBuilderResource.java`, `BuilderBucket`. `checkOrRequestBucket(...)`
  превращает нехватку в запросы RequestSystem; `getRequiredResources()` недоступен на стадии `CLEAR`.
- Модули зданий (переиспользуемые «поведения»): `core/colony/buildings/modules/` — `WorkerBuildingModule`
  (найм), `AbstractCraftingBuildingModule` (рецепты, 1155 строк — сложная «политиками» модель),
  `MinimumStockModule`, `ItemListModule`, `BuildingResourcesModule`, `BuildingExtensionsModule`,
  `LivingBuildingModule`/`HomeBuildingModule` (жильё → население), `GuardBuildingModule`,
  `RestaurantMenuModule`, `SettingsModule` и др. Виды: `core/colony/buildings/moduleviews/`.

### 5. Economy — производство, еда, население

- **Денег/валюты/торговли нет.** Экономика MineColonies — это дефицитное распределение через
  RequestSystem (спрос → дочерние запросы → склад/крафт/курьер), а излишки физически лежат на складе.
  «Минимальный запас» (`MinimumStockModule`) — единственный «буфер», `MINIMUM_STOCK`.
- Крафт: `api/crafting/` — `IRecipeStorage.java`, `RecipeStorage.java`, `ItemStorage.java`,
  `GenericRecipe.java`, `IGenericRecipe.java`, `IRecipeManager.java`; ядро — `core/colony/crafting/` —
  `RecipeAnalyzer.java`, `GenericRecipeUtils.java`, `ToolsAnalyzer.java`, `LootTableAnalyzer.java`,
  `CustomRecipe.java`/`CustomRecipeManager.java` (кастомные рецепты колонии). Модуль рецептов и «обучение»
  рецептам — `AbstractCraftingBuildingModule` (`getMaxRecipes`, `canRecipeBeAdded`, `RECIPE_MODE`).
- Еда: ресторан как спрос `RestaurantMenuModule.java`; `api/colony/requestsystem/requestable/Food.java`;
  `api/util/FoodUtils.java`; `core/entity/citizen/citizenhandlers/CitizenFoodHandler.java`;
  `core/recipes/FoodIngredient.java`, `core/items/ItemFood.java`, `ItemBowlFood.java`.
- Производство: AI-классы `core/entity/ai/workers/production/**` (земледелие, скотоводство, шахта,
  лес, рыбалка, крафт), `.../service/**` (повар, чародей, лекарь, курьер).
- **Население и рост:** `core/colony/managers/CitizenManager.java` — `calculateMaxCitizens()` считает
  лимит как сумму слотов жилых модулей всех зданий (`LivingBuildingModule.getModuleMax()`),
  ограниченный research и конфигом; `getJoblessCitizen()`, `spawnOrCreateCivilian`.
  `core/colony/managers/ReproductionManager.java` — раз в ~10 мин (`CHILD_SPAWN_INTERVAL`) пытается
  завести ребёнка, если `count < maxCitizens`, есть свободная кровать (`getHouseWithSpareBed`) и настройка
  `MOVE_IN`; ребёнок наследует статы родителей ±2. NPC `citizenManager.tickCitizenData(TICKS_SECOND * 3)`.
- **Настроение/счастье:** `core/entity/citizen/citizenhandlers/CitizenHappinessHandler.java`,
  `api/entity/citizen/happiness/*` (`StaticHappinessModifier`, `TimeBasedHappinessModifier`,
  `ExpirationBasedHappinessModifier`), `api/util/constant/HappinessConstants.java`
  (`IDLE_AT_JOB_COMPLAINS_DAYS`, `IDLE_AT_JOB_DEMANDS_DAYS`). Простой работник жалуется/требует решение.

### 6. AI и тайминги (планирование задач работника)

- State machine: `api/entity/ai/statemachine/**`, база `core/entity/ai/workers/AbstractAISkeleton.java`
  создаёт `TickRateStateMachine<>(AIWorkerState.INIT, ...)`; `registerTarget(s)` добавляет переходы.
- Состояния: `api/entity/ai/statemachine/states/AIWorkerState.java` (712 строк) — общие
  (`IDLE, INIT, INVENTORY_FULL, PREPARING, START_WORKING, NEEDS_ITEM, DECIDE, PAUSED`) и по профессиям.
- Переход: `api/entity/ai/statemachine/tickratestatemachine/TickingTransition.java` — `(fromState,
  predicate, nextState, tickRate)`; смещения (`tickOffsetVariant % tickRate`) разносят проверки по тикам,
  чтобы AI не срабатывали в один тик. Лимиты: `TickRateConstants.java` (`MAX_TICKRATE=500`,
  `MAX_AI_TICKRATE`, `MAX_TICKRATE_VARIANT=50`).
- Базовая логика работника: `core/entity/ai/workers/AbstractEntityAIBasic.java` (1925 строк) —
  `getNeededItem`, `lookForRequests`, `dumpInventory`, `afterDump`, `waitForRequests`; воркер проходит
  цепочку: получить предмет из сундука → работать → при переполнении/после N действий выгрузить.
- Колония тикает своим state machine: `core/colony/Colony.java` — состояния `ACTIVE/UNLOADED`,
  `worldTickSlow` раз в `MAX_TICKRATE`; тяжёлые менеджеры тикают реже (`citizenManager` раз в 3 сек).

