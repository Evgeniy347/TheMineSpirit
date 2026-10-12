# Агент-стратег духов

Внешняя программа (`agents/strategist/`, Python 3 без зависимостей) раз в несколько минут решает, чем заняться простаивающему духу,
и ставит ему задачу по REST мода (`docs/API.md`). Мод LLM не вызывает; тактика (путь, бой, отход) детерминирована и живёт в моде.
Стратег меняет только цель и короткий план. Рабочий каталог `STRATEGIST_HOME` (по умолчанию `/opt/minecraft/strategist`) лежит вне
репозитория, содержит ники и в git не попадает.

## Схема цикла

1. `GET /v1/capabilities`, `GET /v1/bots` (контракт — объект `{"bots": [...]}`); неотправленные решения прошлого запуска досылаются с тем
   же `request_id`, если не старше `STRATEGIST_PENDING_TTL`, нет `PAUSE`, дух жив.
2. Для каждого `idle` духа (занятых и мёртвых не трогает никогда; при файле `PAUSE` новых задач нет):
   страж (пауза духа, лимиты вызовов модели и задач — до наблюдения) → `POST /v1/bots/{id}/observe` → промпт → мозг → разбор ответа → локальная проверка плана → `POST /v1/plan` →
   (при отказе проверки — один повтор с текстом ошибки) → страж → свежий `GET /v1/bots/{id}` → `POST /v1/bots/{id}/tasks`.
3. Отслеживание раз в 10 с: `GET /v1/tasks/{id}` до `succeeded|failed|cancelled`; `404` — исход `lost`; 30 мин — `DELETE`, исход `timeout`.

Ответ модели — один JSON: `{"reason","goal","plan"}` или `{"reason","skip":true}`. Отправляются только скрипты из `GET /v1/capabilities`
и из белого списка `agents/strategist/policy.json`, ≤ 32 узлов, ≤ 8 шагов в корне; `goto` не дальше `max_goto_distance` (256) блоков по
горизонтали от духа, `gather.block` — только природный блок из последнего наблюдения (`observed_block_params` + список `natural_blocks`
в `policy.json`: руды, камень, земля, песок, гравий, глина, брёвна, листва, растения; доски, стекло, кирпич и прочие постройки
игрока — отказ). Ошибка одного духа остальных не останавливает.

## Переменные окружения

| Переменная | По умолчанию | Смысл |
|---|---|---|
| `SPIRIT_API` | `http://127.0.0.1:8765` | адрес REST мода |
| `STRATEGIST_HOME` | `/opt/minecraft/strategist` | журнал, состояние, логи, песочница opencode |
| `STRATEGIST_MODE` | `dry` | `dry` — всё, кроме `POST tasks`; `live` — задачи по-настоящему |
| `STRATEGIST_CONFIRM_LIVE` | пусто | `1` — обязательно для `live` (согласие пользователя получено) |
| `STRATEGIST_INTERVAL` | `300` | секунд между циклами, 60..3600 |
| `STRATEGIST_BRAIN` | `opencode` | `opencode` или `command` |
| `STRATEGIST_MODEL`, `STRATEGIST_MODEL_FALLBACK` | `opencode/longcat-2.5-preview-free`, `opencode/nemotron-3-ultra-free` | основная и запасная модель |
| `STRATEGIST_BRAIN_COMMAND` | — | команда мозга при `command` |
| `STRATEGIST_BRAIN_TIMEOUT` | `240` | секунд на ответ модели |
| `STRATEGIST_TASKS_PER_HOUR`, `STRATEGIST_TASK_GAP` | `6`, `60` | задач на духа в час, пауза между задачами |
| `STRATEGIST_BRAIN_CALLS_PER_HOUR` | `30` | вызовов модели в час на всех |
| `STRATEGIST_PLAN_COOLDOWN`, `STRATEGIST_FAILURES_TO_PAUSE`, `STRATEGIST_PAUSE_DURATION` | `1800`, `3`, `1800` | кулдаун провалившегося плана, провалов до паузы духа, длина паузы |
| `STRATEGIST_TASK_POLL`, `STRATEGIST_TASK_TIMEOUT` | `10`, `1800` | опрос задач, тайм-аут задачи |
| `STRATEGIST_PENDING_TTL` | `900` | срок годности неотправленного решения (потом сброс с `alert`) |
| `STRATEGIST_REST_PER_SECOND`, `STRATEGIST_OBSERVE_RADIUS` | `5`, `24` | лимит запросов клиента, радиус наблюдения |
| `STRATEGIST_MAX_NODES`, `STRATEGIST_MAX_ROOT_STEPS` | `32`, `8` | лимиты плана |

Неверное значение — ошибка с именем переменной и допустимым диапазоном, молчаливых умолчаний нет. Остальные (`…JOURNAL_*`, `…MAX_BACKOFF`,
`…PROBE_TIMEOUT`, `…OPENCODE_BIN`) — в `agents/strategist/config.py`.

## Режимы и этапы

- `dry` (по умолчанию): наблюдения, модель, проверки и `POST /v1/plan` идут как обычно; вместо `POST tasks` в журнал пишется `posted`
  с `dry:true, outcome:"would_post"`.
- `live`: задачи ставятся. `405` или `404` при живом духе (маршрута нет) — один `alert` и `dry` до конца запуска; `404` при исчезнувшем духе —
  запись `rejected`, режим не меняется.
- Этапы: 0 — фейковый REST и `smoke`; 1 — `dry` против живого сервера, когда опубликован чеклист 14; 2 — `live` на одном духе, затем
  на всех, первые 10 решений просматривает человек, когда опубликован чеклист 15. Публикация и `live` — только по согласованию с пользователем.

## Защита от спама и зацикливания

≤ 6 задач на духа в час и пауза ≥ 60 с; ≤ 30 вызовов модели в час; тот же план после `failed/cancelled/lost/timeout` — кулдаун 30 мин;
3 провала подряд — пауза духа 30 мин и `alert`; `hash` наблюдения не изменился с прошлого решения — модель не вызывается; провайдер
недоступен — интервал ×2 до 30 мин и проба «ответь OK» вместо полного промпта; запросы к REST ≤ 5/с, `429` — ждать `Retry-After`;
дух стал `busy` до отправки — решение отброшено; `request_id` детерминирован (`uuid5` от духа, цикла и отпечатка плана).

## Журнал и состояние

`STRATEGIST_HOME/journal.jsonl`: строка на событие, поля `schema, ts, cycle, event, bot, model, prompt_sha, reason, plan, request_id,
task_id, outcome`. События: `cycle_start, bot_skipped, observed, decision, rejected, posted, task_state, task_done, brain_failure, alert`.
Ответ модели усечён до 4 КиБ; ротация 10 МиБ × 5 файлов. Примеры: `grep '"event":"rejected"' journal.jsonl | tail`, `scripts/strategist.sh status`.
`state.json` (активные и неотправленные задачи, счётчики стража) пишется атомарно; повреждённый файл переименовывается в
`state.json.corrupt-<время>` с `alert`, молчаливого сброса нет. `strategist.log` — вывод процесса.

## Мозг: сменить модель, подключить Sonnet

`opencode`: `opencode run` в песочнице `STRATEGIST_HOME/sandbox` без флагов обхода разрешений. Бесплатный тариф отвечает `403` («free tier
can only be used from within OpenCode»), если запретить `read/edit/bash` как `deny`, поэтому для них `ask` (в неинтерактивном запуске
отклоняется), `"*": "deny"` стоит первым ключом. Файл проверяется при каждом вызове, а при старте `opencode debug agent strategist` даёт
итоговые права: для каждого включённого инструмента последнее общее правило обязано быть ask/deny, иначе отказ запуска (код 4).
Переменные `OPENCODE_PERMISSION/CONFIG/CONFIG_CONTENT/CONFIG_DIR` из окружения мозга убираются. Выполненный инструмент —
`alert sandbox_breach`, файл `PAUSE`, остановка агента (код 5). Модель меняется переменной, правка кода не нужна; рабочие модели —
в `docs/EXECUTOR-MODELS.md`.
`command`: `STRATEGIST_BRAIN=command STRATEGIST_BRAIN_COMMAND='/путь/к/скрипту'`. Команда читает со stdin JSON `{"system","user","model"}`
и печатает ответ в stdout; код выхода 75 — «модель недоступна» (включает интервал ×2), любой другой ненулевой — ошибка мозга. Так
подключается Sonnet 5.5.

## Белый список скриптов

Добавить имя скрипта в `agents/strategist/policy.json` (`allowed_scripts`), скрипт должен быть в `GET /v1/capabilities`. Проверка
`test_plan_check.py` покажет, что стартовый список не изменился случайно; при осознанной смене поправьте тест.

## Природные блоки: что духу можно добывать

`natural_blocks` в `policy.json` — группы имён блоков без префикса `minecraft:` (руды, камень, земля, сыпучие, лёд, брёвна, листва,
растения, терракота, прочее природное). Правило работает вместе с `observed_block_params`: блок должен быть и в последнем наблюдении,
и в этом списке. Постройки игрока (доски, стекло, кирпич, двери, кровати, сундуки) в наблюдение попадают, но в список не входят,
поэтому `gather.block` с ними — отказ до отправки с текстом для модели. Менять состав — менять и тест
`test_policy_NaturalBlocks_CoverNaturalGroupsAndExcludePlayerMade`: он сторожит обе стороны правила.

## Команды

`scripts/strategist.sh start|stop|status|pause|resume|once|smoke [модель]|logs [N]` (tmux-сессия `mc-strategist`, второй экземпляр
отказывается). `strategist.py … --fixtures agents/strategist/tests/fixtures` поднимает фейковый REST из записанных наблюдений. Тесты:
`scripts/testing/check-strategist.sh` (все строки `OK`) или `cd agents/strategist && python3 -m unittest discover -s tests -t .`.
Сценарии (`tests/scenarios/*.json`) и свойство случайных сценариев идут **без сети**: один и тот же фейковый REST зовётся
внутри процесса (`InProcessClient` вместо сокета), поэтому оба прогона укладываются в предел 10 с, записанный в самих
тестах (чеклист 22, п. 11). Сам фейк по HTTP проверяется `tests/test_fake_rest.py`, чтобы сценарии не зеленели вхолостую.

`smoke` не входит в проверку: он пишет `STRATEGIST_HOME/smoke-<время>.md` (доли валидных ответов, время, колонка для оценки глазами).
