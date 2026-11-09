# Скрипты

Карта каталога. Пути к исходникам не дублируются: `scripts/module-paths.sh` читает `config/layout.properties`
и `settings.gradle` (единственный список модулей), поэтому скрипт не ломается при переезде файлов.

| Скрипт | Что делает |
|---|---|
| `test.sh unit [--changed | --module <м>] [--tests …]` | быстрые JUnit-тесты: весь набор, затронутые модули или один модуль; лог — `TestResults/logs/` |
| `changed-modules.sh` | путь → модуль → зависящие модули для `test.sh unit --changed` (граф из settings/build.gradle с кэшем в `build/tmp`) |
| `test.sh gametest [--full] [фильтр…]` | GameTest под замком, шарды, `--full` — полный предел |
| `gametest.sh` | обёртка над `test.sh gametest` |
| `module-paths.sh` | общие переменные путей для остальных скриптов (`MOD_MAIN_DIR`, ресурсы, jar, GameTest) |
| `publish.sh` | публикация мода целиком (только по согласованию), `--temp`/`--sync` |
| `live.sh status`, `spirit`, `loops`, `slice`, `log`, `fixture`, `check` | **с него начинать**: что с духами и миром, одной командой; копия дерева или основной сервер на чтение, `docs/LIVE-DEBUG.md` |
| `dev-server.sh`, `live-debug.sh` | своя копия сервера и живая отладка, `docs/LIVE-DEBUG.md` |
| `schematic.sh` | постройки текстом: `show`, `check` |
| `prove-red.sh` | тест `fix:`-коммита краснеет на родителе и зеленеет на коммите |
| `progress.sh` | прогресс по `todo.md` |
| `server-cleanup.sh` | уборка `/opt/minecraft` по cron |
| `nightly-analyzers.sh` | ночной полный набор анализаторов и unit-тестов по main (03:00), только при изменениях; статус — `/opt/minecraft/logs/nightly-analyzers.status` |
| `strategist.sh`, `opencode-*.sh`, `mem-guard.sh` | надзор агентов и памяти |
| `lead/`, `testing/`, `git-hooks/`, `dev-server/`, `live-debug/` | инструменты главного агента, проверки на подделках, хуки, обвязка |

Правило: новый скрипт появляется в этой таблице или в строке каталога README (DocsMapGuardTests).
