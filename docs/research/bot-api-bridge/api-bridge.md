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

