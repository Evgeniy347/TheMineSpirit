# Ночь и охрана, интерфейс, конфиги и данные

## 1. Ночь и охрана

- **Где:** `core/entity/ai/workers/guard/AbstractEntityAIGuard.java`,
  `EntityAIMelee.java`, `EntityAIRange.java`, `EntityAIDruid.java`,
  `core/colony/buildings/AbstractBuildingGuards.java`,
  `core/colony/buildings/workerbuildings/BuildingGuardTower.java`,
  `core/colony/buildings/modules/GuardBuildingModule.java`,
  `core/colony/buildings/modules/settings/GuardPatrolModeSetting.java`,
  `core/colony/events/raid/RaidManager.java`.
- **Как охраняют.** У стражи есть задача (патруль/охрана/тренировка), режим патруля `AUTO` или `MANUAL`.
  В `MANUAL` стражник идёт по списку точек, которые задал игрок (`patrolTargets`), по кругу; в `AUTO` —
  к случайному зданию колонии. Есть отдельный патруль шахты (`patrolMine`) по завершённым узлам.
  При опасности — бой, погоня в пределах радиуса, отступление/лечение, «разбудить» спящего соседа,
  сбор по тревоге (`rally`).
- **Сон.** Стражник засыпает случайно (шанс ~1 к `20 + уровень_adaptability/2`, дальше режет
  исследование `SLEEP_LESS`), спит 2500–3000 тиков, восстанавливает здоровье; просыпается от урона,
  цели, болезни. То есть сон — не жёсткое расписание «ночь = спать», а состояние с прерыванием.
- **Ночь и рейды.** `RaidManager.onNightFall` — точка входа ночи; рейд планируется заранее
  (`determineRaidForNextDay`), между рейдами минимум/в среднем N ночей (`minimum…=10`, `average…=14`),
  сложность и разрушение блоков/дверей — конфиг; спавн ищется вне колонии, с проверкой точек.
- **У нас:** `mech/small/.../night/NightLeash` (вечер, радиус поселения, «за границу не выходим»,
  отказ с причиной), `NightLeashGate`, `GraphDefensePost` (`ACTIVITY_DEFEND`, выбор защитника,
  `defend`/`protect_spirit`), `DefensePlan`, `More7Rules` (ночь как условие мира), `GraphEvents`
  (`hostile_nearby`, `raid`, `night`), `LifeThreats`. Жёсткого расписания сна у нас нет — и это
  согласуется с правилом «ночь — условие мира, а не нужда духа».
- **Идея:** «пост охраны» как узел графа с точками патруля (данные поселения) и режимом AUTO/MANUAL;
  стражник не «работа», а `ACTIVITY_DEFEND`/`guard` с маршрутом. Точка: `GraphDefensePost`,
  `GraphEvents` `night`/`raid`. Цена: M.
- **Идея:** просыпаться/прерывать отдых по угрозе и звать соседа — у нас есть `hostile_nearby` и
  `DefensePlan`, но нет явного «разбудить и поднять». Точка: `More7Rules`/`GraphDefensePost`. Цена: S.
- **Идея:** патруль шахты (обход узлов) — дешёвая проверка «шахта цела/не завалило»; у нас это может быть
  отдельный узел `patrol_mine`/событие «шахта повреждена». Цена: M.
- **Идея:** рейд как событие с подготовкой (укрытие, двери, свет), а не только бой: распределить роли
  (защита/эвакуация/ремонт) через граф. Точка: `GraphEvents.raid`, `More4Rules`. Цена: L.

## 2. Интерфейс: карта, легенда, подсказки

- **Где:** `core/client/gui/map/WindowColonyMap.java`, `MinecraftMap.java`, `ColonySize.java`,
  `core/client/gui/WindowResourceList.java`, `WindowClipBoard.java`,
  `core/items/ItemColonyMap.java`, `ItemResourceScroll.java`,
  `core/client/gui/townhall/*`, `src/main/resources/assets/minecolonies/lang/manual_en_us.json`,
  `src/main/resources/assets/minecolonies/gui/**/*.xml`.
- **Карта.** Масштабируемая 2D-карта: маркеры зданий и жителей, размер колонии по числу жителей
  (`SMALL/MEDIUM/LARGE`), детализация пропадает при отдалении (`COLONY_DETAIL_SCALE = 0.3`), клик по
  маркеру открывает здание; карта колонии — предмет, карта района — экран.
- **Список ресурсов и доска.** `WindowResourceList` показывает нужное/доступное/в доставке по каждому
  предмету (`DONT_HAVE / NEED_MORE / IN_DELIVERY / HAVE_ENOUGH / NOT_NEEDED`) и снимок склада;
  `WindowClipBoard` — дерево запросов с раскрытием, «важное», переход к зданию. Подсказки — тултипы
  в XML/языковых файлах и руководство, а не отдельная логика.
- **У нас:** GUI нет; интерфейс — REST `/v1` (`rest/.../GraphRoutes`), журнал (`JournalService`,
  `BoardNotices`, `BoardReporter`) и `waitReason`. Стратег видит состояние только через REST/журнал.
- **Идея:** «одна страница колонии» в REST/журнале: сетка/список построек и духов с легендой (что значит
  цвет/статус), как карта MineColonies, но без GUI. Точка: `GraphRoutes` + `BoardNotices`; формат —
  JSON, пригодный и игроку, и стратегу. Цена: M.
- **Идея:** статус материала `в пути` (как `IN_DELIVERY`) в журнале/`StageMaterialNeed`, чтобы игрок и
  стратег не видели «не хватает», когда курьер уже несёт. Цена: S.
- **Идея:** подсказки-причины вместо «нет»: `waitReason` уже есть; привязать к каждой причине короткую
  строку «что делать» (как `Field LIST … NO_SEED`/жалобы MineColonies) — это дешёвый UI без экрана.
  Цена: S.

## 3. Конфиги и данные

- **Где:** `api/configuration/ServerConfiguration.java`, `CommonConfiguration.java`,
  `ClientConfiguration.java`; датапак: `src/datagen/generated/minecolonies/data/minecolonies/tags/block/
  pathblocks.json`, `.../researches/**/*.json`, `.../crafterrecipes/**`, `src/main/resources/data/
  minecolonies/{structure,structures}/**`; `api/util/constant/TagConstants.java` (`PATHING_BLOCKS`),
  `api/util/constant/PathingConstants.java`.
- **Как:** серверные настройки — секции gameplay/research/commands/claim/combat/permission/compatibility/
  pathfinding/request; отдельные величины (макс. дерево `maxTreeSize = 400`, лимит узлов пути
  `pathNodeLimitMultiplier`, рейды, защита колонии, лимит жителей). Датапак задаёт теги (например,
  какие блоки считать тропой), исследования — дерево JSON, рецепты крафтеров — JSON, схемы — паки
  структур. То есть «правила мира» в данных, код читает их.
- **У нас:** `mod/src/main/resources/data/theminespirit/board/graph.json` (веса, способы, здания),
  `vital.json`, `doors.json`, `movement/route.json`, схемы в `data/theminespirit/structure`, таблицы
  `DataTables`; константы — `CatalogLimits`/`BoardTuning`.
- **Идея:** всё, что игрок/владелец может захотеть покрутить (радиусы поиска, цены пути, материал
  засыпки, лимиты шахты), — в JSON с проверкой полей (`JsonResource.checkFields`) и сторожем, как
  `route.json` уже делает. Цена: S–M.
- **Идея:** тег «тропа» как данные (аналог `pathblocks`) — один тег, который читают и стройка дорожек,
  и цена пути; не хардкодить список блоков. Точка: `RouteRules`/`RouteWalk`. Цена: S.
- **Идея:** исследования/тиры как данные графа: `tiers` уже есть; не добавлять «дерево исследований»
  отдельной подсистемой, а выражать новыми узлами/условиями. Цена: M.

## 4. Сводка

| Область | MineColonies | У нас | Что взять |
|---|---|---|---|
| Охрана | патруль AUTO/MANUAL, сон с прерыванием, рейды по ночам | `NightLeash`, `GraphDefensePost`, события | пост/патруль как данные, побудка |
| Карта/UI | 2D-карта, легенда, список ресурсов, доска запросов | REST + журнал | «страница колонии» JSON, статус «в пути» |
| Конфиги | серверные секции + датапак | JSON графа/правил, константы | данные + проверка полей |
| Данные мира | теги, исследования, рецепты, паки схем | graph/vital/doors/route, схемы | тег тропы, тиры вместо новой подсистемы |
