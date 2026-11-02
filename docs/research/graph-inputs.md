# Вход и выход графа тактики: разбор 02.11

Разбор по коду `main` (02.11), без изменения логики. Граф — `board/graph.json` + `board/graph-recipes.json`
(после слияния: 1038 предметов, 135 построек, 1385 путей, 41 событие). Точка входа — `GraphSource.tick`
(`neoforge/board/GraphSource.java:59`): `FirstStepsRunner` → `GraphSurroundings` → `GraphInputs.state` →
`GraphPlanner.plan` → `GraphTickets.of` → `GraphPost`. Полные таблицы — в
[graph-inputs-tables.md](graph-inputs-tables.md).

## 0. Что уже сделано (02.11, graphcore)

- **Фрагменты графа.** `SupplyGraph.loadDefault`/`merge` и `Graph.load_merged` читают
  `board/graph.json` + `board/graph-recipes.json` + все `board/graph.d/*.json`. Фрагмент добавляет только новые id,
  конфликт id — ошибка с путём файла. Разные исполнители правят разные файлы.
- **`GraphFacts`** — новые структуры входа `GraphState.facts()`: `spirits`, `threats`, `chests`, `resources`,
  `stations`, `buildings`, `strays`, `terrain`, `stageReqs`. Старые поля `GraphState` сохранены для планировщика
  (число духов `spirits()` считается по `kits`, счётчики станций — по `stations`).
- **Заполнено сейчас:** духи (id, позиция, HP, еда, воздух, вода, застревание, занятость, инвентарь, броня,
  инструменты), сундуки из `StockReader` (позиция, содержимое, свободные слоты), ресурсы (тег, координаты,
  расстояние, `travelTicks`; достижимость — `UNKNOWN`), станции (тип, позиция, счётчик для планировщика),
  постройки (id, уровень, процент 100/0, координаты участка, `unknown=false`), `stageReqs` (минимум из
  `stages.json` + факт из журнала). `distances` теперь заполняются из `GraphSurroundings`, `stations` — из построек.
- **Пока пусто:** `threats`, `strays`, `terrain`, топливо/вход/выход станций, точный процент аудита и повреждения
  построек, «замурован» духа, достижимость ресурсов. Это узлы `life:*`, `repair`, `clear`, `station:*`, `store:*`;
  сборщики оставлены пустыми до реализации.

## 1. Что граф получает сейчас

`GraphState` (`core/board/graph/GraphState.java`) — 11 полей. В моде заполняются все, кроме `distances` и
`stations`: `GraphInputs.state` (`neoforge/board/GraphInputs.java:55`) вызывает конструктор без них, поэтому
`distances` и `stations` всегда пустые.

| Поле | Откуда | Как считается | Точность |
|---|---|---|---|
| `stock` | `StockReader` + инвентари духов | сундуки у целей доски (радиус 8, бюджет 8 контейнеров/4096 проб), плюс несомое; минус брони доски | по миру, но обрезано бюджетом и только загруженные чанки |
| `stage` | `BoardLedger` | номер из журнала; мир сверяется только на переходе этапа (`StageWorldGate`) | по журналу, не по миру |
| `kits` | инвентарь + главная рука каждого духа | предмет → максимальный уровень инструмента (`GraphKits`); брони, offhand, точной прочности и еды нет | инвентарь точно, смысл узкий |
| `buildings` | `StageBuilt`, маршруты, `TownPlan`, ledger, `worldBuilt`, статус сада | факты мира для участка этапа 1; для плана — ledger, аудит схемы только если `confident`, иначе не BUILT | смешанно: мир там, где аудит уверен, иначе журнал/план |
| `materials` | схемы этапа + склад + рецепты | нехватка материалов для непостроенных и не забронированных построек этапа; занятые — пустая карта | точно для покрытых схем, пусто для забронированных |
| `storageFreePercent` | `ChestSpaceWatch.space` | свободные слоты известных сундуков поселения / всего слотов | по миру; 100 %, если сундуков нет |
| `events` | `GraphSource.events`, `GraphSurroundings`, `RouteWatch` | `hostile_nearby` (моб в 16 блоках от любого духа), `creeper_crater`, `fence_broken`, `building_damaged` (по открытому тикету), `night`, `hungry` (любой голоден), заказ игрока; `drops_nearby`, `leaf_decay`, `lava_seen`; `biome_found:*`, `source_depleted:*`, `route_retarget_*` | смешанно; из 41 события реально производятся ~10–13, часть — из журнала/доски, не из мира |
| `world` | `GraphSurroundings.blocks` + сущности + маршруты | блоки вокруг дома поселения 65×65×17 (загруженные чанки), только теги; ближайшие существа; теги маршрутов | по миру, но без координат, числа и расстояния; незагруженное = «неизвестно» |
| `distances` | — | не заполняется | нет данных: `travelFactor` всегда 1 |
| `stations` | — | не заполняется; `stations(building)` = 1, если постройка BUILT | нет данных: ёмкость станций не учитывается |
| `orders` | `CraftOrders.shortage` | недостача по заказам игрока | точно, по книге заказов |

Кроме этого, `spirits()` = число `kits`, то есть число духов. Позиций, здоровья, еды, занятости, воды,
замурованности, брони, переносимых предметов по духам в графе нет — эти факты появляются только при выдаче
(`DispatchPick.candidate`).

## 2. Что ещё нужно подать на вход

Полный список с типами, источником, ценой и решениями — в
[таблице 2](graph-inputs-tables.md#2-чего-не-хватает-на-входе-графа). Коротко по группам:

- **Дух**: позиция, здоровье, еда, воздух/вода/огонь, инвентарь с прочностью, броня, занятость, застревание,
  дом/зона, достижимость, занятая станция, роль. Без этого нельзя ни назначить пару «дух → работа», ни отличить
  «недостижимо» от «отказался».
- **Мир**: ресурсы по тегам и координатам (руды всех видов, деревья, камень, песок, глина, вода, лава, цветы,
  грибы), расстояния до них, деревья/саженцы, сундуки с содержимым и свободными слотами, станции с очередью и
  топливом, животные, угрозы, дропы, время/погода/свет, игроки. Сейчас `world` — только множество тегов.
- **План и аудит**: постройки с уровнем, координатами, схемой, процентом, повреждениями и `unknown`; лишние
  постройки, ямы, висячее, воронки; рельеф и проходимость; контур, улицы, участки, дороги; этап и гейт; материалы
  и резервы. Это вход для узлов `building:*`, `repair`, `clear`, `storage`.
- **Логистика и порядок**: заказы игрока, кто что несёт, топливо/вход/выход печей, смерть/респавн. Сейчас заказ
  игрока не только входит в граф, но и выполняется отдельно через `ChainExecutor`.
- **Событийная полнота**: `fire`, `raid`, `thunderstorm`, `phantom`, `poisoned`, `falling`, `village_found`,
  `player_request`, `bound_to_player`, `rain`, `player_died*`, `nether_expedition` объявлены в графе, но никем не
  подаются. `tool_worn`, `armor_worn`, `drowning`, `suffocation`, `fall_risk`, `ore_found_diamond` — тоже мёртвые
  ветки. `building_damaged` подаётся по открытому ремонтному тикету, а не по аудиту мира — это круг.

### 2a. Угрозы — обязательный вход (решение владельца 02.11)

`ThreatFact` на каждого врага: `type`/`id`, абсолютное `health` и доля, `state` (горит, заряжен/усилен крипер,
невидим, детёныш, приручён, плывёт/летит, эффекты, предмет в руке, паника/еда), `pos` + измерение, `aggro`
(UUID цели и вид: дух/игрок/животное/другое), `targetingSelf`, `lastHurtBy`, скорость, видимость/LOS, расстояние
до поселения и каждого духа. Источник: `level.getEntitiesOfClass(Monster/LivingEntity, AABB объединения духов ±16)`,
`Mob.getTarget()`/`getLastHurtByMob()`, `getHealth/getMaxHealth`, состояние блока/эффекты, `LineOfSight`. Заготовка —
`core/tactics/EnemyFact.java`: уже есть `type`, `id`, `pos`, `hpFraction`, `armor`, `visible`, `targetingSelf`,
`count`; не хватает цели и её вида, состояния, абсолютного HP и LOS. Цена: один AABB-запрос `O(E)`, `getTarget`
`O(1)`, LOS `O(расстояние)` на пару враг/дух; бюджет как в `WorldFactCollector` (`entityQueries`).

Решения, зависящие от угроз: есть ли угроза; вес узла жизни; кто защищает, бежит или продолжает работу; кого бить
первым; эвакуация; защита игрока/животного; риск дороги и стройки; зов помощи. Сейчас `hostile_nearby` — только
флаг, который толкает `tool:sword`, броню, свет и забор; ни цели, ни вида агрессии, ни состояния врага граф не знает.

## 3. Где задания ставятся мимо графа

Счёт: 23 файла `*Watch`; 31 файл в `neoforge/board` вызывает `submit`/`StageRepair.submit`/`DemolitionTickets.submit`.
Плюс REST, интеллект, `DigOutWatch`, `PetWatch` и `ChainExecutor`. Полная таблица «файл → что ставит → судьба →
риски» — в [таблице 3](graph-inputs-tables.md#3-все-места-где-задание-ставится-мимо-графа).

Четыре вида обхода:

1. **Слушатели и сторожа** (`CraterWatch`, `FenceWatch`, `LeftOreWatch`, `HerdWatch`, `WoolWatch`, `HiveWatch`,
   `FishingWatch`, `RepairWatch`, `WarehouseWatch`, `HouseLevels`, `MineLevels`, `EnchantRoomWatch`,
   `RepairAuditWatch`, `RescueWatch`, `EvergreenWatch`, `ChestSpaceWatch`, `OldChests`, `OrchardHarvest`) —
   сами ставят билеты по своему порогу. Почти для всех в графе уже есть событие/узел; их дело — только состояние.
2. **Этап и ремонт** (`FirstStepsRunner`, `StageSchedule`, `StageReopen/Close/Open`, `StageRepair`,
   `YardFloorWatch`, `PlotAudit`, `StageSettle`, `PlanCompletion`) — самые опасные: `GraphPost` для постройки
   вызывает `FirstStepsRunner.open`, который снова ставит `first:*` и `repair:*`. Граф и «первые шаги» ставят
   одну работу дважды, а журнал закрывает её вместо аудита.
3. **Прямые действия без доски** (`DigOutWatch` запускает `dig_out`/`walk_careful`, `PetWatch` — приручение и
   разведение, `SettlementIntellect`/`ExpeditionController` — `ACTIVITY_COMBAT/ARM/EXPEDITION`, `ChainExecutor` —
   заказ игрока). Дух занят, но на доске этого нет.
4. **Внешние входы** (`SpiritBoardGateway` REST, `SettlementStageService`). Это допустимый стратег, но он должен
   менять только вход графа (этап, заказ, событие), а не ставить работу напрямую.

## 4. Выход: сейчас и как перейти на «дух → работа»

**Сейчас.** `GraphPlan.Task` несёт `id`, `boardCase`, `makes`, `priority`, `consumes`, `feasible`, `chance`,
`implemented`, `ticks`; `spirit` заполнен только у инструментов (`tool:<kind>@<spirit>`). `GraphTickets.of`
делает из выполнимых задач билеты с приоритетом, `GraphPost` ставит их на доску. Духа выбирает `DispatchPick`
(`neoforge/board/DispatchPick.java:62`): сначала «свой» или крафтер, затем **евклидово расстояние** до цели,
затем `DispatchRound.choose`/`refusal` (занят, застрял, инструмент, запас, вода, evergreen). Проверки
достижимости нет — «недостижимый» становится провалом, застреванием или спасением уже после выдачи. Один дух может держать до `maxActivePerSpirit` билетов (по умолчанию 1).

**Модель.** Нужны три слоя:

1. `SpiritFact` на входе (см. §2) и `TargetFact` (ресурс/станция/постройка/сундук) с `Reach` (путь/тики/unknown).
2. Узлы работы с пригодностью: `work@spirit` или ребро `(task, spirit)` с весом
   `utility(task) × suitability(spirit, task) / cost(reach, time)`. Ограничения: один активный дух — одна работа,
   ёмкость станции, инструмент, запас, «не брать занятого/застрявшего». Это назначение (bipartite max-weight
   matching или min-cost flow), а не сложение потоков.
3. Выход — список `(spiritId, taskId, target, priority, reason)`; `DispatchPick` остаётся последней милей
   (достать цель, проверить инструмент), но не выбирает, кому работать.

**Python.** `tools/graph-gpu/graph_model.py` уже повторяет `GraphState`/`Planner`, `sim_gpu.assign` раздаёт
задачи по порядку свободным духам. Нужно: поля `spirits`, `reach`, `threat`, `stations` в `State`; разделить
`Planner` на генерацию задач и `assign`; в `World` — позиции, здоровье, инвентарь, достижимость; в `search.py` —
веса пригодности, дороги, риска, простоя; в `test_conformance.py` сверять не только `id/priority/feasible`, но и
пары «дух → работа». Пакетная матрица `(I−A)` остаётся для оценки задач, назначение — отдельный шаг (духов
обычно < 20, его можно считать на CPU или батчем на GPU).

**«Недостижимо» как состояние.** `Reach = UNKNOWN | PATH(ticks) | NO_PATH`. `NO_PATH` — вес пары 0, а не провал;
`UNKNOWN` (незагруженный чанк) — не назначать, а послать `explore`/`scan`. Замурован — `trapped=true` у духа и
узел `life:dig_out`/`life:rescue` с максимальным весом; другой дух получает `help` по тому же состоянию.

**Защита жизни.** Сейчас бой — отдельный слой: `TacticsService` собирает `Situation` (`WorldFactCollector`,
`SettlementScanner`), `TacticsEngine.step` по `tactics/weights.json` (FIGHT/RETREAT/IGNORE, scales, stickiness,
holdTicks) выбирает, `IntentActionExecutor` ставит target/attack/retreat, `wantsControl` отбирает духа у доски;
`SettlementIntellect`/`DefenseService` через `DefensePlanner`/`DefenseAllocator` раздаёт посты и вызывает
`board.launch(ACTIVITY_COMBAT/ARM)`. Граф видит только `hostile_nearby` и push на sword/armor/lighting/fence.
Свести: `ThreatFact` на входе; `event:life_threat` с задачами `defend`/`retreat`/`arm` весом в отдельной полосе
выше `maxPriority` (иначе ничья с обычным потолком 100); граф выбирает духа, цель и пост; `tactics` остаётся
микро-исполнителем (FIGHT/RETREAT, дистанция, щит) и хранит `enemies.json`; `SettlementIntellect` больше не
ставит `ACTIVITY_*` сам. Смерть/утрата инструмента — такое же событие, а не отдельный сторож.

## 5. Действия: зависимости и помощники

В коде 100 классов `*Script` и 100 `ScriptSpec`; «200+» — оценка сверху. Вместе с 19 `*Session` это 119 узлов и
55 ссылок, **циклов нет**; самая длинная цепочка — 4 (Script → Script максимум 2). Полный список рёбер — в
[таблице 5](graph-inputs-tables.md#5-зависимости-действий). Связи «составной → базовый» (`BreedScript →
HerdScript`, `BuildShelterScript → BuildSchematicScript`) полезны, обратные — запрещены.

Вынести в helper-классы: `InventoryOps`, `BlockTargeting`, `ContainerOps`, `MovementOps`, `EntityOps`,
`ProgressOps`. Предложение сторожа-теста `ActionDependencyGuardTests`:

```java
// собрать все *Script.java; для каждого — ссылки на другие *Script/*Session по имени класса;
// граф ориентированный; найти SCC (Тарьян); упасть, если SCC.size() > 1.
// сообщение: «цикл действий A→B→A: действие не должно зависеть от себя через посредника;
// вынеси общее в helper или разверни связь».
```

Второй сторож — `BoardPostGuardTests`: `new BoardTicket`, `service.submit`, `StageRepair.submit`,
`DemolitionTickets.submit` разрешены только в `GraphPost` и явном белом списке; каждое событие из `graph.json`
обязано иметь производителя в `GraphSource`/`GraphSurroundings`/`RouteWatch`. Третий — сверка `GraphState`:
каждое поле обязано быть заполнено (`distances`/`stations` не пустые, если в мире есть данные).

## 6. План перехода блоками

Порядок — от измерения к удалению обходов; каждый блок заканчивается тестами, пересчётом весов и сверкой 200/200.

| Блок | Что делаем | Тесты | Веса и сверка | Объём |
|---|---|---|---|---|
| B0. Измерение | Выгрузить фактический `GraphState` и покрытие событий; починить пустые `distances`/`stations`; отчёт по бюджетам | unit на заполнение, экспорт 200 состояний | веса не меняются, 200/200 | 1–2 дня |
| B1. Состояние духов | `SpiritFact`: позиция, HP, еда, инвентарь, броня, прочность, занятость, застревание, дом/зона, достижимость | unit + живые фикстуры | новые поля, сверка расширяется | 3–5 дней |
| B2. Состояние мира | Ресурсы с координатами и расстояниями, сундуки/станции, животные, угрозы, рельеф, аудит плана | unit сканов, GameTest сцены | веса дороги/риска | 5–8 дней |
| B3. Новые узлы | `life:*`, `transfer/carry`, `station:*`, `storage`, `repair/clear`, заказы; данные в `graph.json` | `GraphPlannerTests`, `SupplyGraph` | пересчёт на GPU, 200/200 + симуляция | 5–8 дней |
| B4. Выход парами | Назначение `(task, spirit)` в Java и Python; `DispatchPick` — только last mile; сверка пар | unit назначения, краевые: 0/1/N духов, нет пути | новые веса пригодности, 200/200 | 5–7 дней |
| B5. Снять безопасные сторожа | `LeftOre`, `Orchard`, `Fence`, `Crater`, `Herd`, `Wool`, `Hive`, `ChestSpace`, `OldChests` → состояние | удалить их тесты заявок, оставить state-тесты | пересчёт после каждой группы | 5–7 дней |
| B6. Этап и ремонт | `FirstStepsRunner`/`StageSchedule`/`StageRepair` → факты мира и узлы; журнал только подтверждает | GameTest этапа 1, аудит, переоткрытие | веса этапов, 200/200 | 7–10 дней |
| B7. Жизнь и бой | `ThreatFact`, `life_threat`, `defend/retreat`; `tactics/weights.json` — параметры исполнителя | unit угроз, GameTest боя/спасения | веса жизни выше потолка, 200/200 | 4–6 дней |
| B8. Чистка | REST/`ChainExecutor`/`PetWatch` через граф; сторожа `BoardPost`, `ActionDependency`, покрытие событий | полный unit + GameTest | финальный пересчёт и 200/200 | 3–5 дней |

Пофайловый план — [graph-inputs-migration.md](graph-inputs-migration.md): для каждого источника вход, узел/путь,
что удаляется и сколько строк (из ≈10.8 тыс. строк 50 файлов уйдёт ≈6–7 тыс.). Критерий приёмки шага: новый тест
до правки красный, после — зелёный; `./gradlew graphExport` + `tools/graph-gpu/test_conformance.py` — 200/200
(набор, `feasible`, `chance`, `priority` ±1), пары «дух → работа» — отдельный `pairs.json` 200/200;
`sim_gpu.py` повторяет `GraphSimulationTests` по шагам, тикам и порядку на 5 базовых мирах; `search.py` пересчитал
веса, они внесены в `board/graph.json`, сверка 200/200 повторена; есть живая проверка на dev-копии сцены шага.

Итого ~35–55 дней одного исполнителя; критический путь — B1→B2→B3→B4. Начинать с B0: без измерения
`distances`/`stations` и покрытия событий остальные блоки будут чинить вслепую.

## Что не проверено

Код логики не менялся; живой прогон и `graphExport` не выполнялись. Числа действий и графа — из исходников и
JSON на `main`; «200+ действий» в задании не подтвердилось (100 классов, 100 спецификаций). `GraphSurroundings`
считает 65×65×17 = 71 825 блоков на цикл 600 тиков — оценка цены приблизительная, точные замеры даст B0.
