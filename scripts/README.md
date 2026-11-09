# Скрипты

Карта каталога: что запускается, где и меняет ли мир или файлы. Пути к исходникам не дублируются:
`scripts/module-paths.sh` читает `config/layout.properties` и `settings.gradle` (единственный список модулей),
поэтому скрипт не ломается при переезде файлов. Новый скрипт появляется в этой таблице
(`DocsMapGuardTests`, `ScriptsMapGuardTests`).

| Скрипт (ссылка на файл) | Что делает | Пример запуска | Где работает | Меняет ли что-либо |
|---|---|---|---|---|
| [test.sh](test.sh) | unit и GameTest через один вход, логи в `TestResults/logs/` | `scripts/test.sh unit --changed` | ПК, сервер | только `build/` и `TestResults/` |
| [changed-modules.sh](changed-modules.sh) | путь → модуль → зависящие модули для `unit --changed` | `scripts/changed-modules.sh src/main/java` | ПК, сервер | кэш в `build/tmp` |
| [gametest.sh](gametest.sh) | обёртка над `test.sh gametest` | `scripts/gametest.sh --full` | ПК, сервер | миры `build/gametest-run` |
| [module-paths.sh](module-paths.sh) | общие переменные путей для скриптов | `scripts/module-paths.sh` | ПК, сервер | нет |
| [python-bin.sh](python-bin.sh) | один выбор python, пропускает заглушки WindowsApps | `. scripts/python-bin.sh && python_bin` | ПК, сервер | нет |
| [logq.sh](logq.sh) | поиск по базе логов: клетка, дух, задание, провалы | `scripts/logq.sh cell -181,68,-518 2` | ПК, сервер, копия | дочитывает JSONL в базу (на основном сервере — cron) |
| [logdb/logq.py](logdb/logq.py) | команды `logq`: `cell`, `spirit`, `failures`, `schema` | `python3 scripts/logdb/logq.py --db logs/events.db health` | ПК, сервер, копия | только база логов |
| [logdb/loader.py](logdb/loader.py) | загрузчик JSONL и старых логов в SQLite (WAL, файл+смещение); на сервере cron дочитывает JSONL раз в минуту | `python3 scripts/logdb/loader.py --db logs/events.db --events logs/events` | ПК, сервер, копия | пишет `logs/events.db` |
| [logdb/import_legacy.py](logdb/import_legacy.py) | импорт старых логов в базу, сверка строк с событиями; удаляет только сошедшиеся `*.log.gz` | `python3 scripts/logdb/import_legacy.py --db events.db --logs logs --delete-verified` | ПК, сервер | пишет базу и отчёт, удаляет сверенные `*.log.gz` |
| [logdb/schema.json](logdb/schema.json) | единственное описание таблиц, причин и типов JSONL | `python3 scripts/logdb/logq.py --db events.db schema` | ПК, сервер | нет |
| [logdb/tests/test_loader.py](logdb/tests/test_loader.py) | тесты загрузчика, импорта и `logq` на маленькой базе | `python -m unittest tests.test_loader` | ПК, сервер | временная база |
| [logdb/fixtures/](logdb/fixtures/) | обезличенные старые строки для тестов разбора | `scripts/test.sh unit --tests "*Logdb*"` | ПК, сервер | нет |
| [live.sh](live.sh) | духи, доска, срез мира, журнал, фикстура, проверка | `scripts/live.sh status` | ПК, сервер, копия | фикстура в `mech/movement/...` |
| [lead/live.py](lead/live.py) | реализация `live.sh` и выбор цели (копия или сервер) | `scripts/live.sh spirit spirit-1` | ПК, сервер, копия | нет для `status`/`log` |
| [dev-server.sh](dev-server.sh) | своя копия сервера: `up`, `down`, `status`, `list` | `scripts/dev-server.sh up` | ПК | `run-live/<имя>` |
| [dev-server/fetch-jbr.py](dev-server/fetch-jbr.py) | разовая загрузка JBR SDK для копии | `python scripts/dev-server/fetch-jbr.py` | ПК | `.live-server` |
| [live-debug.sh](live-debug.sh) | hotswap, eval и брейкпоинты в копии | `scripts/live-debug.sh --run run-live/a eval '1+1'` | ПК | классы копии |
| [live-debug/LiveDebug.java](live-debug/LiveDebug.java) | клиент JDI для `live-debug.sh` | `scripts/live-debug.sh --run run-live/a init` | ПК | процесс копии |
| [live-debug/LiveShell.java](live-debug/LiveShell.java) | JShell-сниппеты внутри копии | `scripts/live-debug.sh --run run-live/a eval` | ПК | состояние копии |
| [schematic.sh](schematic.sh) | постройки текстом: `show`, `check` | `scripts/schematic.sh show familyhouse` | ПК, сервер | нет |
| [prove-red.sh](prove-red.sh) | доказывает, что тест `fix:`-коммита краснел на родителе | `scripts/prove-red.sh HEAD` | ПК, сервер | временный worktree |
| [progress.sh](progress.sh) | прогресс по `todo.md` | `scripts/progress.sh` | ПК, сервер | нет |
| [publish.sh](publish.sh) | публикация мода, пака и страницы (только по согласованию) | `scripts/publish.sh --temp -y` | ПК, сервер | сервер и пак |
| [publish-status.sh](publish-status.sh) | состояние публикации | `scripts/publish-status.sh` | ПК, сервер | нет |
| [server-cleanup.sh](server-cleanup.sh) | уборка временного на `/opt/minecraft` по cron | `scripts/server-cleanup.sh` | сервер | удаляет старое в `/opt/minecraft/tmp` |
| [nightly-analyzers.sh](nightly-analyzers.sh) | ночной полный набор анализаторов и unit по main | `scripts/nightly-analyzers.sh` | сервер | `/opt/minecraft/nightly`, логи |
| [warm-gradle-cache.sh](warm-gradle-cache.sh) | прогрев кэша Gradle | `scripts/warm-gradle-cache.sh` | ПК, сервер | кэш Gradle |
| [mem-guard.sh](mem-guard.sh) | сторож памяти агентов и Gradle | `scripts/mem-guard.sh` | ПК | нет |
| [strategist.sh](strategist.sh) | запуск внешнего стратега | `scripts/strategist.sh status` | ПК, сервер | процесс стратега |
| [opencode-start.sh](opencode-start.sh) | запуск сессии OpenCode | `scripts/opencode-start.sh` | ПК | процессы агента |
| [opencode-task.sh](opencode-task.sh) | запуск задачи исполнителя с ловушками | `scripts/opencode-task.sh ветка задача` | ПК | worktree, копия |
| [opencode-pick-model.sh](opencode-pick-model.sh) | выбор модели исполнителя | `scripts/opencode-pick-model.sh` | ПК | нет |
| [opencode-status.sh](opencode-status.sh) | состояние сессий OpenCode | `scripts/opencode-status.sh` | ПК | нет |
| [opencode-status-notify.sh](opencode-status-notify.sh) | уведомление о статусе сессии | `scripts/opencode-status-notify.sh` | ПК | нет |
| [install-git-hooks.sh](install-git-hooks.sh) | подключает хуки репозитория | `scripts/install-git-hooks.sh` | ПК, сервер | `.git/hooks` |
| [git-hooks/pre-commit](git-hooks/pre-commit) | гоняет `unit --changed` перед коммитом | `git commit` | ПК, сервер | нет |
| [git-hooks/commit-msg](git-hooks/commit-msg) | проверяет формат сообщения коммита | `git commit` | ПК, сервер | нет |
| [git-hooks/pre-rebase](git-hooks/pre-rebase) | запрещает перезапись истории | `git rebase main` | ПК, сервер | нет |
| [git-hooks/reference-transaction](git-hooks/reference-transaction) | держит «ветка только растёт» | `git update-ref` | ПК, сервер | нет |
| [lead/chkq.sh](lead/chkq.sh) | приёмка ветки главным агентом | `scripts/lead/chkq.sh ветка` | ПК, сервер | worktree, сборка |
| [lead/agent-list.sh](lead/agent-list.sh) | список агентов и их работы | `scripts/lead/agent-list.sh` | ПК | нет |
| [lead/nice-guard.sh](lead/nice-guard.sh) | понижает приоритет лишних процессов | `scripts/lead/nice-guard.sh` | ПК | приоритет процессов |
| [lead/oc-watch.sh](lead/oc-watch.sh) | наблюдение за сессией OpenCode | `scripts/lead/oc-watch.sh` | ПК | нет |
| [lead/orphan-sweep.sh](lead/orphan-sweep.sh) | гасит осиротевшие процессы на Linux | `scripts/lead/orphan-sweep.sh` | сервер | процессы |
| [lead/orphan-sweep-win.sh](lead/orphan-sweep-win.sh) | гасит осиротевшие процессы на Windows | `scripts/lead/orphan-sweep-win.sh` | ПК | процессы |
| [lead/orphans-win.sh](lead/orphans-win.sh) | список осиротевших процессов Windows | `scripts/lead/orphans-win.sh` | ПК | нет |
| [lead/keepawake.ps1](lead/keepawake.ps1) | не даёт ПК уснуть во время прогона | `powershell -File scripts/lead/keepawake.ps1` | ПК | состояние питания |
| [lead/res-pc.ps1](lead/res-pc.ps1) | ресурсы ПК для главного агента | `powershell -File scripts/lead/res-pc.ps1` | ПК | нет |
| [lead/world-slice.py](lead/world-slice.py) | карта блоков слоями для `live.sh slice` | `python scripts/lead/world-slice.py 0 64 0 --world world` | ПК, сервер, копия | нет |
| [testing/check-changed-modules.sh](testing/check-changed-modules.sh) | проверка `changed-modules.sh` на подделке | `scripts/testing/check-changed-modules.sh` | ПК, сервер | временные файлы |
| [testing/check-chkq.sh](testing/check-chkq.sh) | проверка приёмки `chkq.sh` | `scripts/testing/check-chkq.sh` | ПК, сервер | временные файлы |
| [testing/check-commit-msg.sh](testing/check-commit-msg.sh) | проверка хука сообщения коммита | `scripts/testing/check-commit-msg.sh` | ПК, сервер | временный репозиторий |
| [testing/check-gametest-lock.sh](testing/check-gametest-lock.sh) | проверка замка GameTest | `scripts/testing/check-gametest-lock.sh` | ПК, сервер | временные файлы |
| [testing/check-hang-watch.sh](testing/check-hang-watch.sh) | проверка гашения зависшего прогона | `scripts/testing/check-hang-watch.sh` | ПК, сервер | временные файлы |
| [testing/check-history-hooks.sh](testing/check-history-hooks.sh) | проверка запретов перезаписи истории | `scripts/testing/check-history-hooks.sh` | ПК, сервер | временный репозиторий |
| [testing/check-nightly-analyzers.sh](testing/check-nightly-analyzers.sh) | проверка ночного прогона | `scripts/testing/check-nightly-analyzers.sh` | ПК, сервер | временные файлы |
| [testing/check-opencode-task.sh](testing/check-opencode-task.sh) | проверка обвязки `opencode-task.sh` | `scripts/testing/check-opencode-task.sh` | ПК | временные файлы |
| [testing/check-orphan-sweep-win.sh](testing/check-orphan-sweep-win.sh) | проверка сторожа осиротевших копий | `scripts/testing/check-orphan-sweep-win.sh` | ПК | временные файлы |
| [testing/check-pre-commit.sh](testing/check-pre-commit.sh) | проверка хука `pre-commit` | `scripts/testing/check-pre-commit.sh` | ПК, сервер | временный репозиторий |
| [testing/check-prove-red.sh](testing/check-prove-red.sh) | проверка `prove-red.sh` | `scripts/testing/check-prove-red.sh` | ПК, сервер | временный worktree |
| [testing/check-publish.sh](testing/check-publish.sh) | проверка публикации на подделке | `scripts/testing/check-publish.sh` | ПК, сервер | временные файлы |
| [testing/check-static-analysis-cache.sh](testing/check-static-analysis-cache.sh) | проверка кэша анализаторов | `scripts/testing/check-static-analysis-cache.sh` | ПК, сервер | временные файлы |
| [testing/check-strategist.sh](testing/check-strategist.sh) | проверка запуска стратега | `scripts/testing/check-strategist.sh` | ПК, сервер | временные файлы |
| [testing/check-test-runner.sh](testing/check-test-runner.sh) | проверка пределов и замков `test.sh` | `scripts/testing/check-test-runner.sh` | ПК, сервер | временные файлы |
| [testing/fake-codegraph.sh](testing/fake-codegraph.sh) | подделка codegraph для проверок | `scripts/testing/fake-codegraph.sh` | ПК | временные файлы |
| [testing/fake-opencode.sh](testing/fake-opencode.sh) | подделка OpenCode для проверок | `scripts/testing/fake-opencode.sh` | ПК | временные файлы |
| [testing/measure-analyzers.sh](testing/measure-analyzers.sh) | замер анализаторов | `scripts/testing/measure-analyzers.sh` | ПК, сервер | временный отчёт |

Правило: новый скрипт появляется в этой таблице или в строке каталога README (`DocsMapGuardTests`).
Скрипт меняет мир или файлы вне `build/` и `TestResults/` — это прямо написано в последнем столбце.
Каталоги `scripts/logdb/fixtures/` и `scripts/logdb/tests/` — обезличенные данные и тесты загрузчика.
