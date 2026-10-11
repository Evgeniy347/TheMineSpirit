# API духов (bridge) — контракт v1

Мост живёт внутри мода и обслуживает контракт встроенным HTTP-сервером JDK. Реализованы `capabilities`, `bots`,
`bots/{id}` и `observe`; задачи, события и `plan` помечены «чеклист 15» и пока не отвечают (404 `not_found`).
Ядро `core` обслуживает контракт, но не зависит от HTTP.

## Общие правила

- База: `/v1`, формат JSON (UTF-8), кроме потока событий (`text/event-stream`). Ответы идут с
  `Content-Type: application/json; charset=utf-8` и `Cache-Control: no-store`.
- Авторизации нет: сеть доверенная (решение пользователя 2026-10-11); доступ ограничивается адресом привязки
  (`bindAddress` в `serverconfig/theminespirit-rest.toml`) и файрволом.
- Тело запроса — не больше 1 МиБ; больше — 400 `invalid_request`.
- Идемпотентность: изменяющие запросы несут `request_id` (UUID). Повтор с тем же `request_id` возвращает
  прежний результат, а не выполняет действие заново.
- Лимиты плана (проверяются валидатором): `repeat.times` 1..64, глубина дерева ≤ 16, число узлов ≤ 256.
- Ошибки — единый объект:

```json
{"error":{"code":"invalid_plan","message":"У скрипта 'wait' не задан обязательный параметр 'ticks' (INT)","details":{}}}
```

`details` — всегда объект (может быть пустым). Тексты — на русском (см. CONVENTIONS.md → «Ошибки»).

| Код | HTTP-статус | Когда |
|---|---|---|
| `invalid_request` | 400 | неверное тело, поле или тип поля; тело больше лимита; 405 (см. ниже) |
| `forbidden` | 403 | зарезервирован, пока не выдаётся |
| `not_found` | 404 | неизвестный путь или дух |
| `conflict` | 409 | состояние не позволяет действие (например, наблюдение мёртвого духа) |
| `invalid_plan` | 422 | план не прошёл валидацию |
| `rate_limited` | 429 | ограничение частоты — чеклист 15, пока не выдаётся |
| `internal` | 500 | сбой моста; сервер не ответил на задачу в серверном потоке за отведённое время |

Известный путь с чужим методом даёт 405 с кодом `invalid_request` и заголовком `Allow` (методы через запятую).
Кода `unauthorized` нет.

## Ресурсы

- **Bot** — дух: `id` (UUID сущности), `name`, `state` (`idle`/`busy`/`dead`), `position` (`[x, y, z]` — блок духа),
  `dimension`, `current_task` (`null`, пока не реализованы задачи — чеклист 15). Состояние `dead` — дух не жив, `busy` —
  у него идёт задача, иначе `idle`.
- **Observation** — текстовая модель мира вокруг духа (не картинка): позиция, инвентарь, перечень блоков
  в радиусе, сущности, контейнеры, `hash`.
- **Task** — поставленная цель и/или готовый план; статус, прогресс, результат.
- **Capability** — описание скрипта: `id`, `summary`, `params` (JSON-Schema), предусловия, эффекты.

## Конечные точки

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/v1/capabilities` | каталог скриптов (для ИИ-планировщика) |
| GET | `/v1/bots` | список духов и их состояние |
| GET | `/v1/bots/{id}` | состояние одного духа |
| POST | `/v1/bots/{id}/observe` | наблюдение мира вокруг духа |
| POST | `/v1/bots/{id}/tasks` | поставить цель (и/или план) — чеклист 15 |
| GET | `/v1/tasks/{id}` | статус, прогресс, результат — чеклист 15 |
| DELETE | `/v1/tasks/{id}` | отменить задачу (мир остаётся консистентным) — чеклист 15 |
| GET | `/v1/tasks/{id}/events` | поток событий задачи (SSE) — чеклист 15 |
| POST | `/v1/plan` | сухой прогон: проверить план без исполнения — чеклист 15 |

### GET /v1/capabilities

Каталог только реализованных скриптов (не весь набор из плана): ИИ не должен получить скрипт, который дух не выполнит.
Скрипты идут в порядке полного каталога; `params.required` перечисляет обязательные параметры.

```json
{ "scripts": [
  { "id": "chop_tree", "summary": "срубить деревья",
    "params": { "type": "object", "properties": {
      "species": { "type": "string", "default": "any" },
      "count": { "type": "integer", "minimum": 1, "maximum": 256, "default": 1 } },
      "required": [] },
    "preconditions": ["есть подходящие блоки в радиусе сканирования"],
    "effects": ["брёвна срублены", "предметы подобраны в инвентарь"] } ] }
```

### GET /v1/bots и GET /v1/bots/{id}

`{id}` — UUID сущности духа. Неизвестный `id` (в том числе не UUID) — 404 `not_found` с `id` в тексте.

```json
{"bots":[{"id":"6f1c2d3e-0000-4000-8000-000000000001","name":"spirit","state":"idle","position":[10,64,-3],"dimension":"minecraft:overworld","current_task":null}]}
```

`GET /v1/bots/{id}` возвращает один такой объект без обёртки `bots`.

### POST /v1/bots/{id}/observe

Снимок идёт на серверном потоке и не пишет ничего духу. Запрос строгий: тело пустое (все умолчания) или JSON-объект
только с полями ниже; неизвестное поле, неверный тип, значение вне границ и повтор поля — 400 `invalid_request` с названием
поля и допустимыми значениями, неверный JSON — 400 с позицией ошибки (строка и столбец).

| Поле | Тип | Умолчание | Границы |
|---|---|---|---|
| `radius` | целое | 8 | 1..32 |
| `filter` | массив строк-шаблонов | `[]` | до 16 шаблонов, каждый не пуст и ≤ 64 символов |
| `include_entities` | bool | `true` | `false` — список `entities` пуст |

`filter` принимается и проверяется, но пока не применяется к `blocks`: фильтр по идентификаторам подключается
после слияния чеклиста 10.

Запрос:

```json
{ "radius": 32, "filter": ["*_log", "*_ore"], "include_entities": true }
```

Ответ (`bot` — `id` духа):

```json
{ "bot": "6f1c2d3e-0000-4000-8000-000000000001", "tick": 123456, "pos": [10, 64, -3], "radius": 32,
  "inventory": [ { "item": "oak_log", "count": 5 }, { "item": "iron_pickaxe", "count": 1 } ],
  "blocks":    [ { "item": "grass_block", "count": 340 }, { "item": "oak_log", "count": 12 } ],
  "entities":  [ { "type": "minecraft:cow", "pos": [14, 64, -1], "distance": 5.0 } ],
  "containers":[ { "type": "minecraft:chest", "pos": [8, 63, -5] } ],
  "hash": "<64 hex-символа SHA-256 от картины мира: без tick и distance>" }
```

### POST /v1/bots/{id}/tasks

Запрос (план необязателен — без него планирует движок):

```json
{ "request_id": "3f1c...", "goal": "срубить деревья вокруг",
  "plan": { "type": "sequence", "steps": [
    { "type": "script", "script": "scan", "params": { "radius": 32, "filter": ["*_log"] } },
    { "type": "script", "script": "chop_tree", "params": { "species": "any", "count": 5 } } ] } }
```

Ответ `202 Accepted`:

```json
{ "task_id": "t-42", "bot": "spirit_1", "state": "accepted", "request_id": "3f1c..." }
```

### GET /v1/tasks/{id}

```json
{ "task_id": "t-42", "bot": "spirit_1", "state": "running",
  "goal": "срубить деревья вокруг",
  "progress": { "step": 2, "total_steps": 2, "current_script": "chop_tree", "done": 3, "target": 5 },
  "result": null, "error": null }
```

`state`: `accepted` / `running` / `succeeded` / `failed` / `cancelled`.

### GET /v1/tasks/{id}/events (SSE)

```
event: progress
data: {"step":2,"current_script":"chop_tree","done":3,"target":5}

event: script_failed
data: {"script":"mine_vein","reason":"нет подходящей жилы в радиусе"}
```

### POST /v1/plan

Сухой прогон: валидация плана по каталогу без исполнения (для ИИ до постановки задачи).

```json
{ "plan": { "type": "script", "script": "chop_tree", "params": { "count": 5 } } }
```

Ответ `200` — `{ "valid": true }`; `422` — объект ошибки `invalid_plan` с причиной.

## Формат плана (дерево задач)

| `type` | Поля | Смысл |
|---|---|---|
| `script` | `script`, `params` | вызов скрипта |
| `sequence` | `steps[]` | шаги по порядку |
| `repeat` | `times`, `step` | повторить шаг |
| `fallback` | `steps[]` | первый успешный шаг |
| `parallel` | `mode` (`all`/`any`), `steps[]` | параллельно |
| `condition` | `check`, `params`, `then`, `else` | ветвление по предикату |

ИИ возвращает только такие узлы со скриптами из каталога. Произвольный код, команды оболочки и
скрипты вне whitelist отклоняются валидатором (`invalid_plan`).
