# Карта знаний: переиспользование готовых решений

Сводка исследования сторонних проектов (боты, NPC-фреймворки, колонии, LLM-агенты, Millénaire).
Цель — понять, какие идеи, алгоритмы, схемы и форматы данных можно переосмыслить для The Mine Spirit.
Локальные клоны лежат в `/opt/minecraft/research/` (вне репозитория мода).

## Главное правило: лицензии

| Репозиторий | Лицензия | Копировать код можно? |
|---|---|---|
| `cabaletta/baritone` | LGPL-3.0 | Нет (copyleft) — только идеи |
| `gaucho-matrero/altoclef` | MIT, но встраивает LGPL-форк Baritone | Осторожно, по факту под LGPL |
| `ldtteam/minecolonies` | GPL-3.0 (код), ассеты All Rights Reserved | Нет (copyleft) — только идеи |
| `CitizensDev/Citizens2` | OSL-3.0 | Нет (copyleft) — только идеи |
| `kolbytn/mindcraft` | MIT | Да (с атрибуцией) |
| `zoyluoblue/mc_aiplayer` | MIT | Да (с атрибуцией) |
| `MineDojo/Voyager` | MIT (вложенный `mineflayer-collectblock` проверить) | Да (с атрибуцией) |
| `Sam-ni-deco/Minecraft-AI-Agent-Mod` | AGPL-3.0 | Нет (copyleft) — только идеи |
| `PrismarineJS/mineflayer` | MIT | Да (с атрибуцией) |
| `yuniko-software/minecraft-mcp-server` | Apache-2.0 | Да (с NOTICE/атрибуцией) |
| `ArclightPowered/minecraft-mcp` | MIT | Да (с атрибуцией) |
| `Kinniken/Millenaire` (классика) | нет LICENSE / проприетарная; запрет использования элементов в других модах | Нет — только clean-room |
| `Leviaria/Millenaire` (neoforge) | GPL-3.0 (в gradle — All Rights Reserved) | Нет (copyleft) — только идеи |
| `WangMioG/Millenaire-rewrite` | противоречива (MIT в README vs LGPL-шаблон в LICENSE.txt) | Нет — трактуем all-rights-reserved |

Достоверно permissive (можно переиспользовать код с атрибуцией): **mineflayer (MIT)**, **mindcraft (MIT)**, **mc_aiplayer (MIT)**, **voyager (MIT)**, **minecraft-mcp (MIT)**, **minecraft-mcp-server (Apache-2.0)**. Всё остальное — источник идей и алгоритмов, реализуем сами.

## Карты по областям

| Файл | Область | Репозитории |
|---|---|---|
| [navigation-movement.md](navigation-movement.md) | Навигация, движение, добыча | baritone, altoclef |
| [colony-economy.md](colony-economy.md) | Работы, склад, снабжение, экономика | minecolonies |
| [npc-ai-commands.md](npc-ai-commands.md) | Цели NPC, черты, команды, патруль | citizens |
| [llm-agents-skills.md](llm-agents-skills.md) | Наблюдение, промпты, память, навыки, куррикулум, верификация | mindcraft, mc_aiplayer, mc-ai-agent-mod, voyager |
| [bot-api-bridge.md](bot-api-bridge.md) | REST/MCP-мосты и игровые примитивы | mineflayer, minecraft-mcp-server, minecraft-mcp |
| [millenaire-classic.md](millenaire-classic.md) | Деревни, экономика, торговля, квесты, культуры, стройка (классика) | Kinniken/Millenaire |
| [millenaire-modern.md](millenaire-modern.md) | То же на NeoForge 1.21.1 и в rewrite | Leviaria/Millenaire, WangMioG/Millenaire-rewrite |

## Самое ценное по областям

### Навигация и движение (baritone, altoclef)
- Иерархия целей `Goal*/GoalComposite/GoalRunAway`, стоимости движений в тиках (`ActionCosts`), A* в отдельном потоке.
- Кэш мира для поиска блоков без загрузки чанков (`CachedWorld/WorldScanner`).
- Реактивные цепочки задач поверх основной цели (`TaskChain` + маркеры «можно форсировать/нужна земля») — модель «тактика прерывает стратегию».
- MLG-падение с ведром воды, `BlockTracker` с чёрным списком, таймауты прогресса (`MovementProgressChecker`).

### Работы, склад, экономика (minecolonies)
- Профессия = тонкие данные (`AbstractJob`), поведение — в AI-слое: у нас роль = набор `ScriptSpec` + план, а не Java-подкласс.
- **RequestSystem** — «экономика без денег»: дерево запросов parent/child, недостача порождает дочерний запрос (склад → крафт → добыча).
- Состояния запроса `CREATED…ASSIGNED…RESOLVED…COMPLETED`, `MinimumStack` («не отдавать ниже N»).
- Склад-агрегатор поверх всех контейнеров → функция над `WorldObservation`.
- Стройка стадиями (`BuildingProgressStage`) + bucket ресурсов; курьер — отдельная профессия.
- Анти-thrash: разнесённые периоды опроса нужд.

### Цели NPC и команды (citizens)
- Единый контракт Goal≈Behavior: `shouldExecute()` + `run()→RUNNING/SUCCESS/FAILURE` + `reset()`; приоритет — порядок/квоты.
- Blackboard-память (`Memory`) — общая персистентная память NPC для всех целей и команд.
- Патруль = провайдер точек (loop/ping-pong) + цель + триггеры на точке (delay/command/chat/speed).
- Анти-застревание (`stationaryTicks` + `stuckAction`); бой через `AttackStrategy` (отдельно «когда цель» и «как бить»).
- Команды-данные с cooldown/лимитами/режимами исполнения.

### LLM, навыки, обучение (mindcraft, mc_aiplayer, mc-ai-agent-mod, voyager)
- **Библиотека навыков** (Voyager): retrieval топ-k по описанию, сохранение только при подтверждённом успехе, LLM-описания.
- **Авто-куррикулум**: warm-up, до/недо-выполненные задачи, QA-кэш, `decompose_task` в подцели.
- **Self-verification** (Voyager critic; mc_aiplayer postcondition `GoalResult`): завершение шага ≠ успех цели, critique идёт в следующий промпт.
- Структурированное наблюдение (`PerceptionSnapshot.toJson()`) вместо одного `hash()` — вкладывать сам JSON в промпт.
- Долговременная память без LLM (`KnowledgeBase`): danger zones, resource points, lessons — переживает рестарт.
- `DecisionSession` lease/latest-wins — отбрасывание устаревших async-ответов LLM.
- Mindcraft: автогенерация документа скиллов из контрактов, retrieval с fallback на word-overlap, суммаризация памяти.

### API-мосты и примитивы (mineflayer, MCP)
- Mineflayer — паритет примитивов: blockAt/findBlock(s), dig/place, inventory/equip/toss, chest/furnace, craft/recipesFor, nearestEntity/attack, health/food/oxygen, chat, bed; отдельные модули pathfinder/pvp/auto-eat/tool/collectblock/armor-manager.
- MCP-протокол: JSON-RPC 2.0 + Bearer + discovery (`server.json`) + `timeoutMs`/`request_id`; `tools/list` строить из `/v1/capabilities`.
- Наш `docs/API.md` (REST `/v1`) — первичен; MCP — вторичный адаптер (P6); ядро `core` не знает про HTTP и MCP; формат JSON плана и наблюдения (Jackson) — часть контракта ядра.

### Деревни, экономика, квесты (Millénaire)
- **Данные решают всё**: культуры/работы/магазины/товары/квесты/цели — декларативны, не код.
- Экономика избытка: у товара `targetQuantity` (цель), `reservedQuantity` (неприкосновенный резерв), цены, `minReputation`; доступно = запас − резерв − нужды − стройка.
- Логистика: `shopNeeds` + цели «добудь/разнеси» — дефицит → заказ, разнос свободными жителями.
- Торговля межпоселенческая через чужеземного торговца, наценка за чужую культуру, шанс приезда растёт от дружбы (`relation > VERYGOOD`).
- Отношения деревень `-100..100`; репутация двух уровней (деревня + культура), пороговые лестницы.
- Квесты — DSL: `chancePerHour`, `minReputation`, `maxSimultaneous`, награды/штрафы, теги, связь NPC (`samehouse/samevillage/nearbyvillage/anyvillage`).
- Стройка: `BuildingPlanSet` = варианты × уровни-апгрейды, чертёж = `.txt`+PNG, стоимость выводится из плана; маркеры-роли (`mainchest`, `sleepingPos`, `sellingPos`, `soils`, `spawns`).
- Семья: `VillagerRecord` (spouse/father/mother/housePos, инвентарь, questTags) в NBT ратуши.
- Неофордж-порт (Leviaria, 1.21.1): `MillVillager extends PathfinderMob` + свой `GoalScheduler` (приоритет/окна/cooldown/watchdog/`forceTask()`) — шов под внешний ИИ; данные в `resources/millenaire/_manifest/*.json`; `SavedData` `millenaire_villages` с версионированием.

## Что из этого уже отражено в todo.md
- Раздел «Заимствование у других проектов» — навигация, навыки, куррикулум, верификация, память, привычки, опасности.
- Модель работ/склада/торговли строится по мотивам MineColonies и Millénaire (избыток, резерв, пороги).
- Слои политики/тактика/стратегия и событийный ре-план — согласуются с `GoalScheduler`/`TaskChain`.

## Что стоит добавить в дизайн (по итогам исследования)
- Явная **верификация результата задачи** (postcondition) и ре-план по провалу с обратной связью в промпт.
- **Библиотека навыков** с retrieval и сохранением только подтверждённых успехов.
- **Модель товаропотоков**: `targetQuantity` + `reservedQuantity` + «доступно = запас − резерв − нужды − стройка».
- **Отношения/репутация** как явные шкалы (деревня/культура, `-100..100`).
- **Квест-DSL** как данные (chancePerHour/minReputation/награды/теги).
- **Лизинг/epoch для LLM-ответов** (latest-wins), чтобы отбрасывать устаревшие решения.
- Долговременная **память без LLM** (danger zones, resource points, lessons), переживающая рестарт.
