# API духов (bridge) — контракт v1

Черновик. Реализуется в P4 (см. todo.md). Ядро `core` обслуживает этот контракт, но не зависит от HTTP.

## Общие правила

- База: `/v1`, формат JSON (UTF-8), кроме потока событий (`text/event-stream`).
- Авторизация: `Authorization: Bearer <token>`. Токен хранится в окружении bridge, в git не попадает.
- Идемпотентность: изменяющие запросы несут `request_id` (UUID). Повтор с тем же `request_id` возвращает
  прежний результат, а не выполняет действие заново.
- Ошибки — единый объект:

```json
{ "error": { "code": "invalid_plan", "message": "скрипт chop_tree: не задан обязательный параметр count", "details": {} } }
```

Коды: `invalid_request`, `unauthorized`, `forbidden`, `not_found`, `conflict`, `invalid_plan`,
`rate_limited`, `internal`. Тексты — на русском (см. CONVENTIONS.md → «Ошибки»).

## Ресурсы

- **Bot** — дух: `id`, `name`, `state` (`idle`/`busy`/`dead`), `position`, `current_task`.
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
| POST | `/v1/bots/{id}/tasks` | поставить цель (и/или план) |
| GET | `/v1/tasks/{id}` | статус, прогресс, результат |
| DELETE | `/v1/tasks/{id}` | отменить задачу (мир остаётся консистентным) |
| GET | `/v1/tasks/{id}/events` | поток событий задачи (SSE) |
| POST | `/v1/plan` | сухой прогон: проверить план без исполнения |

### GET /v1/capabilities

```json
{ "scripts": [
  { "id": "chop_tree", "summary": "срубить деревья",
    "params": { "type": "object", "properties": {
      "species": { "type": "string", "default": "any" },
      "count": { "type": "integer", "minimum": 1, "maximum": 256 } },
      "required": ["count"] },
    "preconditions": ["есть подходящие блоки в радиусе сканирования"],
    "effects": ["брёвна срублены", "предметы подобраны в инвентарь"] } ] }
```

### POST /v1/bots/{id}/observe

Запрос:

```json
{ "radius": 32, "filter": ["*_log", "*_ore"], "include_entities": true }
```

Ответ:

```json
{ "bot": "spirit_1", "tick": 123456, "pos": [10, 64, -3], "radius": 32,
  "inventory": [ { "item": "oak_log", "count": 5 }, { "item": "iron_pickaxe", "count": 1 } ],
  "blocks":    [ { "item": "grass_block", "count": 340 }, { "item": "oak_log", "count": 12 } ],
  "entities":  [ { "type": "minecraft:cow", "pos": [14, 64, -1], "distance": 5.0 } ],
  "containers":[ { "type": "minecraft:chest", "pos": [8, 63, -5] } ],
  "hash": "sha256:..." }
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
