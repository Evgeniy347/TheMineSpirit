# The Mine Spirit

Мод для Minecraft **1.21.1** на **NeoForge 21.1.257** (Java 21, Gradle + ModDevGradle).

Это единственный README проекта и карта всех инструкций и скриптов. При добавлении, переносе
или удалении документа или скрипта карта обновляется в том же изменении.

## Команды

Выполнять из корня репозитория.

| Команда | Что делает | Результат |
|---|---|---|
| `./gradlew build` | компиляция (`-Werror`), быстрые JUnit-тесты, сборка jar | `build/libs/theminespirit-<версия>.jar`, отчёты в `TestResults/unit/` |
| `./gradlew test` | быстрые JUnit-тесты (включая свойства jqwik, кроме `@Tag("slow")`) | `BUILD SUCCESSFUL`, отчёты в `TestResults/unit/` |
| `./gradlew slowTest` | только `@Tag("slow")`; сейчас таких тестов нет, поэтому задача падает на 0 тестов | отчёты в `TestResults/slow/` |
| `./gradlew check` | быстрый тир (`test`); `slowTest` запускается отдельно | зелёный прогон быстрых тестов |
| `./gradlew jacocoTestReport` | отчёт покрытия JaCoCo | `TestResults/coverage/` |
| `./gradlew runGameTestServer` | поднимает сервер, гоняет все GameTest мода, выходит | в логе `All N required tests passed` |
| `./gradlew runServer` | dev-сервер с модом в `run/` | сервер на порту 25565 |
| `./gradlew runClient` | dev-клиент с модом (нужен графический стол) | окно Minecraft |
| `./gradlew runData` | datagen в `src/generated/resources/` | сгенерированные json |
| `scripts/testing/check-strategist.sh` | тесты агента-стратега (`python3 -m unittest`, сценарии, сторожа) и сценарии надзора `scripts/strategist.sh` на фейковом REST; сервер Minecraft и настоящий opencode не нужны | все строки `OK` |
| `cd agents/strategist && python3 -m unittest discover -s tests -t .` | только Python-тесты агента-стратега | `OK` в последней строке |
| `scripts/publish.sh` | собирает мод и ставит jar на сервер `/opt/minecraft/server`, перезапуская его, если он работал. **Только по согласованию с пользователем** (нужно подтверждение или `-y`) | `Установлен theminespirit-<версия>.jar` |

Первая сборка скачивает Gradle, Minecraft и NeoForge — несколько минут; дальше секунды.

## Конфиг

Серверный конфиг мода — `config/theminespirit-server.toml` (создаётся при первом запуске сервера).

| Параметр | Значение | Смысл |
|---|---|---|
| `spirit.chunkLoadingRadius` | `-1` (по умолчанию), `1`–`32` | Радиус `R` квадрата принудительно загружаемых и тикающих чанков вокруг духа. `-1` — как simulation distance сервера: дух держит столько же, сколько игрок. `(2R+1)²` чанков на духа |

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
| [opencode.json](opencode.json) | настройки opencode в проекте: модель big-pickle, полный доступ, вопросы запрещены |
| [docs/STRATEGIST.md](docs/STRATEGIST.md) | агент-стратег духов (`agents/strategist`): цикл, режимы, журнал, лимиты, смена модели |
| [docs/API.md](docs/API.md) | контракт REST-сервиса духов (bridge), формат плана |
| [docs/research/README.md](docs/research/README.md) | карта знаний: что переиспользовать из 13 готовых проектов, лицензии |

## Скрипты

| Скрипт | Назначение | Проверка |
|---|---|---|
| [scripts/publish.sh](scripts/publish.sh) | сборка и установка мода на сервер (только по согласованию с пользователем) | в `/opt/minecraft/server/mods/` лежит свежий jar |
| [scripts/opencode-task.sh](scripts/opencode-task.sh) | задание для opencode (big-pickle) с надзором: при сбое провайдера (free usage, rate limit, квота, 429) или молчании модели ≥ 5 мин — перезапуск той же сессии (5, 5, 10, 30, 60 с), затем режим ожидания: раз в 120 с (`OPENCODE_PROBE_INTERVAL`) короткая проба модели из пустого каталога, провайдер вернулся — та же сессия продолжается, предел ожидания `OPENCODE_WAIT_LIMIT` 7200 с, потом стоп; сбои пишутся в `TestResults/opencode/failures.log`; новое задание не стартует в грязном дереве (там может работать другой агент), `OPENCODE_ALLOW_DIRTY=1` — осознанно | код выхода 0 и `Сессия: ses_…`; 75 (предел ожидания исчерпан) — сообщить пользователю |
| [scripts/testing/check-opencode-task.sh](scripts/testing/check-opencode-task.sh) | регрессионная проверка `opencode-task.sh` на подделке [scripts/testing/fake-opencode.sh](scripts/testing/fake-opencode.sh): сбои провайдера, режим ожидания (провайдер вернулся / предел исчерпан), молчание модели, долгая команда, ложные срабатывания на файлах и логе INFO | все строки `OK` |
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
