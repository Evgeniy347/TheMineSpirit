### GET /v1/bots и GET /v1/bots/{id}

`{id}` — UUID сущности духа. Неизвестный `id` (в том числе не UUID) — 404 `not_found` с `id` в тексте.

```json
{"bots":[{"id":"6f1c2d3e-0000-4000-8000-000000000001","name":"spirit","state":"idle","position":[10,64,-3],
  "dimension":"minecraft:overworld","current_task":null,"activity":null,"script":null,"board_task":null,
  "tick":123456,"position_tick":123400,"state_since_tick":123400,"food":1.0}]}
```

`GET /v1/bots/{id}` возвращает один такой объект без обёртки `bots`.

| Поле | Смысл |
|---|---|
| `state` | `idle` — дух свободен; `busy` — исполняет задачу или держит задание доски/занятие; `dead` — погиб |
| `current_task` | `task_id` активной REST-задачи; иначе `id` задания/карточки занятия доски; иначе занятие или скрипт; у простоя — `null` |
| `activity` | занятие доски (`ACTIVITY_*`, например `ACTIVITY_CHOP`); нет занятия — `null` |
| `script` | исполняемый сейчас скрипт; скрипт не идёт — `null` |
| `board_task` | задание доски духа: `id`, `case`, `status`; задания нет — `null` |
| `state_since_tick` | тик, с которого не менялись `state`, `activity` и `script`: по нему видно, сколько дух стоит в одном состоянии |

### GET /v1/spirits/{id}/decisions

Журнал решений тактики: по нему видно, почему дух сделал то, что сделал. Запись появляется только при смене
решения (`decision`: FIGHT/RETREAT/IGNORE), режима (`mode`), цели (`target`) или приказа координатора (`order`) —
и не чаще одной записи в секунду на духа (числа — `adapter.json → journalCapacity`, `journalMaxPerSecond`).
Журнал живёт в памяти сервера и держит последние `journalCapacity` записей: после перезапуска он пуст.
`{id}` — UUID сущности духа; неизвестный `id` — 404 `not_found` с `id` в тексте.

`limit` (query, необязателен): сколько записей отдать, по умолчанию 5, границы 1..64. Записи идут от свежих
к старым. Нечисловое значение, значение вне границ и лишний параметр запроса — 400 `invalid_request` с названием
параметра; другой метод — 405.

```json
{ "bot": "6f1c2d3e-0000-4000-8000-000000000001", "limit": 5, "decisions": [
  { "tick": 56199, "spirit": "6f1c2d3e-0000-4000-8000-000000000001", "changed": "decision",
    "from": "IGNORE", "to": "FIGHT", "rule": "score", "hold": "none",
    "factors": [ { "factor": "WIN_MARGIN", "value": 0.62, "weight": 1.06 },
                 { "factor": "PROXIMITY", "value": 0.58, "weight": 0.23 } ],
    "facts": { "enemies": 1, "nearest": "minecraft:husk в 5.0", "hp": 20.0, "max_hp": 20.0,
               "armor": 0.0, "weapon": "оружие ближнего боя" } } ] }
```

| Поле | Смысл |
|---|---|
| `tick` | игровой тик решения |
| `changed` | сменившееся поле: `decision`, `mode`, `target` или `order` |
| `from` / `to` | было → стало (для `order` — режим, зона и подопечный целиком) |
| `rule` | id сработавшего жёсткого правила либо `score` (решение по оценке) или `hold` (удержание прежнего) |
| `hold` | удержание прежнего решения: `none`, `held`, `bonus`, `released_by_rule`, `released_hp_drop`, `released_excluded` |
| `factors` | топ-3 фактора с весами — вклад каждого в решение (пусто, когда решило правило) |
| `facts` | ключевые факты: врагов рядом, ближайший, здоровье, броня, чем дух дерётся |

Те же записи дух печатает в лог сервера строкой с префиксом `[tactics]` (уровень INFO) и показывает командой
`/spirit decisions <дух>` (последние 5).

### POST /v1/bots/{id}/observe

Снимок идёт на серверном потоке по частям (несколько тиков) и не пишет ничего духу. Запрос строгий: тело пустое (все
умолчания) или JSON-объект только с полями ниже; неизвестное поле, неверный тип, значение вне границ и повтор поля —
400 `invalid_request` с названием поля и допустимыми значениями, неверный JSON — 400 с позицией ошибки (строка и столбец).

`filter` применяется к `blocks` (те же шаблоны со `*`, что у `scan`): в ответе остаются только совпавшие блоки.
Одновременно крутятся не больше 4 наблюдений; запрос сверх предела — 429 `rate_limited`, повторите позже.

| Поле | Тип | Умолчание | Границы |
|---|---|---|---|
| `radius` | целое | 8 | 1..32 |
| `filter` | массив строк-шаблонов | `[]` | до 16 шаблонов, каждый не пуст и ≤ 64 символов; символы a-z, 0-9, `_ : . / -` и подстановка `*` |
| `include_entities` | bool | `true` | `false` — список `entities` пуст |

Запрос:

```json
{ "radius": 32, "filter": ["*_log", "*_ore"], "include_entities": true }
```

Ответ (`bot` — `id` духа; поля `held`, `health`, `max_health`, `food`, `board_task`, `ground_items`,
`container_items`, `time` — необязательные, в старом ответе их нет):

```json
{ "bot": "6f1c2d3e-0000-4000-8000-000000000001", "tick": 123456, "pos": [10, 64, -3], "radius": 32,
  "inventory": [ { "item": "oak_log", "count": 5 }, { "item": "iron_pickaxe", "count": 1 } ],
  "blocks":    [ { "item": "grass_block", "count": 340 }, { "item": "oak_log", "count": 12 } ],
  "entities":  [ { "type": "minecraft:cow", "pos": [14, 64, -1], "distance": 5.0 } ],
  "containers":[ { "type": "minecraft:chest", "pos": [8, 63, -5] } ],
  "open_blocks":[ { "item": "minecraft:iron_ore", "pos": [11, 45, -7], "distance": 19.5 } ],
  "held":        { "item": "minecraft:iron_pickaxe", "durability": 250 },
  "health": 18.0, "max_health": 20.0, "food": 0.7,
  "board_task":  { "case": "SUPPLY_SITE", "status": "ASSIGNED" },
  "ground_items":[ { "item": "minecraft:oak_log", "count": 3 } ],
  "container_items": [ { "pos": [8, 63, -5], "items": [ { "item": "minecraft:iron_ingot", "count": 7 } ] } ],
  "time": { "day": true, "day_time": 6000 },
  "hash": "<64 hex-символа SHA-256 от картины мира: без tick, distance и точного тика суток>" }
```

| Поле | Смысл |
|---|---|
| `inventory` | предметы в инвентаре духа: `id → количество` (сводятся по предмету) |
| `held` | предмет в руке: `item` и остаток прочности `durability` (`null` не пишется); пустая рука — поля нет |
| `health`, `max_health` | здоровье духа и его максимум |
| `food` | сытость долей шкалы голода `[0; 1]` |
| `board_task` | текущее задание доски духа: `case` и `status`; задания нет — поля нет |
| `ground_items` | выпавшие рядом предметы: `id → количество` (сводятся по предмету) |
| `container_items` | содержимое известных контейнеров: `pos` → `items` (`id → количество`); число контейнеров, записей и открытых блоков ограничено данными `data/theminespirit/observation/observation.json` (`maxContainers`, `maxContainerItems`, `maxOpenBlocks`) |
| `open_blocks` | ближайшие открытые (рядом есть воздух) руды, камень и брёвна: `item`, `pos`, `distance` от духа; не больше `maxOpenBlocks` записей, по возрастанию расстояния |
| `time` | время суток: `day` (день/ночь) и `day_time` (тик суток) |

В `hash` входит только `day`, но не `day_time`: точный тик суток растёт каждую секунду игры и ломал бы сравнение
«мир не изменился». Хэш наблюдения служит ключом кэша решений ИИ.

