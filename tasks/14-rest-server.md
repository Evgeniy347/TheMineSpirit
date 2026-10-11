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
