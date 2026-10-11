# 10 — P2: скрипты scan и collect_drops, общая основа добычи

Источник: `todo.md` → «P2 — добыча ресурсов», «Карта действий духа» → «Восприятие» (`scan`) и «Добыча» (`collect_drops`),
«Инвентарь, ценности и поход» (выпавшее подбираем), «Заимствование у других проектов» → «Навигация».
Идеи — `docs/research/navigation-movement.md` (`MineProcess`: подбор дропа; `WorldScanner`/`CachedWorld`: поиск без
загрузки чанков; `MovementProgressChecker`: тайм-аут по прогрессу). Только идеи: код Baritone/altoclef не копируется
(LGPL, `docs/research/README.md` → «Главное правило: лицензии»).

Перед работой прочитай `AGENTS.md`, `docs/CONVENTIONS.md` (Быстрый путь, Структура, Производительность, Ошибки),
`docs/TESTING.md` (целиком), `docs/GIT.md`, `docs/API.md` (`observe`, `capabilities`). Код: `neoforge/{GotoScript,
WorldScanner,SpiritScripts,SpiritEntity}`, `core/script/{CoreScripts,CatalogLimits,ScriptArgs}`, `core/movement/*`,
`core/observation/{ScanRadius,WorldObservation}`, `gametest/SpiritGameTests`. Публикацию и перезапуск сервера не делать.

## Что должно получиться

1. `scan` обновляет наблюдение духа: в плане `scan(radius=16, filter=["*_ore"])` дух просматривает куб вокруг себя
   только по уже загруженным чанкам, за несколько тиков, и кладёт результат в `spirit.observation()`. В `blocks`
   попадают только блоки по фильтру; сущности и контейнеры — без фильтра.
2. `collect_drops` подбирает выпавшие предметы: дух подходит к каждой кучке в радиусе, кладёт её в инвентарь,
   и скрипт завершается успехом, когда подходящих предметов вокруг не осталось. Нечего подбирать — успех сразу.
   Инвентарь полон — провал с названием предмета; предметы не выбрасываются и не пропадают.
3. Основа для чеклистов 11 и 12 (часть А): поиск блоков порциями, подход к цели, ломка блока с учётом инструмента,
   времени и дропа. Ничего из основы игроку не видно, пока её не вызовут `chop_tree`/`mine_vein`/`gather`.

Размер: часть А ≈ 1,5 ч, Б ≈ 0,7 ч, В ≈ 1 ч. Часть А (пп. 1–8) коммитится первой и сообщается главному агенту:
после её слияния в `main` стартуют адаптерные пункты 11 и 12. Если исполнитель не укладывается — Б и В
отдельным заданием.

## Общие точки с другими чеклистами (merge)

- `core/script/CatalogLimits` — добавить константы блоком сразу после `SCAN_RADIUS_MAX` (только `DROP_RADIUS_*`).
- `core/script/CoreScripts` — править только описание `collect_drops` (строки его `spec(...)`); `scan` уже описан верно.
- `neoforge/SpiritScripts` — две новые строки в `Map.of(...)` **перед** `"goto"`.
- `neoforge/SpiritEntity` — поле `perception` и два метода (`observation()`, `updateObservation(...)`); чеклист 13
  правит другие методы того же класса.
- `core/script/ScriptArgs` и `ParamSpec` — только добавление методов (пункт 3); `neoforge/GotoScript` правит только этот чеклист.
- Конфликт в этих файлах решается «оставить обе стороны»; чужие строки не переписывать и не переформатировать.

## Часть А — основа (для 10, 11, 12)

- [ ] 1. **`IdFilter`** — `core/observation/IdFilter`: список glob-шаблонов id блока или предмета (`*_log`,
      `minecraft:oak_log`, `*_ore`, `oak_*`). Правила: `*` — любая последовательность (в т.ч. пустая), других
      спецсимволов нет; id без `:` читается как `minecraft:<id>`; пустой список — совпадает со всем; шаблон
      вне `[a-z0-9_:./*-]` или пустой — `IllegalArgumentException` с шаблоном в тексте.
      Тесты (`core/observation/IdFilterTests`, `IdFilterProperties`): таблица `@ParameterizedTest` (≥ 15 строк:
      `*_log` ~ `minecraft:oak_log`, но не `minecraft:log_cabin`; `iron_ore` ~ `minecraft:iron_ore`; `*iron*`),
      свойства: шаблон без `*` совпадает только с самим собой; `*` совпадает с любым id; `P*` совпадает ровно с id,
      начинающимися на `P`.
- [ ] 2. **`BlockBox` и `SectionCursor`** — `core/observation`. `BlockBox(BlockCoord min, BlockCoord max)`;
      `SectionCursor.of(BlockBox, BlockCoord center)` режет куб на срезы по секциям 16×16×16 (`SectionSlice`:
      координаты секции и границы среза внутри куба), сортирует по расстоянию от `center` до ближайшей точки среза
      (срез с центром — первым; ничья — по `x, y, z`), `next(int maxSlices)` выдаёт порцию и сдвигает курсор.
      Тесты: пример (куб в одной секции — один срез; куб через границу секций — два); свойства на случайных
      кубах: срезы не пересекаются и покрывают куб ровно (сумма объёмов = объём куба), порядок не убывает по
      расстоянию, порция ≤ `maxSlices`, курсор опустошается за `ceil(N / maxSlices)` вызовов.
- [ ] 3. **Параметры скриптов для 10–12** — `core/script/ScriptArgs`: `optionalStringList` (отсутствует →
      `Optional.empty()`; неверный тип — `IllegalArgumentException` в стиле `requireInt`) и `requireBoolean`.
      `core/script/ParamSpec`: перегрузки `bool(name, required, boolean defaultValue)` и
      `enumeration(name, required, values, String defaultValue)` (сейчас у них нет умолчания, а чеклистам 11 и 12
      нужны `replant` и `species`/`ore` как enum со значением по умолчанию — ИИ видит допустимые значения в
      `capabilities`). Убедись, что `PlanValidator` подставляет эти умолчания и отвергает значение вне списка.
      Тесты: `ScriptArgsProperties`, `ParamSpecTests` (умолчание вне списка enum — ошибка), `PlanValidatorRulesTests`.
- [ ] 4. **`BreakTiming` и `ToolChoice`** — `core/mining/`.
      `BreakTiming.ticks(double hardness, double toolSpeed, boolean correctForDrops)` = `max(1, ceil(hardness ×
      (correct ? 30 : 100) / toolSpeed))`; `hardness < 0` (неразрушимый) и `toolSpeed ≤ 0` — `IllegalArgumentException`.
      `ToolChoice.best(List<ToolOption(slot, speed, correctForDrops)>)` → `Choice(slot, speed, dropsAllowed)`:
      из `correctForDrops` берётся максимальная скорость (ничья — меньший слот); инструмент, не быстрее руки (1.0),
      не выбирается — `slot = HAND (-1)`; нет ни одного `correctForDrops` и блок требует инструмента — `HAND`
      с `dropsAllowed = false`. Блок, не требующий инструмента, считается «правильным» для любого (так ведёт себя
      игрок), поэтому адаптер передаёт `correctForDrops = !state.requiresCorrectToolForDrops() ||
      tool.isCorrectToolForDrops(state)`.
      Тесты — таблица по ванили (красными до реализации): бревно (твёрдость 2.0) рукой 60 тиков, железным топором
      (скорость 6) 10; камень (1.5) деревянной киркой (скорость 2) 23, рукой без кирки 150; твёрдость 0 — 1 тик.
      `ToolChoice`: примеры (кирка и топор на камень → кирка), свойство «выбранный слот имеет максимальную скорость
      среди правильных».
- [ ] 5. **`DropRoute`** — `core/pickup/`. `DropRef(String id, String item, int count, double x, double y, double z,
      boolean pickable)`; `DropRoute.next(double fromX, fromY, fromZ, List<DropRef> drops, Set<String> skipped)` →
      ближайшая `pickable` не из `skipped` (ничья — по `id`), иначе `Optional.empty()`.
      Тесты: примеры; свойства: результат — минимум расстояния среди допустимых; порядок списка не влияет.
- [ ] 6. **`Approach`** — `neoforge/Approach`: подход духа к точке или сущности, вынесенный из `GotoScript`
      (`PathRefreshPolicy` + `StuckWatcher` + `GotoTuning.timeoutTicks`). Интерфейс: `begin(Vec3 target, double
      arrival)`, `begin(Entity target, double arrival)`, `tick()` → `MOVING | ARRIVED | STUCK | TIMEOUT`, `cancel()`.
      `GotoScript` переписывается на него (`refactor:`), поведение и тексты причин не меняются: три существующих
      GameTest `goto` зелёные без правок. Стоимость: путь пересчитывается политикой `PathRefreshPolicy`, не каждый тик.
- [ ] 7. **`BlockLocator`** — `neoforge/BlockLocator` и `BlockSets`. `BlockSets.resolve(IdFilter)` один раз превращает
      шаблоны в `Set<Block>` по `BuiltInRegistries.BLOCK`. `BlockLocator.search(level, center, radius, Set<Block>)`
      возвращает `Search`: `step(int maxSlices)` → найденные `BlockPos` этой порции (ближние срезы первыми), `done()`,
      счётчики `examinedPositions()`, `skippedChunks()`. Чанки — только `level.getChunk(x, z, ChunkStatus.FULL, false)`
      (незагруженный пропускается, не грузится). Секция без блоков из набора пропускается целиком
      (`hasOnlyAir()`, `maybeHas(...)`), позиции внутри не читаются.
- [ ] 8. **`BlockBreaker`** — `neoforge/BlockBreaker`: ломка одного блока духом. `begin(BlockPos)` →
      `STARTED` или `REFUSED(причина)`: воздух, неразрушимый (`getDestroySpeed < 0`), дальше `REACH` = 4.5 от глаз
      до центра блока (вызывающий подходит ближе), чанк не загружен. `tick()` → `BREAKING | BROKEN | GONE` (блок
      исчез сам). Инструмент выбирается `ToolChoice` по инвентарю духа; срок — `BreakTiming`; по готовности блок
      убирается без дропа (`destroyBlock(pos, false, spirit)`), дроп создаётся отдельно через
      `Block.dropResources(...)` с выбранным инструментом и только при `dropsAllowed`; износ инструмента −1 за блок,
      сломанный предмет исчезает из слота. Стадии трещин `destroyBlockProgress(spirit.getId(), pos, stage)` ведутся
      по ходу и **снимаются** (`-1`) при `BROKEN`, `GONE` и `cancel()`. Опыт не подбирается (вне задачи).

## Часть Б — скрипт scan

- [ ] 9. **Сессия скана и скрипт.** `WorldScanner` разбирается на `WorldScanner.Session` (создание: уровень, `botId`,
      центр, `ScanRadius`, `IdFilter` блоков, инвентарь): `step(int maxSlices)` просматривает порцию срезов
      `SectionCursor` по загруженным чанкам (пустая секция пропускается; контейнеры чанка берутся один раз на чанк;
      сущности — одним запросом в `finish()`), `done()`, `result()` → `Scan`. Старый `WorldScanner.observe(...)`
      остаётся обёрткой «шагать до конца» с пустым фильтром — существующие тесты зелёные без правок.
      `ScanScript` (`neoforge/`): центр — блок духа на старте; каждый тик `step(ScanTuning.SLICES_PER_TICK)`;
      по завершении `spirit.updateObservation(scan.observation())` и `SUCCEEDED`; удаление духа — `FAILED`
      «дух исчез во время скана». Отмена не пишет частичное наблюдение. Параметры — из `ScriptArgs`:
      `radius` (подставляет валидатор), `filter` (`optionalStringList`). `SpiritEntity.observation()` →
      `Optional<WorldObservation>`; наблюдение не сохраняется в NBT (это кэш: чеклист 13 его не трогает).
      Константы — `core/observation/ScanTuning`: `SLICES_PER_TICK = 4` (предварительно; ≤ 16 384 чтений блоков за тик).
      Регистрация `"scan"` в `SpiritScripts`.
- [ ] 10. **GameTest скана** (`gametest/ScanGameTests`, шаблон `empty`, площадка строится в тесте, как в
      `SpiritGameTests`): (а) пол 9×9, в нём 3 блока `iron_ore` и 20 `coal_ore`, дух в центре; план `scan(radius=8,
      filter=["*_ore"])`: через ≤ 60 тиков `SUCCEEDED`, `spirit.observation()` содержит `iron_ore=3`, `coal_ore=20`
      и не содержит `stone`; (б) тот же мир без фильтра содержит `stone`; (в) радиус 32 в пустоте: задача завершается,
      а **за один тик прочитано ≤ 16 384 позиций** (счётчик сессии, не время), пустые секции не читаются;
      (г) скан у края загруженной области не увеличивает число загруженных чанков (по образцу существующего
      `scan_Radius32NearUnloadedChunks_...`); (д) отмена на 2-м тике: `observation()` остаётся прежним.

## Часть В — скрипт collect_drops

- [ ] 11. **`DropCollector`** — `neoforge/DropCollector(spirit, IdFilter items, BlockCoord center, int radius,
      PickupTuning)`; `tick()` → `RUNNING | DONE | FAILED`, `failureReason()`, `collected()`. Цикл: раз в
      `REFRESH_TICKS` один запрос `getEntitiesOfClass(ItemEntity.class, AABB вокруг center)` → `DropRoute.next` →
      `Approach` к кучке → при расстоянии ≤ `PICKUP_REACH` и без задержки подбора `inventory.addItem(...)`
      (остаток остаётся в сущности). Кучка, к которой не дошли (`STUCK`/`TIMEOUT`), попадает в `skipped`.
      Полный инвентарь — `FAILED` «инвентарь полон: не помещается <предмет>» (предметы не выбрасываются).
      Остались только `skipped` — `FAILED` «не дотянулся до N предметов». Нет подходящих — `DONE` сразу. Общий
      потолок `TOTAL_TIMEOUT_TICKS`. Радиус считается от `center` (точка старта), а не от текущей позиции духа.
      `core/pickup/PickupTuning` (запись с данными, не функциями): `REFRESH_TICKS = 10`, `PICKUP_REACH = 1.5`,
      `TOTAL_TIMEOUT_TICKS = 2400` (всё предварительно).
- [ ] 12. **Скрипт `collect_drops`** — `neoforge/CollectDropsScript`: параметры `radius` (`CatalogLimits.DROP_RADIUS_MIN
      = 1`, `DROP_RADIUS_MAX = 16`, по умолчанию 8 — предварительно) и `filter` (`ParamSpec.textList`, `IdFilter`
      по id предмета; пусто — всё). Обновить описание в `CoreScripts`, зарегистрировать `"collect_drops"`.
      Тесты ядра: в `ScriptCatalogTests` — параметры `radius` и `filter` (границы, умолчание, необязательность);
      список id в каталоге не меняется (скрипт уже в нём).
- [ ] 13. **GameTest `collect_drops`** (`gametest/CollectDropsGameTests`): (а) три кучки `cobblestone` (5+3+1) в
      радиусе 6 на полу 9×9: `SUCCEEDED` ≤ 200 тиков, в инвентаре 9 `cobblestone`, предметных сущностей этого
      типа не осталось; (б) `filter=["*_log"]` при кучках брёвен и булыжника: брёвна подобраны, булыжник на месте;
      (в) инвентарь забит неподходящими стопками: `FAILED` с «инвентарь полон», сущность предмета жива, суммарное
      число предметов (инвентарь + мир) прежнее; (г) кучка на столбе высотой 4 вне досягаемости: остальные
      подобраны, итог `FAILED` «не дотянулся», время ≤ 120 тиков (определяется `StuckWatcher`, не тайм-аутом
      маршрута); (д) подбирать нечего: `SUCCEEDED` за ≤ 2 тика; (е) кучка за пределами `radius` не трогается.
      Предметы создаются `helper.spawnItem(...)` с нулевой задержкой подбора; тест считает только предметы своей области.
- [ ] 14. **Тест на масштаб.** `DropCollector` за прогон в п. 13а делает ≤ `ceil(тиков / REFRESH_TICKS) + 1` запросов
      сущностей (счётчик в коллекторе, доступный пакету; не время); `BlockLocator` по пустой области (воздух)
      читает 0 позиций, по области с одним блоком искомого типа — ≤ `4096 × число секций с этим блоком`.

## Красными до реализации

`IdFilterTests/Properties`, `SectionCursor*`, `BreakTimingTests`, `ToolChoiceTests`, `DropRouteTests`,
`ScriptArgs`, `ParamSpecTests`, `ScriptCatalogTests` (параметры `collect_drops`), все GameTest пп. 10, 13, 14
(скриптов `scan`/`collect_drops` в реестре нет — задача падает «Неизвестный скрипт»). GameTest `goto` из
`SpiritGameTests` до и после п. 6 зелёные — это страховка рефакторинга. До прогона назови красные в отчёте,
после — докажи подлогом, что новые тесты умеют краснеть (например, `SLICES_PER_TICK = 40` краснит п. 10в).

## Стоимость в тике

- Скан: ≤ `SLICES_PER_TICK` срезов за тик, радиус 32 — ≤ 125 срезов, ≤ ~32 тиков; обхода мира целиком в один тик нет.
- Подбор: один запрос сущностей раз в 10 тиков, путь — по `PathRefreshPolicy`.
- Ломка: один блок на дух, стадия трещины обновляется только при смене стадии.
- `BlockLocator`: пропуск секций по палитре, незагруженные чанки не грузятся.

## Завершение

- [ ] 15. `./gradlew test` и `./gradlew runGameTestServer` зелёные. Коммиты по пунктам (`test:` перед `feat:` или в одном
      коммите, хук требует тест в `feat:`/`fix:`). `todo.md` не править — строку закрывает главный агент.
      Отчёт: хэши коммитов; сколько тестов прошло и упало; какие были красными до реализации; время самого
      медленного GameTest; что не сделано. Отдельной строкой — хэш последнего коммита части А (пп. 1–8).

## Не входит

Опыт-шары; подбор «инстинктом» без задачи (пассивный магнит); сортировка/выброс при переполнении инвентаря
(политика «дорого/дёшево» — отдельно); сообщение планировщику о пропущенных незагруженных чанках (в
`WorldObservation` пока нет поля).

## Замечания ревью (коммиты f2fc703..8354ec4)

Принято: `IdFilter`, `BlockBox`/`SectionCursor`, `BreakTiming`/`ToolChoice` (таблица по ванили), `DropRoute`,
`Approach` (порядок проверок `GotoScript` сохранён, тесты `goto` без правок), `BlockLocator` (только `FULL` без
загрузки, пропуск секций по палитре), `BlockBreaker` (дроп с инструментом, износ, снятие трещин при
`BROKEN/GONE/cancel`), `WorldScanner.Session` (≤ 4 среза за тик, контейнеры раз на чанк, сущности одним запросом),
`ScanScript` (отмена не пишет наблюдение), счётчик запросов `DropCollector`. Коммиты по пунктам.

- [ ] R1. **Дефект: дух подбирает чужое и ждёт вечно.** `DropCollector.java:96-102,161-163`. (а) Кучка с
      `ItemEntity.getTarget()` — вещь, которую игра адресовала конкретному игроку (ванильный `playerTouch` её другим не
      отдаёт), — дух забирает себе. (б) Кучка с бесконечной задержкой подбора (`setPickUpDelay(32767)`: витрины,
      декорации карт) — `hasPickUpDelay()` навсегда, дух стоит рядом до общего потолка 2400 тиков и только тогда
      `FAILED`, остальные кучки не подобраны. Как надо: в `refresh` отбрасывать кучки с `getTarget() != null` и не
      равным UUID духа; ожидание задержки у кучки ограничить `PickupTuning.MAX_DELAY_WAIT_TICKS` (например, 40 — больше
      ванильной задержки броска), дальше — в `skipped`. GameTest (красный сейчас): кучка с `setPickUpDelay(32767)` и
      обычная — обычная подобрана, итог `FAILED` «не дотянулся до 1…» ≤ 120 тиков духа; кучка с `setTarget(<чужой UUID>)`
      не тронута.
- [ ] R2. **Тест/возможный дефект 07: «дух за габаритами структуры стоит неподвижно».** `docs/TESTING.md:16` объясняет
      `arena` и пачку на тест тем, что сущности тикают только в чанках структуры. Но дух сам держит тикающие билеты
      (`SpiritChunkTickets`, `forceChunk(..., true, true)`) — за пределами структуры он обязан тикать. Если утверждение
      верно, это дефект чеклиста 07 в игре (дух, ушедший от игрока, замирает); если неверно — пачки и правило в
      TESTING.md держатся на ложной причине. Как надо: GameTest — дух на краю площадки идёт `goto` на 24 блока за её
      пределы: `tickCount` растёт, задача `SUCCEEDED`. Зелёный — исправить абзац в TESTING.md (истинная причина
      пачек); красный — отдельный fix в `SpiritChunkTickets`, тест тот же.
- [ ] R3. **API основы: результат `begin` — флаг и nullable-строка.** `BlockBreaker.java:32` `Begin(boolean started,
      String refusal)` + `isRefused()`. Остальная основа и ядро 12 отвечают закрытыми типами (`BreakSafety.Safe/Unsafe`,
      `GatherPolicy.Allowed/Refused`, `MiningPlanner.Next/Exhausted`); на этот API лягут 11, 12 и 16 — сменить его
      потом дороже. Как надо: `sealed interface Begin permits Started, Refused(String reason)`. Тесты в
      `BlockBreakerGameTests` — через `instanceof Refused refused` и `refused.reason()`.
- [ ] R4. **Слияние с `main` (ядро 12 и сторожа уже там).** Ветка от `13dbedc`; после слияния:
      (а) `guards/CatalogDefaultGuardTests` покраснеет на `collect_drops.filter` (необязательный без умолчания) —
      добавить исключение с причиной, как у `scan.filter` («пусто — все предметы, это не подставленное значение»);
      (б) `ParamSpec.enumeration(name, required, values, defaultValue)` уже есть в `main` (12) — оставить одну копию,
      лишнюю пустую строку после `bool(...)` убрать; (в) сторож `TaskReferenceCommentGuardTests` — нарушений нет.
      Прогнать полный `./gradlew test` и `runGameTestServer` на слитом дереве; числа — в отчёт.
