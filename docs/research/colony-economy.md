# Колония, работы и экономика — карта переиспользования

Исследование по локальному клону MineColonies. Изменений в коде MineColonies нет; это справочник
для наших подсистем работ, склада и экономики (P5+). Дата анализа: 2026-10-11.
Наш проект — `The Mine Spirit`, NeoForge 1.21.1 / Java 21; ядро `com.theminespirit.core` — без импортов Minecraft.

## Обзор (назначение, стек, версии, лицензия)

- **Репозиторий:** `/opt/minecraft/research/minecolonies`, ветка `version/main`, последний коммит
  `4eb1b870`, тег `v1.20.1-1.1.1302-snapshot`.
- **Стек:** Java 17, Minecraft Forge **1.20.1** (Forge 47.1.3), Gradle, мод `com.ldtteam`, базовый пакет
  `com.minecolonies`. Внешние зависимости: `structurize` (блэкпринты/схемы), `domum-ornamentum`,
  `blockui`, `multi-piston`, `towntalk`. Огромный мод: сотни классов в `src/main/java/com/minecolonies`.
- **Назначение:** симуляция городка NPC («колонистов»): дома, профессии, склад, доставка, строительство,
  производство, население. Это ближайший аналог нашей «колонии духов».
- **Важное расхождение версий.** В клоне — Forge 1.20.1 / Java 17, у нас — NeoForge 1.21.1 / Java 21.
  Архитектурные идеи переносимы, но API-классы (`IRequest`, `IBuilding`, `IPersistentModule`) и NBT-форматы
  использовать напрямую нельзя: их надо переписывать под наш `core`. Также у MineColonies есть ветки под
  более новые версии, но **этот** клон — 1.20.1.

### Лицензии (критично)

| Объект | Файл | Тип |
|---|---|---|
| Код мода | `/opt/minecraft/research/minecolonies/LICENSE` | **GNU GPL-3.0** (сильный copyleft) |
| Ресурсы/ассеты | `src/main/resources/assets/minecolonies/LICENSE` | **All Rights Reserved** |
| CLA контрибьюторов | `CLA.md` | не лицензия, а соглашение LDTTeam |

Отдельных лицензий у подкаталогов (`api/`, `core/`, `requestsystem/`) **нет** — весь код под общей
GPL-3.0. Встроенная «Request System» исторически пришла из отдельного проекта (в комментариях остались
упоминания автора «Orion»), но в этом репозитории она покрыта тем же GPL-3.0.

**Вывод:** код копировать нельзя — это наложит GPL-3.0 на наш мод (см. «Риски»). Ассеты под ARR —
тем более нельзя (ни код, ни текстуры, ни модели, ни blueprints). Разрешён только анализ идей и
самостоятельная переработка алгоритмов.

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

## Что переиспользовать у нас (идея → как ложится на нашу систему работ/склад/торговлю)

1. **Профессия = данные, а не класс поведения.** У MineColonies `Job` — тонкий носитель состояния
   (здание, счётчик действий, async-токены), а поведение в отдельном AI. У нас это ложится на
   `ScriptCatalog` + план: профессия → набор `ScriptSpec` (роль), а не Java-подкласс. Наш `core` уже
   работает через `ScriptCall`/`TaskNode`, т.е. «AI-состояния» станут именованными скриптами.
2. **Пуш-назначение работы (здание тянет безработного).** Идея `WorkerBuildingModule.onColonyTick` →
   в нашем планировщике «заявка на слот»: у каждой рабочей точки есть потребность (`required`),
   диспетчер раз в N тиков сверяет «свободные слоты» с реестром свободных духов и выдаёт им `Goal`.
   Это ровно ложится на наш REST `POST /v1/bots/{id}/tasks`.
3. **Запрос-дерево как дефицитная экономика.** `RequestState` + `parent/child` + «решатель на недостачу»
   → у нас заводит ясную модель: склад отдаёт остаток и порождает подцель «добыть/скрафтить недостачу».
   В наших терминах это `fallback`/`condition` + `ResourceKnowledge` (у нас уже есть `isCraftable`,
   `ingredientsFor`, `isSmeltable`). `RequestState` — хороший образец состояний для наших задач
   (`accepted/running/succeeded/failed` в API уже похожи).
4. **Резервирование через состояния, а не локи.** `asyncRequests`, `parent/child`, `MinimumStack` →
   у нас: задача, зарезервировавшая предмет, помечает его «в пути»; `MinimumStock` — политика «не отдавать
   ниже минимума» на нашем складе.
5. **Склад как агрегатор контейнеров.** `getMatchingItemStacksInWarehouse(predicate)` → наш
   `WorldObservation.containers`/`InventoryEntry` уже дают перечень сундуков; нужна функция «найти N
   предметов X по всем контейнерам колонии и вернуть (stack, pos)».
6. **Курьер как отдельная работа.** `JobDeliveryman` + `Delivery`/`Pickup` requestable → у нас профессия
   «разведчик/курьер» может быть духом с планом `sequence{ goto(container); take; goto(target); put }`.
   Доставка как отдельный тип работы, а не как побочный эффект склада.
7. **Стройка как стадии + bucket ресурсов.** `BuildingProgressStage` + `BuilderBucket` →
   наш план постройки — это `sequence` стадий, каждая стадия — `parallel` заявок на материалы, затем
   `repeat` по блокам. `neededResources` = наш «список ингредиентов» для `ResourceResolver`.
8. **Крафт через анализ рецептов + «обучение».** `RecipeAnalyzer`/`GenericRecipe` → расширение нашего
   `ResourceKnowledge` (рецепты из датапаков, а не хардкод). «Обучение рецептам» (`getMaxRecipes`) →
   идея: дух знает ограниченный набор рецептов, прокачивается.
9. **Настроение как мягкий регулятор.** `HappinessConstants` (жалуется через N дней простоя) → у нас
   метрика «дух простаивает» с приоритетом на диспетчеризацию; лёгкий сигнал в `/v1/tasks`.
10. **Автоматический найм (HiringMode).** `AUTO/MANUAL/LOCKED` → в нашем REST: флаг рабочей точки
    «сама набирает духов» vs «только по явному назначению».
11. **Идле-тайминги как anti-thrash.** `TickRateConstants` и `tickOffsetVariant` → у нашего
    диспетчера должны быть разные периоды опроса для разных нужд и разброс, чтобы не «залипать» в один тик.
12. **Счастье/рост как пороги.** `calculateMaxCitizens` (жильё = лимит) и `ReproductionManager` (рост
    при недоборе) → модель роста нашей колонии: новые духи появляются, когда есть дом и недобор рабочих.

## Портируемость

- **Идеи/алгоритмы — переносимы полностью.** Назначение по приоритету + метрике пригодности, дерево
  запросов, стадии стройки, state-machine с tick-rate, пуш-найм — это концепции, не привязанные к версии.
- **Код — не переносим.** Java 17 Forge 1.20.1 → наш Java 21 NeoForge 1.21.1: отличаются
  `IRequest`/`IBuilding`/NBT-сериализация/сеть. Даже при желании копировать, пришлось бы переписывать
  половину; а GPL-3.0 это запрещает (см. Риски).
- **Blueprint/Structurize — внешняя зависимость**, её архитектуру (схемы + итераторы обхода) мы можем
  повторить на своих структурах (`src/main/resources/data/theminespirit/structure/`), но формат
  `.blueprint` и классы Structurize брать нельзя (они не в этом репо; проверять лицензию отдельно).
- **NBT/сеть/реестры.** MineColonies сериализует всё в `CompoundTag` руками. У нас ядро чистое (Jackson),
  поэтому «сеть» — это JSON плана и наблюдения; их compact-формат (`StandardRequests`) — пример, как
  описать типы запросов, но не готовый код.
- **Масштаб.** RequestSystem — огромная библиотека. Нам нужна лёгкая реплика ~5 классов, а не перенос.

## Конкретные рекомендации (брать/адаптировать/избегать)

**Брать (как образец проектирования, переписать самим):**
- Модель состояния запроса `RequestState` и жизненный цикл «создан → назначен → в работе → решён →
  завершён» — для наших задач и склада.
- Алгоритм назначения: сортировка по приоритету, затем лучшая метрика пригодности, затем «дочерний
  запрос на недостачу» (`RequestHandler.assignRequestDefault`).
- Разделение «работа = данные» / «работа = поведение»: у нас роль = каталог скриптов + план.
- Пуш-найм из `WorkerBuildingModule.onColonyTick` → наш диспетчер потребностей.
- Стадии стройки (`BuildingProgressStage`) и bucket ресурсов (`BuilderBucket`).
- Tick-rate state machine с разбросом (`TickingTransition`, `TickRateConstants`).

**Адаптировать (идея с изменениями):**
- Warehouse «минимальный запас» → политики нашего склада (мин/макс, «не отдавать ниже N»).
- Рецепты → наш `ResourceKnowledge` через датапак/рецепты Minecraft, не хардкод.
- Курьер → профессия-дух с планом `take/put` (мы не имеем одного моба-курьера на всё).
- Настроение/рост населения → пороговые метрики колонии, не подсистема «счастья» целиком.
- Blueprint-итераторы → наши структуры GameTest/постройки, свои шаблоны.

**Избегать:**
- Любого копирования Java-кода, NBT-схем, имён пакетов/классов MineColonies в наш код (GPL-3.0).
- Ассетов (текстуры, модели, звуки, lang, blueprints) — они под «All Rights Reserved».
- Переноса громоздкого RequestSystem целиком — не та задача и не та лицензия.
- Зависимости от Structurize/Domum Ornamentum в нашем `core` (транспорт/декор — только адаптер).
- Прямого `Forge`-специфичного API (`net.minecraftforge.common.util.INBTSerializable`) — у нас NeoForge.

## Риски и лицензионные ограничения (copyleft!)

- **GPL-3.0 — сильный copyleft.** Если скопировать хотя бы фрагмент кода MineColonies в наш мод,
  производная работа (весь мод, включая `core`) должна распространяться под GPL-3.0 с исходниками.
  Это несовместимо с нашими текущими планами (проприетарная/иная лицензия ядра). Вывод: **не копировать
  код**; воспроизводить архитектуру и алгоритмы самостоятельно, своими именами и структурой.
- **Ассеты — All Rights Reserved.** Даже если код переписан, чужие текстуры/модели/lang/blueprints
  использовать нельзя. Все наши ресурсы рисуем/пишем сами.
- **Идеи не охраняются авторским правом**, но конкретное выражение — охраняется. Безопасный путь:
  прочитать, понять, закрыть файл, написать свою реализацию под наш `core`. В этом документе — только
  ссылки на пути/классы для навигации, что допустимо.
- **Атрибуция.** Даже при самостоятельной переработке корректно указать источник вдохновения
  (MineColonies, GPL-3.0) в комментарии к коммиту/доке; это не накладывает GPL на наш код.
- **Внешние зависимости.** `structurize`, `domum-ornamentum`, `blockui`, `multi-piston` — отдельные
  проекты LDTTeam; если решим использовать, их лицензии надо проверить отдельно (в этом репо их нет).
  Скорее всего тоже GPL/copyleft — не тянуть в `core`.
- **Расхождение версий.** Клон — 1.20.1 Forge. Перед любым заимствованием API сверять с веткой под
  1.21.x NeoForge (в этом клоне её нет); но правила лицензии одинаковы для всех веток.
