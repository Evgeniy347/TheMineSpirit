# Приложение к graph-inputs: полные таблицы

Основной разбор — [graph-inputs.md](graph-inputs.md). Здесь полные списки, чтобы отчёт не превышал предел.

## 2. Чего не хватает на входе графа

Типы — предлагаемые поля `GraphState`/`SpiritFact`. Цена — порядок работы на одну пересборку (100 тиков),
если не сказано иное; «кэш» значит, что дорогой скан идёт реже или по чанкам.

| Вход | Тип | Источник | Цена CPU | Какие решения графа |
|---|---|---|---|---|
| Дух: id, имя | `UUID`, `String` | `SpiritEntity` | `O(S)` | кому назначать, журнал |
| Дух: позиция, измерение | `BlockCoord`, `ResourceKey` | сущность | `O(S)` | путь, достижимость, риск |
| Дух: здоровье, максимум, реген | `int`, `int`, `float` | сущность | `O(S)` | жизнь, бой, отдых |
| Дух: еда, насыщение, голод | `int`, `float` | `SpiritHunger` | `O(S)` | еда, работа, запас еды |
| Дух: воздух, огонь, вода, падение, замурован | флаги + `BlockCoord` | сущность + проба блока | `O(S·k)` | узлы жизни, `dig_out`, спасение |
| Дух: инвентарь (предмет → число, слоты) | `Map<String,Integer>` | инвентарь | `O(S·36)` | перенос, крафт, еда, топливо |
| Дух: броня, offhand, экипировка, чары | слоты, прочность | инвентарь | `O(S·слоты)` | бой, опасность, ремонт |
| Дух: инструмент (вид, уровень, прочность) | `Map<kind, TierDurability>` | инвентарь | `O(S·слоты)` | добыча, ремонт, замена |
| Дух: занятость, задача, поколение, control | `enum`, `String`, `long`, `bool` | `SpiritEntity` | `O(S)` | только свободным, не отбирать |
| Дух: застревание, прогресс, последний сдвиг | тики, `BlockCoord` | `StuckSpirits`, `Progress` | `O(S)` | `help`/`dig_out`, не назначать |
| Дух: дом, зона, лагерь | `BlockCoord`, радиус | `SpiritEntity` | `O(S)` | допустимые цели, дорога |
| Дух: достижимость до целей (матрица) | `Map<Target, Reach>` | A* / кэш маршрутов | `O(S·T)`, кэш | «недостижимо» = 0, выбор духа |
| Дух: занятая станция/бронь | `String` | `WorkReserveHub` | `O(S)` | ёмкость станции, без дублей |
| Дух: роль/навык (шахта, ферма, стройка) | `enum`/`int` | сущность/память | `O(S)` | пригодность работы |
| Мир: блоки-ресурсы по тегам и координатам | `Map<Tag,List<BlockCoord>>` | скан чанков/кэш | `O(A·H)`, кэш | что и где добывать |
| Мир: расстояние/путь до ресурса | `Map<Tag, Integer>` | путь/маршрут | `O(R·S)`, кэш | вес дороги, выбор источника |
| Мир: деревья (вид, высота, зрелость, саженцы) | `Map<BlockCoord,Tree>` | скан | `O(A)` | сад, древесина, порода |
| Мир: вода, рыба, лёд, лава | позиции/теги | скан | `O(A)` | рыбалка, ведро, опасность |
| Мир: сундуки (позиция, содержимое, свободно, резерв) | `Map<BlockCoord,Container>` | `StockReader`, `ChestSpaceWatch` | `O(C·27)` | склад, перенос, `storageFreePercent` |
| Мир: станции (тип, позиция, занятость, очередь, топливо, вход/выход) | `StationFact` | блок + контейнер | `O(K·27)` | плавка, крафт, рыбалка, чары |
| Мир: животные (вид, позиция, приручён, детёныш, загон) | `List<AnimalFact>` | `HerdSurvey` | `O(E)` | ловля, разведение, забой, шерсть |
| Мир: угрозы (тип, позиция, HP, дальность) | `List<Threat>` | сущности вокруг | `O(S·E)` | узел жизни, бой/бегство |
| Мир: дропы (предмет, позиция, возраст) | `List<Drop>` | `GraphSurroundings` | `O(E)` | подбор |
| Мир: время, погода, свет, измерение | `long`, `enum` | `ServerLevel` | `O(1)` | ночь, гроза, рейд, фантомы |
| Мир: игроки/жители рядом, заказы | `List<PlayerFact>` | игроки | `O(P)` | заказы, защита, торговля |
| План: постройки (id, уровень, координаты, поворот, схема, статус) | `Map<String,BuildingFact>` | `TownPlan` + ledger | `O(B)` | узлы `building:*` |
| План: процент, повреждения, `unknown` | `done/total/unknown` | аудит клеток | `O(клетки)`, кэш | готовность, ремонт, доверие |
| План: лишнее, ямы, висячее, воронки | `List<Defect>` | `PlotAudit`, `FloatingScan`, `CraterWatch` | `O(площадь)` | уборка, ремонт, засыпка |
| План: рельеф, высоты, проходимость, уклон | сетка | `GroundProbe`, `PlotHeights` | `O(A)` | стройка, дороги, путь |
| План: контур, улицы, калитки, участки | `TownPlan` | `SettlementPlotStore` | `O(B)` | места построек |
| План: дороги и маршруты (построено, цель, статус) | `RoutePlan` | `RouteStore`, `RouteWatch` | `O(R)` | узлы маршрутов, дорога |
| Этап: номер, цели, гейт, остаток, лимиты | `StageRules`, `StageGate` | ledger + мир | `O(1)` | корни приоритетов, что разрешено |
| Материалы построек: нужно/есть/резерв/в пути | `Map<String,Integer>` | схемы + склад | `O(B·items)` | пути снабжения |
| Рецепты и крафт-цепочки | граф | `graph*.json` | `O(1)` | выбор способа |
| Заказы игрока (предмет, число, срок, игрок) | `Map`/`List` | `CraftOrderStore` | `O(1)` | корень `order:*` |
| Население, койки, запас еды, ёмкость склада | `int` | склад/состояние | `O(1)` | пределы, еда, склад |
| Инциденты: пожар, крипер, урон, смерть, потеря инструмента | `enum` + позиция | сторожа/журнал | `O(1)` | узлы событий |
| Защищённые области и владелец | регионы | protection store | `O(1)` | что можно трогать |
| События округи: `fire`, `raid`, `thunderstorm`, `phantom`, `poisoned`, `falling`, `village_found`, `player_request`, `bound_to_player`, `rain`, `player_died*`, `nether_expedition` | `Set<String>` | сейчас не подаются | `O(1)` | 20+ мёртвых веток графа |
| Логистика: кто что несёт, кому и куда | `Map<UUID,Carry>` | инвентарь + заявки | `O(S·items)` | `transfer`, выдача, сдача |
| Печь: топливо, вход, выход, прогресс | `FurnaceFact` | контейнер | `O(K·27)` | плавка, уголь, еда |
| Смерть/респавн/потеря вещей | события | respawn/journal | `O(1)` | пересборка состояния, жизнь |

## 3. Все места, где задание ставится мимо графа

Счёт: 23 файла `*Watch`, 31 файл в `neoforge/board` вызывает `submit`/`StageRepair.submit`/`DemolitionTickets.submit`;
плюс REST, интеллект, прямой запуск действий и `ChainExecutor`. «Вход» — только состояние, «узел» — событие/путь
`board/graph*.json`, «удалить» — заявку снять после переноса, «оставить» — это сервис/исполнитель, а не задачник.

| Файл | Что ставит | Во что превращается | Риски |
|---|---|---|---|
| `CraterWatch` | `FILL_CRATER` по взрыву крипера + live-cause | вход `creeper_crater`; узел `event:creeper_crater`; заявку удалить | дубли, двойная засыпка, событие из listener, не из мира |
| `FenceWatch` | `REPAIR_FENCE` по сломанному блоку + live-cause | вход `fence_broken`; узел `event:fence_broken`; удалить | дубли, ложные срабатывания |
| `EvergreenWatch` | `EAT_TO_FULL` на духа | вход `hungry` по духу; узел `event:hungry/eat`; удалить | глобальный `hungry` не знает, кто голоден; спам |
| `IdleWatch` | заявок нет, снимает паузы и блокировки | вход `idle/blocked`; оставить диагностику | маскирует провалы, снимает защиту от thrash |
| `FishingWatch` | `CRAFT_SMOKER`, `FISH` по своим порогам | узлы `fish`, `smoker`; удалить | дубли, скрытые пороги, нет воды/станции |
| `RepairWatch` | `REPAIR_TOOL` на духа | вход прочности; узел `event:tool_worn/repair_tool`; удалить | прочности нет в state, дубли |
| `PenBuild` | `BUILD_PEN` по уровням | узел `building:pen_*`; удалить | виды, уровни, животные не в state |
| `WarehouseWatch` | `BUILD_WAREHOUSE`, `RELOCATE_BUILDING` | узел `building:warehouse`; удалить | уровни/перенос, защита постройки |
| `DemandWatch` | заявок нет, только лог нужды | вход `demand`; удалить | скрытая логика выбора стройки |
| `RepairAuditWatch` | `REPAIR_BUILDING` по аудиту дома | вход damage; узел `repair_building`; удалить | `building_damaged` сейчас из открытого тикета — круг |
| `HouseLevels` | `BUILD_HOUSE` уровня | узел `building:hut`; удалить | уровень и схема не в state |
| `MineLevels` | `BUILD_MINE` уровня | узел `building:mine`; удалить | уровень, вход шахты |
| `EnchantRoomWatch` | `ENCHANT_ROOM` по уровням | узел `building:enchanting_table`; удалить | лазурит и полки не в state |
| `HerdWatch` | `CAPTURE_ANIMAL`, `BREED_ANIMALS`, `HERD_CULL` | вход стадо; узел `event:herd`; удалить | поголовье, пол, детёныши, загон |
| `PetWatch` | прямые tame/breed/follow, без доски | вход питомцы; узел `activity:pet` или оставить | действие мимо доски и графа |
| `TransferWatch` | `TRANSFER_ITEM` и переносы к крафту | вход логистика; узел `transfer/carry`; удалить | дубли, застревание носильщиков |
| `TransferDriftWatch` | отменяет `TRANSFER_ITEM` при колебаниях | вход drift; узел route cost; оставить диагностику | отменяет работу, не сообщая графу |
| `WoolWatch` | `CRAFT_SHEARS`, `HUNT_WILD_SHEEP`, `SHEAR_SHEEP`, `WEAVE_WOOL`, `CRAFT_BED` | узлы `wool_*`; удалить | дубли, резерв шерсти и кроватей |
| `HiveWatch` | `HARVEST_HIVE`, `PLACE_BEEHIVE` | узлы `hive_*`; удалить | ульи, пчёлы, цветы не в state |
| `LeftOreWatch` | `MINE_LEFT_ORE` по журналу | вход руды; узел `mine_left_ore`; удалить | журнал ≠ мир |
| `RescueWatch` | `RESCUE_SPIRIT` по застреванию | вход stuck/pit; узел `life:rescue`; удалить | жизнь мимо графа, дубли с `DigOutWatch` |
| `DigOutWatch` | прямой `dig_out`/`walk_careful` при замуровке | вход trapped/pit; узел `life:dig_out`; удалить | старт мимо доски, отбирает духа |
| `PlotAudit` | `REPAIR_BUILDING` на лишнее в контуре; зовёт `YardFloorWatch` | вход дефекты; узел repair/clear; удалить заявку | дубли с `RepairAuditWatch`/`StageRepair` |
| `YardFloorWatch` | `StageRepair` (`STOCK_CHESTS`) — дыры под станками | вход floor holes; узел repair; удалить | дубли, приоритет |
| `OldChests` | `SUPPLY_SITE` — перенос старых сундуков | вход сундуки; узел transfer; удалить | потеря/защита содержимого |
| `ChestSpaceWatch` | `STOCK_CHESTS` по свободным слотам | вход `storageFreePercent`; узел warehouse/chest; удалить | дубли с `storage` notice |
| `OrchardHarvest` | `HARVEST_ORCHARD` по зрелости | вход деревья; узел harvest_orchard; удалить | дубли, нет потребности |
| `PlanDemolition` | `DISMANTLE_SHELTER` пачками | вход лишнее; узел clear/demolish; удалить | необратимо, конфликт защиты |
| `DemolitionTickets` | helper для `DISMANTLE_SHELTER` | то же | — |
| `StageRepair` | `REPAIR_BUILDING` из аудита шага | узел repair; удалить | цепочки приоритетов |
| `FirstStepsRunner` | `first:*` этапа 1 (10+ кейсов) | вход stage facts; узел stage/building; удалить | главный обход: этап 1 вне графа |
| `StageSchedule` | переоткрывает `first:*` и шаги этапа | вход stage gate; узел stage; удалить | дубли с `GraphPost` |
| `StageReopen`/`StageClose`/`StageOpen` | открывает/снимает шаги этапа по ledger | вход ledger; узел completion by audit; оставить переходы | журнал закрывает вместо мира |
| `StageWorldGate`/`StageBootstrap` | гейт этапа по миру, переоткрытие | вход stage facts; оставить state | регресс этапа |
| `StageSettle`/`PlanCompletion` | закрытие/сверка плана | вход audit; оставить completion | журнал vs мир |
| `SettlementStageService` | REST open/close/rollback этапа | вход stage; оставить стратегу | прямой обход графа |
| `SpiritBoardGateway` | REST `POST /board/tasks` | вход order/event или узел; ограничить | внешняя задача вне графа |
| `BoardService.reportDropped` | `COLLECT_DROPS` на выкинутое | вход drops; узел collect_drops; удалить | дубли с `GraphSurroundings` |
| `BoardService.ensureSupply`/`SupplyTickets` | `supply`, `supply_gather` | вход нехватка; узел supply path; удалить | дубли добычи |
| `GraphPost`/`GraphRoutes` | путь графа, но зовёт `FirstStepsRunner`/`StagePlacePost` | graph path; оставить, убрать внутренний обход | двойная постановка |
| `SettlementIntellect`/`ExpeditionController` | `ACTIVITY_COMBAT/ARM/EXPEDITION` через `board.launch` | вход угрозы; узел activity/defend; перенести | два центра решений (tactics/`weights.json`) |
| `ChainExecutor`/`CraftOrders.refresh` | выполняет заказ игрока прямо в инвентаре | вход order; узел craft_order; перенести | действие вне доски и графа |

Не считать обходом: `GraphCollect`, `GraphCrafting`, `GraphBrew`, `GraphFood`, `GraphRoutes`, `PortalPost`,
`StagePlacePost`, `ExtraOrchardPost` — это исполнители заданий, поставленных графом.

## 5. Зависимости действий

В коде 100 классов `*Script` и 100 зарегистрированных `ScriptSpec` (оценка «200+» завышена). Плюс 19 `*Session`.
Среди них 55 ссылок «класс → класс», циклов нет; самая длинная цепочка — 4 (`BoneMealScript → FarmScript → FarmSession → UnloadScript`).
Верхние рёбра (полный список из 18 рёбер Script → Script):

```
BoneMealScript → FarmScript       HarvestCropScript → FarmScript   PlantCropScript → FarmScript
TillScript → FarmScript           BreedScript → HerdScript          CollectEggsScript → HerdScript
CullScript → HerdScript           FeedScript → HerdScript           HerdCheckScript → HerdScript
HuntScript → HerdScript           MilkScript → HerdScript           ShearScript → HerdScript
BuildFenceScript → PlaceBlockScript   BuildShelterScript → BuildSchematicScript
CrossWaterScript → GotoScript, SwimScript   GatherScript → TramplePathScript
ReturnHomeScript → GotoScript
```

Вынести в helper-классы: `InventoryOps` (взять/положить/надеть/прочность), `BlockTargeting` (клетка, грань,
досягаемость), `ContainerOps` (сундук/печь/котёл), `MovementOps` (шаг/дверь/вода), `EntityOps` (животные,
угрозы), `ProgressOps` (тик/шаг/успех). Связи «составной → базовый» оставить; обратные запретить сторожем.
