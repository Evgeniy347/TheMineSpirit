# Стройка по схемам (MineColonies + Structurize)

## 1. Схема и её установка

- **Где:** `structurize/blueprints/v1/Blueprint.java` (формат, палитра, размеры), `placement/StructurePlacer.java`
  (один шаг установки), `placement/handlers/placement/PlacementHandlers.java` (правила для дверей, кроватей,
  жидкостей, «заглушек»), `util/ChangeStorage.java` (снимок изменённых блоков).
- **Как:** `.blueprint` — сжатый NBT: размеры, палитра BlockState, блоки/BlockEntity/сущности. Установка идёт
  не «весь массив сразу», а порциями: итератор выдаёт следующую клетку, `StructurePlacer` ставит/ломает блок,
  возвращает `StepPerCall`-лимит и накопленный список требуемых предметов. Отдельные обработчики не дают
  поставить дверь без второй половины, кровать без пары, воду/лаву «как есть».
- **У нас:** `mech/building/Schematic.java`, `mod/.../SchematicSite.java`, `BuildPlanner.java` — своя схема,
  поворот, порции и стоянки. Своего формата «палитра + обработчики особых блоков» нет: свойства блоков
  разбираются по путям (`SchematicBlocks`).
- **Идея:** добавить в `Schematic` явные «связки частей» (дверь: нижняя+верхняя, кровать: две клетки) и
  правило «не ставить половину без второй» — сейчас это размазано по правилам проходимости. Цена: S–M.
- **Идея:** `ChangeStorage` как обязательный журнал отката: каждая правка схемы записывает «было → стало».
  У нас это `RemovalLog`/`PlacementJournal`; свести все пути стройки к одному журналу. Цена: M.

## 2. Порядок и слои

- **Где:** `structurize/placement/BlueprintIteratorDefault.java`, `BlueprintIteratorInwardCircle*.java`,
  `BlueprintIteratorHilbert.java`, `BlueprintIteratorRandom.java`, `StructureIterators.java`;
  `minecolonies/core/entity/ai/workers/util/LayerBlueprintIterator.java`.
- **Как:** базовый обход — «змейка» по слою X/Z снизу вверх (Y растёт), затем следующий слой. Есть варианты:
  по спирали внутрь, по кривой Гильберта, случайный. Карьер/шахта используют `LayerBlueprintIterator` —
  итератор только по одному Y-слою, чтобы спускаться ярус за ярусом.
- **У нас:** `BuildPlanner` уже упорядочивает по фазам и «полосам» высоты (`BuildPhase`), ставит снизу вверх,
  обходит стоянки и отступает; `LayerBlueprintIterator` аналога нет — ярусы задаются фазами.
- **Идея:** именованные стратегии обхода слоя (змейка / от центра / от лестницы) как данные схемы или
  параметр плана: для шахты и карьера удобнее «от входа к краю», чем общий порядок. Класс:
  `BuildPhase` + `BuildPlanner`. Цена: S.
- **Идея:** «шагов за вызов» и «блоков за тик» — не константа в коде, а бюджет действия (`stepsPerCall`):
  тяжёлая схема не должна съедать тик. У нас бюджет времени есть в `RouteBudget`/`SearchMillis`; добавить
  такой же лимит в `BuildPlanner.plan`. Цена: S.

## 3. Стадии стройки

- **Где:** `minecolonies/core/entity/ai/workers/AbstractEntityAIStructure.java` (`loadStructure`,
  `structureStep`, `skipBuilding`, `skipClearing`, `skipRemoval`),
  `core/entity/ai/workers/util/BuildingProgressStage.java`, `BuildingStructureHandler.java`.
- **Как:** набор стадий зависит от случая:
  - новая постройка: `CLEAR → BUILD_FRAME → BUILD_SOLID → WEAK_SOLID → CLEAR_WATER → CLEAR_NON_SOLIDS →
    DECORATE → SPAWN`;
  - апгрейд: без `CLEAR` и `BUILD_FRAME` — сразу `BUILD_SOLID…`, старое не сносится целиком;
  - снос: `REMOVE_WATER → REMOVE`.
  `CLEAR` убирает лишнее над площадкой (но не воздух/жидкости/бедрок/защищённые блоки), `BUILD_FRAME`
  ставит несущий каркас, `WEAK_SOLID` — блоки, которые не держатся сами (растения, ковры), `DECORATE` —
  только декор, `SPAWN` — сущности. `skip*`-правила не трогают бедрок, блок ратуши и `IBuilderUndestroyable`.
- **У нас:** `BuildPhase` (грубая/чистовая фаза), `BuildPlanner` (опора, отступ, крыша), `SchematicWalk`
  (проходимость), `RetreatPath` (не отрезать себе выход). Явной стадии «жидкости/непрочные/сущности» нет —
  вода, растения и сущности обрабатываются по типу блока.
- **Идея:** ввести в `BuildPhase` отдельную стадию `WEAK` для блоков без опоры и `SPAWN` для сущностей:
  сейчас они попадают в общий поток и могут блокировать стоянку. Класс: `BuildPhase`, `BuildPlanner`.
  Цена: M.
- **Идея:** отдельная стадия `WATER` — сначала убрать жидкость из объёма, потом ставить блоки; правило
  «жидкость не считается опорой» уже есть в `MiningSafety`, но порядок стадий стоит сделать явным. Цена: S.

## 4. Ресурсы стройки

- **Где:** `core/colony/buildings/AbstractBuildingStructureBuilder.java` (`neededResources`,
  `getRequiredResources`, `checkOrRequestBucket`), `core/colony/buildings/modules/BuildingResourcesModule.java`,
  `core/colony/buildings/utils/BuilderBucket.java`, `BuildingBuilderResource.java`.
- **Как:** на старте стройки схема разбирается в карту «предмет → количество»; для каждой стадии
  формируется `BuilderBucket` (что нужно именно сейчас). Нехватка превращается в запрос RequestSystem,
  излишек не тащится. Строитель берёт партию в инвентарь, ставит, при переполнении выгружает; в UI ресурс
  имеет состояние `DONT_HAVE / NEED_MORE / IN_DELIVERY / HAVE_ENOUGH / NOT_NEEDED`.
- **У нас:** `BuildPlanner.trip` уже считает заходы, слоты, остаток в сундуке и «хлам»; материалы приходят
  из `GraphBuildingMaterials`/`CaseMaterials`, доставка — `TransferBuildDesk`, `SupplyTickets`.
- **Идея:** состояние «в пути» (`IN_DELIVERY`) как отдельный факт входа графа: сейчас дух-строитель может
  снова просить то, что уже несёт курьер. Класс: `GraphLack`/`StageMaterialNeed`, узел `storage_space`.
  Цена: S–M.
- **Идея:** `BuilderBucket`-подобная «партия стадии» — брать только то, что нужно текущей фазе, а не весь
  список дома. В `BuildPlanner` это `Portion.PHASE`; распространить на все пути стройки. Цена: S.

## 5. Апгрейд и снос уровня

- **Где:** `core/colony/workorders/WorkOrderBuilding.java` (`create`, `targetLevel`),
  `core/colony/buildings/AbstractBuilding.java` (`requestWorkOrder`, `canDeconstruct`),
  `core/colony/workorders/WorkManager.java`.
- **Как:** имя схемы — `<имя><уровень>.blueprint`. `BUILD` → уровень 1, `UPGRADE` → текущий+1,
  `REMOVE` → 0, но грузится схема текущего уровня в режиме удаления. Апгрейд не сносит дом: строится
  следующий уровень поверх, лишнее убирается стадиями; снос идёт стадиями `REMOVE_WATER/REMOVE`,
  защищённые блоки пропускаются. Работа выдаётся через work order и закрепляется за зданием.
- **У нас:** уровни есть в `BuildingType`/`SettlementLevel` и планах этапов; снос — `PlanDemolition`,
  `DemolitionRestore`, `DemolitionGuard`; апгрейд как отдельная стадия поверх существующего — не выделен.
- **Идея:** явный `UpgradePlan` (схема уровня N+1, старт без `CLEAR`, список «что убрать» = старое минус
  новое) и отдельный `DemolishPlan` со стадиями «жидкость → блоки → восстановление площадки». Классы:
  `PlanStatesInputs`, `PlanDemolition`, `SettlementPlot`. Цена: M.
- **Идея:** «заявка на апгрейд» как узел графа с ценой = материалы уровня + время; очередь работ по
  зданиям, а не по духам. Узел: `build_*`/`upgrade_*` в `graph.json`. Цена: M.

## 6. Защита блоков постройки

- **Где:** `AbstractEntityAIStructure.DONT_TOUCH_PREDICATE` (бедрок, `IBuilderUndestroyable`, центральный
  блок ратуши), `api/entity/ai/workers/util/IBuilderUndestroyable.java`,
  `core/colony/permissions/ColonyPermissionEventHandler.java` (`BlockEvent.BreakEvent/PlaceEvent`).
- **Как:** строитель и игрок не ломают защищённые блоки; территория колонии закрыта правами на ломание/
  установку, блоки ратуши — особо. Защита идёт не «списком координат», а типом блока + правами поселения.
- **У нас:** `DemolitionGuard` + `BlockProtection` охраняют клетки плана от сноса, `PlotPlanBlocks` знает
  ожидаемый блок, `PlacedRepair` чинит поставленное. Но запрет на ломание клетки плана не является
  единым правилом для всех действий (копание, добыча, стройка).
- **Идея:** один `PlanProtection` на все действия: «клетка плана ломается только задачей ремонта/сноса,
  остальным — отказ с причиной». Точка: `BlockProtection` (ядро), использовать в `DigOut`, `HarvestSearch`,
  `BuildPlanner`, `PitRescue`. Цена: S–M.
- **Идея:** маркер `неразрушимо` в наших схемах (аналог `IBuilderUndestroyable`), чтобы фундамент/углы не
  выгрызались случайной копкой. Класс: `Schematic.PlacedEntry` + `SchematicRules`. Цена: S.

## 7. Выравнивание и засыпка площадки

- **Где:** `structurize/placement/handlers/placement/PlacementHandlers.SolidSubstitutionPlacementHandler`,
  `minecolonies/core/entity/ai/workers/AbstractEntityAIStructure.getSolidSubstitution` (настройка `FILL_BLOCK`),
  `BuildingStructureHandler.getSolidBlockForPos/replaceWithSolidBlock`, `BlockUtils.isGoodFloorBlock`;
  `core/items/ItemSupplyCampDeployer.java` (допуск по «воздух сверху / твёрдое снизу»).
- **Как:** в схеме вместо конкретного грунта стоит «заглушка твёрдого блока». При установке она заменяется
  выбранным материалом засыпки (по умолчанию земля), а если пол уже «хороший» — не переставляется.
  Лагерь/сундук припасов проверяют площадку допуском: сколько клеток имеют воздух сверху и твёрдое снизу;
  выше допуска — отказ с ошибкой, а не «сровнять всё».
- **У нас:** `PlotLeveling` (медианная высота, срезка/подсыпка до `MAX_SHIFT`, допуск 1), `FillOrder`
  (слои снизу вверх, опора, занятые клетки), `PitFillPlan` (ямa/воронка), `FillBucketScript`,
  `GroundsPlan`. Материал подсыпки — `PlotLeveling.block` (по умолчанию `dirt`), «хороший пол» — только
  `world.solid(cell)`, без понятия «достаточно хороший другой блок».
- **Идея:** `FillOrder` принимает предикат `floorOk(cell)` (любой твёрдый/подходящий блок, не только
  ожидаемый), чтобы не тратить землю на уже нормальный пол и не менять тропы. Точка: `FillOrder.fill`,
  `PlotLeveling`. Цена: S.
- **Идея:** материал засыпки на участок/здание — данные (земля у жилья, булыжник у шахты, песок у берега),
  как `FILL_BLOCK` у MineColonies. У нас `PlotLeveling.block` уже есть — довести до плана и графа
  (`fill`/`grounds`), чтобы не хардкодить `dirt` в `PitFillPlan`. Цена: S.
- **Идея:** допуск площадки как явное правило (сколько клеток можно оставить неровными) — для лагеря,
  склада, поля; иначе духи тратят время на выравнивание всего квадрата. Класс: `PlotLeveling.isLevel`
  (`TOLERANCE` уже 1) + параметр допуска в `StagePlotSurvey`. Цена: S.

## Сводка отличий

| Механика | MineColonies | У нас | Что взять |
|---|---|---|---|
| Схема | палитра + обработчики частей | схема + правила путей | связки частей, журнал отката |
| Порядок | змейка/спираль/Гильберт, ярусы | фазы + полосы высоты | стратегии обхода как данные |
| Стадии | 8 стадий, отдельно вода/непрочные | фазы, вода по типу блока | `WEAK`, `WATER`, `SPAWN` |
| Ресурсы | bucket на стадию, «в пути» | заходы/слоты, материалы графа | статус «в пути» на входе |
| Апгрейд | схема N+1 поверх, снос стадиями | снос есть, апгрейд не выделен | `UpgradePlan`/`DemolishPlan` |
| Защита | тип блока + права колонии | клетки плана в сносе | единый `PlanProtection` |
| Засыпка | заглушка + `FILL_BLOCK` + допуск | `PlotLeveling`/`FillOrder` | «хороший пол», материал как данные |
