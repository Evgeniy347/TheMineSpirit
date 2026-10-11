# API бота, мосты (REST/MCP) и примитивы — карта переиспользования

Исследование по локально склонированным репозиториям. Изменений в коде нет; это справочник
для P4 (REST-мост), P6 (MCP) и примитивов духа (движение/добыча/инвентарь/крафт/сундуки/эффекты).
Дата анализа: 2026-10-11.

## Обзор репозиториев

| Репозиторий | Назначение | Стек | Лицензия | Copyleft |
|---|---|---|---|---|
| `/opt/minecraft/research/mineflayer` | Высокоуровневая JS-библиотека бота: единый API над Minecraft-протоколом + система плагинов | Node.js (>=22), JS + `index.d.ts`; зав. `minecraft-protocol`, `prismarine-*`, `minecraft-data`, `vec3` | MIT (© 2015 Andrew Kelley), файл `LICENSE` | нет |
| `/opt/minecraft/research/minecraft-mcp-server` | MCP-сервер (stdio), управляющий ботом Mineflayer через MCP-tools | TypeScript, Node (>=20.10), `@modelcontextprotocol/sdk`, `mineflayer`, `mineflayer-pathfinder`, `zod` | Apache-2.0, файл `LICENSE` | нет |
| `/opt/minecraft/research/minecraft-mcp` | Java-мод Minecraft 1.21.1 (Fabric + NeoForge), поднимающий локальный MCP-style JSON-RPC endpoint | Java 21, Gradle; общий `common` + `fabric`/`neoforge`; `com.sun.net.httpserver` | MIT (© 2026 IzzelAliz), файл `LICENSE` | нет |

Вывод по лицензиям: все три — permissive, copyleft (GPL/LGPL/AGPL) среди них нет. Apache-2.0 (mcp-server)
требует сохранять NOTICE/атрибуцию и даёт явный патентный грант; MIT (mineflayer, minecraft-mcp) требует
сохранения копирайта и текста лицензии. Прямое копирование кода в наш мод допустимо при соблюдении
атрибуции; предпочтительнее переиспользовать идеи/схемы, а не файлы (см. «Риски»).

## API/примитивы mineflayer (по модулям, пути файлов)

Ядро:
- `index.js` — точка входа (`createBot`), `package.json` (v4.39.0).
- `index.d.ts` — TypeScript-декларации всего публичного API; `lib/loader.js` — сборка Bot.
- `lib/plugin_loader.js`, `bot.loadPlugin(plugin)`/`loadPlugins`/`hasPlugin` (docs/api.md:1813) —
  механизм расширения: плагин получает `bot` и навешивает поля/методы/обработчики. Всё, что ниже, — это
  встроенные плагины из `lib/plugins/`, каждый самодостаточен; наш адаптер NeoForge может повторять ту же
  модульную схему (интерфейс «примитив» + реестр).
- `docs/api.md` (2342 строки) — полный справочник: Enums/Classes/Bot/Events/Functions/Methods.

Движение и управление:
- `lib/plugins/physics.js` — `bot.setControlState(control, state)` (forward/back/left/right/jump/sneak/sprint),
  `bot.look(yaw,pitch)`, `bot.lookAt(point, force)`, `bot.waitForTicks`, `bot.clearControlStates`, `bot.controlState`.
- События `move`, `forcedMove`, `physicsTick`.
- Внешний `mineflayer-pathfinder` (npm, в клоне отсутствует) — A*: `bot.pathfinder.goto(goal)`, `setGoal`,
  `stop`, `goals.GoalNear/GoalBlock/GoalXZ`. Именно им пользуется mcp-server.

Блоки, добыча, установка:
- `lib/plugins/blocks.js` — `bot.blockAt`, `bot.findBlocks`, `bot.findBlock`, `bot.canSeeBlock`.
- `lib/plugins/digging.js` — `bot.dig(block)`, `bot.stopDigging`, `bot.canDigBlock`, `bot.digTime`;
  события `diggingCompleted`/`diggingAborted`, `blockBreakProgressObserved`.
- `lib/plugins/place_block.js` — `bot.placeBlock(referenceBlock, faceVector)`.
- `lib/plugins/inventory.js` — `bot.activateBlock`, `bot.activateEntity`, `bot.activateEntityAt`,
  `bot.activateItem`/`deactivateItem`, `bot.consume` (событие «поел»), `bot.useOn`.
- Внешние `mineflayer-tool` (авто-выбор инструмента/оружия), `mineflayer-collectblock` (сбор блоков) — npm.

Сущности, бой, здоровье:
- `lib/plugins/entities.js` — `bot.entities`, `bot.entity`, `bot.nearestEntity(match)`, `bot.attack(entity, swing)`,
  `bot.swingArm`; события `entitySpawn/Gone/Moved/Hurt/Dead/Equip`, `playerCollect`, `itemDrop`.
- `lib/plugins/health.js` — `bot.health`, `bot.food`, `bot.foodSaturation`, событие `health`.
- `lib/plugins/breath.js` — `bot.oxygenLevel`, событие `breath`.
- Эффекты зелий: отдельных методов нет — активные эффекты лежат в `entity.effects[effectId]`
  (`lib/plugins/entities.js:559`), события `entityEffect`/`entityEffectEnd`, интерфейс `Effect` в `index.d.ts:596`.
- Внешние `mineflayer-pvp` (PvP/PvE), `mineflayer-auto-eat` (авто-еда), `MineflayerArmorManager` — npm.

Инвентарь и экипировка:
- `lib/plugins/inventory.js` — `bot.inventory`, `bot.quickBarSlot`, `bot.clickWindow`, `bot.moveSlotItem`,
  `bot.transfer`, `bot.putAway`, `bot.openBlock`, `bot.openEntity`, `bot.closeWindow`, `bot.updateHeldItem`.
- `lib/plugins/simple_inventory.js` — `bot.equip(item, destination)`, `bot.unequip`, `bot.toss`, `bot.tossStack`,
  `bot.setQuickBarSlot`, `bot.getEquipmentDestSlot`. `destination`: hand/head/torso/legs/feet/off-hand.

Контейнеры, печи, крафт, торговля:
- `lib/plugins/chest.js` — `bot.openContainer(block)`, `bot.openChest` → `mineflayer.Container`;
  `window.deposit/withdraw(itemType, metadata, count, nbt)`, `window.items()`, `window.close()`.
- `lib/plugins/furnace.js` — `bot.openFurnace` → `putInput/putFuel/takeInput/takeFuel/takeOutput/inputItem/
  fuelItem/outputItem/fuel/progress`, событие `update`, `window.deposit/withdraw`.
- `lib/plugins/craft.js` — `bot.craft(recipe, count, craftingTable)`, `bot.recipesFor`, `bot.recipesAll`.
- `lib/plugins/villager.js`, `enchantment_table.js`, `anvil.js`, `command_block.js` — торговля/зачарование/наковальня/командный блок.

Прочее, что может пригодиться:
- `lib/plugins/chat.js` — `bot.chat`, `bot.whisper`, `bot.addChatPattern`; события `chat`, `message`, `whisper`.
- `lib/plugins/bed.js` — `bot.sleep`, `bot.wake`, `bot.isABed`; события `sleep`/`wake`.
- `lib/plugins/creative.js` — `bot.creative.setInventorySlot/flyTo/startFlying/stopFlying` (только creative).
- `lib/plugins/experience.js`, `time.js`, `scoreboard.js`, `team.js`, `boss_bar.js`, `particle.js`, `sound.js`,
  `fishing.js`, `game.js`, `settings.js`.
- Полный список встроенных плагинов — каталог `lib/plugins/` (43 файла).

Внешние ботовые модули (не в этом клоне, только ссылки в `docs/README.md:205-223`):
`pathfinder`, `prismarine-viewer`, `web-inventory`, `statemachine`, `Armor Manager`, `Dashboard`, `PVP`,
`Auto Eat`, `Tool`, `Collect Block` и др. Их исходников локально нет — при переносе идей сверяться с upstream.

## MCP: tools и схема протокола

### minecraft-mcp-server (stdio)

- Транспорт: `StdioServerTransport` (`src/main.ts:69`), пакет `@modelcontextprotocol/sdk`. Конфигурация —
  аргументы CLI `--host/--port/--username` (`src/config.ts`).
- Регистрация: `ToolFactory.registerTool` (`src/tool-factory.ts:17`) → `server.tool(name, description,
  zodSchema, handler)`. Схемы — `zod`, валидация с человекочитаемой ошибкой.
- Формат ответа: `{ content: [{ type: "text", text }], isError?: boolean }`; все данные — **строки**, а не
  структурный JSON. Перед вызовом — проверка/переподключение соединения (`bot-connection.ts`).
- Зарегистрировано 22 инструмента (README `Available Commands` неполон — в коде есть и крафт):

| Группа | Инструменты (имя → примитив mineflayer) |
|---|---|
| Позиция | `get-position`, `move-to-position`(pathfinder GoalNear), `look-at`, `jump`, `move-in-direction` |
| Полёт | `fly-to` (только creative) |
| Инвентарь | `list-inventory`, `find-item`, `equip-item` |
| Блоки | `place-block`, `dig-block`, `get-block-info`, `find-blocks` |
| Печь | `smelt-item` (furnace putInput/putFuel/takeOutput) |
| Сущности | `find-entity` |
| Чат | `send-chat`, `read-chat` |
| Состояние | `detect-gamemode` |
| Крафт | `list-recipes`, `craft-item`, `get-recipe`, `can-craft` (по `minecraft-data` recipes) |

- Схема вызова: `tools/call` с `{ name, arguments }`; `arguments` — плоский объект (x/y/z, blockType,
  itemName, amount, timeoutMs и т. п.). Аргументы проверяются zod-схемой, лишние поля пропускаются
  (`.passthrough()`), ошибки возвращаются как `isError: true` с текстом `Failed: ...`.

### minecraft-mcp (мод, JSON-RPC поверх HTTP)

- Транспорт: локальный HTTP POST `/mcp` (`mcp/LocalHttpMcpServer.java`), сервер `com.sun.net.httpserver`,
  bind `127.0.0.1` (настраивается), Bearer-токен обязателен (иначе 401). Discovery-файл
  `<gameDir>/mcp/server.json`: `{protocol, transport:"http-jsonrpc", host, port, path, authToken, loader,
  minecraftVersion}` (`LocalHttpMcpServer.java:25`).
- JSON-RPC 2.0 (`mcp/JsonRpcHandler.java`): методы `initialize` (protocolVersion `2024-11-05`,
  serverInfo, capabilities), `tools/list`, `tools/call`. Ответ `tools/call` —
  `{ content:[{type:"text", text: <stringified JSON>}], isError:false }`; ошибки JSON-RPC `-32601`/`-32603`.
- Реестр инструментов: `mcp/ToolRegistry.java`, интерфейс `mcp/McpTool.java` (`name/description/inputSchema/
  call`). `inputSchema` у встроенных инструментов сейчас всегда `{ "type":"object" }` (без подробных свойств).
  Есть `FutureResult` + per-call `timeoutMs` (по умолчанию 10 с).
- Только **клиентские** инструменты в `tools/BuiltinTools.java` (~54 шт.): `mc.client.state`, `mc.player.state`,
  `mc.player.swing/look/look_at/use_item/attack.block/destroy.block/drop/jump`, `mc.vehicle.state`,
  `mc.inventory.state/find/count/selected`, `mc.container.state/click/quick_move/drop/close`,
  `mc.hotbar.select`, `mc.block.state/interact`, `mc.command.run/suggest`, `mc.server.sync/connect/call/
  disconnect.state/disconnect.wait`, `mc.chat.send`, `mc.screen.current/state/text.type/click.at/widget.click`,
  `mc.world.join/leave/snapshot`, `mc.condition.wait`, `mc.keyboard.press/hold`, `mc.ticks.wait`,
  `mc.movement.waypoints`, `mc.screenshot.take`, `mc.packet.recording.start/stop/clear/status`, `mc.packet.dump`,
  `mc.packet.wait`, `mc.debug.capabilities`, `mc.scenario.batch.run/report`.
- **Серверные** инструменты (`tools/BuiltinServerTools.java`, доступны с клиента через `mc.server.call`):
  `mc.server.state`, `mc.server.debug.capabilities`, `mc.server.command.run`, `mc.server.ticks.wait`,
  `mc.schematic.info/export/paste` (только Sponge Schematic v3, `.schem`).
- Отдельные подсистемы: `packet/*` (запись/фильтр/дамп пакетов через Netty), `scenario/*` (JSON-сценарии с
  шагами `tool/args/expect`, dot-path ассерты), `condition/*` (DSL предикатов вида `client.inWorld == true`),
  `serverlink/*` (проксирование server-tools через plugin-message канал), `bridge/*` (снимки клиента/сервера).

## Что переиспользовать у нас (идея → наш REST API / MCP / примитивы NeoForge)

| Идея из исследования | Куда у нас |
|---|---|
| Единый «plugin»-реестр примитивов (`mineflayer` `loadPlugin`) | Примитивы NeoForge в `core` + реестр скриптов; тот же каталог отдаётся в `/v1/capabilities` |
| Схема MCP tool `{name, description, inputSchema}` ↔ `McpTool` | Наш `/v1/capabilities` `{id, summary, params JSON-Schema}` уже совпадает — добавить `mcpName` |
| Формат ответа `content:[{type:"text",text}]` | Для MCP-обёртки P6; в REST основной ответ — структурный JSON, не текст |
| JSON-RPC 2.0 + discovery `mcp/server.json` + Bearer | Готовая модель для P6: принять как эталон и не изобретать формат |
| `inputSchema` без свойств у minecraft-mcp | Не копировать: у нас полноценный JSON-Schema (сильнее для LLM) |
| `request_id`/идемпотентность + `timeoutMs` | Уже в нашем `docs/API.md`; MCP-`timeoutMs` взять как параметр дефолт-таймаута задачи |
| `scenario` (шаги tool/args/expect) | Переиспользовать идею как контрактный headless-тест для P4/P5 |
| Наблюдение мира (`mc.world.snapshot`, `WorldSnapshot`) | Уже есть Observation; сверить поля (containers/entities/hash) |
| Контейнерные клики (`PICKUP`/`QUICK_MOVE`/`THROW`) | Наши скрипты `deposit/withdraw/sort_chest` (P3) |
| Пути/навигация A* (pathfinder GoalNear) | `goto`/`follow` в `core`; GoalNear-семантика для «подойти к блоку/сундуку» |

## Отношение к нашему docs/API.md (что добавить/изменить в контракте)

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
- Привязки `core` к HTTP/MCP/Jackson — транспорт только во внешнем адаптере (уже принято в `docs/API.md`).
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
