# Агент-стратег духов

Внешняя программа (`agents/strategist/`, Python 3 без зависимостей) раз в несколько минут решает, чем заняться простаивающему духу,
и ставит ему задачу по REST мода (`docs/API.md`). Мод LLM не вызывает; тактика (путь, бой, отход) детерминирована и живёт в моде.
Стратег меняет только цель и короткий план. Рабочий каталог `STRATEGIST_HOME` (по умолчанию `/opt/minecraft/strategist`) лежит вне
репозитория, содержит ники и в git не попадает.

## Схема цикла

1. `GET /v1/capabilities`, `GET /v1/bots`; неотправленные решения прошлого запуска досылаются с тем же `request_id`.
2. Для каждого `idle` духа (занятых и мёртвых не трогает никогда; при файле `PAUSE` новых задач нет):
   страж → `POST /v1/bots/{id}/observe` → промпт → мозг → разбор ответа → локальная проверка плана → `POST /v1/plan` →
   (при отказе проверки — один повтор с текстом ошибки) → страж → свежий `GET /v1/bots/{id}` → `POST /v1/bots/{id}/tasks`.
3. Отслеживание раз в 10 с: `GET /v1/tasks/{id}` до `succeeded|failed|cancelled`; `404` — исход `lost`; 30 мин — `DELETE`, исход `timeout`.

Ответ модели — один JSON: `{"reason","goal","plan"}` или `{"reason","skip":true}`. Отправляются только скрипты из `GET /v1/capabilities`
и из белого списка `agents/strategist/policy.json`, ≤ 32 узлов, ≤ 8 шагов в корне. Ошибка одного духа остальных не останавливает.

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
| `STRATEGIST_REST_PER_SECOND`, `STRATEGIST_OBSERVE_RADIUS` | `5`, `24` | лимит запросов клиента, радиус наблюдения |
| `STRATEGIST_MAX_NODES`, `STRATEGIST_MAX_ROOT_STEPS` | `32`, `8` | лимиты плана |

Неверное значение — ошибка с именем переменной и допустимым диапазоном, молчаливых умолчаний нет. Остальные (`…JOURNAL_*`, `…MAX_BACKOFF`,
`…PROBE_TIMEOUT`, `…OPENCODE_BIN`) — в `agents/strategist/config.py`.

## Режимы и этапы

- `dry` (по умолчанию): наблюдения, модель, проверки и `POST /v1/plan` идут как обычно; вместо `POST tasks` в журнал пишется `posted`
  с `dry:true, outcome:"would_post"`.
- `live`: задачи ставятся. Если `POST tasks` отвечает `404/405` (маршрута нет), агент пишет один `alert` и до конца запуска работает как `dry`.
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

`opencode`: `opencode run` в песочнице `STRATEGIST_HOME/sandbox` без флага автоодобрения. Бесплатный тариф отвечает `403` («free tier can only
be used from within OpenCode»), если запретить `read/edit/bash` как `deny`, поэтому для них `ask` (в неинтерактивном запуске отклоняется),
остальные инструменты `deny`; песочница проверяется при каждом вызове. Модель меняется переменной, правка кода не нужна; рабочие модели —
в `docs/EXECUTOR-MODELS.md`.
`command`: `STRATEGIST_BRAIN=command STRATEGIST_BRAIN_COMMAND='/путь/к/скрипту'`. Команда читает со stdin JSON `{"system","user","model"}`
и печатает ответ в stdout; код выхода 75 — «модель недоступна» (включает интервал ×2), любой другой ненулевой — ошибка мозга. Так
подключается Sonnet 5.5.

## Белый список скриптов

Добавить имя скрипта в `agents/strategist/policy.json` (`allowed_scripts`), скрипт должен быть в `GET /v1/capabilities`. Проверка
`test_plan_check.py` покажет, что стартовый список не изменился случайно; при осознанной смене поправьте тест.

## Команды

`scripts/strategist.sh start|stop|status|pause|resume|once|smoke [модель]|logs [N]` (tmux-сессия `mc-strategist`, второй экземпляр
отказывается). `strategist.py … --fixtures agents/strategist/tests/fixtures` поднимает фейковый REST из записанных наблюдений. Тесты:
`scripts/testing/check-strategist.sh` (все строки `OK`) или `cd agents/strategist && python3 -m unittest discover -s tests -t .`.
`smoke` не входит в проверку: он пишет `STRATEGIST_HOME/smoke-<время>.md` (доли валидных ответов, время, колонка для оценки глазами).
