# 27 — Результаты скриптов доходят до ИИ-стратега: общий канал итога и результаты восприятия

Источник: `todo.md` → «Общий канал итога успешного скрипта `ScriptInstance.resultMessage()` для REST и чата (сейчас
`ChopTreeScript.summary()` и INFO в лог; после перезагрузки итог теряет оставленные брёвна и саженцы)» и «Восприятие
(ревью 26): результаты `scan_blocks`/`sense_hazard`/`locate_*`/`look_at`/`recall` лежат в духе
(`SpiritEntity.perceptionResult()`/`finding()`) — довести до REST (observe или итог задачи) и стратега: whitelist
`agents/strategist/policy.json`, `docs/API.md`. Связано с «общим каналом итога» `ScriptInstance.resultMessage()`».

Перед работой прочитай `AGENTS.md`, `docs/CONVENTIONS.md` (Быстрый путь — чистые проверки в `core`, адаптон тонкий;
Структура — тип блока решается в одном месте; Производительность — тик стоит дорого; Ошибки — тихий fallback
запрещён), `docs/TESTING.md` (JUnit и GameTest, имена тестов, сторожа, время тестов), `docs/GIT.md`, `docs/API.md`
(контракт задач и событий). Код: `core/engine/{ScriptInstance,TaskRunner,TaskSnapshot,TaskSnapshotCodec,TaskProgress,
RunStatus}` (готово, не менять), `core/task/{TaskRecord,TaskRegistry,TaskReconciler,TaskEvent,TaskEventDiff,TaskJson,
TaskState,SpiritReading}` (готово, не менять), `core/rest/{TaskRoutes,RestRouter,SseFormat,TaskEventStream}`
(готово, не менять), `core/perception/{PerceptionResult,Finding,Sighting,LocateType,PerceptionType}` (готово, не
менять), `core/observation/{BlockCoord,EntityRef,ItemCount,HazardRef,ScanRadius}` (готово, не менять),
`neoforge/{SpiritEntity,SpiritBrain,SpiritTaskStore,ScanScript,LocateScript,LookAtScript,HazardScript,MemoryScript,
ChopTreeScript,ObserveJobs}` (готово, не менять), `agents/strategist/{policy.json,tracker.py,cycle.py}` (готово, не
менять). Публикацию и перезапуск сервера не делать.

**Зависимости.** Часть А (ядро, пп. 1–6) не зависит ни от кого — идёт параллельно. Часть Б (адаптер, пп. 7–13) —
после слияния чеклистов 13 (`TaskRunner`, `SpiritTaskStore`, `SpiritBrain`), 15 (`TaskRegistry`, `TaskRoutes`,
`TaskWatcher`), 26 (`PerceptionResult`, `Finding`, `ScanScript`, `LocateScript`). Часть В (GameTest, пп. 14–17) —
после Б. Чеклист 24 (навигация) не нужен. Публикацию и перезапуск сервера не делать.

## Что должно получиться

Итог успешного скрипта и результаты восприятия доходят до ИИ-стратега через REST и переживают перезагрузку сервера:

- **`ScriptInstance.resultMessage()`** — default-метод интерфейса: возвращает `Optional<String>` (пусто, если итога
  нет). Скрипты с содержательным итогом переопределяют его. `ChopTreeScript.summary()` становится реализацией
  `resultMessage()`, а не отдельным публичным методом. Скрипты восприятия (`ScanScript`, `LocateScript`, `LookAtScript`,
  `HazardScript`, `MemoryScript`) не переопределяют `resultMessage()` — их результат приходит через объект
  `PerceptionResult`/`Finding` и кладётся в поле `result` задачи как JSON-объект (см. п. 2 и «На согласование» п. 2).
- **Итог задачи в REST** — поле `result` в `GET /v1/tasks/{id}` (уже есть в `TaskJson.toMap`, но всегда `null`)
  заполняется при завершении задачи. Для текстовых скриптов — строка из `resultMessage()`. Для скриптов восприятия —
  JSON-объект, сериализованный из `PerceptionResult`/`Finding`.
- **Итог в SSE** — финальное событие `state` (уже содержит `result` в `TaskEventStream.stateData`) несёт итог задачи.
  Отдельного события `result` не вводим: итог приходит в том же кадре, что и состояние `succeeded`.
- **Итог переживает перезагрузку** — завершённая задача хранит итог в NBT духа (ключ `SpiritResult`, версия 1,
  формат — JSON-строка). После перезапуска сервера итог доступен через `GET /v1/tasks/{id}` (если задача ещё в
  реестре) или через последний сохранённый итог духа. Реестр задач живёт в памяти и очищается при перезапуске, поэтому
  для итога нужен отдельный канал хранения — NBT духа.
- **Результаты восприятия в REST** — `SpiritEntity.perceptionResult()` и `SpiritEntity.finding()` отдаются через
  итог задачи: скрипты восприятия при завершении кладут `PerceptionResult`/`Finding` в поле `result` задачи как
  JSON-объект. Стратег читает результат из `GET /v1/tasks/{id}`.
- **Формат результатов восприятия** — JSON-объект с полями: `type` (строка: `blocks`/`entities`/`hazards`/
  `located`/`cell`/`memory`), `blocks` (массив `{item, count, pos}`), `entities` (массив `{type, pos, distance}`),
  `hazards` (массив `{type, pos, distance}`), `sightings` (массив `{kind, label, pos, distance}`). Лимиты размера:
  `max_string_length: 200` на строку, `max_list_items: 32` на список (из `policy.json` стратега). Результат, превышающий
  лимит, обрезается с пометкой `"truncated": true`.
- **Стратег читает итог** — `agents/strategist/tracker.py` при терминальном состоянии задачи читает поле `result`
  (сейчас читает только `state` и `error`). Whitelist в `agents/strategist/policy.json` не меняется — результат
  приходит в рамках задачи, а не нового скрипта.
- **Версия контракта API** — остаётся `v1` (аддитивное изменение: новое поле `result` в ответах, которое раньше
  всегда было `null`). В `docs/API.md` добавляется описание поля `result` и формата результатов восприятия.

Числа (предварительно, на согласование H1): `max_string_length` 200 (из `policy.json`, не менять); `max_list_items`
32 (из `policy.json`, не менять); ключ NBT `SpiritResult`, версия 1; лимит JSON-объекта результата — 4 КиБ.

## Общие точки с другими чеклистами (merge)

- `core/engine/ScriptInstance` — новый default-метод `resultMessage()`. Конфликтов нет (метод добавляется в интерфейс,
  существующие реализации не ломаются).
- `core/engine/TaskRunner` — при `SUCCEEDED` собирает итог из скрипта. Конфликтов нет.
- `core/task/SpiritReading` — новое поле `result`. Конфликтов нет.
- `core/task/TaskReconciler` — при `SUCCEEDED` передаёт `result` в `TaskRegistry`. Конфликтов нет.
- `core/task/TaskEventDiff` — при смене состояния на `SUCCEEDED` шлёт событие `state` с `result` (если есть).
  Конфликтов нет.
- `neoforge/SpiritEntity` — новый метод `taskResult()`. Конфликтов нет.
- `neoforge/SpiritBrain` — `recordResult()` сохраняет итог. Конфликтов нет.
- `neoforge/SpiritTaskStore` — новый ключ `SpiritResult`. Конфликт с чеклистом 13 (`SpiritTask`) — «оставить обе
  стороны».
- `neoforge/PerceptionResultCodec` (новый) — сериализация `PerceptionResult`/`Finding` в JSON. Конфликтов нет.
- `agents/strategist/policy.json` — whitelist не меняется. Конфликтов нет.
- `agents/strategist/tracker.py` — чтение поля `result`. Конфликтов нет.
- `docs/API.md` — описание поля `result`. Конфликтов нет.
- `guards/CatalogDefaultGuardTests`, `core/script/ScriptCatalogTests` — не меняются (новые скрипты не добавляются).

## Часть А — ядро (`core/engine`, `core/task`, без Minecraft)

- [ ] 1. **`ScriptInstance.resultMessage()`** — default-метод интерфейса: `default Optional<String> resultMessage() {
      return Optional.empty(); }`. Скрипты с содержательным итогом переопределяют его. Тесты: default-метод возвращает
      пустой `Optional`; переопределённый метод возвращает значение; метод не бросает исключений. Тесты — в
      `ScriptInstanceTests` (новый класс или расширение существующего).
- [ ] 2. **`TaskRunner` — сбор итога** — при переходе в `SUCCEEDED` собирает итог из скрипта: `root.result()`
      возвращает `Optional<String>` (из `ScriptInstance.resultMessage()`). Итог сохраняется в поле `successResult`.
      Метод `successResult()` возвращает `Optional<String>`. Тесты: задача со скриптом, имеющим итог, — `successResult()`
      возвращает значение; задача без итога — пустой `Optional`; итог не теряется при `cancel()`.
- [ ] 3. **`SpiritReading` — поле `result`** — record получает новое поле `String result` (может быть null).
      Фабрики: `living(...)`, `failed(...)`, `absent()`, `dead()` — принимают `result` (по умолчанию null). Метод
      `withGeneration(...)` сохраняет `result`. Тесты: round-trip полей; `result` не теряется при `withGeneration`;
      `absent()`/`dead()` имеют `result == null`.
- [ ] 4. **`TaskReconciler` — передача `result`** — при `SUCCEEDED` передаёт `result` в `Registry.observeIfActive`.
      Итог берётся из `SpiritReading.result()`. При `FAILED`/`CANCELLED` `result` не передаётся (остаётся null). Тесты:
      успешная задача — `result` в `TaskRecord`; провал — `result == null`; отмена — `result == null`.
- [ ] 5. **`TaskEventDiff` — событие с итогом** — при смене состояния на `SUCCEEDED` событие `state` содержит
      `result` (если есть). Формат: `{"state":"succeeded","result":"..."}`. Тесты: успех с итогом — событие содержит
      `result`; успех без итога — `result == null`; провал — событие `script_failed` не содержит `result`.
- [ ] 6. **`TaskSnapshotCodec` — итог в снимке** — снимок задачи (RUNNING) не содержит итог (итог появляется только
      при завершении). Значит, `TaskSnapshot` не меняется. Но проверяется, что `encodeForSave` при завершённой
      задаче (которая уже не RUNNING) не ломается: `snapshot()` возвращает `Optional.empty()` для не-RUNNING, итог
      сохраняется отдельно (в NBT духа, п. 10). Тесты: round-trip снимка RUNNING; снимок завершённой задачи —
      `Optional.empty()`.

## Часть Б — адаптер (после слияния чеклистов 13, 15, 26)

- [ ] 7. **`SpiritEntity.taskResult()`** — метод возвращает `Optional<String>` (последний сохранённый итог). Итог
      сохраняется в поле `lastResult` при завершении задачи. Тесты: до задачи — пустой `Optional`; после успешной
      задачи — итог; после провала — пустой `Optional`.
- [ ] 8. **`SpiritBrain.recordResult()` — сохранение итога** — при `SUCCEEDED` сохраняет итог из
      `TaskRunner.successResult()` в поле `lastResult`. Метод `successResult()` возвращает `Optional<String>`. Тесты:
      успех с итогом — `lastResult` заполнен; успех без итога — `lastResult == null`; провал — `lastResult` не
      трогается.
- [ ] 9. **`SpiritTaskStore` — ключ `SpiritResult`** — NBT-ключ `SpiritResult`, версия 1, формат JSON-строка:
      `{"version":1,"result":"..."}`. Итог сохраняется при завершении задачи. `read` возвращает строку или null.
      Тесты: round-trip NBT; повреждённые данные — ошибка, а не тихий fallback; старой версии нет — null.
- [ ] 10. **`PerceptionResultCodec` (новый)** — сериализация `PerceptionResult`/`Finding` в JSON-объект и обратно.
      Формат: `{"type":"blocks","blocks":[{"item":"...","count":N,"pos":[x,y,z]}],"radius":N}` для `PerceptionResult`;
      `{"type":"located","sightings":[{"kind":"block","label":"...","pos":[x,y,z],"distance":"12.3"}]}` для `Finding`.
      Лимиты: `max_string_length` 200, `max_list_items` 32. Тесты: round-trip `PerceptionResult` и `Finding`; результат,
      превышающий лимит, обрезается с `"truncated": true`.
- [ ] 11. **Скрипты восприятия — результат в задаче** — `ScanScript`, `LocateScript`, `LookAtScript`, `HazardScript`,
      `MemoryScript` при завершении кладут результат в поле `result` задачи (не только в `SpiritEntity`). Результат
      сериализуется через `PerceptionResultCodec` и передаётся в `TaskRunner.successResult()`. Тесты: каждый скрипт
      при успехе кладёт результат в задачу; результат проходит через `PerceptionResultCodec` без потерь.
- [ ] 12. **`ChopTreeScript` — `resultMessage()`** — `summary()` становится реализацией `resultMessage()`. Публичный
      метод `summary()` удаляется (или остаётся как делегат к `resultMessage()` для обратной совместимости). Тесты:
      `resultMessage()` возвращает текст с числами; после перезагрузки (`saveState`/`restoreState`) итог содержит
      сохранённые счётчики (`standingLogs`, `plantedSaplings` и т.д.) — для этого счётчики добавляются в `saveState()`.
- [ ] 13. **`TaskWatcher` — чтение `result`** — при чтении состояния духа передаёт `result` из `SpiritEntity`.
      `SpiritReading` получает `result` из `spirit.taskResult()`. Тесты: успешная задача — `result` в `SpiritReading`;
      провал — `result == null`; дух без задачи — `result == null`.

## Часть В — GameTest (`gametest/ResultGameTests`)

Шаблон `arena` 11×8×11, всё внутри площадки, духи удаляются в конце (`GameTestFixtures.reset`), без `sleep`, время по
тикам духа (`SpiritRun`). Каждый тест — своя пачка (`batch = ...`). Сценарии ≤ 200 тиков, кроме оговорённых.

- [ ] 14. **`chop_tree` — итог задачи** (пачка `result_chop`): (а) дух срубил дерево — `GET /v1/tasks/{id}`
      возвращает `result` с текстом «срублено 1 из 1»; (б) после перезагрузки духа (save/restore NBT) `result`
      содержит сохранённые счётчики (`standingLogs`, `plantedSaplings`); (в) SSE-поток содержит финальное событие с
      `result`. Подлоги: `resultMessage()` не переопределён (итог не приходит); `saveState()` не сохраняет
      счётчики (после перезагрузки итог неполный).
- [ ] 15. **`scan_blocks` — результат восприятия** (пачка `result_scan`): (а) `scan_blocks` с фильтром —
      `GET /v1/tasks/{id}` возвращает `result` с JSON-объектом `{type:"blocks", blocks:[...]}`; (б) `sense_hazard` —
      `result` содержит `{type:"hazards", hazards:[...]}`; (в) `locate_block` — `result` содержит
      `{type:"located", sightings:[...]}`; (г) результат, превышающий лимит, обрезается с `truncated: true`. Подлоги:
      результат не кладётся в задачу (приходит пустой `result`); лимиты не соблюдаются.
- [ ] 16. **Стратег читает итог** (пачку `result_strategy`): (а) цикл стратега опрашивает задачу и получает `result`
      в `tracker.py`; (б) `result` попадает в журнал стратега. Подлог: `tracker.py` не читает `result` (в журнал
      попадает только `state` и `error`).

## Красными до реализации

Все тесты `core/engine/ScriptInstanceTests` (метод `resultMessage()`), `core/task/TaskReconcilerTests` (поле
`result`), `core/rest/TaskEventStreamTests` (событие с `result`), `neoforge/SpiritBrainTests` (сохранение итога),
`neoforge/PerceptionResultCodecTests` (сериализация) и все GameTest пп. 14–16 (скрипты восприятия не кладут результат
в задачу; `ChopTreeScript.resultMessage()` отсутствует). Назови красные до прогона. Для `fix:`-коммитов —
`scripts/prove-red.sh`; GameTest скрипт не проверяет — подлоги вручную.

## Стоимость в тике

- Итог собирается один раз при завершении задачи (не каждый тек). `resultMessage()` вызывается один раз в тике
  завершения. Сериализация `PerceptionResult`/`Finding` — один вызов при завершении скрипта восприятия.
- Чтение итога из NBT — при загрузке духа, не каждый тек. Запись итога в NBT — при завершении задачи.
- Вложенных циклов «духи × задачи» нет; числа — в тестах, не время.

## Завершение

- [ ] 17. `./gradlew test` и `./gradlew runGameTestServer` зелёные. Коммиты по пунктам (`test:`/`feat:`).
      `todo.md` не править. Отчёт: хэши; сколько тестов прошло и упало; красные до реализации; подлоги пп. 14–16;
      время самого медленного GameTest и причина; что не сделано.

## Не входит (уходит в следующие чеклисты)

- MCP-адаптер поверх REST (решение главного агента 2026-10-11: MCP позже).
- Расширение `observe` для чтения `result` из духа (сейчас `observe` не пишет ничего духу; если понадобится —
  отдельный чеклист).
- Синхронизация итога между духами (общий итог всех духов).
- История итогов (последние N результатов задач).
- Изменение `policy.json` стратега (whitelist не меняется).

## На согласование

1. **H1 — лимиты результата:** `max_string_length: 200`, `max_list_items: 32` (из `policy.json` стратега) — менять
   только с согласия. Лимит JSON-объекта результата — 4 КиБ (предварительно). Цена завышения — большой JSON в
   каждой задаче; цена занижения — стратег не видит полный результат `scan_blocks` с большим фильтром.
2. **H2 — формат `result` для восприятия:** JSON-объект (как в п. 2 и «Что должно получить») или строка (весь
   JSON как текст)? Предлагаю объект: стратегу проще парсить, не нужен вложенный `json.loads`. Цена объекта —
   новая схема в контракте API; цена строки — двойное кодирование.
3. **H3 — хранение итога в NBT:** ключ `SpiritResult` (версия 1) или отдельный файл в каталоге мира? Предлагаю
   NBT (переживает перезагрузку, привязан к духу). Цена NBT — итог привязан к духу, а не к `task_id`; цена файла —
   отдельная логика очистки.
4. **H4 — версия контракта API:** аддитивное изменение `v1` (поле `result` было всегда `null`) или `v2`? Предлагаю
   `v1` (обратная совместимость, старые клиенты не сломаются). Цена `v1` — поле `result` появляется в старом
   контракте; цена `v2` — нужно поддерживать две версии.

## Поправки главного агента (13.10) — обязательны, при расхождении с текстом выше действуют они

- П1. **Зависимости выполнены:** 13, 15, 26 в main — части А, Б, В идут подряд. Список «готово, не менять» в начале —
  это карта кода для чтения: файлы, которые требуют пункты ниже (TaskRunner, SpiritReading, TaskReconciler,
  TaskEventDiff, скрипты восприятия, ChopTreeScript, TaskWatcher, tracker.py, docs/API.md), менять можно и нужно.
- П2. **Один канал итога:** `ScriptInstance.result(): Optional<ScriptResult>`, где `ScriptResult` — sealed:
  `Text(String)` для текстовых итогов (рубка и т. п.) и `Data(Map<String,Object>)` для восприятия. Отдельного пути
  «результат через SpiritEntity.perceptionResult()» в REST нет; поля в SpiritEntity остаются только как удобство для
  GameTest/команд, если они уже используются. `resultMessage()` как отдельный метод не нужен.
- П3. **В REST `result`:** для `Text` — строка, для `Data` — JSON-объект (H2 — объект, принято). Контракт остаётся `v1`
  (H4 — принято), в docs/API.md — описание поля и схемы `Data` по типам.
- П4. **Без NBT-ключа `SpiritResult` (п. 9 убрать, H3 — отклонено):** реестр задач после перезапуска пуст, итог в NBT
  никто бы не прочитал. Требование todo «после перезагрузки итог теряет брёвна и саженцы» закрывает п. 12: счётчики
  рубки — в `saveState`/`restoreState`, задача, прерванная перезапуском, завершается с полным итогом. П. 7–8 —
  итог живёт в памяти до чтения TaskWatcher.
- П5. **Лимиты (H1 — принято):** 200 символов на строку, 32 элемента на список, 4 КиБ на объект; обрезка с
  `"truncated": true` — в одном месте ядра (кодек), лимиты — константы с тестом равенства числам.
- П6. **П. 6 убрать** — проверяет уже существующее поведение без изменений.
- П7. **П. 16 — не GameTest, а pytest** в `agents/strategist` (как устроены существующие тесты стратега): tracker при
  терминальном состоянии с `result` (строка и объект) кладёт его в журнал/наблюдение цикла; подлог — tracker не читает
  `result`. GameTest пп. 14–15 — через REST-фикстуру (`RestFixture`, ожидание ответа по реальному времени).
- П8. GameTest: timeoutTicks ≤ 400 без метки-обоснования (сторож); если время суток влияет — фиксировать полдень.
