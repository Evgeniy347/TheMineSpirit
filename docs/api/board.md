### GET /v1/board

Очередь заданий доски поселения — наблюдение для стратегического интеллекта и игрока. Отдаётся порциями: по
умолчанию `limit=50`, максимум `200`; сервер не собирает и не сериализует всю доску на каждый запрос.

```
GET /v1/board?limit=30&offset=60&status=ASSIGNED&group=ACTIVE&assignee=<uuid>&case=BUILD_FENCE&since=1200
```

```json
{"tasks":[
  {"id":"board-6f1c2d3e-0000-4000-8000-000000000001","case":"SUPPLY_SITE","priority":20,
   "assignee":"11111111-2222-3333-4444-555555555555","status":"WAITING_RESOURCES",
   "requiredItems":{"oak_log":16},"missing":{"oak_log":10},"dependsOn":["board-root"],
   "createdAt":1234,"level":1,"cause":"always","effectivePriority":55,
   "modifiers":["lure_bait_ready"],"done":false,"waitReason":"не хватает материалов: {oak_log=16}"}
],"total":138,"offset":0,"limit":50,
 "groups":{"DONE":80,"ACTIVE":5,"WAITING":3,"AVAILABLE":50},
 "version":1234567890123456789,"nextCursor":"bzoxNTA",
 "counts":{"done":1231,"reopened":1}}
```

- `limit` (1..200, по умолчанию 50), `offset` (≥ 0) — нарезка стабильного порядка `createdAt`, затем `id`.
- `cursor` — вместо `offset`: значение `nextCursor` из прошлого ответа; `cursor` и `offset` вместе — 400.
- `status` — имя `BoardStatus` (`PENDING`/`WAITING_RESOURCES`/`ASSIGNED`/`BLOCKED`/`DONE`/`FAILED`/`CANCELLED`);
  `group` — колонка `BoardTileGroup` (`DONE`/`ACTIVE`/`WAITING`/`AVAILABLE`); регистр не важен.
- `assignee` — UUID духа-держателя; `case` — имя `BoardCase`; `since`/`updated_after` — нижняя граница тика
  постановки (`updated_after` — синоним, при двух значениях побеждает поздний).
- `total` — сколько заданий подходит под фильтры (не размер порции); `groups` — счётчики колонок после фильтров
  статуса/духа/кейса/времени; `nextCursor` есть, пока за порцией остался хвост; `version` меняется при изменении
  состава и состояния доски.
- `counts` — итоги журнала: `done` (переходы DONE, только растёт) и `reopened` (REOPENED, отдельно); текущий
  статус задачи в `tasks` от этого не зависит.
- `id` — id задания; `case` — кейс `BoardCase`; `priority` — вес (больше — раньше);
- `assignee` — дух-держатель или `null` («любой свободный»);
- `status` — `PENDING`/`WAITING_RESOURCES`/`ASSIGNED`/`BLOCKED`/`DONE`/`FAILED`/`CANCELLED`;
  `BLOCKED` — задание проваливалось подряд `ticketFailLimit` раз (число в `board.json`), пока условия
  (`requiredItems` по снимку запасов или само определение задания) не изменятся;
- `requiredItems` — нужда гейта; `missing` — сколько чего не хватает по последнему снимку запасов (`null`-полей нет,
  пустая нехватка — `{}`);
- `dependsOn` — id заданий-предшественников; `createdAt` — тик постановки; `level` — уровень постройки (≥ 1);
  `cause` — причина задания (`always` у постоянных, иначе ключ вида `crater:x:y:z`): по ней пересборка проверяет
  актуальность, срока жизни у заданий нет;
- `effectivePriority` — база плюс сработавшие модификаторы (с зажимом в `[0, 100]`), `modifiers` — их ключи;
- `done` — отметка «постройка стоит»: задание уровня выполнено или уровень подтверждён журналом прошлой сессии.
- `waitReason` — почему задание сейчас не выдаётся (`BLOCKED`, предшественники, нехватка материалов, откат после
  провала, пауза кейса у держателя) или `null`, если оно готово и ждёт свободного духа (живой прогон 2, INC-23).

### POST /v1/board/tasks

Стратегический интеллект ставит задание поселения (не адресное — адресные идут через `POST /v1/bots/{id}/tasks`).
Обязателен `request_id` (UUID): повтор с тем же `request_id` возвращает то же задание, а не ставит второе; тот же
`request_id` с другим телом — 409 `conflict`.

```json
{"request_id":"6f1c2d3e-0000-4000-8000-000000000001","case":"BUILD_FENCE",
 "assignee":"11111111-2222-3333-4444-555555555555","requiredItems":{"oak_log":16},
 "dependsOn":["board-root"],"level":1,"cause":"always"}
```

- `case` — обязателен, одно из имён `BoardCase`; неизвестный кейс — 422 `invalid_plan` с перечнем `allowed` (текст
  ошибки несёт список допустимых кейсов). `RESCUE_SPIRIT` — внутренний кейс спасения: ставится источником доски,
  цель — координаты застрявшего духа; его план вскрывает клетку вокруг цели (`clear_area`) и подходит к ней;
- `assignee` — необязателен: UUID духа-владельца или отсутствует/`null` («любой свободный»);
- `requiredItems` — необязателен: `{предмет: количество ≥ 0}` (гейт по ресурсам);
- `dependsOn` — необязателен: список id заданий-предшественников;
- `level` — необязателен, по умолчанию 1; обязан быть ≥ 1;
- `cause` — необязательна, по умолчанию `always`; обязана быть непустой строкой;
- `building`, `from`, `to` — только для `case: RELOCATE_BUILDING`: `building` — кейс переносимой постройки,
  `from`/`to` — две точки `"x,y,z"`; неподвижная постройка (данные `board.json → immovable`) — 422
  `invalid_request` с перечнем `immovable`; `from = to` и отсутствие точек — 400;
- лишнее поле — 400 `invalid_request` с именем поля.
- `TRANSFER_ITEM` через REST не ставится: это внутренний кейс доски, его создаёт источник поселения по нехватке
  ингредиентов у крафтера (`TransferWatch` → `TransferPlanner`, передача одного предмета от духа к духу или со
  склада через `withdraw`). Запрос с этим кейсом — 422 `invalid_request`.

Ответ `202` — задание целиком (как элемент `GET /v1/board`) плюс `request_id` клиента. Идём через `IdempotencyCache`,
как и задачи духа.

### GET /v1/board/log

Последние записи журнала заданий: `{"entries":[{...}]}` от свежих к старым. `limit` (query, необязателен) —
сколько записей отдать, по умолчанию 5, границы 1..64; неизвестный параметр — 400 `invalid_request`.
Запись журнала — та же, что строка JSONL на диске (`<папка мира>/theminespirit/board-log.jsonl`): `event`
(`SUBMITTED`/`ASSIGNED`/`DONE`/`FAILED`/`BLOCKED`/`UNBLOCKED`/`CANCELLED`/`DETACHED`), `tick`, `ticketId`, `case`, `level`, `cause`,
`basePriority`, `effectivePriority`, `modifiers`, `spirit`, `configHash`, `worldTime`.

### DELETE /v1/board/tasks/{id}

Отмена задания: `200` с заданием в статусе `CANCELLED`; зависимые задания снимаются вместе с ним (доска
`cancelWithDependants`), цель и брони работ освобождаются. Неизвестное задание — 404 `not_found`.

