# Сервер The Mine Spirit

Сервер Minecraft **1.21.1** на **NeoForge 21.1.257** (Java 21). Мир, конфиги и сторонние моды лежат здесь;
сам мод собирается в соседнем репозитории `/opt/minecraft/the-mine-spirit`.

**Мир в git не хранится.** Снимки — tar-архивы (`scripts/world-snapshot.sh`), откат — `scripts/world-restore.sh`.
Скрипты и конфиги сервера живут в репозитории мода (`/opt/minecraft/the-mine-spirit/server/`), а сюда их
раскладывает `scripts/publish.sh` при публикации. Править файлы на сервере вручную нельзя: правка потеряется
при следующей публикации. Правка — в репозитории мода, затем `publish.sh`.

## Что где

| Путь | Что это |
|---|---|
| `world/` | мир сервера (не в git) |
| `mods/` | сторонние моды и jar The Mine Spirit (его ставит `publish.sh`) |
| `config/`, `server.properties`, `ops.json`, `whitelist.json`, `banned-*.json`, `eula.txt`, `user_jvm_args.txt` | конфиги; источник — `server/` репозитория мода |
| `scripts/` | скрипты управления; источник — `server/scripts/` репозитория мода |
| `run.sh` | запуск сервера, создан установщиком NeoForge; напрямую не вызывать — только через `start.sh` |
| `libraries/` | библиотеки NeoForge (не в git; `scripts/install-neoforge.sh`) |

## Скрипты

| Скрипт | Назначение | Проверка |
|---|---|---|
| [scripts/start.sh](scripts/start.sh) | запускает сервер в tmux-сессии `minecraft`, ждёт готовности; перед запуском зовёт `publish.sh --sync` и, если пак клиента или страница разошлись с `mods/`, обновляет их (лог — `/opt/minecraft/logs/publish-sync.log`) | `Сервер запущен` |
| [scripts/stop.sh](scripts/stop.sh) | корректно останавливает сервер с сохранением мира | `Сервер остановлен` |
| [scripts/hang-watch.sh](scripts/hang-watch.sh) | сторож зависания: тик стоит дольше порога → `jcmd Thread.print` в `logs/hang-*.txt`, строка в `logs/hang.log`, перезапуск `stop.sh` → kill по PID → `start.sh`; пауза живой отладки и идущая остановка пропускаются | дамп и `hang.log` |
| [scripts/console.sh](scripts/console.sh) | консоль сервера; выйти, не останавливая: `Ctrl+B`, затем `D` | приглашение консоли |
| [scripts/world-snapshot.sh](scripts/world-snapshot.sh) | консистентный архив мира без остановки сервера: `save-off` → `save-all flush` → `tar --zstd` → `save-on`, каждая команда ждёт подтверждения в `latest.log`; молчащая консоль — отказ с причиной; метка (`before-<что>`, `publish-<версия>`) защищает архив от ротации | `снимок готов: …` |
| [scripts/world-restore.sh](scripts/world-restore.sh) | откат мира на архив: снимок `before-restore` → остановка → разворот → запуск; без архива печатает список | `Мир восстановлен` |
| [scripts/install-neoforge.sh](scripts/install-neoforge.sh) | ставит библиотеки NeoForge после клона или смены версии (`NEOFORGE_VERSION` в `common.sh`) | `NeoForge <версия> установлен` |
| [scripts/common.sh](scripts/common.sh) | общие настройки скриптов: tmux-сессия, версия NeoForge, ожидание строки в логе | — |

Память JVM — в `user_jvm_args.txt` (сейчас 2–4 ГБ).

## Правила

- **Мир в git не коммитится.** Снимок — `scripts/world-snapshot.sh [метка]`, откат — `scripts/world-restore.sh`.
  Cron снимает архив каждые 10 минут в `/opt/minecraft/backups/world/`; архивы с меткой ротация не удаляет.
- Cron раз в минуту зовёт `scripts/hang-watch.sh` (строку ставит `publish.sh`): зависший тик снимается дампом
  в `/opt/minecraft/logs/hang-*.txt` и перезапуском, отметка — `/opt/minecraft/logs/hang.log`.
- Скрипты и конфиги правятся в репозитории мода (`server/`), на сервер их кладёт `scripts/publish.sh`.
  Ручная правка на сервере будет перезаписана публикацией.
- Мир, конфиги и jar текущей версии без согласования с пользователем не трогать.
- Версию Minecraft и NeoForge выбирает пользователь.
- Логи содержат ники и IP игроков: не публиковать и не коммитить.
- Остальные правила разработки — в `/opt/minecraft/the-mine-spirit/AGENTS.md`.
