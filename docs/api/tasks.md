### POST /v1/bots/{id}/tasks

Запрос: нужен `goal` или `plan` (план выигрывает, `goal` остаётся подписью). Поля: `request_id` (UUID, обязателен),
`goal` (строка до 500 символов), `plan` (дерево плана), `params` (необязательно: объект из строк и целых — параметры
цели для планировщика, например `{"target":"Steve"}` для «следуй за мной»). Неизвестное поле — 400 `invalid_request`.

```json
{ "request_id": "3f1c...", "goal": "найди руду",
  "plan": { "type": "sequence", "steps": [
    { "type": "script", "script": "scan", "params": { "radius": 32, "filter": ["*_ore"] } },
    { "type": "script", "script": "mine_vein", "params": { "ore": "iron", "count": 3, "radius": 16 } } ] } }
```

Ответ `202 Accepted`:

```json
{ "task_id": "t-42", "bot": "6f1c2d3e-0000-4000-8000-000000000001", "state": "accepted", "request_id": "3f1c..." }
```

- План проверяется по каталогу **реализованных** скриптов; цель без плана планирует резервный планировщик
  (`FallbackPlanner`, LLM в моде нет). Непонятная цель, нереализованный или неизвестный скрипт, лишний параметр,
  нарушение лимитов, «следуй за мной» без `params.target` — 422 `invalid_plan` с причиной.
- Идемпотентность: повтор с тем же `request_id` и тем же телом возвращает прежний ответ `202` (та же задача, дух не
  перезапускается), даже если дух всё ещё занят этой же задачей; тот же `request_id` с другим телом — 409 `conflict`.
  Память — последние 1024 `request_id`; вытесненный считается новым.
- `{id}` духа в пути приводится к каноническому UUID в нижнем регистре: регистр не создаёт второй задачи.
- Один дух — одна активная задача, очереди и вытеснения нет. Дух занят своей REST-задачей (409 с `task_id` текущей) или
  задачей вне REST, например командой `/spirit` (409 «поставлена вне REST») — 409 `conflict`.
- Реестр задач живёт в памяти: после перезапуска сервера он пуст, старые `task_id` дают 404.

### GET /v1/tasks/{id}

```json
{ "task_id": "t-42", "bot": "6f1c2d3e-0000-4000-8000-000000000001", "state": "running",
  "goal": "найди руду",
  "progress": { "step": 2, "total_steps": 2, "current_script": "mine_vein", "done": 1, "target": 3 },
  "result": null, "error": null }
```

`{id}` задания доски (например `graph:chop_jungle_log`) отдаётся тем же запросом из очереди доски: ответ —
`id`, `case`, `status`, `waitReason`, `assignee` и прочие поля `GET /v1/board`. Для REST-задачи `state`:
`accepted` / `running` / `succeeded` / `failed` / `cancelled`. `progress.done` и `progress.target` есть у счётных
скриптов (`mine_vein`, `gather`, `place_block`, `fill`, `clear_area`, `dig_area`, `dig_descent`, `build_schematic`), у остальных
`null`; у завершённой задачи остаётся последний прогресс, у проваленной —
шаг и скрипт, на которых она упала. `error` у `failed` называет причину; «дух исчез» — дух погиб или не вернулся в
мир за 100 тиков (перезагрузка чанка короче не проваливает задачу). Если духу поставили другую задачу вне REST
(команда `/spirit`, планировщик ИИ), REST-задача закрывается как `cancelled` с `error` «задача вытеснена вне REST».

`result` у `succeeded` несёт итог задачи, у остальных состояний — `null`. Для текстовых скриптов (`chop_tree`
и т.п.) — строка (`«срублено 1 из 1»`). Для скриптов восприятия (`scan_blocks`, `scan_entities`, `sense_hazard`,
`locate_*`, `look_at`, `remember`/`recall`/`recall_all`/`forget`) — JSON-объект (см. схему ниже); у обычного `scan`
без фокуса итога нет (`null`). Итог доступен, пока задача в реестре (реестр очищается при перезапуске сервера).

Схема объекта восприятия (`result.type`):

| `type` | Откуда | Поля |
|---|---|---|
| `blocks` | `scan_blocks` | `blocks[]` (`{item, count}`), `radius` |
| `entities` | `scan_entities` | `entities[]` (`{type, pos, distance}`), `radius` |
| `hazards` | `sense_hazard` | `hazards[]` (`{type, pos, distance}`), `radius` |
| `located` | `locate_*` | `script`, `sightings[]` (`{kind, label, pos, detail}`) |
| `cell` | `look_at` | `script`, `sightings[]` (`{kind, label, pos, detail}`) |
| `memory` | `remember`/`recall`/`recall_all`/`forget` | `script`, `sightings[]` (`{kind, label, pos, detail}`) |

Лимиты итога: строка — не дольше 200 символов, список — не больше 32 записей, объект — не больше 4 КиБ;
превышение обрезается с пометкой `"truncated": true`.

### DELETE /v1/tasks/{id}

Отмена: `200` с задачей в состоянии `cancelled`; дух останавливается, незавершённая ломка блока откатывается (блок цел
или сломан с дропом, трещины сняты). Повторный `DELETE` и `DELETE` завершённой задачи идемпотентны: `200` и текущее
состояние. Неизвестная задача — 404.

### GET /v1/tasks/{id}/events (SSE)

`Content-Type: text/event-stream`. Первым кадром идёт текущее `state`, затем `progress` (при смене шага, скрипта или
счётчика), `script_failed` и завершающий `state` (`succeeded` / `failed` / `cancelled`), после чего поток закрывается.
Завершающий `state` со `succeeded` несёт `result` — тот же итог, что `GET /v1/tasks/{id}` (см. схему выше); отдельного
события итога нет.
Каждый кадр несёт `id:`; заголовок `Last-Event-ID` продолжает с места обрыва (приходят только более поздние кадры; если
журнал, окно 256 событий, уже вытеснил нужные, или `Last-Event-ID` больше последнего id журнала, сначала приходит
текущее `state`, дальше поток идёт с позиции журнала). Раз в 15 секунд при тишине идёт
`: keepalive`. Одновременно открыто не больше 4 потоков, пятый — 429.

```
id: 3
event: progress
data: {"step":2,"total_steps":2,"current_script":"mine_vein","done":1,"target":3}

id: 4
event: script_failed
data: {"script":"mine_vein","reason":"нет подходящей жилы в радиусе"}

id: 5
event: state
data: {"state":"failed"}
```

### POST /v1/plan

Сухой прогон: валидация плана по каталогу реализованных скриптов без исполнения (для ИИ до постановки задачи). Мир и
духи не затрагиваются, на серверный поток запрос не ходит.

```json
{ "plan": { "type": "script", "script": "goto", "params": { "x": 10, "y": 64, "z": -3 } } }
```

Ответ `200` — `{ "valid": true }`; `422` — объект ошибки `invalid_plan` с причиной; нет поля `plan` — 400.

