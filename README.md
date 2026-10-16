# The Mine Spirit

Мод для Minecraft **1.21.1** на **NeoForge 21.1.257** (Java 21, Gradle + ModDevGradle).

Это единственный README проекта и карта всех инструкций и скриптов. При добавлении, переносе
или удалении документа или скрипта карта обновляется в том же изменении.

## Команды

Выполнять из корня репозитория.

| Команда | Что делает | Результат |
|---|---|---|
| `scripts/test.sh unit` | быстрые JUnit-тесты (включая свойства jqwik, кроме `@Tag("slow")`) | `OK unit: N тестов, T с` |
| `scripts/test.sh unit --tests "*Guard*"` | только тесты, подходящие под фильтр gradle | `OK unit: N тестов, T с` |
| `scripts/test.sh gametest` | GameTest под замком (один сервер на машину): все тесты | `OK gametest: N тестов, T с` |
| `scripts/test.sh gametest <фильтр>` | GameTest только по имени пачки/класса/теста | `OK gametest: N тестов, T с` |
| `./gradlew build` | компиляция (`-Werror`), быстрые JUnit-тесты, сборка jar | `build/libs/theminespirit-<версия>.jar`, отчёты в `TestResults/unit/` |
| `./gradlew slowTest` | только `@Tag("slow")`; отчёты в `TestResults/slow/` |
| `./gradlew check` | быстрый тир (`test`); `slowTest` запускается отдельно | зелёный прогон быстрых тестов |
| `./gradlew jacocoTestReport` | отчёт покрытия JaCoCo | `TestResults/coverage/` |
| `scripts/gametest.sh` | тонкая обёртка над `scripts/test.sh gametest` (совместимость) | то же, что `scripts/test.sh gametest` |
| `./gradlew runServer` | dev-сервер с модом в `run/` | сервер на порту 25565 |
| `./gradlew runClient` | dev-клиент с модом (нужен графический стол) | окно Minecraft |
| `./gradlew runData` | datagen в `src/generated/resources/` | сгенерированные json |
| `scripts/testing/check-strategist.sh` | тесты агента-стратега (`python3 -m unittest`, сценарии, сторожа) и сценарии надзора `scripts/strategist.sh` на фейковом REST; сервер Minecraft и настоящий opencode не нужны | все строки `OK` |
| `cd agents/strategist && python3 -m unittest discover -s tests -t .` | только Python-тесты агента-стратега | `OK` в последней строке |
| `scripts/publish.sh` | собирает мод и ставит jar на сервер `/opt/minecraft/server`, перезапуская его, если он работал. **Только по согласованию с пользователем** (нужно подтверждение или `-y`) | `Установлен theminespirit-<версия>.jar` |

Первая сборка скачивает Gradle, Minecraft и NeoForge — несколько минут; дальше секунды.

## Команды духов

Подкоманды мода в игре. Требуют прав оператора (уровень 2); `<духи>` — селектор сущностей, из которого берутся духи.
Список сверяется с `SpiritCommands` сторожем `SpiritCommandDocGuardTests`.

| Подкоманда | Что делает |
|---|---|
| `/spirit spawn [pos]` | призывает духа: без `pos` — рядом с игроком, с `pos` — в точке |
| `/spirit observe [radius] [json]` | наблюдение вокруг игрока: хэш и счётчики, с `json` — JSON |
| `/spirit goal <духи> <цель>` | ставит цель выбранным духам |
| `/spirit status <духи>` | показывает состояние задачи каждого духа |
| `/spirit cancel <духи>` | отменяет выполняемую задачу духов |
| `/spirit memory <духи> recall <имя>` | вспоминает запись памяти по имени |
| `/spirit memory <духи> all <тип>` | вспоминает все записи памяти заданного типа |
| `/spirit memory <духи> forget <имя>` | забывает запись памяти по имени |
| `/spirit zone <духи> <центр> <радиус>` | ставит рабочую зону вокруг точки (цели вне неё отвергаются) |
| `/spirit zone <духи> clear` | снимает рабочую зону |
| `/spirit home <духи> set [x y z]` | ставит дом духа (без координат — на его месте) |
| `/spirit home <духи> clear` | снимает дом духа |
| `/spirit camp <духи> set [x y z]` | ставит лагерь похода духа (без координат — на его месте) |
| `/spirit camp <духи> clear` | снимает лагерь похода |
| `/spirit decisions <духи>` | показывает журнал решений тактики (последние записи) |
| `/spirit tables` | показывает таблицы данных мода: источник, время, хэш, ошибки |
| `/spirit presets` | показывает библиотеку готовых построек: id, этап, тип, габариты, вход, материалы |
| `/spirit craters` | показывает запомненные воронки криперов: id, центр, радиус, уровень края |
| `/spirit invite <id> wait/go/join` | ответ на приглашение в экспедицию: подождите (+60 с), идите без меня, иду с вами |

## Конфиг

Серверный конфиг мода — `config/theminespirit-server.toml` (создаётся при первом запуске сервера).

| Параметр | Значение | Смысл |
|---|---|---|
| `spirit.chunkLoadingRadius` | `-1` (по умолчанию), `1`–`32` | Радиус `R` квадрата принудительно загружаемых и тикающих чанков вокруг духа. `-1` — как simulation distance сервера: дух держит столько же, сколько игрок. `(2R+1)²` чанков на духа |

## REST-мост

Внешний ИИ-агент управляет духами по HTTP: мост живёт внутри мода и поднимается при старте выделенного сервера.
Контракт — [docs/API.md](docs/API.md). Реализованы все конечные точки: `capabilities`, `bots`, `bots/{id}`, `observe`, задачи, события и `plan`.

Конфиг — `serverconfig/theminespirit-rest.toml` (в каталоге мира, создаётся при первом запуске сервера):

| Параметр | По умолчанию | Смысл |
|---|---|---|
| `rest.enabled` | `true` | запускать ли мост |
| `rest.port` | `8765` | TCP-порт, `1024`–`65535` (8099 занят раздачей клиента, 25565 — игра) |
| `rest.bindAddress` | `0.0.0.0` | адрес привязки: `0.0.0.0` — вся локальная сеть, `127.0.0.1` — только этот компьютер |

Авторизации нет: сеть доверенная (решение пользователя), **порт открыт всей сети**, до которой достаёт адрес привязки;
ограничивайте доступ адресом и файрволом. Проверка: `curl http://<хост>:<порт>/v1/bots`. Занятый порт или неверный адрес
привязки — ERROR в логе сервера, Minecraft-сервер при этом продолжает работать.

Задача духу, слежение и отмена (`BOT` — UUID духа из `GET /v1/bots`):

```bash
H=http://<хост>:<порт>; BOT=<uuid духа>
curl -s -X POST $H/v1/plan -d '{"plan":{"type":"script","script":"goto","params":{"x":10,"y":64,"z":-3}}}'
curl -s -X POST $H/v1/bots/$BOT/tasks -d '{"request_id":"'$(uuidgen)'","plan":{"type":"script","script":"goto","params":{"x":10,"y":64,"z":-3}}}'
curl -s $H/v1/tasks/t-1                  # состояние и прогресс
curl -N $H/v1/tasks/t-1/events           # поток событий (SSE) до завершения задачи
curl -N -H 'Last-Event-ID: 3' $H/v1/tasks/t-1/events   # продолжить после обрыва
curl -s -X DELETE $H/v1/tasks/t-1        # отмена, повтор безопасен
```

## Политики безопасности

Политики включены у каждого духа всегда и выше тактики и стратегии: рефлексы (лава и огонь, горение, вода у духа без задачи, нехватка воздуха, крипер и активный TNT,
клетка портала, выход за рабочую зону) прерывают задачу, не отменяя её, и через 10 безопасных тиков отдают тик обратно; запреты не дают ломать защищённые блоки, чужие постройки
и блоки в защищённых зонах и копать под собой над пропастью глубже 2 блоков. Дух не ходит по лаве, огню, кактусу, ягодам, магме, паутине и рыхлому снегу, воду считает дорогой, а не запретной.

Конфиг — `serverconfig/theminespirit-policy.toml`:

| Параметр | По умолчанию | Смысл |
|---|---|---|
| `policy.protectedZones` | `[]` | до 64 строк «`minecraft:overworld x1 y1 z1 x2 y2 z2`»: в зоне дух не ломает и не ставит блоки; ошибка в строке останавливает загрузку конфига |

Рабочая зона духа: `/spirit zone <духи> <x> <y> <z> <радиус>` ставит куб вокруг точки (цели вне него отвергаются, вышедший дальше 3 блоков возвращается), `/spirit zone <духи> clear` снимает.
Списки защищённых и природных блоков и порог «здания» (6 искусственных блоков среди 26 соседей) — `src/main/resources/data/theminespirit/policy/protection.json`.

## Журнал решений тактики

Каждый дух ведёт журнал своих решений: почему он дрался, отходил или игнорировал врага. Запись появляется только
при смене решения, режима, цели или приказа координатора (и не чаще одной в секунду на духа):
`[tactics] тик 56199; дух spirit (01e686da-…); решение IGNORE → FIGHT; по оценке, факторы WIN_MARGIN +1.06, PROXIMITY +0.23; врагов рядом 1 (ближайший minecraft:husk в 5.0), здоровье 20/20, броня 0, бьёт: оружие ближнего боя`.
Те же записи держатся в памяти (последние 32 на духа) и доступны двумя способами:

```bash
curl -s 'http://<хост>:<порт>/v1/spirits/<uuid духа>/decisions?limit=5'   # для ИИ-агента, docs/API.md
/spirit decisions <дух>                                                  # последние 5 записей в чат
```

Подробнее — docs/TACTICS.md → «Адаптер в игре» (журнал решений).

## Документация

| Файл | Назначение |
|---|---|
| [AGENTS.md](AGENTS.md) | карта правил для разработчика и AI-агента, решения пользователя |
| [todo.md](todo.md) | примерный план работ, техдолг и открытые решения |
| [tasks/](tasks/) | подробные чеклисты блоков из todo.md для opencode; удаляются после выполнения и ревью |
| [CLAUDE.md](CLAUDE.md) | вход для Claude Code, отсылает к AGENTS.md |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | соглашения кода |
| [docs/TESTING.md](docs/TESTING.md) | тесты: JUnit, GameTest, сторожа |
| [docs/GIT.md](docs/GIT.md) | коммиты и работа с git |
| [docs/PUBLISH.md](docs/PUBLISH.md) | публикация мода и перезапуск сервера, согласование |
| [docs/EXECUTOR-MODELS.md](docs/EXECUTOR-MODELS.md) | модели-исполнители opencode: проба и качество на заданиях |
| [docs/LEAD-AGENT.md](docs/LEAD-AGENT.md) | роль главного агента и ревьюера: задания для opencode, ревью, сторожа |
| [docs/TACTICS.md](docs/TACTICS.md) | ядро тактики боя: факты, факторы, правила, режимы, схема JSON, trace |
| [opencode.json](opencode.json) | настройки opencode в проекте: модель big-pickle, полный доступ, вопросы запрещены |
| [docs/STRATEGIST.md](docs/STRATEGIST.md) | агент-стратег духов (`agents/strategist`): цикл, режимы, журнал, лимиты, смена модели |
| [docs/API.md](docs/API.md) | контракт REST-сервиса духов (bridge), формат плана |
| [docs/HOT-RELOAD.md](docs/HOT-RELOAD.md) | горячая перезагрузка таблиц: датапак мира + /reload, команда /spirit tables |
| [docs/research/README.md](docs/research/README.md) | карта знаний: что переиспользовать из 13 готовых проектов, лицензии |

## Скрипты

| Скрипт | Назначение | Проверка |
|---|---|---|
| [scripts/publish.sh](scripts/publish.sh) | сборка и установка мода на сервер (только по согласованию с пользователем) | в `/opt/minecraft/server/mods/` лежит свежий jar |
| [scripts/test.sh](scripts/test.sh) | единый запуск тестов: `unit [--tests <фильтр>]` и `gametest [имя пачки или теста…]`. Полный лог — в `TestResults/logs/`, на экран — итог. Пределы времени (переменные `THEMINESPIRIT_*_WARN_SECONDS` / `THEMINESPIRIT_*_STOP_SECONDS`): unit — предупреждение 3 мин, стоп 6 мин; gametest — предупреждение 5 мин, стоп 10 мин (жёсткий стоп, код 124). GameTest — под замком «один сервер на машину» | `OK unit: N тестов, T с` / `OK gametest: N тестов, T с`; `FAIL …` + первая ошибка + короткий стек + путь к логу; код 75 — идёт чужой GameTest; код 124 — стоп по пределу |
| [scripts/gametest.sh](scripts/gametest.sh) | тонкая обёртка над `scripts/test.sh gametest` (совместимость) | то же, что `scripts/test.sh gametest` |
| [scripts/mem-guard.sh](scripts/mem-guard.sh) | Сторож памяти на domovoy при параллельных исполнителях: свободно < 15 % — SIGSTOP самой тяжёлой java сборки, > 25 % — SIGCONT | строка в журнале TestResults/mem-guard.log на каждое действие |
| [scripts/lead/oc-watch.sh](scripts/lead/oc-watch.sh) | слежение главного агента за исполнителями (ПК и domovoy): завершения, зависания, ресурсы, сироты — только события; запуск через Monitor (docs/LEAD-AGENT.md → «Присмотр — инструменты») | строка на каждое событие |
| [scripts/lead/res-pc.ps1](scripts/lead/res-pc.ps1) | ресурсы ПК и уборка сирот исполнителей (только процессы проекта и `find /`) | строки только при проблеме |
| [scripts/lead/orphan-sweep.sh](scripts/lead/orphan-sweep.sh) | уборка сирот исполнителей на domovoy (PPID 1, worktree задач или `find /`; Gradle-демон и игровой сервер не трогает) | строка на каждое убийство |
| [scripts/lead/keepawake.ps1](scripts/lead/keepawake.ps1) | ПК не засыпает N часов, пока работает процесс (SetThreadExecutionState, без изменения настроек питания) | процесс жив |
| [scripts/opencode-task.sh](scripts/opencode-task.sh) | задание для opencode (big-pickle) с надзором: при сбое провайдера (free usage, rate limit, квота, 429), сетевом сбое (нет соединения, «Endpoint is unavailable») или молчании модели ≥ 5 мин — перезапуск той же сессии (5, 5, 10, 30, 60 с), затем режим ожидания: раз в 120 с (`OPENCODE_PROBE_INTERVAL`) короткая проба модели из пустого каталога, провайдер вернулся — та же сессия продолжается, предел ожидания `OPENCODE_WAIT_LIMIT` 7200 с, потом стоп; сбои пишутся в `TestResults/opencode/failures.log`; новое задание не стартует в грязном дереве (там может работать другой агент), `OPENCODE_ALLOW_DIRTY=1` — осознанно | код выхода 0 и `Сессия: ses_…`; 75 (предел ожидания исчерпан) — сообщить пользователю |
| [scripts/testing/check-opencode-task.sh](scripts/testing/check-opencode-task.sh) | регрессионная проверка `opencode-task.sh` на подделке [scripts/testing/fake-opencode.sh](scripts/testing/fake-opencode.sh): сбои провайдера, сетевые сбои (json «Cannot connect», stderr «Endpoint is unavailable» опознаются как сбой провайдера, а не молчание), режим ожидания (провайдер вернулся / предел исчерпан), молчание модели, долгая команда, ложные срабатывания на файлах и логе INFO | все строки `OK` |
| [scripts/strategist.sh](scripts/strategist.sh) | надзор за агентом-стратегом: `start`, `stop`, `status`, `pause`, `resume`, `once`, `smoke [модель]`, `logs [N]`; tmux-сессия `mc-strategist`, второй экземпляр отказывается, `live` только с `STRATEGIST_CONFIRM_LIVE=1` (после согласия пользователя) | `status` показывает «агент: работает», режим, модель и счётчики за час |
| [scripts/testing/check-strategist.sh](scripts/testing/check-strategist.sh) | регрессионная проверка агента-стратега и `strategist.sh` на временном `STRATEGIST_HOME` | все строки `OK` |
| [scripts/opencode-status.sh](scripts/opencode-status.sh) | короткая сводка о текущем задании opencode: жив ли, давно ли писал, последние действия, состояние git | строка `opencode: работает; …` |
| [scripts/opencode-status-notify.sh](scripts/opencode-status-notify.sh) | сводка в тему «Оповещения» Telegram: прогресс плана, работающие исполнители (tmux `oc-*`), сбои модели; таймер раз в 5 мин, молчит без исполнителей | `sudo systemd-run --unit=mc-opencode-status --on-calendar='*:0/5' <путь>` |
| [scripts/progress.sh](scripts/progress.sh) | общий прогресс плана по `todo.md`: отмеченные `[x]` / все пункты | строка `N% (x из всего)` |
| [scripts/prove-red.sh](scripts/prove-red.sh) | `prove-red.sh <fix-коммит> [фильтр]`: доказывает, что тест fix-коммита краснеет на родителе и зеленеет на самом коммите; временный worktree в `TestResults/prove-red/<хэш>/`, основное дерево не трогает; GameTest не проверяет | строка `OK красный→зелёный` на каждый тест-класс, код выхода 0; `FAIL …` и код 1 — тест не доказывает исправление |
| [scripts/install-git-hooks.sh](scripts/install-git-hooks.sh) | подключает git-хуки из `scripts/git-hooks/` (один раз после клонирования) | `git config core.hooksPath` → `scripts/git-hooks` |
| [scripts/git-hooks/reference-transaction](scripts/git-hooks/reference-transaction) | сторож истории: запрещает `--amend`, `rebase`, `reset` назад, `branch -f`, `stash` (docs/GIT.md) | `git commit --amend` отклоняется с объяснением |
| [scripts/git-hooks/pre-rebase](scripts/git-hooks/pre-rebase) | ранний отказ `rebase` с объяснением | `git rebase` отклоняется |
| [scripts/git-hooks/commit-msg](scripts/git-hooks/commit-msg) | сторож формата сообщения коммита (docs/GIT.md) | `git commit -m "build: x"` отклоняется с объяснением |
| [scripts/git-hooks/pre-commit](scripts/git-hooks/pre-commit) | сторож зелёного коммита: при изменениях в `src/`, `build.gradle`, `gradle.properties`, `settings.gradle` прогоняет `./gradlew test` и отклоняет коммит при красном; документы, скрипты и слияния — без прогона (docs/GIT.md) | коммит с падающим тестом отклоняется с объяснением |
| [scripts/testing/check-pre-commit.sh](scripts/testing/check-pre-commit.sh) | регрессионная проверка `pre-commit` на временном клоне: красный тест, документ, зелёный тест, `git commit -- путь`, незастейдженная правка, слияние | все строки `OK` |
| [scripts/testing/check-gametest-lock.sh](scripts/testing/check-gametest-lock.sh) | регрессионная проверка замка `gametest.sh` на фейковом gradlew без Gradle: параллельные запуски не пересекаются, протухший замок снимает ровно один из двух ожидающих (атомарное переименование), оба отрабатывают по очереди, живой замок даёт код 75, код gradlew возвращается, `trap` снимает замок | все строки `OK` |
| `gradlew`, `gradlew.bat` | Gradle wrapper | `./gradlew --version` |

## Структура

```
agents/strategist/                    ← агент-стратег духов (Python, стандартная библиотека), docs/STRATEGIST.md
src/main/java/com/theminespirit/
├── TheMineSpirit.java   ← точка входа @Mod
├── core/                ← логика без Minecraft/NeoForge, тестируется JUnit
└── gametest/            ← GameTest-классы
src/main/resources/data/theminespirit/structure/  ← шаблоны структур для GameTest
src/main/templates/META-INF/neoforge.mods.toml    ← метаданные мода (подставляются из gradle.properties)
src/test/java/                                    ← JUnit-тесты
```

Версии Minecraft, NeoForge и зависимостей — в `gradle.properties`.

## Локальные рабочие каталоги

`temp-scripts/` и `TestResults/` игнорируются Git; после клонирования их может не быть:
`mkdir -p temp-scripts TestResults`.
