# Навигация и движение — карта переиспользования

Исследование двух локальных репозиториев для модуля навигации/движения проектов «The Mine Spirit»:
- `/opt/minecraft/research/baritone` — Baritone 1.13.1 (ветка `1.21.4`)
- `/opt/minecraft/research/altoclef` — AltoClef 0.4.0 (ветка MC 1.18.2)

## Обзор репозиториев

| Репозиторий | Назначение | Стек | Версия MC / загрузчик | Лицензия |
|---|---|---|---|---|
| **Baritone** | Поиск пути, исполнение движения, добыча, фермы, исследование. Библиотека для клиент-модов | Java 21, Gradle (unimined), fastutil, Jackson (только загрузка бехецх) | MC 1.21.4, Java 21; fabric + forge + neoforge + tweaker (neoforge_version=9-beta) | **LGPL-3.0** (copyleft) + «anime exception» (`LICENSE-Part-2.jpg`) |
| **AltoClef** | Слой задач (tasks) над Baritone: добыча, крафт, стройка, убийство мобов, speedrun | Java 17, Gradle (Fabric Loom 0.10), Jackson, shadow | MC 1.18.2, Fabric только (loader 0.13.3, yarn 1.18.2+build.3) | **MIT** (Copyright (c) 2020 Adris Jautakas); но в факте зависит от форка Baritone (LGPL) |

Источники версий: `baritone/gradle.properties` (mod_version=1.13.1, minecraft_version=1.21.4, java_version=21, available_loaders=..., neoforge_version=9-beta), `altoclef/gradle.properties` (mod_version=0.4.0, minecraft_version=1.18.2).

## Архитектура

### Baritone — `baritone/src/main/java` + `baritone/src/api/java`

**API (не зависит от деталей реализации)**
- `baritone/api/pathing/goals/*` — иерархия целей: интерфейс `Goal` (`isInGoal`, `heuristic`), `GoalBlock` (координаты+комбайн «не провалиться»), `GoalXZ`, `GoalNear` (радиус), `GoalComposite` (список целей), `GoalInverted`, `GoalRunAway` (убегать от точки), `GoalGetToBlock`, `GoalTwoBlocks`, `GoalYLevel`, `GoalAxis`, `GoalStrictDirection`.
- `baritone/api/pathing/movement/ActionCosts` — константы стоимости движения в тиках: `WALK_ONE_BLOCK_COST = 20/4.317`, `WALK_ONE_IN_WATER_COST`, `SWIM_ONE_BLOCK_COST`, `LADDER_UP_ONE_COST`, `LADDER_DOWN_ONE_COST`, `SNEAK_ONE_BLOCK_COST`, `SPRINT_ONE_BLOCK_COST`, `BLOCK_TO_ITEM` (ценность блока как ресурса).
- `baritone/api/Settings` (1745 строк) — весь конфиг через сеттера-объекты `.value`: `allowBreak`, `allowBreakAnyway`, `allowPlace`, `allowPlaceInFluidsSource/Flow`, `allowSprint`, `allowDiagonalAscend`, `allowParkourAscend`, `allowWaterBucketFall` (+`throwawayWaterBucket`), `autoTool`, `blockPlacementPenalty`, `blockBreakAdditionalPenalty`, `jumpPenalty`, `walkOnWaterOnePenalty`, `assumeWalkOnWater/Lava`, `assumeStep`, `assumeSafeWalk`, `acceptableThrowawayItems`, `mineScanDroppedItems`, `mineDropLoiterDurationMS`, `failureTimeoutMS`, `movementTimeoutTicks`, `mmbAvoidanceCoefficient/Radius`, `backtrackCostFavoringCoefficient`.
- `baritone/api/BaritoneAPI`, `IProvider`, `IGetBaritone` — точка входа и доступ к процессам/поведениям.

**Поиск пути — `baritone/pathing`**
- `calc/AStarPathFinder` + `calc/AbstractNodeCostSearch` — A*, эвристика октиальная (octile); узлы хранятся в сетке ячеек 16×8×16 (упакованные long); открытое множество — бинарная куча `BinaryHeapOpenSet` (fastutil). Поиск отдельным потоком, таймауты (failureTimeoutMS).
- `movement/CalculationContext` — контекст для расчёта стоимости: инвентарь, инструменты, зачарования, наличие воды/ковчегов/лестниц, способности (спринт, паркoур), настройки Baritone.
- `movement/Movement` (abstract) + `movement/MovementHelper` (общие проверки/стоимости, ведро воды при падении, разрушение блоков), подклассы в `movement/movements`: `MovementTraverse`, `MovementAscend`, `MovementDescend`, `MovementFall`, `MovementPillar`, `MovementDownward`, `MovementDiagonal`, `MovementParkour`.
- `movement/Moves` — enum всех допустимых перемещений: статичные (`TRAVERSE_*`, `ASCEND_*`, `PILLAR`, `DOWNWARD`, 4 диагонали) и динамические (`PARKOUR_*` на 1–4 блока, `DESCEND_*`→`MovementFall`, если падение>1). `dynamicXZ/dynamicY` — движение само определяет целевую клетку.
- `path/Path` + `path/PathExecutor` — исполнение заранее посчитанного пути: проход сегментов, таймауты на движение, пересчёт/реплан при неудаче.

**Процессы (высокоуровневые задачи) — `baritone/process`**
- `MineProcess` — добыча N блоков: приоритетная очередь ячеек (i,j,k), приоритет по высоте; слушатель dropped-предметов; выбор «следующей шахты».
- `FarmProcess` — высадка/сбор культур по кэшу чанков.
- `ExploreProcess` — исследование: сканирование чанк-секторов, поиск новых регионов, `GoalComposite`/`GoalXZ`/`GoalYLevel`; персистент через Gson (`baritone.cache`).
- `GetToBlockProcess`, `FollowProcess` (следовать за сущностью), `BuildProcess` (схемы), `BackfillProcess` (засыпать под собой), `CustomGoalProcess`, `InventoryPauserProcess`.
- `behavior/PathingBehavior` — тиковый цикл (план → исполнение → реплан), `behavior/WaypointBehavior` — сохранение точек/кроватей.

**Кэш мира и поиск без загрузки чанков — `baritone/cache`**
- `CachedChunk`, `CachedRegion`, `CachedWorld` (+ персистент на диск), `ChunkPacker` (упаковка состояний блоков в палитры), `WorldScanner` (поиск блоков по кэшу/сканирование без загрузки), `WorldProvider` из `baritone.api.utils`.

**Поведенческие весовые коэффициенты — `baritone/utils/pathing`**
- `Favoring` — уменьшение стоимости повторного посещения позиций предыдущего пути (`backtrackCostFavoringCoefficient`).
- `Avoidance` — создание сферических зон-избегания вокруг монстров (множитель `mobAvoidanceCoefficient`), `BetterBlockPos`.

**Elytra/Nether pathfinder — `baritone/process/elytra`**
- Порт babbaj/nether-pathfinder (нативный JNI) в чистый Java: `NetherPathfinder`, `PathFinder`, `Chunk`, рейтрейсер полёта на элитрах по аду. Единственные Minecraft-зависимости — `BlockPos`, `Direction`, `Vec3`, `Mth` (~3% файлов). Хороший образец алгоритма, почти не завязанного на ядро MC.

**NeoForge-модуль — `baritone/neoforge`**
- `build.gradle` на `unimined`; единственный класс — @Mod-stub (`@Mod(value = "baritoe", dist = Dist.CLIENT)`), переиспользует общий source с fabric/forge. Один код на все загрузчики.

### AltoClef — `altoclef/src/main/java/adris/altoclef`

**Ядро и оркестрация**
- `AltoClef.java` (ModInitializer): `onInitializeLoad` — настройка Baritone-настроек, создание менеджеров, тиковый цикл `onClientTick` (трекеры → taskRunner → butler); фасады к `getClientBaritone()`, `getClientBaritoneSettings()`, `getExtraBaritoneSettings()`.
- `tasksystem/Task` — абстрактная задача: `onStart/onTick/onStop`, подзадачи, `isEqual`/`canBeInterrupted`, приоритет `getPriority`.
- `tasksystem/TaskChain` — цепочка, выполняющая текущее дерево задач; `tasksystem/TaskRunner` — выбирает активную цепочку по приоритету и прерывает/восстанавливает.
- Маркеры задач: `tasksystem/ITaskCanForce`, `ITaskRequiresGrounded`, `ITaskOverridesGrounded`, `ITaskUsesCraftingGrid`.
- Цепочки `chains/`: `UserTaskChain` (команда игрока), `FoodChain` (поесть), `MobDefenseChain`, `MLGBucketFallChain`, `DeathMenuChain`, `WorldSurvivalChain`, `PlayerInteractionFixChain`.

**Задачи — `tasks/`**
- `tasks/resources/ResourceTask` (abstract): сначала проверить землю/сундуки, только потом добыча; `MineAndCollectTask` (добыть+подобрать и довезти до инвентаря/сундука), `CollectBucketLiquidTask`, `CollectAmethystBlockTask`, `CollectObsidianTask`.
- `tasks/misc/MLGBucketTask` — water-bucket clutch при падении (конфиг `ConeClutchContext`: castDownDistance=40, средняя горизонтальная скорость 0.25); управляется `chains/MLGBucketFallChain`.
- `tasks/misc/ChunkSearchTask` — поиск структур/биомов; `tasks/misc/TimeoutWanderTask`, `PlaceBedAndSetSpawnTask`.
- Крафт: `CraftInTableTask`, `CraftInInventoryTask`, `CraftGenericManuallyTask`, `CraftGenericWithRecipeBooksTask`.
- Контейнеры: `LootContainerTask`, `StoreInContainerTask`, `SmeltInFurnaceTask`, `DoStuffInContainerTask`; сущности: `DoToClosestEntityTask`, `KillEntityTask`.
- `tasks/squashed/CataloguedResourceTask` + `tasks/TaskCatalogue` (925 строк) + `CataloguedResources.txt` — каталог динамически получаемых предметов (универсальная «собери X» задача).

**Трекеры состояния — `trackers/`**
- `BlockTracker` (677 строк) — собственная карта найденных блоков (свои записи, чёрные списки `WorldLocateBlacklist`, scan по требованию, конфиг `configs/block_tracker.json`); работает через Baritone `WorldScanner`/`CachedWorld`.
- `storage/ContainerCache`/`ItemStorageTracker` — учёт предметов в инвентаре/сундуках; `EntityTracker`, `SimpleChunkTracker`, `MiscBlockTracker`.

**Интеграция с Baritone — `util/baritone/`, `util/progresscheck/`**
- Кастомные цели: `GoalAnd`, `GoalBlockSide`, `GoalChunk`, `GoalDirectionXZ`, `GoalDodgeProjectiles`, `GoalFollowEntity`, `GoalRunAwayFromEntities`, `PlaceBlockSchematic`, `GoToAndInteract`.
- `MovementProgressChecker` — прогресс-чекинговый таймаут движения (дистанция 6 c, minDistance 0.1, mine 0.5 c); `DistanceProgressChecker`, `LinearProgressChecker`, `ProgressCheckerRetry`.
- Прямые импорты внутренностей Baritone: `baritone.process.MineProcess`, `baritone.pathing.movement.CalculationContext` в `BlockTracker`.
- Патч Baritone: `AltoClefSettings` в пакете `baritone.altoclef`, каллбеки `avoidBlockBreak/avoidBlockPlace`, `configurePlaceBucketButDontFall` — классы отсутствуют в исходниках AltoClef и приходят из форка/launch «baritone-plus» (см. ниже сборку).

**Сборка зависимости от Baritone — `altoclef/build.gradle`**
- `modImplementation "cabaletta:baritone-unoptimized-fabric:1.18.2"` + `include` (jar кладётся внутрь мода); при `-Paltoclef.development` — `baritone-api-fabric:baritone-unoptimized-fabric:1.18.2` из репо gaucho-matrero с flatDir `../baritone/build/libs`. AltoClef фактически работает только с патченным форком Baritone.

**Конфигурация Baritone из AltoClef.java**
- `allowInventory=true` (перенос предметов на хотбар), `allowDiagonalAscend=true`, `fadePath=true`, `mineScanDroppedItems=false` (сканирует сам), `mineDropLoiterDurationMS=0`, `mobAvoidanceCoefficient=2.0`, `mobAvoidanceRadius=12`, `failureTimeoutMS=6000`, `planAheadFailureTimeoutMS=10000`, `movementTimeoutTicks=200`; `acceptableThrowawayItems` синхронизируется с собственным списком (исключая soul sand/magma). MLG-водов присвоено только AltoClef (`configurePlaceBucketButDontFall(true)`).

## Что переиспользовать у нас

Идеи и алгоритмы (без копирования кода — см. риски):
1. **Цели как комбинируемые объекты** — `GoalBlock`/`GoalXZ`/`GoalComposite`/`GoalInverted`/`GoalRunAway` с методом `heuristic()`: навигационную модель ядра `com.theminespirit.core` строим на таком же интерфейсе цели.
2. **Константы стоимости движения в тиках** — `ActionCosts` как эталон «времени движения» для симуляции мира-духов.
3. **Парадигма «enum движений»** — фиксированный набор перемещений с заранее вычисляемой стоимостью/целевой клеткой (`Moves` + классы `Movement*`) — естественный контракт между ядром (лоб) и исполнением (мод).
4. **A*** с упакованными ячейками 16×8×16 + бинарная куча — быстрый алгоритм, переносим в чистую Java без Minecraft (ядро) с работой по абстрактной «карте мира»-духов (нанки/туман войны).
5. **CachedWorld + WorldScanner** — поиск блоков и структур по кэшу без загрузки чанков ⇔ серверная модель «известности мира» (ревижим под GrepoData-запросы).
6. **Favoring/Avoidance** — «поведенческие» весовые коэффициенты в A* (предпочитать прежний путь; избегать точки/сущности).
7. **MineProcess** — приоритетная очередь ячеек добычи и сканирование дропа: базовый сценарий «добыть N ресурсов» для духов.
8. **FarmProcess / ExploreProcess** — массовые действия по кэшу чанков и исследование по секторам.
9. **Settings** (1745 строк) — готовый каталог тумблеров (allowBreak/allowPlace/allowWaterBucketFall/autoTool/...) как источник спецификации конфига мода.
10. **Цепочки задач AltoClef** (TaskChain/TaskRunner + маркеры ITaskCanForce/ITaskRequiresGrounded/ITaskOverridesGrounded) — модель реактивных слоёв духа: поверх основной цели «добыть/построить» висят цепочки «съесть», «отбиться от монстров», «mlg-спасение».
11. **Трекеры состояния + ProgressChecker** — источники статуса духа для REST-эндпоинтов (что добыто, где, движется ли, завис ли; таймауты 0.1 блока/6 с).
12. **Water-bucket clutch** (MLGBucketTask + ConeClutchContext: castDownDistance=40) — готовая механика безопасного падения.
13. **Кастомные цели AltoClef** (`GoalRunAwayFromEntities`, `GoalDodgeProjectiles`, `GoalFollowEntity`) — образец «целей-поведений» для NPC-духов.
14. **Elytra/nether pathfinder** (чистый Java, ~без MC-импортов) — референс переноса алгоритма в ядро `com.theminespirit.core`.

## Портируемость

- **Baritone** (MC 1.21.4, Java 21) — ближайший к нашему NeoForge 1.21.1. Модуль neoforge собран на unimined с общим source; Java-код API (Goals, ActionCosts, Settings) почти не зависит от версии MC и тривиально переписывается в ядро. Алгоритмы (A*, кэш чанков) переносятся без MC-зависимостей.
- **AltoClef** (MC 1.18.2, .yarn, Fabric, Java 17) — устарел относительно 1.21.1, жёстко клиентский (MinecraftClient, ClientPlayerEntity, ClientPlayerInteractionManager, рендер оверлея), работает только с форком Baritone 1.18.x из мавен gaucho-matrero. Портировать выгодно только идеи задач/цепочек, не код.
- Для нашего проекта важный факт: **и Baritone, и AltoClef — клиент-сайд** (симулируют ввод, используют клиентские API). «Дух» должен иметь клиентский исполнитель движения; ядро при этом остаётся чистым от Minecraft.
- Не переносим: прямые импорты внутренностей Baritone (`MineProcess`, `CalculationContext`), mixins-хуки AltoClef, интеграцию через jar Baritone (см. лицензии).

## Конкретные рекомендации

**Брать (идеи/алгоритмы для ядра `com.theminespirit.core`)**
- Интерфейс цели `Goal` + `GoalComposite/GoalInverted/GoalXZ` и октиальная эвристика.
- Константы `ActionCosts` и структура costs-контекста (аналогично `CalculationContext`).
- Парадигма `Moves`-enum + классы `Movement*` (traverse/ascend/descend/fall/pillar/downward/diagonal/parkour).
- A* с ячейками 16×8×16 и binary-heap; `Favoring`/`Avoidance` как «поведенческие веса».
- Модель CachedWorld/WorldScanner для «известного мира».
- Приоритетная очередь добычи `MineProcess` и логика ферм `FarmProcess`.
- Структуры задач AltoClef: Task/TaskChain/TaskRunner, маркеры прерывания, цепочки Food/MobDefense/MLG.

**Адаптировать**
- Алгоритмы портировать на примитивные коллекции вместо fastutil (если не хотим тянуть зависимость).
- Писать свою версию `CalculationContext`/`Settings` под конфиг мода (only-Read/Write из NeoForge), не тащить 1745 строк как есть.
- MLG-clutch и ProgressChecker адаптировать под нашу модель тиков и REST-статус.

**Избегать**
- Копирование файлов Baritone (LGPL — см. риски).
- Любые импорты `baritone.*` из нашего мода; не включать jar Baritone в мод.
- Клиентские mixins AltoClef под 1.18.2 (бессмысленный перенос и зависимость от Baritone 1.18.x).
- Копирование всего модуля `src/main/java/adris/altoclef` (MIT позволяет, но код устарел и жёстко завязан на старый клиент).

## Риски и лицензионные ограничения

- **Baritone — LGPL-3.0 (copyleft).** Жёсткая лицензия: производные/изменённые файлы — под LGPL; интеграция через модификацию Baritone (как у AltoClef's fork) обязывает распространять модифицированный LGPL-код под LGPL с предоставлением исходников и права на замену. Для нашего мода это неприемлемо, если мы не хотим открывать его под LGPL/OGPL.
- **«Anime exception»** Baritone (`LICENSE-Part-2.jpg` в корне репо, бейдж «LGPL-3.0 with anime exception») — расширение/уточнение, не снимает copyleft.
- **AltoClef — MIT.** Разрешительная. Но на практике AltoClef прописан (`include`) с патченным форком Baritone → в распространяемом jar AltoClef присутствует LGPL-код Baritone, и его распространение обязано соблюдать LGPL. То есть «MIT-мод + LGPL-ядро внутри» — комбинация, которую повторять не стоит.
- **Смешения лицензий по каталогам** в обоих репо нет: каждый файл Baritone несёт LGPL-шапку, AltoClef распространяет один MIT LICENSE.
- Rekomendация для нас: **не линковать и не копировать файлы Baritone**. Изучать идеи, писать собственную реализацию pathfinding/daving в ядре по описанным алгоритмам; если позже понадобится использовать настоящий Baritone — только как внешний процесс/инструмент вне нашего jar, а не как встроенную библиотеку.