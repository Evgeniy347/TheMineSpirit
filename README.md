# The Mine Spirit

Мод для Minecraft **1.21.1** на **NeoForge 21.1.257** (Java 21, Gradle + ModDevGradle).

Это единственный README проекта и карта всех инструкций и скриптов. При добавлении, переносе
или удалении документа или скрипта карта обновляется в том же изменении.

## Команды

Выполнять из корня репозитория.

| Команда | Что делает | Результат |
|---|---|---|
| `./gradlew build` | компиляция (`-Werror`), JUnit-тесты, сборка jar | `build/libs/theminespirit-<версия>.jar`, отчёты в `TestResults/unit/` |
| `./gradlew test` | только JUnit-тесты | `BUILD SUCCESSFUL`, отчёты в `TestResults/unit/` |
| `./gradlew runGameTestServer` | поднимает сервер, гоняет все GameTest мода, выходит | в логе `All N required tests passed` |
| `./gradlew runServer` | dev-сервер с модом в `run/` | сервер на порту 25565 |
| `./gradlew runClient` | dev-клиент с модом (нужен графический стол) | окно Minecraft |
| `./gradlew runData` | datagen в `src/generated/resources/` | сгенерированные json |
| `scripts/deploy.sh` | собирает мод и ставит jar на сервер `/opt/minecraft/server`, перезапуская его, если он работал | `Установлен theminespirit-<версия>.jar` |

Первая сборка скачивает Gradle, Minecraft и NeoForge — несколько минут; дальше секунды.

## Документация

| Файл | Назначение |
|---|---|
| [AGENTS.md](AGENTS.md) | карта правил для разработчика и AI-агента, решения пользователя |
| [todo.md](todo.md) | живой чеклист плана работ и открытые решения |
| [CLAUDE.md](CLAUDE.md) | вход для Claude Code, отсылает к AGENTS.md |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | соглашения кода |
| [docs/TESTING.md](docs/TESTING.md) | тесты: JUnit, GameTest, сторожа |
| [docs/GIT.md](docs/GIT.md) | коммиты и работа с git |

## Скрипты

| Скрипт | Назначение | Проверка |
|---|---|---|
| [scripts/deploy.sh](scripts/deploy.sh) | сборка и установка мода на сервер | в `/opt/minecraft/server/mods/` лежит свежий jar |
| `gradlew`, `gradlew.bat` | Gradle wrapper | `./gradlew --version` |

## Структура

```
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
