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

## Разделы

| Файл | О чём |
|---|---|
| [api-bridge.md](api-bridge.md) | API/примитивы mineflayer и MCP-мосты (tools, схема протокола) |
| [reuse.md](reuse.md) | отношение к нашему контракту, рекомендации, риски и лицензии |

