# 26 — Восприятие и память: сканирование, поиск, опасности, память между сессиями

Источник: `todo.md` → «Восприятие» (`scan_blocks`, `scan_entities`, `sense_hazard`, `locate_block`, `locate_tree`,
`locate_container`, `look_at`, `remember`/`recall`) и «Автономия и обучение» → «память между сессиями: известные
ресурсы, точки (NBT/диск)». Идеи — `docs/research/llm-agents-skills.md` (Voyager: `KnowledgeBase` — долговременная
память без LLM, danger zones, resource points; `PerceptionSnapshot.toJson()` — структурированное наблюдение в промпт)
и `docs/research/npc-ai-commands.md` (Citizens: Blackboard-память `Memory` — общая персистентная память NPC). Код
сторонних проектов не копируется (лицензии — `docs/research/README.md`): только идеи, реализация своя.

Перед работой прочитай `AGENTS.md`, `docs/CONVENTIONS.md` (Быстрый путь: чистые проверки — в `core`, адаптер тонкий;
Структура — тип решается в одном месте; Производительность — тест на масштаб считает операции, не время; Ошибки — тихий
fallback запрещён), `docs/TESTING.md` (GameTest-пачки, `GameTestFixtures`, `SpiritRun`, шаблон `arena` 11×8×11, подлог
«тест обязан уметь краснеть», тест на масштаб считает операции), `docs/API.md` (контракт observe, лимиты), `docs/GIT.md`.
Код: `core/observation/{WorldObservation,ScanTuning,IdFilter,SectionCursor,SectionSlice,BlockBox,BlockCoord,EntityRef,
ContainerRef,ItemCount,ObservationHash,ObservationJson,ScanRadius}` (готово, не менять), `neoforge/{WorldScanner,
BlockLocator,ScanScript,LoadedWorld,SpiritEntity,SpiritScripts,SpiritTaskStore,SpiritBrain,ObserveJobs,SpiritBotGateway}`
(готово, не менять), `core/script/{CoreScripts,CatalogLimits,ScanParams,ParamSpec,ScriptArgs,ScriptSpec,ParamType,
ScriptCatalog,SpecCatalog}` (готово, не менять), `core/engine/{ScriptInstance,TaskRunner,TaskSnapshot,TaskSnapshotCodec,
NodeExecutor,ScriptExecutor,TaskProgress,RunStatus,ScriptStatus,ScriptRegistry,ScriptFactory,UnknownScriptException,
EngineCounters,SnapshotRestorer}` (готово, не менять), `core/plan/{ScriptCall,PlanParser,PlanValidator,TaskNode,
SequenceNode,RepeatNode,FallbackNode,ParallelNode,ConditionNode,ParallelMode,PlanJson,PlanValidationException}` (готово,
не менять), `gametest/{GameTestFixtures,SpiritRun,ScanGameTests}` (образец GameTest). Публикацию и перезапуск сервера
не делать.

**Зависимости.** Чеклисты 10, 13 и 14 уже в main, `LevelSafetyView` — тоже: части А, Б, В идут подряд без ожидания. Чеклист 20 (политики) не нужен, но `sense_hazard`
не дублирует рефлексы политик, а отдаёт факты (см. п. 3). Публикацию и перезапуск сервера не делать.

## Что должно получиться

Дух воспринимает мир порциями и помнит важное между сессиями:

- `scan_blocks(filter, radius)` — сканирование только блоков с фильтром (без сущностей/контейнеров); результат — список
  блоков по фильтру в радиусе. Используется ИИ для поиска ресурсов.
- `scan_entities(filter, radius)` — сканирование только сущностей с фильтром (без блоков/контейнеров); результат — список
  сущностей по фильтру в радиусе. Используется ИИ для поиска мобов.
- `sense_hazard(radius)` — факты об опасностях (лава, вода, обрыв) в радиусе; НЕ рефлекс, а данные для ИИ и тактики.
  Возвращает список опасностей с типом, позицией, расстоянием. Не дублирует политики чеклиста 20.
- `locate_block(filter, radius)` — поиск ближайшего блока по фильтру; результат — позиция блока или «не найдено».
- `locate_tree(species, radius)` — поиск ближайшего дерева по виду; результат — позиция ствола или «не найдено».
- `locate_container(radius)` — поиск ближайшего контейнера; результат — позиция контейнера или «не найдено».
- `look_at(x, y, z)` — посмотреть на точку; результат — описание того, что видно в точке (блок, сущность, контейнер).
  Используется ИИ для уточнения обстановки.
- `remember(name, x, y, z, type)` — запомнить точку с именем и типом (resource, danger, base); точка сохраняется в NBT
  духа и переживает перезапуск сервера.
- `recall(name)` — вспомнить точку по имени; результат — позиция и тип точки или «не найдено».
- `recall_all(type)` — вспомнить все точки типа (resource, danger, base); результат — список точек.
- `forget(name)` — забыть точку по имени.

Память между сессиями — версионированный NBT в духе `SpiritTaskStore`/`TaskSnapshotCodec`: ключ `SpiritMemory`,
версия 1, ограничение по объёму (см. «На согласование»). Точки хранятся списком: имя, тип, позиция. Известные ресурсы —
точки типа `resource` (руда, дерево, контейнер); опасности — точки типа `danger` (лава, обрыв); база — точки типа `base`.

## Общие точки с другими чеклистами (merge)

- `core/script/CoreScripts` — новые `spec(...)` после `scan` (чеклист 08); конфликт с чеклистами, добавляющими скрипты,
  — «оставить обе стороны», имена не занимать дважды.
- `core/script/CatalogLimits` — блок `PERCEPTION_*` в конце файла, после `NAV_ARRIVAL_TOLERANCE` (чеклист 24).
- `guards/CatalogDefaultGuardTests`, `core/script/ScriptCatalogTests` — строки новых скриптов под новые параметры.
- `neoforge/SpiritScripts` — новые записи в `table.put(...)` (там `LinkedHashMap`, лимита пар нет).
- `neoforge/SpiritEntity` — поле `memory` (NBT-ключ `SpiritMemory`), методы `remember`/`recall`/`forget`; конфликт с
  чеклистом 13 (`SpiritTaskStore`) — «оставить обе стороны».
- `neoforge/WorldScanner` — переиспользуется для `scan_blocks`/`scan_entities`; не дублировать.
- `neoforge/BlockLocator` — переиспользуется для `locate_block`; не дублировать.
- `core/observation/IdFilter` — переиспользуется для фильтров; не дублировать.
- `core/observation/ScanTuning` — переиспользуется для порций; не дублировать.
- `core/engine/TaskSnapshotCodec` — образец версионированного NBT для `MemoryCodec`; не дублировать.
- `core/policy/*`, `neoforge/policy/*` (чеклист 20) — `sense_hazard` не дублирует рефлексы политик, а отдаёт факты; политики
  чеклиста 20 используют те же данные об опасностях.
- Новые пакеты `core/perception/*`, `neoforge/perception/*`, ресурсы `data/theminespirit/perception/*.json`,
  `gametest/PerceptionGameTests*`, `gametest/MemoryGameTests*`.

## Часть А — ядро (`core/perception`, без Minecraft)

- [ ] 1. **`PerceptionQuery`** — запрос восприятия: `PerceptionQuery(type, filter, radius)`, где `type` — `BLOCKS`,
      `ENTITIES`, `HAZARDS`, `ALL`; `filter` — `IdFilter` (для `BLOCKS`/`ENTITIES`); `radius` — `ScanRadius`. Тесты:
      таблица ≥ 8 строк (каждый тип, фильтр с `*`, фильтр без `*`, границы радиуса); свойство «`type` всегда один из
      допустимых», «`radius` всегда в диапазоне `ScanRadius`».
- [ ] 2. **`PerceptionResult`** — результат восприятия: `PerceptionResult(type, blocks, entities, hazards, radius)`,
      где `blocks` — `List<ItemCount>`, `entities` — `List<EntityRef>`, `hazards` — `List<HazardRef>`. `HazardRef` —
      `record HazardRef(String type, BlockCoord pos, double distance)` (`type` — `LAVA`, `WATER`, `DROP`). Тесты: таблица
      ≥ 8 строк (пустой результат, только блоки, только сущности, только опасности, смесь); свойство «`type` всегда один из
      допустимых», «списки не содержат null».
- [ ] 3. **`HazardFacts`** — факты об опасностях: `HazardFacts(hazards)`, где `hazards` — `List<HazardRef>`; методы
      `hasLava()`, `hasWater()`, `hasDrop()`, `nearest()`, `count()`. Тесты: таблица ≥ 10 строк (нет опасностей, только лава,
      только вода, только обрыв, смесь, несколько опасностей одного типа); свойство «`nearest()` возвращает ближайшую опасность
      или null», «`count()` равен размеру списка».
- [ ] 4. **`LocateQuery`** — запрос поиска: `LocateQuery(type, filter, radius)`, где `type` — `BLOCK`, `TREE`,
      `CONTAINER`; `filter` — `IdFilter` (для `BLOCK`); `species` — `String` (для `TREE`, по умолчанию `any`); `radius` —
      `ScanRadius`. Тесты: таблица ≥ 8 строк (каждый тип, фильтр с `*`, фильтр без `*`, границы радиуса); свойство «`type`
      всегда один из допустимых».
- [ ] 5. **`MemoryStore`** — память между сессиями: `MemoryStore(points)`, где `points` — `List<MemoryPoint>`;
      `MemoryPoint` — `record MemoryPoint(String name, String type, BlockCoord pos)` (`type` — `RESOURCE`, `DANGER`, `BASE`).
      Методы: `remember(point)`, `recall(name)`, `recallAll(type)`, `forget(name)`, `all()`. Тесты: таблица ≥ 12 строк
      (пустая память, одна точка, несколько точек, повторное имя — замена, `recall` несуществующего — null, `recallAll` по типу,
      `forget` несуществующего — нет ошибки); свойство «`recallAll` возвращает только точки заданного типа», «после `forget`
      точка не находится».
- [ ] 6. **`MemoryCodec`** — кодек памяти (версионированный NBT, по образцу `TaskSnapshotCodec`): `encode(MemoryStore) →
      String`, `decode(String) → MemoryStore`, версия 1, ограничение по объёму (см. «На согласование»). Тесты: round-trip
      кодека, версия NBT, повреждённые данные — ошибка, а не тихий fallback; свойство «`decode(encode(store))` равен `store`».

## Часть Б — адаптер (после слияния чеклистов 10, 13, 14)

- [ ] 7. **`PerceptionScript`** — скрипт восприятия: `PerceptionScript(spirit, PerceptionQuery)`; `tick()` — порциями через
      `WorldScanner.Session` (для `BLOCKS`/`ENTITIES`/`ALL`) или через `LevelSafetyView` (для `HAZARDS`); `saveState()`/
      `restoreState()` — прогресс курсора. Тесты: таблица ≥ 8 строк (каждый тип, порции, отмена); свойство «за один тик не
      больше `ScanTuning.SLICES_PER_TICK` срезов».
- [ ] 8. **`LocateScript`** — скрипт поиска: `LocateScript(spirit, LocateQuery)`; `tick()` — порциями через
      `BlockLocator.Search` (для `BLOCK`/`TREE`) или через `WorldScanner.Session` (для `CONTAINER`); `saveState()`/
      `restoreState()` — прогресс курсора. Тесты: таблица ≥ 8 строк (каждый тип, порции, отмена); свойство «за один тик не
      больше `ScanTuning.SLICES_PER_TICK` срезов».
- [ ] 9. **`LookAtScript`** — скрипт взгляда: `LookAtScript(spirit, x, y, z)`; `tick()` — чтение клетки через
      `LevelSafetyView` (без загрузки чанков); результат — описание клетки (блок, сущность, контейнер). Тесты: таблица ≥ 6
      строк (пустая клетка, блок, сущность, контейнер, незагруженный чанк — пропуск); свойство «чтений блоков ≤ 1 за тик».
- [ ] 10. **`MemoryScript`** — скрипт памяти: `MemoryScript(spirit, action, name, type, x, y, z)`, где `action` —
       `RECALL`, `REMEMBER`, `FORGET`, `RECALL_ALL`; `tick()` — чтение/запись NBT духа (`SpiritMemory`); `saveState()`/
       `restoreState()` — прогресс. Тесты: таблица ≥ 8 строк (каждое действие, точка есть, точки нет); свойство «`RECALL`
       несуществующего — null», «`REMEMBER` перезаписывает точку с тем же именем».
- [ ] 11. **Регистрация** — записи в `SpiritScripts` (см. «Общие точки»); новые скрипты появляются в каталоге `/v1/capabilities`.
- [ ] 12. **Команды** — `/spirit memory <духи> recall <name>|all <type>|forget <name>` (тонкий класс команды по образцу зоны из
      чеклиста 20, п. 19). Тесты: таблица ≥ 6 строк (каждая команда, несуществующая точка); свойство «`recall all` возвращает
      только точки заданного типа».

## Часть В — GameTest (`gametest/PerceptionGameTests`, `gametest/MemoryGameTests`)

Шаблон `arena` 11×8×11, всё внутри площадки, духи удаляются в конце (`GameTestFixtures.reset`), без `sleep`, время по тикам
духа (`SpiritRun`). Каждый тест — своя пачка (`batch = ...`). Сценарии ≤ 200 тиков, кроме оговорённых.

- [ ] 13. **`scan_blocks` и `scan_entities`** (пачка `perception_scan`): (а) пол 9×9 с рудами: `scan_blocks` с фильтром
      `*_ore` — в результате только руды; (б) мобы на площадке: `scan_entities` с фильтром `minecraft:cow` — в результате
      только коровы; (в) `sense_hazard` — лава в 4 блоках: в результате есть `LAVA`; (г) `sense_hazard` — обрыв в 3 блоках:
      в результате есть `DROP`. Подлоги: фильтр не применяется (в результате все блоки); `sense_hazard` возвращает рефлекс
      вместо фактов.
- [ ] 14. **`locate_block`, `locate_tree`, `locate_container`** (пачка `perception_locate`): (а) блок в 5 блоках:
      `locate_block` — позиция блока; (б) дерево в 6 блоках: `locate_tree` — позиция ствола; (в) сундук в 4 блоках:
      `locate_container` — позиция сундука; (г) блок не найден: `locate_block` — «не найдено». Подлоги: поиск не порциями
      (читает весь куб за тик); фильтр не применяется.
- [ ] 15. **`look_at`** (пачка `perception_look`): (а) блок в 3 блоках: `look_at` — описание блока; (б) моб в 2 блоках:
      `look_at` — описание моба; (в) сундук в 1 блоке: `look_at` — описание контейнера; (г) незагруженный чанк: `look_at` —
      пропуск, без загрузки чанка. Подлоги: `look_at` загружает чанк (`getChunk` вместо `getChunkNow`).
- [ ] 16. **`remember`/`recall`/`forget`** (пачка `memory_points`): (а) `remember` точки: `recall` — позиция и тип; (б)
      `recall_all` по типу — только точки этого типа; (в) `forget` — точка не находится; (г) сохранение: точки переживают
      `saveState/restoreState` (round trip без мира). Подлоги: `remember` не сохраняет в NBT; `recall` не читает из NBT.
- [ ] 17. **Тест на масштаб** (пачка `perception_scale`): 20 духов, 100 тиков — (1) чтений блоков ≤ 20 × 100 ×
      `ScanTuning.SLICES_PER_TICK` × 256; (2) запросов сущностей ≤ 20 × 100; (3) вызовов `WorldScanner.Session.step` ≤ 20 × 100.
      Числа в самом тесте, не время. Подлог: снять ограничение порций — чтений больше границы.

## Красными до реализации

Все тесты `core/perception/*` (классов нет), таблицы и свойства пп. 1–6, JUnit-тесты пп. 7–12, все GameTest пп. 13–17
(ни `PerceptionScript`, ни `LocateScript`, ни `MemoryScript` нет: дух не сканирует, не ищет, не помнит). Назови красные до
прогона. Для `fix:`-коммитов — `scripts/prove-red.sh`; GameTest скрипт не проверяет — подлоги вручную. Пределы в тестах —
литералы с проверкой равенства production-константе (`assertEquals(4, ScanTuning.SLICES_PER_TICK)` — образец из
фермерского R4); сравнение литерала с литералом — не проверка.

## Стоимость в тике

- Сканирование — порциями ≤ `ScanTuning.SLICES_PER_TICK` срезов за тик; без загрузки чанков (`getChunkNow`).
- Поиск — порциями ≤ `ScanTuning.SLICES_PER_TICK` срезов за тик; без загрузки чанков.
- `look_at` — ≤ 1 чтение блока за тик; без загрузки чанков.
- Память — чтение/запись NBT при вызове скрипта; в тике 0 чтений мира.
- Тест на масштаб (п. 17): числа операций, не время.

## Завершение

- [ ] 18. `./gradlew test` и `./gradlew runGameTestServer` зелёные (GameTest-сервер — только под общим замком:
   `flock /tmp/mc-gametest.lock ./gradlew runGameTestServer`). Коммиты по пунктам (`test:`/`feat:`/`refactor:`),
   к каждому `fix:` с JUnit-тестом — вывод `scripts/prove-red.sh <коммит>`. `todo.md` не править. Отчёт: хэши;
   сколько тестов прошло и упало; красные до реализации; время самого медленного GameTest и причина; что не сделано и
   почему.

## Не входит

`explore` (поиск по чанкам, кэш известности мира — чеклист 24б); библиотека навыков (retrieval топ-k по описанию —
позже); верификация результата задачи (postcondition — позже); авто-куррикулум (warm-up, до/недо-выполненные задачи —
позже); долговременная память без LLM (danger zones, resource points, lessons — частично здесь, полная — позже);
лизинг/epoch ответов LLM (latest-wins — позже); MCP-обёртка над bridge (P6); память на диске (NBT — здесь, диск —
позже); кэш известности мира (24б); память семьи (социум — позже).

## На согласование

1. **Ограничение по объёму памяти.** Предварительно: ≤ 64 точки на духа (по 16 на тип: `RESOURCE`, `DANGER`, `BASE`).
   Цена завышения — больше NBT на духа; цена занижения — стратег теряет точки при переполнении.
2. **Типы точек памяти.** Предварительно: `RESOURCE` (руда, дерево, контейнер), `DANGER` (лава, обрыв), `BASE` (дом,
   склад). Цена расширения — больше типов в данных; цена сужения — стратег не отличает руду от дерева.
3. **`sense_hazard` — факты, не рефлекс.** Предварительно: `sense_hazard` возвращает данные об опасностях, не двигает духа.
   Рефлексы — политики чеклиста 20. Цена альтернативы (рефлекс в скрипте) — конфликт с политиками, двойное движение.
4. **`look_at` — описание клетки.** Предварительно: `look_at` возвращает блок/сущность/контейнер в точке, не картинку.
   Цена альтернативы (картинка) — дороже для ИИ, не текстовый контракт.
5. **Порции сканирования.** Предварительно: `ScanTuning.SLICES_PER_TICK = 4` (общее с чеклистом 08). Цена изменения —
   другой темп сканирования, другой тест на масштаб.

## Поправки главного агента (13.10) — обязательны, при расхождении с текстом выше действуют они

- П1. **Не дублировать `scan`.** `scan(radius, filter)` уже есть (`ScanParams`, `ScanScript`, `WorldScanner`).
  `scan_blocks` и `scan_entities` — тонкие записи каталога над тем же `ScanScript` с ограничением, что собирать (только
  блоки / только сущности); второго сканера и второго курсора не писать. Нужна ли `PerceptionQuery` с типом `ALL` —
  нет: `ALL` и есть `scan`.
- П2. **Объём памяти:** 16 точек на тип, всего 48 (3 типа). Переполнение типа — ошибка скрипта `remember`
  («память типа X заполнена: забудь точку»), не тихое вытеснение старой точки. Повтор имени — замена (не считается новой).
- П3. **Точка помнит измерение:** `MemoryPoint(name, type, dimension, pos)`; `dimension` — измерение духа в момент
  `remember`. `recall` из другого измерения возвращает точку с её измерением, решает стратег.
- П4. **Память живёт в поле духа**, NBT читается при загрузке духа и пишется при сохранении (как `SpiritTaskStore`);
  скрипты памяти работают с полем, NBT в тике не трогают. `MemoryScript` завершается за один тик, `saveState` ему не нужен.
- П5. **Повреждённая память при загрузке** не роняет загрузку духа: память пустая, громкая запись в лог с причиной
  и счётчик в `EngineCounters` (если там есть подходящий — иначе новый). `MemoryCodec.decode` при этом бросает
  исключение — его ловит адаптер в одном месте. Тихий fallback в самом кодеке запрещён.
- П6. **Параметр `type` в API — строчными** (`resource`, `danger`, `base`), как остальные перечисления каталога;
  перевод в enum — в одном месте ядра.
- П7. **JUnit-тесты адаптерных скриптов (пп. 7–10)** — только на то, что вынесено в ядро (правила порций, разбор
  параметров, логика памяти); поведение в мире — GameTest пп. 13–17. Таблицы ради числа строк не раздувать.
- П8. **Масштаб (п. 17):** считать операции существующими счётчиками сканера (как в тесте на масштаб чеклиста 08), не
  вводить параллельный учёт. Чтобы не было гонки тиков, духи стоят на месте, проверка — после фиксированного числа тиков
  духа (`SpiritRun`).
- П9. Пункты «На согласование» 1, 3, 4, 5 — приняты, 2 — с поправками П2 и П6.
