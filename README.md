# The Mine Spirit

Мод для Minecraft **1.21.1** на **NeoForge 21.1.257** (Java 21, Gradle + ModDevGradle).

Это единственный README проекта и карта всех инструкций и скриптов (карту и ссылки держит `DocsMapGuardTests`).

## Команды

Выполнять из корня репозитория.

| Команда | Что делает | Результат |
|---|---|---|
| `scripts/test.sh unit` | быстрые JUnit-тесты (включая свойства jqwik, кроме `@Tag("slow")`) | `OK unit: N тестов, T с` |
| `scripts/test.sh unit --tests "*Guard*"` | только тесты, подходящие под фильтр gradle | `OK unit: N тестов, T с` |
| `scripts/test.sh gametest` | GameTest под замком шардами: общий набор без ручных демо-аудитов | `OK gametest: N тестов, T с` |
| `scripts/test.sh gametest --full` | то же с пределом полного прогона (демо-аудиты и здесь не идут) | `OK gametest: N тестов, T с` |
| `scripts/test.sh gametest demo_audit_<постройка>` | ручной аудит одной постройки (только явно) | `OK gametest: N тестов, T с` |
| `scripts/test.sh gametest <фильтр>` | GameTest только по имени пачки/класса/теста | `OK gametest: N тестов, T с` |
| `./gradlew build` | компиляция (`-Werror`), быстрые JUnit-тесты, сборка jar | `build/libs/theminespirit-<версия>.jar`, отчёты в `TestResults/unit/` |
| `./gradlew slowTest` | только `@Tag("slow")`; отчёты в `TestResults/slow/` |
| `./gradlew check` | быстрый тир (`test`); `slowTest` запускается отдельно | зелёный прогон быстрых тестов |
| `./gradlew jacocoTestReport` | отчёт покрытия JaCoCo | `TestResults/coverage/` |
| `scripts/gametest.sh` | тонкая обёртка над `scripts/test.sh gametest` (совместимость) | то же, что `scripts/test.sh gametest` |
| `./gradlew runServer` | dev-сервер с модом в `run/` | сервер на порту 25565 |
| `./gradlew runClient` | dev-клиент с модом (нужен графический стол) | окно Minecraft |
| `./gradlew runData` | datagen в `src/generated/resources/` | сгенерированные json |
| `scripts/schematic.sh check [файл\|id…]` | разбор и сторожа текстовых построек (`schematics/*.txt`) тем же кодом, что в моде | `Все постройки прошли: N` |
| `scripts/schematic.sh show <файл\|id> [поворот]` | шапка, материалы и срезы по слоям постройки (с поворотом) | срезы `y0…` и строки `НАРУШЕНИЕ`, если есть |
| `scripts/testing/check-strategist.sh` | тесты агента-стратега (`python3 -m unittest`, сценарии, сторожа) и сценарии надзора `scripts/strategist.sh` на фейковом REST; сервер Minecraft и настоящий opencode не нужны | все строки `OK` |
| `cd agents/strategist && python3 -m unittest discover -s tests -t .` | только Python-тесты агента-стратега | `OK` в последней строке |
| `scripts/publish.sh` | публикует мод одним запуском: сборка, раскладка `server/`, снимок мира в архив, jar на сервер, пак клиента, страница «Сервер», тег; `--temp` — живая сборка без тега, `--sync` — согласовать пак и страницу с `mods/`. **Только по согласованию с пользователем** (нужно подтверждение или `-y`) | `Установлен theminespirit-<версия>.jar` |

Первая сборка скачивает Gradle, Minecraft и NeoForge — несколько минут; дальше секунды.

## Команды духов

Подкоманды мода в игре. Требуют прав оператора (уровень 2), кроме `/spirit version`; `<духи>` — селектор сущностей, из которого берутся духи.
Список сверяется с `SpiritCommands` сторожем `SpiritCommandDocGuardTests`.

| Подкоманда | Что делает |
|---|---|
| `/spirit version` | показывает версию мода (0.1.N, где N — число коммитов сборки); прав оператора не требует |
| `/spirit spawn [pos]` | призывает духа: без `pos` — рядом с игроком, с `pos` — в точке |
| `/spirit observe [radius] [json]` | наблюдение вокруг игрока: хэш и счётчики, с `json` — JSON |
| `/spirit audit floating` | висящие без опоры объекты на участке поселения: координаты, вид, высота над землёй, вне плана |
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
| `/spirit board` | показывает очередь заданий доски поселения: кейс, приоритет, владелец, статус, нехватка |
| `/spirit board cancel <id>` | отменяет задание доски и зависимые от него (как `DELETE /v1/board/tasks/{id}`) |
| `/spirit tables` | показывает таблицы данных мода: источник, время, хэш, ошибки |
| `/spirit presets` | показывает библиотеку готовых построек: id, этап, тип, габариты, вход, материалы |
| `/spirit craters` | показывает запомненные воронки криперов: id, центр, радиус, уровень края |
| `/spirit plan` | показывает план поселения: площадь, контур и калитки, места построек этапа с готовностью в % |
| `/spirit invite <id> wait/go/join` | ответ на приглашение в экспедицию: подождите (+60 с), идите без меня, иду с вами |
| `/spirit unprotect <x1 y1 z1 x2 y2 z2> [причина]` | снимает защиту построек игрока в области (постройки вне её защищены как раньше) |
| `/spirit unprotect list` | показывает разрешённые области: id, мир, куб, кто попросил, причина, срок |
| `/spirit unprotect remove <id>` | снимает разрешение: постройки области снова защищены |
| `/spirit demo <постройка> [уровень] [x y z] [поворот]` | демонстрация стройки в креативе: очищает площадку, ставит сундук с материалами и ведёт духа |
| `/spirit demo <постройка> upgrade <с уровня> <на уровень> [x y z] [поворот]` | строит младший уровень, затем улучшает его на месте |
| `/spirit demo list` | показывает постройки демо-стройки и их уровни |
| `/spirit demo stop` | отменяет демо-стройки уровня и убирает духов |

## Конфиг

Серверный конфиг мода — `config/theminespirit-server.toml` (создаётся при первом запуске сервера).

| Параметр | Значение | Смысл |
|---|---|---|
| `spirit.chunkLoadingRadius` | `-1` (по умолчанию), `1`–`32` | Радиус `R` квадрата принудительно загружаемых и тикающих чанков вокруг духа, пока на сервере есть игрок. `-1` — как simulation distance сервера: дух держит столько же, сколько игрок. `(2R+1)²` чанков на духа |

Без подключённых игроков духи и чанки поселения выгружаются полностью. Вход первого игрока (в любом измерении)
ставит тикеты домов и лагерей поселения, выход последнего — снимает; после перезапуска сервера действует то же правило.

## REST-мост

Внешний ИИ-агент управляет духами по HTTP: мост живёт внутри мода и поднимается при старте выделенного сервера.
Контракт — [docs/api/README.md](docs/api/README.md). Реализованы все конечные точки: `capabilities`, `bots`, `bots/{id}`, `observe`, задачи, события и `plan`.

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
curl -s 'http://<хост>:<порт>/v1/spirits/<uuid духа>/decisions?limit=5'   # для ИИ-агента, docs/api/README.md
/spirit decisions <дух>                                                  # последние 5 записей в чат
```

Подробнее — docs/tactics/README.md → «Адаптер в игре» (журнал решений).

## Документация

| Файл | Назначение |
|---|---|
| [AGENTS.md](AGENTS.md) | карта правил, принцип «правило — сторож, а не текст», решения пользователя |
| [todo.md](todo.md) | открытые задачи плана и прогресс |
| [tasks/](tasks/) | чеклисты блоков в работе; удаляются после приёмки |
| [CLAUDE.md](CLAUDE.md) | вход для Claude Code, отсылает к AGENTS.md |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md), [docs/TESTING.md](docs/TESTING.md), [docs/GIT.md](docs/GIT.md) | код, тесты, git |
| [docs/PUBLISH.md](docs/PUBLISH.md) | публикация мода (только по согласованию) |
| [docs/LEAD-AGENT.md](docs/LEAD-AGENT.md), [docs/EXECUTOR-MODELS.md](docs/EXECUTOR-MODELS.md) | главный агент, приёмка, модели-исполнители |
| [docs/api/README.md](docs/api/README.md) | контракт REST-сервиса духов, формат плана и построек |
| [docs/tactics/README.md](docs/tactics/README.md), [docs/STRATEGIST.md](docs/STRATEGIST.md), [docs/HOT-RELOAD.md](docs/HOT-RELOAD.md) | тактика, стратег, горячая перезагрузка таблиц |
| [docs/research/README.md](docs/research/README.md) | карта знаний о похожих проектах и лицензиях |
| [docs/settlement/README.md](docs/settlement/README.md), [docs/SIMPLIFICATIONS.md](docs/SIMPLIFICATIONS.md) | каталог построек поселения и стиль; согласованные упрощения механик |
| [opencode.json](opencode.json) | настройки opencode в проекте |

## Скрипты

| Скрипт | Назначение |
|---|---|
| [scripts/test.sh](scripts/test.sh) | единственный запуск тестов: `unit [--tests …]`, `gametest [пачка…]`; лог — `TestResults/logs/`, замок GameTest и пределы — в шапке скрипта; коды 0/1/2/75/124 |
| [scripts/gametest.sh](scripts/gametest.sh) | обёртка над `scripts/test.sh gametest` (совместимость) |
| [scripts/prove-red.sh](scripts/prove-red.sh) | тест `fix:`-коммита краснеет на родителе и зеленеет на коммите |
| [scripts/schematic.sh](scripts/schematic.sh) | постройки текстом: `show` (материалы, срезы), `check` (сторожа) |
| [scripts/publish.sh](scripts/publish.sh) | сборка и публикация мода целиком (раскладка `server/`, снимок мира в архив, сервер, пак клиента, страница, тег) — только по согласованию; `--temp`/`--sync` |
| [server/](server/README.md), [server/scripts/world-snapshot.sh](server/scripts/world-snapshot.sh), [server/scripts/world-restore.sh](server/scripts/world-restore.sh) | скрипты и конфиги сервера (источник для `publish.sh`), снимок мира в tar-архив и откат; cron каждые 10 минут; мир в git не хранится |
| [scripts/server-cleanup.sh](scripts/server-cleanup.sh) | уборка сервера по cron: `tmp/` старше суток, `logs/` старше 14 дней |
| [scripts/live-debug.sh](scripts/live-debug.sh), [scripts/live-debug/](scripts/live-debug/), [scripts/dev-server.sh](scripts/dev-server.sh), [scripts/dev-server/](scripts/dev-server/) | живая отладка: копия сервера на ПК, замок, hotswap, eval, брейкпоинты; [docs/LIVE-DEBUG.md](docs/LIVE-DEBUG.md) |
| [scripts/progress.sh](scripts/progress.sh) | прогресс плана: «сделано N» из `todo.md` + открытые пункты |
| [scripts/opencode-task.sh](scripts/opencode-task.sh), [scripts/opencode-start.sh](scripts/opencode-start.sh) | исполнитель opencode с надзором (ПК / сервер в tmux) |
| [scripts/opencode-status.sh](scripts/opencode-status.sh), [scripts/opencode-status-notify.sh](scripts/opencode-status-notify.sh) | статус исполнителя; сводка в Telegram раз в 5 мин |
| [scripts/opencode-pick-model.sh](scripts/opencode-pick-model.sh) | выбор бесплатной модели opencode по свежим сбоям и загрузке |
| [scripts/strategist.sh](scripts/strategist.sh) | надзор агента-стратега (`start/stop/status/once/smoke/logs`) |
| [scripts/mem-guard.sh](scripts/mem-guard.sh) | сторож памяти domovoy (SIGSTOP/SIGCONT тяжёлой java) |
| [scripts/lead/](scripts/lead/) | инструменты главного агента: `oc-watch.sh`, `chkq.sh` (очередь приёмки), `agent-list.sh`, `nice-guard.sh`, `orphan-sweep.sh`, `orphan-sweep-win.sh`, `res-pc.ps1`, `keepawake.ps1` |
| [scripts/install-git-hooks.sh](scripts/install-git-hooks.sh), [scripts/git-hooks/](scripts/git-hooks/) | хуки: формат коммита, зелёный коммит, запрет перезаписи истории |
| [scripts/testing/](scripts/testing/) | проверки скриптов на подделках: `check-test-runner.sh`, `check-gametest-lock.sh`, `check-opencode-task.sh`, `check-orphan-sweep-win.sh`, `check-pre-commit.sh`, `check-commit-msg.sh`, `check-history-hooks.sh`, `check-chkq.sh`, `check-strategist.sh` |

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
