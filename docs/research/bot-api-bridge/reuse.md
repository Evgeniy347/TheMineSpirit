## Что переиспользовать у нас (идея → наш REST API / MCP / примитивы NeoForge)

| Идея из исследования | Куда у нас |
|---|---|
| Единый «plugin»-реестр примитивов (`mineflayer` `loadPlugin`) | Примитивы NeoForge в `core` + реестр скриптов; тот же каталог отдаётся в `/v1/capabilities` |
| Схема MCP tool `{name, description, inputSchema}` ↔ `McpTool` | Наш `/v1/capabilities` `{id, summary, params JSON-Schema}` уже совпадает — добавить `mcpName` |
| Формат ответа `content:[{type:"text",text}]` | Для MCP-обёртки P6; в REST основной ответ — структурный JSON, не текст |
| JSON-RPC 2.0 + discovery `mcp/server.json` + Bearer | Готовая модель для P6: принять как эталон и не изобретать формат |
| `inputSchema` без свойств у minecraft-mcp | Не копировать: у нас полноценный JSON-Schema (сильнее для LLM) |
| `request_id`/идемпотентность + `timeoutMs` | Уже в нашем `docs/api/README.md`; MCP-`timeoutMs` взять как параметр дефолт-таймаута задачи |
| `scenario` (шаги tool/args/expect) | Переиспользовать идею как контрактный headless-тест для P4/P5 |
| Наблюдение мира (`mc.world.snapshot`, `WorldSnapshot`) | Уже есть Observation; сверить поля (containers/entities/hash) |
| Контейнерные клики (`PICKUP`/`QUICK_MOVE`/`THROW`) | Наши скрипты `deposit/withdraw/sort_chest` (P3) |
| Пути/навигация A* (pathfinder GoalNear) | `goto`/`follow` в `core`; GoalNear-семантика для «подойти к блоку/сундуку» |

## Отношение к нашему docs/api/README.md (что добавить/изменить в контракте)

- **Оставить REST первичным.** MCP — вторичный транспорт (как в todo P6). Ядро `core` не должно знать ни
  HTTP, ни MCP; оба транспорта — адаптеры над одним каталогом скриптов и планировщиком задач.
- **Добавить MCP-транспорт** разделом: `initialize`/`tools/list`/`tools/call`, **обязательный Bearer**,
  discovery-файл, `protocolVersion`. Для совместимости с клиентами принять JSON-RPC 2.0.
- **Псевдонимы инструментов:** каждой Capability дать `mcpName` (напр. `spirit.chop_tree` ↔ `chop_tree`),
  чтобы `tools/list` собирался из `/v1/capabilities` без второго источника правды.
- **Наблюдение:** уже есть `POST /v1/bots/{id}/observe`; сверить поля с `mc.world.snapshot`/`inventory.state`/
  `container.state` (armor/offhand/hotbar), добавить эффекты (`effects`) и `container.state`, если нужны.
- **`POST /v1/plan`** остаётся сухим прогоном; MCP `mc.condition.wait`/`timeoutMs` учитывать как
  пример параметров ожидания.
- **Каталог скриптов** расширить примитивами из P2/P3/P6 (scan/dig/place/craft/smelt/equip/deposit/withdraw),
  каждый с JSON-Schema, предусловиями и эффектами — как в текущем `chop_tree`.

## Конкретные рекомендации (брать/адаптировать/избегать)

Брать:
- Идею модульных примитивов и единый каталог (mineflayer-плагины → наши `capabilities`).
- JSON-RPC 2.0 + `tools/list`/`tools/call` + Bearer + discovery `server.json` из minecraft-mcp — как эталон P6.
- `timeoutMs`, `request_id`-идемпотентность, `scenario`-тесты.
- Список примитивов mineflayer как чеклист паритета: движение/controls, blockAt/findBlock/dig/placeBlock,
  inventory/equip/toss, openContainer+deposit/withdraw, openFurnace, craft/recipesFor, nearestEntity/attack,
  health/food, consume, activateBlock, chat, sleep; эффекты — через `entity.effects`.
- Ideи `mineflayer-tool`/`armor-manager`/`auto-eat`: авто-выбор инструмента, авто-броня, авто-еда (P6).

Адаптировать:
- Плоские текстовые ответы mcp-server → у нас структурный JSON (машиночитаемо).
- `inputSchema: {"type":"object"}` minecraft-mcp → полноценные JSON-Schema с типами/дефолтами/диапазонами.
- pathfinder `GoalNear` → наша навигация (без прямого порта JS-логики).
- MCP `mc.*` namespaces → наш `spirit.*`/плоские имена скриптов, зафиксировать в контракте.

Избегать:
- Копирования файлов/кода mineflayer (JS) и minecraft-mcp (кроме MIT-совместимого) в Java-мод — риск
  смешения лицензий/атрибуции; переносить идеи и схемы.
- Текстового протокола ответов (human-only) — неудобен для LLM/тестов.
- Привязки `core` к HTTP/MCP/Jackson — транспорт только во внешнем адаптере (уже принято в `docs/api/README.md`).
- Прямого переиспользования packet-recorder/Netty-хуков minecraft-mcp — не наш профиль задачи.

## Риски и лицензионные ограничения

- **Все три лицензии permissive, copyleft нет.** MIT (mineflayer, minecraft-mcp): сохранять копирайт и текст
  лицензии при распространении производной. Apache-2.0 (minecraft-mcp-server): сохранять NOTICE, указывать
  изменения, патентный грант; совместима с MIT-проектом.
- **Атрибуция при переносе идей.** Даже при переписывании с нуля корректно указать источник (этот файл уже
  содержит пути/ссылки). Прямое копирование кода — только с сохранением лицензии и уведомления.
- **Внешние npm-плагины не в клоне.** `pathfinder/pvp/auto-eat/tool/collectblock/armor-manager` локально
  отсутствуют; их точные лицензии и API нужно проверять в upstream перед заимствованием (по данным
  `docs/README.md` это сторонние проекты, ожидаемо permissive, но локально не подтверждено).
- **Версии.** mcp-server заявляет MC 1.21.11; minecraft-mcp — 1.21.1; mineflayer 4.39.0. Наша цель — 1.21.1,
  поэтому schema-примеры minecraft-mcp ближе всего; mcp-server сверять по протоколу, не по версии игры.
- **Стабильность MCP-протокола.** minecraft-mcp использует `protocolVersion 2024-11-05`; фиксировать версию в
  контракте и предусмотреть negotiated version при `initialize`.
- **HttpServer в моде.** `com.sun.net.httpserver` — JDK-сервер, только localhost/Bearer по умолчанию; для
  нашего bridge (P4) нужен отдельный HTTP (Javalin/Netty) и TLS/reverse-proxy, лимиты и аудит (см. todo P4).
