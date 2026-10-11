# 14 — REST-мост (P4, часть 1): HTTP-сервер в моде, capabilities, bots, observe

Источник: `todo.md` → «P4 — REST-мост»; `AGENTS.md` → «Решения пользователя» (стратег — внешний ИИ-агент по REST в
локальной сети; мост живёт внутри мода: HTTP-сервер поднимается при старте сервера Minecraft, порт и адрес — из
серверного конфига, слушает LAN, **без авторизации** — сеть доверенная; MCP позже, адаптером поверх REST);
контракт — `docs/API.md` (v1). Идеи по мостам — `docs/research/bot-api-bridge.md` (там Bearer и localhost — они
противоречат решению пользователя; брать только устройство маршрутов, ошибок и потоков).

Перед работой прочитай `AGENTS.md`, `docs/CONVENTIONS.md` (Быстрый путь — маршрутизация/валидация/сериализация в
`core`; Структура; Производительность; Ошибки), `docs/TESTING.md`, `docs/GIT.md`, `docs/API.md` целиком, `README.md`
(карта и раздел команд). Код: `neoforge/{SpiritEntity,SpiritScripts,WorldScanner,SpiritConfig}`, `TheMineSpirit`,
`core/observation/*` (формат наблюдения уже совпадает с `docs/API.md`), `core/script/ScriptCatalog`, `core/plan/PlanParser`.
**Новых зависимостей нет**: только `com.sun.net.httpserver` из JDK (версии и библиотеки выбирает пользователь,
`AGENTS.md`). Публикацию и перезапуск сервера не делать.

**Зависимости.** Этот чеклист стартует после слияния чеклиста 10 (нужны `IdFilter` и `WorldScanner.Session` — скан порциями)
и 11–12 (чтобы правка `SpiritScripts` не конфликтовала). Чеклист 13 не нужен. Чеклист 15 идёт после этого.

## Что должно получиться

Работающий сервер Minecraft слушает HTTP `<bindAddress>:<port>`. Внешний агент из той же сети получает:

- `GET /v1/capabilities` — каталог **реализованных** скриптов (`SpiritScripts`, а не весь `CoreScripts`) с
  JSON-Schema параметров (`type`, `minimum`/`maximum`, `default`, `enum`, `required`), предусловиями и эффектами;
- `GET /v1/bots` и `GET /v1/bots/{id}` — духи и их состояние (`id`, `name`, `state` = `idle|busy|dead`, `position`,
  `dimension`, `current_task` = `null` до чеклиста 15); `id` — UUID сущности (решение исполнителя; предварительно);
- `POST /v1/bots/{id}/observe` — наблюдение духа в формате `docs/API.md` (запрос: `radius`, `filter`,
  `include_entities`), снимается порциями за несколько тиков, мир не фризится, результат ничего не пишет духу.
- Ошибки — единый объект `{"error":{"code","message","details"}}`, тексты на русском, называют, что сломалось и что
  проверить. Коды и статусы: `invalid_request` 400, `forbidden` 403, `not_found` 404, `conflict` 409, `invalid_plan` 422,
  `rate_limited` 429, `internal` 500. Неизвестный путь — 404 `not_found`; известный путь с чужим методом — 405
  `invalid_request` + заголовок `Allow`; тело больше лимита — 400 `invalid_request`. Кода `unauthorized` нет.
- Конфиг `serverconfig/theminespirit-rest.toml`: `enabled` (по умолчанию `true`), `port` (по умолчанию 8765,
  1024..65535; предварительно — проверь, что порт свободен на сервере: 8099 занят раздачей клиента, 25565 — игра),
  `bindAddress` (по умолчанию `0.0.0.0` — LAN). Токена нет ни в конфиге, ни в запросах.
- **Потоки.** HTTP-потоки не трогают `Level`, `Entity`, инвентарь и наблюдение; любое обращение к миру —
  `ServerThreadCall` (задача на `server.execute(...)`, ожидание `CompletableFuture` с тайм-аутом, не стартовавшая задача
  после тайм-аута пропускается). Зависший тик даёт клиенту `internal` «сервер не ответил за N с», а не вечный запрос.
- Старт при `ServerStartedEvent`, остановка при `ServerStoppingEvent` (идемпотентно, порт освобождается). Занятый
  порт или неверный адрес — ERROR в лог с портом, адресом и причиной; Minecraft-сервер при этом живёт (REST не
  критичен), статус виден в логе — не тихий fallback.

## Общие точки с другими чеклистами (merge)

- `TheMineSpirit` — 3 строки (конфиг, слушатели событий).
- `neoforge/SpiritScripts` — вынести перечень реализованных скриптов в одно место (таблица фабрик) и добавить статический
  `catalog()` без духа; `registryFor(spirit)` и `catalogFor(spirit)` остаются. Единый источник списка — чтобы `capabilities`
  и реестр исполнения не разошлись.
- `docs/API.md`, `README.md` — правки текста (пункт 15).
- Всё остальное новое: `core/rest/*`, `neoforge/rest/*`, `gametest/RestGameTests`. Чеклист 15 достраивает те же
  `Router`/`RestServer`/`BotGateway` — эти классы обязаны быть расширяемыми добавлением маршрутов без правки существующих.

## Часть А — проверка осуществимости

- [ ] 1. **Спайк `HttpServer` в рантайме мода** (≤ 20 мин, результат — в отчёт): на `./gradlew runGameTestServer` и
      `./gradlew runServer` простейший `HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)` создаётся и отвечает
      (`jdk.httpserver` виден слою мода; тест клиентом `java.net.http.HttpClient` — модуль `java.net.http` тоже).
      Если не виден — **остановись и сообщи главному агенту** с текстом ошибки и вариантами (добавить модуль в
      запуск сервера; другая библиотека — решает пользователь). Обходных зависимостей не добавлять. Спайк не
      коммитится.

## Часть Б — ядро (`core/rest`, без HTTP-типов JDK и без Minecraft)

- [ ] 2. **Модель и ошибки** — `RestRequest(method, path, query, headers, body)`, `RestResponse(status, headers, body)`,
      `ErrorCode` (код → статус), `ApiError`/`ApiException`, `ErrorJson` (формат из `docs/API.md`, `details` всегда объект).
      Тесты (красными до реализации): таблица код → статус (7 строк); JSON ошибки побайтно равен примеру из `docs/API.md`.
- [ ] 3. **`Router`** — таблица маршрутов `GET /v1/bots/{id}`; параметр пути `[A-Za-z0-9_-]{1,64}`, иначе 404; 404/405 по
      правилам выше; обработчик получает `RestRequest` и параметры пути. Интерфейс обработчика не знает потоков.
      Тесты: таблица ≥ 20 случаев (метод × путь → обработчик/404/405, лишний слэш, регистр, `..`, `%00`, юникод);
      свойство jqwik: на любых строках метода и пути маршрутизатор не бросает исключений и всегда возвращает
      валидный JSON-ответ с кодом из списка.
- [ ] 4. **`ServerThreadCall`** — `submit(Executor serverThread, Callable<T>)` → `CompletableFuture<T>`; `await(future, Duration)`
      → результат либо `ApiException(internal, "сервер не ответил за N с")`; не стартовавшая задача после тайм-аута
      пропускается; исключение задачи → `internal` с причиной в `details`, а не молчание.
      Тесты на реальном исполнителе-потоке и управляемом (очередь без запуска): результат доходит; задача выполняется
      не на потоке вызывающего (сравнение потоков); тайм-аут даёт `internal`; поздно запущенная после тайм-аута задача
      не выполняет тело; исключение сохраняет причину.
- [ ] 5. **Граница `BotGateway`** (интерфейс платформы): `List<BotInfo> bots()`, `Optional<BotInfo> bot(id)`,
      `WorldObservation observe(id, ObserveRequest)` — вызываются с HTTP-потока, блокируются до результата, при
      ошибках бросают `ApiException`. `BotInfo(id, name, state, x, y, z, dimension, currentTask)`.
- [ ] 6. **`CapabilitiesJson`** — `ScriptCatalog` → JSON по `docs/API.md` (`INT` → `integer` + `minimum`/`maximum`/`default`;
      `DOUBLE` → `number`; `STRING` → `string`; `STRING_LIST` → `array` of `string`; `BOOL` → `boolean`; `ENUM` → `string` +
      `enum`; `required` — список обязательных; порядок — как в каталоге). Тесты: сравнение с эталоном-JSON на
      маленьком каталоге (все типы параметров); свойство: каждый параметр каждого скрипта присутствует, `required`
      совпадает с `ParamSpec.required()`, результат — валидный JSON; полный `CoreScripts.defaultCatalog()` сериализуется.
- [ ] 7. **`ObserveRequest`** — разбор тела (пустое тело = умолчания): `radius` целое в границах `ScanRadius` (умолчание
      `ScanRadius.DEFAULT`), `filter` — до 16 шаблонов `IdFilter` (чеклист 10) длиной ≤ 64, `include_entities` bool
      (по умолчанию `true`; `false` → пустой список сущностей). Неизвестное поле или неверный тип → `invalid_request`
      с названием поля и допустимыми. Тесты: таблица ≥ 15 строк, свойство «никогда не бросает ничего, кроме `ApiException`».
- [ ] 8. **Обработчики v1** — `capabilities`, `bots`, `bot`, `observe` поверх `BotGateway` (фальшивый шлюз в тестах).
      Тесты (≥ 20 случаев в таблице): 200 с ожидаемым JSON; неизвестный бот → 404 `not_found` с `id` в тексте;
      неверный `radius` → 400; шлюз бросил `internal` → 500 с тем же объектом; метод не тот → 405 с `Allow`.
- [ ] 9. **`RestTuning`** (данные): `HTTP_THREADS = 8`, `MAX_BODY_BYTES = 1 MiB`, `SERVER_CALL_TIMEOUT = 5 с`,
      `OBSERVE_TIMEOUT = 15 с` — всё предварительно. Сторож `guards/RestCoreHttpGuardTests`: пакет `core/rest` не
      импортирует `com.sun.net.httpserver` и `java.net.http` (+ тест на собственный шаблон, как у `CoreIsolationTests`).

## Часть В — адаптер (`neoforge/rest`)

- [ ] 10. **`RestConfig`** — `ModConfigSpec` в отдельном классе (не в `SpiritConfig`: другая тема), файл
      `theminespirit-rest.toml`, регистрация в `TheMineSpirit` (`registerConfig(ModConfig.Type.SERVER, spec, имя)`).
      Проверка `bindAddress` (`InetAddress.getByName`) при старте с понятной ошибкой.
- [ ] 11. **`RestServer`** — обёртка над `HttpServer`: `start(address, port, handlers)`, `stop()`, `port()` (порт 0 =
      эфемерный, для тестов); `HttpExchange` ⇒ `RestRequest` (тело читается не более `MAX_BODY_BYTES + 1` байт),
      `RestResponse` ⇒ `sendResponseHeaders` (`Content-Type: application/json; charset=utf-8`, `Cache-Control: no-store`);
      пул `HTTP_THREADS` потоков-демонов `theminespirit-rest-N`; необработанное исключение → 500 `internal` без стека
      клиенту и с полным стеком в лог. IP клиентов в лог не пишутся. Остановка — `stop(1)` + завершение пула.
- [ ] 12. **`SpiritIndex` и `SpiritBotGateway`.** `SpiritIndex` — `Map<UUID, SpiritEntity>` по `EntityJoinLevelEvent` /
      `EntityLeaveLevelEvent`; читается и меняется только на серверном потоке (проверка `ensureServerThread()` с
      понятным `IllegalStateException`). `SpiritBotGateway` реализует `BotGateway` через `ServerThreadCall`: `bots()` —
      один проход по индексу (O(духов), без обхода сущностей мира), `bot(id)` — O(1). `observe` — на серверном потоке
      создаётся `WorldScanner.Session` (чеклист 10) и ставится в `ObserveJobs`; `ServerTickEvent.Post` двигает не более
      `SLICES_PER_TICK` срезов за тик на задачу и не более 4 задач одновременно (пятая — `rate_limited` «слишком
      много снимков одновременно»); дух исчез во время скана — `not_found`. `include_entities=false` — пустые сущности;
      `botId` в наблюдении — id бота. Состояние: `dead` — `!isAlive()`, `busy` — задача `RUNNING`, иначе `idle`.
- [ ] 13. **Подключение** — `ServerStartedEvent` читает конфиг и запускает `RestServer`, `ServerStoppingEvent` останавливает;
      ERROR при неудаче по правилам выше. В `TheMineSpirit` — только регистрация слушателей.
- [ ] 14. **GameTest `RestGameTests`** (шаблон `empty`; свой `RestServer` на `127.0.0.1:0`; клиент `HttpClient.sendAsync`).
      **Тест не блокируется на запросе:** он выполняется на серверном потоке, а обработчик ждёт этот же поток — синхронный
      `send` — взаимная блокировка. Запрос отправляется асинхронно, результат проверяется в `helper.succeedWhen(...)`.
      Сценарии: (а) `capabilities`: 200, есть `goto` и `scan`, нет `patrol` (не реализован), у `goto` в `required` x, y, z;
      (б) `bots`: заспавненный дух в списке; после `discard()` — нет; (в) `bots/{id}`: `idle`, позиция совпадает; после
      `startTask(goto…)` — `busy`; (г) неизвестный id → 404 `not_found` с объектом ошибки; (д) `observe` радиус 8:
      200, `hash` 64 символа, `bot` = id, блоки пола присутствуют; радиус 99 → 400 с названием `radius`; фильтр `["*_ore"]`
      оставляет в `blocks` только руду; (е) HTTP-поток не касается мира: вызов `SpiritIndex`/шлюза с постороннего потока
      даёт `IllegalStateException`, а запросы по HTTP при этом работают; (ж) `stop()`: новое соединение отвергнуто, порт
      можно занять снова; повторный `stop()` без ошибки; (з) неверный JSON → 400 с позицией ошибки; тело 2 МиБ → 400;
      GET на `/v1/nope` → 404; POST на `/v1/bots` → 405 с `Allow: GET`; (и) 20 параллельных `bots` ответили 200 без
      ошибок, число переходов на серверный поток ровно по одному на запрос (счётчик `ServerThreadCall`).
- [ ] 15. **Документы.** `docs/API.md`: убрать строку про `Authorization: Bearer` и код `unauthorized` из списка кодов;
      дописать «Авторизации нет: сеть доверенная (решение пользователя 2026-10-11); доступ ограничивается адресом
      привязки и файрволом»; таблицу «код → HTTP-статус», 405/`Allow`; формат `id` бота (UUID); строгость полей запроса
      `observe`; убрать пометку «Черновик» у реализованных конечных точек (задачи/события/plan пока не реализованы —
      пометить «чеклист 15»). `README.md`: раздел про REST (файл конфига, порт по умолчанию, `curl http://<хост>:<порт>/v1/bots`,
      «порт открыт всей сети»). `docs/research/*` не править.
- [ ] 16. **Тест на масштаб** (числа, не время): (а) `SpiritIndex` — 30 духов, `bots()` читает ровно 30 записей и не
      вызывает `getEntities`; (б) одна задача `observe` за тик читает ≤ `SLICES_PER_TICK × 4096` позиций (счётчик сессии);
      (в) одновременных скан-задач ≤ 4; (г) переходов на серверный поток — по одному на запрос (п. 14и).

## Красными до реализации

Все тесты `core/rest/*` (классов нет), `RestCoreHttpGuardTests` (нужен собственный подлог: положить `import
com.sun.net.httpserver.*` в файл `core/rest` — краснеет), все GameTest п. 14, 16. Подлоги: убрать переход на серверный поток
в `bots()` — краснеет (е); вернуть `Allow` пустым — краснеет (з). Назови красные до прогона.

## Стоимость в тике

Обработчики HTTP — вне серверного тика; на серверном потоке — одна короткая задача на запрос (чтение индекса) и
`ObserveJobs`: ≤ 4 задачи × 4 среза за тик. Пока запросов нет, слушатели событий ничего не делают (`join/leave` — O(1),
`ServerTickEvent` без задач — один пустой цикл).

## Завершение

- [ ] 17. `./gradlew test` и `./gradlew runGameTestServer` зелёные. Коммиты по пунктам (`feat:`/`test:`/`docs:`). `todo.md`
      не править. Отчёт: хэши; сколько тестов прошло и упало; красные до реализации; результат спайка (п. 1); порт,
      выбранный по умолчанию, и проверка, что он свободен на сервере (`ss -ltn`, команда только на чтение); что не
      сделано. Живой запрос к настоящему серверу — только после согласованной публикации (`docs/PUBLISH.md`).

## Не входит

Задачи, события, `plan` (чеклист 15); MCP; HTTPS; авторизация и токены (решение пользователя — их нет); CORS;
ограничение частоты запросов (чеклист 15); сжатие ответов; запись наблюдения духу через REST.

## Замечания ревью (коммиты 82b5399..b7098bb)

Принято: модель и единый объект ошибки, `Router` (404/405 с `Allow`, лимит тела, дочитывание лишнего тела → 400),
`ServerThreadCall` (тайм-аут, отмена, пропуск устаревшей задачи, `ApiException` проходит насквозь), граница
`BotGateway` и сторож «`core/rest` без HTTP-типов JDK», `SpiritIndex` только на серверном потоке (join/leave O(1),
снятие по паре uuid+сущность — смена измерения безопасна), мост только на выделенном сервере, демон-потоки, GameTest
без синхронного `send`.

- [ ] R1. **Дефект/стоимость: `observe` сканирует весь куб за один тик.** `neoforge/rest/SpiritBotGateway.java:64` —
      `WorldScanner.observe(...)` на серверном потоке целиком: радиус 32 — до 274 тыс. позиций в одном тике. Мост
      открыт в локальную сеть без авторизации, HTTP-потоков 8 — восемь таких запросов подряд замораживают сервер на
      секунды. Чеклист 10 уже в `main` (`1bef975`). Как надо: `git merge main` в ветку; п. 12 по тексту —
      `WorldScanner.Session` ставится в `ObserveJobs`, `ServerTickEvent.Post` двигает ≤ `ScanTuning.SLICES_PER_TICK`
      срезов на задачу, ≤ 4 задачи одновременно, пятая — `rate_limited`, дух исчез во время скана — `not_found`;
      HTTP-поток ждёт результат с `OBSERVE_TIMEOUT`, не держа серверный поток. Тесты: п. 14д (фильтр `["*_ore"]`
      оставляет в `blocks` только руду), п. 16б (≤ `SLICES_PER_TICK × 4096` чтений за тик на задачу — счётчик сессии),
      п. 16в (≤ 4 задач, пятая — 429 `rate_limited`), красные на текущем коде.
- [ ] R2. **Фильтр `observe` принимается и молча не применяется; правило шаблона описано дважды.** `docs/API.md:100`
      признаёт, что `filter` пока игнорируется: клиент просит руду и получает всё — тихий fallback (CONVENTIONS →
      «Ошибки»). А `ObserveRequest.filter(...)` проверяет шаблоны своими правилами (длина, непустота), тогда как
      допустимые символы и `*` определяет `IdFilter.of` из 10 — одно правило в двух местах разойдётся. Как надо:
      после слияния `ObserveRequest` строит `IdFilter.of(...)` (его `IllegalArgumentException` → 400 `invalid_request` с
      `filter[i]` в `details`), лимиты количества и длины остаются в `ObserveRequest`; фильтр передаётся в `Session`;
      абзац в `API.md` удалить. Тест: шаблон `"Iron!"` → 400 с текстом `IdFilter`.
- [ ] R3. **Слияние с `main`: таблица скриптов и селекторы GameTest.** `neoforge/SpiritScripts` переписан в таблицу
      `IMPLEMENTED`, а в `main` (10) в старый `Map.of` добавлены `scan` и `collect_drops` — при слиянии перенести их в
      таблицу, иначе `capabilities` их не покажет и духи их не выполнят (п. 14а требует `scan` в ответе). В `main`
      селектор `SpiritCommandGameTests` получил `sort=nearest` — взять версию `main`: здесь без сортировки `@e[...,limit=1]`
      берёт случайного духа из соседних структур (REST-тесты тоже спавнят духов) — источник флаков. Заодно
      `SpiritScripts.java:48` `ids.addAll(IMPLEMENTED.keySet())` после цикла дублирует его: достаточно
      `defaultCatalog().restrictedTo(IMPLEMENTED.keySet())`. После слияния — полный `./gradlew test` и
      `runGameTestServer` (сторожа `CatalogDefaultGuardTests`, `TaskReferenceCommentGuardTests` из `main` тоже).
- [ ] R4. **Остановка: запрос в полёте держит остановку сервера 3 с.** `RestServer.stop()` вызывается на серверном
      потоке (`ServerStoppingEvent`); обработчик, ждущий `ServerThreadCall`, не дождётся — серверный поток занят в
      `stop()`. Итог: `httpServer.stop(1)` + `awaitTermination(2)` полностью, клиент получает «прервано». Как надо:
      до `httpServer.stop` перевести шлюз в «останавливается» — новые `submit` и ждущие `await` сразу завершаются
      `ApiException` «сервер останавливается, повторите позже» (без ожидания тайм-аута). Тест (JUnit на
      `ServerThreadCall` с исполнителем, который не выполняет задачи): ожидание прерывается остановкой ≤ 100 мс, текст
      называет остановку.

## Замечания ревью (коммиты 8e7e50e..8f7960b, слияние 015134b)

Принято: R1 (`ObserveJobs`: `WorldScanner.Session` срезами ≤ `SLICES_PER_TICK` за тик, ≤ 4 задачи, пятая — 429, HTTP-поток
ждёт `OBSERVE_TIMEOUT`, не держа серверный поток, отменённое ожидание снимает задачу на следующем тике), R2 (`IdFilter.of`
→ 400 с `filter[i]`, фильтр уходит в `Session`, GameTest с рудным полом краснеет без фильтра), R3 (`scan`/`collect_drops`
в таблице `IMPLEMENTED`, `sort=nearest` из `main`, `restrictedTo` в порядке каталога), R4 (`ServerThreadCall.stop()`
обрывает ждущие `await` и новые `submit`). Каталог `net/` в коммиты не попал. Последний GameTest-прогон
(`run/logs/latest.log`, 20:43) — 61 из 61.

- [x] R5. **Документ обещает `?`, а `IdFilter` его отвергает.** `docs/API.md:94` — «те же шаблоны со `*` и `?`», но
      `IdFilter.ALLOWED_SYMBOLS` = `a-z 0-9 _ : . / - *`: клиент, поверивший документу, получает 400. Таблица `docs/API.md:100`
      не называет допустимые символы. Как надо: убрать `?`, в строке `filter` перечислить символы и `*` (тот же текст, что в
      сообщении `IdFilter`). Тест: строка таблицы `ObserveRequestTests` `{"filter":["*_or?"]}` → 400 `IdFilter` (фиксирует
      контракт, если `?` однажды разрешат — придётся менять и документ).
- [x] R6. **Тест «пятое — 429» не доказывает предел 4.** `gametest/RestScaleGameTests.java:96` — `ok >= 1 && limited >= 1`
      проходит и при `MAX_CONCURRENT = 1` (ok 1, limited 4, пик 1 ≤ 4). Сообщение обещает «первые — 200, пятое — 429», проверка
      этого не требует (TESTING → «Тест, который может упасть»). Как надо: `ok == 4 && limited == 1` и `peak == 4` (пять
      запросов радиуса 32 приходят в один тик, скан идёт ~30 тиков — порядок детерминирован). Подлог: `MAX_CONCURRENT = 1` и
      `= 5` — оба краснеют.
- [x] R7. **Ветки «дух исчез во время скана» и «остановка во время скана» без теста.** `neoforge/rest/ObserveJobs.java:76`
      (`isRemoved` → 404) и `:59` (`close()` → «Сервер останавливается») — поведение п. 12 и R1, тестов нет (LEAD-AGENT →
      «Ревью», п. 4). Без ветки `isRemoved` скан пропавшего духа досчитывается и клиент получает 200 на несуществующего духа.
      Тест (GameTest, batch `rest`): `observe` радиуса 32, через тик `spirit.discard()` → 404 `not_found` с `id`; второй —
      `observe` радиуса 32, через тик `rest.server().stop()` → ответ 500 с «останавливается» быстрее `OBSERVE_TIMEOUT`.
      Подлоги: убрать ветку `isRemoved` — первый краснеет; убрать `jobs.close()` из `SpiritBotGateway.stop` — второй.
- [x] R8. **Подписка на тик в конструкторе переживает неудачный старт.** `ObserveJobs.java:32` подписывается на шину в
      конструкторе; `RestLifecycle.java:45` создаёт шлюз до `RestServer.start`, и при занятом порте (`catch` ниже)
      `gateway.stop()` не зовётся — слушатель `ServerTickEvent` висит до остановки сервера (то же в GameTest, если сценарий
      не дошёл до `close()`). Стоимость мала, но это утечка ресурса и побочный эффект конструктора. Как надо: в `catch`
      `tryStart` вызывать `gateway.stop()` (или подписываться в `start()` шлюза после успешного `bind`). Заодно
      `MAX_CONCURRENT` (`ObserveJobs.java:21`) — к остальным предварительным числам в `RestTuning` (п. 9: числа моста в одном
      месте). Тест: GameTest «`tryStart` на занятом порте → `Optional.empty()`, после него ни одного живого `ObserveJobs`»
      (счётчик подписок `ObserveJobs`, как `peakActive`). Подлог: убрать `gateway.stop()` из `catch` — краснеет.
- [x] R9. **Тип коммита.** `8f7960b fix:` меняет только GameTest — это `test:` (GIT.md → «Сообщение коммита»). И сам
      `8f7960b` чинит GameTest из `71876f6`, который трогал мир из HTTP-потока: значит, `71876f6` закоммичен без прогона
      `runGameTestServer`. Перед `feat:`, трогающим мир, — зелёный GameTest (AGENTS.md → «Исполнитель»).

Принято: R5 (`docs/API.md` называет символы `IdFilter` без `?`, JUnit-строка `{"filter":["*_or?"]}` → 400 `IdFilter`),
R6 (тест 16в требует `ok == 4`, `limited == 1` и `peak == RestTuning.MAX_OBSERVES`; подлоги `MAX_OBSERVES = 1` и `= 5`
покрасили ровно его), R7 (два GameTest: исчезший дух → 404 `not_found` с `details.id`; остановка моста посреди скана →
500 с «останавливается» быстрее `OBSERVE_TIMEOUT` и снятая подписка; подлоги — убранная ветка `isRemoved` и убранный
`jobs.close()` — покрасили каждый свой тест), R8 (`gateway.stop()` в `catch` `tryStart`, лимит — `RestTuning.MAX_OBSERVES`,
счётчик живых подписок `ObserveJobs.liveCount()`; GameTest на занятом порте; подлог без `gateway.stop()` в `catch` —
красный). Детектор R7-второго — `liveSubscriptions() == 0`, а не `activeCount` (`await` отменяет future, и tick убирает
задачу и без `close()`), поэтому R8 (счётчик) закоммичен раньше R7. R9: уточнение внесено в GIT.md (коммит, меняющий
только GameTest, — `test:`); историю не переписывали. Последний GameTest-прогон — 64 из 64.

Наблюдение (не замечание этой ветки): `scan_radius32NearUnloadedChunks_DoesNotLoadChunks` из чеклиста 10 упал 1 раз из 3
(`TestResults/gametest-final-3.log`: 5361 → 5367 загруженных чанков) — вероятно, чанки держат духи соседних тестов. В
`todo.md` → «Техдолг».
