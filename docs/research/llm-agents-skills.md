# LLM-агенты, навыки и обучение — карта переиспользования

Исследование четырёх локально склонированных LLM-агентных проектов под Minecraft.
Цель — извлечь переиспользуемые подходы к наблюдению, промптам, памяти, библиотеке навыков,
авто-куррикулуму, верификации и формату плана, и соотнести их с ядром `com.theminespirit.core`
(`ScriptCall` / `SequenceNode` / `FallbackNode`, `PlanParser`, `LlmPlanner`, `ScriptCatalog`).

> Статус: только анализ. Код проектов не менялся. Файлы репозиториев не редактировались.
> Все пути ниже — относительно корня соответствующего репозитория.

## Обзор репозиториев

| Репозиторий | Назначение | Стек | LLM / провайдеры | Лицензия |
|---|---|---|---|---|
| `mindcraft` | Фреймворк автономных ботов на mineflayer; multi-agent, режимы поведения, tool/skill-команды | Node.js (ESM), mineflayer, socket.io, express, ESLint | Сменные профили в `src/models/*.js`: OpenAI, Claude, Gemini, Grok, Groq, Ollama, vLLM, OpenRouter, DeepSeek, Mistral и др. | **MIT** (© 2024 Kolby Nottingham) |
| `mc_aiplayer` | Серверный Fabric-мод: «LLM выбирает намерение, goal engine планирует, 34 детерминированные task-машины исполняют» | Java 21, Fabric 1.21.3, Gradle; ~197 классов main, отдельный gametest source set | DeepSeek через OpenAI-совместимый `/v1/chat/completions`, tool calling, `thinking`-блок | **MIT** (© 2026 zoyluo) |
| `mc-ai-agent-mod` | Клиентский Fabric-мод: skill-based агент, natural-language планирование, tool calling; вызов скиллов также по WebSocket | Java 21/25, Fabric (1.21.11 и 26.2), multi-module Gradle (common/platform/cloud-agent) | Любой OpenAI-совместимый Chat Completions + WebSocket `skill_call`/`skill_result` + поиск (Google/百度/Bing) | **AGPL-3.0** — **copyleft** |
| `voyager` | Lifelong-learning агент: авто-куррикулум, растущая библиотека скиллов, итеративный промптинг, self-verification | Python + LangChain + Chroma; JS-мост на mineflayer (env/mineflayer) | OpenAI GPT-4 (action/critic/curriculum), GPT-3.5 (skill manager/QA), OpenAI Embeddings | **MIT** (© 2023 MineDojo Team) |

Дополнительно: `voyager/voyager/env/mineflayer/mineflayer-collectblock` — вложенный пакет под своей лицензией
(проверять отдельно); `mc-ai-agent-mod` в `THIRD_PARTY_NOTICES.md` явно указывает, что навигация
вдохновлена `cabaletta/baritone` (**LGPL-3.0+**), но реализована независимо и не линкует Baritone.

---

## Архитектура / паттерны

### 1. Наблюдение мира для LLM

**Voyager — событийная лента, а не «просто состояние».**
`voyager/env/mineflayer/lib/observation/` реализует абстрактный `Observation` (`base.js`) с `inject(bot, obs_list)`;
каждый наблюдатель пишет в `bot.cumulativeObs` запись `[event_name, {obs...}]`. Наблюдатели:
`status.js` (health/food/position/biome/entities/timeOfDay/inventoryUsed/equipment),
`inventory.js`, `chests.js` (запоминает содержимое сундуков по позиции),
`voxels.js` (`Voxels` + `BlockRecords` — «недавно виденные» блоки), `onChat.js`, `onError.js`, `onSave.js`.
Потребители (`agents/action.py`, `agents/curriculum.py`, `agents/critic.py`) собирают из последнего
`observe`-события текстовую сводку: Biome / Time / Nearby blocks / Nearby entities / Health / Hunger /
Position / Equipment / Inventory (xx/36) / Chests / Task / Context / Critique.
Особенность: сущности сортируются «ближайшие→дальние»; при отсутствии поверхностных блоков биом
подменяется на `underground`; инвентарь фильтруется по «ядру» до прогресса `warm_up`.

**Mindcraft — запросы-команды + плейсхолдеры промпта.**
Наблюдение не хранится как снимок, а запрашивается инструментами чтения
`src/agent/commands/queries.js`: `!stats`, `!inventory`, `!nearbyBlocks`, `!craftable`, `!entities` и т.д.
`src/models/prompter.js::replaceStrings` подставляет `$STATS`, `$INVENTORY`, `$COMMAND_DOCS`,
`$CODE_DOCS`, `$MEMORY`, `$EXAMPLES`, `$SELF_PROMPT`, `$LAST_GOALS`, `$BLUEPRINTS`.
Ключевая идея: **наблюдение — это часть system-промпта, который собирается из живых запросов**, а не
фиксированный дамп — LLM видит ровно то, что ей разрешено.

**mc_aiplayer — структурированный JSON-снимок в user-сообщении.**
`perception/PerceptionSnapshot.java` (`SelfState`, `Highlights` с `nearest_tree/stone/ore/water/furnace/chest/bed/crafting_table/hostile`,
`NearbyBlock/Entity/Item`, `TaskInfo`, `TimeInfo`) + `PerceptionCollector.collect(bot)`.
В `brain/BrainCoordinator.handleMessage` снимок кладётся в user-turn:
`"[sender] says: ... Current state:\n" + snapshot.toJson()`. Активная задача также отражается в
`TaskInfo` (`name/state/progress/elapsedTicks/failureReason`) — LLM видит прогресс, не опрашивая мир.

**mc-ai-agent-mod — платформенные интерфейсы + выдаваемый «manifest».**
`platform/minecraft-api/.../Observation.java` (позиция, health, food, dimension, time, nearbyBlocks,
nearbyEntities) и `common/context-api/.../GameContext.java` (+ `Threat`). `AgentRuntime.toolManifest()`
отдаёт LLM JSON-описание всех скиллов (description + JSON-Schema + permission). Наблюдение не
инжектится сплошняком, а добывается скиллами (`ObserveSkill`, `FindBlockSkill`), т.е. «observe before
assuming» зашито в промпт.

### 2. Промпты

- **Voyager** — отдельные файлы `voyager/prompts/`: `action_template.txt` (жёсткий формат
  Explain/Plan/Code, требования async, запрет `bot.dig` в пользу `mineBlock` и т.п.),
  `action_response_format.txt`, `critic.txt` (JSON с примерами), `curriculum.txt`,
  `curriculum_task_decomposition.txt` (список подцелей), `skill.txt` (описание функции), QA-промпты.
  Формат ответа жёстко зафиксирован и парсится регулярками/JSON.
- **Mindcraft** — `profiles/defaults/_default.json`: `conversing`, `coding`, `saving_memory`,
  `bot_responder`, `image_analysis`, `goal_setting`, а также `conversation_examples` и
  `coding_examples` (few-shot). Профили наследуются: `_default` → base (`assistant/survival/creative/god_mode`)
  → индивидуальный профиль (`src/models/prompter.js`).
- **mc_aiplayer** — один большой `systemPrompt(botName)` в `brain/BrainCoordinator` (12 правил:
  «сначала намерение, потом tool calls», приоритет высокоуровневых goal-инструментов, правила
  queue/replace/cancel, запрет ручного mine без кирки и т.п.). Дешёвые правила важнее few-shot.
- **mc-ai-agent-mod** — `NaturalLanguageAgent.SYSTEM_PROMPT`: «наблюдай прежде, чем предполагать;
  не утверждай успех, пока tool result его не подтвердил; перед новым goal вызови `agent.set_plan`».
  Тяжёлый «операционный контракт» вынесен в отдельный документ и читается по требованию скиллом
  `agent.read_document` — **экономия токенов через on-demand документ**.

### 3. Память

- **Mindcraft**: `src/agent/history.js` — окно `max_messages`; при переполнении кусок выносится в
  текстовую суммарную память (`promptMemSaving`, лимит 500 символов) + полная история в файл.
  `src/agent/memory_bank.js` — именованные координаты (`rememberHere`/`recallPlace`).
  `History.save()/load()` сохраняют память, turns, self-prompt, taskStart, last_sender.
- **Voyager**: `action/chest_memory.json` (содержимое сундуков), `skill/skills.json` + Chroma-векторбаза
  (вектор по описанию скилла), `curriculum/qa_cache.json` + отдельная Chroma-база вопросов,
  `completed_tasks.json` / `failed_tasks.json`. Всё в `ckpt_dir`, поддерживается `resume`.
- **mc_aiplayer**: `memory/BotMemory` (fact/places/goal-steps), `BotMemoryStore`, `EpisodeLog`
  (эпизодический поток) и `KnowledgeBase` — **правило-дистилляция без LLM**: повторные смерти в
  радиусе 16 → «danger zone», найденные ресурсы → «resource points», провал/успех цели → «lesson».
  Хранится в `world/aibot/knowledge_<uuid>.json`, переживает рестарт. Это готовый, детерминированный
  и бесплатный слой долговременной памяти.
- **mc-ai-agent-mod**: `ConversationContext` (сжатие контекста), `memory/Checkpoint(Manager)` —
  JSON-checkpoint задачи (`skill`, `status`, `reason`, `position`, `progress`, `createdAt`).

### 4. Библиотека навыков

- **Voyager — эталон по нашему профилю.** `agents/skill.py::SkillManager`:
  `control_primitives` (JS-функции) + выученные скиллы; `add_new_skill` пишет
  `skill/code/<name>.js`, `skill/description/<name>.txt`, `skill/skills.json`; `generate_skill_description`
  через LLM; `retrieve_skills(query)` — **векторный поиск top-k по описанию** (Chroma + OpenAI
  Embeddings). В action-промпт попадают `control_primitives` + `self.skills` + `retrieved skills`.
  Скилл сохраняется только при `success` (см. `voyager.py::learn`).
  Пример наполнения: `skill_library/trial1/skill/skills.json` (51 скилл), `code/*.js`, `description/*.txt`.
- **Mindcraft**: `src/agent/library/skills.js` (~2093 строк, JSDoc-контракты к каждой функции) +
  `library/world.js`; `library/index.js::getSkillDocs` **извлекает JSDoc** и формирует документ
  скиллов. `library/skill_library.js::SkillLibrary` строит эмбеддинги по имени+описанию,
  `getRelevantSkillDocs(message, select_num)` — косинусная близость, fallback `wordOverlapScore`
  (`utils/text.js`), плюс `always_show_skills`. Всё уходит в `$CODE_DOCS` кодинг-промпта.
- **mc-ai-agent-mod**: `common/skill-api` (`Skill`, `SkillSchema`, `SkillResult`, `PermissionLevel`),
  `skill-registry/DefaultSkillRegistry`, `agent-core/BuiltinSkills` (Observe, Navigate, FindBlock,
  Inventory, ManageInventory, Interact, MineBlock, PlaceBlock, Container, ContainerTransfer, Craft,
  FindRecipe, Smelt, FindDroppedItems). Скилл валидирует аргументы схемой, проверяет permission и
  возвращает структурированный `SkillResult{success,message,error,data}`.
- **mc_aiplayer**: `brain/ToolRegistry` — **63 высокоуровневых инструмента** (tool calling JSON-Schema),
  которые распределяются по `Goal`/`Task` (34 детерминированные state machine). Группы
  `CORE/MEMORY/COORDINATION/LOW_LEVEL`; низкоуровневые (`move_to`, `mine_block`, `place_block`) сознательно
  скрыты/понижены в приоритете, чтобы LLM не «дёргала за пиксели».

### 5. Авто-куррикулум

**Voyager** — единственный из четырёх с полноценным куррикулумом (`agents/curriculum.py`):
- `propose_next_task(events, chest_observation)` → `(task, context)`;
- `warm_up`-словарь: какие поля наблюдения показывать с какого прогресса (обучение «от простого к сложному»);
- первый таск захардкожен (`Mine 1 wood log`), а при почти полном инвентаре — принудительный
  «Deposit/Craft chest» (страховка от заклинивания);
- `completed_tasks` / `failed_tasks` дедуплицируются и сохраняются; провал переносит задачу в failed;
- `run_qa`/`get_task_context`: кэш «как сделать X» в векторбазе (similarity < 0.05 = попадание);
- `decompose_task` — LLM выдаёт Python-список подцелей вида `Mine/Craft/Smelt/Kill/Cook/Equip N item`.

**mc_aiplayer** реализует родственное, но детерминированное: `goal/GoalPlanner` раскладывает
`Goal` (HaveItem, HavePickaxeTier, MineOre, HarvestCrop, Armor, Workstation, Stockpile, Food, Build)
в упорядоченные `GoalStep` до `maxPlanDepth`, с гарантией инструментов и еды. Это «шаблонный
куррикулум» для предопределённых целей — дешевле и надёжнее LLM-куррикулума, но менее открытый.

**mc-ai-agent-mod / Mindcraft** явного куррикулума не имеют: Mindcraft использует `self_prompter`
(бесконечный цикл само-цели) и `Task`/профили; mc-ai-agent-mod — планирует по запросу.

### 6. Верификация (self-verification)

- **Voyager — CriticAgent** (`agents/critic.py`) проверяет успех задачи отдельной LLM по состоянию мира
  и выдаёт JSON `{reasoning, success, critique}`; `critique` возвращается в следующий промпт действия.
  Плюс `ActionAgent.process_ai_message` ловит ошибки исполнения, а `voyager.py::step` решает
  `done = success || iter >= max_retries`. Второй уровень — `critic.txt` с примерами (инвентарь →
  вывод «добыл ли 3 бревна»; hunger 20.0 → «поел»; nearby farmland → «посадил»).
- **mc_aiplayer — postcondition-верификация**: `goal/GoalPredicates`/`GoalEvaluation`, `GoalSnapshotCollector`,
  `GoalResult.classify` (SATISFIED → COMPLETED, matched>0 → PARTIAL, иначе FAILED), лимиты
  `MAX_CONSECUTIVE_REPLANS`/`MAX_POSTCONDITION_REPLANS`, durable «receipts» пропущенных шагов,
  checkpoint/pocket-восстановление. Завершение task ≠ успех goal — сильный принцип для нас.
- **mc-ai-agent-mod**: `SkillResult.success/error` обязателен в промпте; `AgentPlanTracker`
  (`startStep/finishStep`), `RunPlan` возвращает `plan_step_mismatch` при отклонении от плана и
  требует `agent.set_plan` заново; `RunMemory` ловит повторяющиеся одинаковые вызовы (anti-loop).
- **Mindcraft**: `src/agent/coder.js` — цикл до 5 попыток кода с линтингом (ESLint + проверка
  несуществующих `skills.*`/`world.*`), исполнение ошибки возвращается в историю;
  `action_manager.js` — таймауты действий, детект «fast action loop»/«infinite action loop», force-stop.

### 7. Оркестрация и безопасность

- **Mindcraft**: `src/mindcraft/mindserver.js` — центральный socket.io-хаб для нескольких агентов;
  `conversation.js` — протокол «двух ботов» (`(FROM OTHER BOT)`, `startConversation`); `modes.js` —
  непрерывные режимы (`self_preservation`, `self_defense`, `hunting`, `item_collecting`, `torch_placing`,
  `cowardice`), работающие поверх LLM. Настройки `settings.js`, блокировка команд `blacklistCommands`.
- **mc-ai-agent-mod**: `PermissionPolicy` (`READ_ONLY/MOVEMENT/INTERACT/DANGEROUS`),
  `SafetyController` с уровнями `NORMAL < MANUAL_CONTROL < DANGER < SURVIVAL_MODE < EMERGENCY_EXIT`,
  `ManualControlMonitor`, `WaterClutchStateMachine` (ведро на падении), `TaskController`/`Checkpoint`.
- **mc_aiplayer**: `OperatingProfile` (`strict_survival`/`operator`), `CapabilityRuntime`/
  `PrivilegedCapability`, `auth/BotAuthorizationGate`, `DecisionSession` — lease-машина для
  асинхронных LLM-callback (latest-wins epoch, отклонение устаревших ответов), `runtime.IntentControlTransaction`.
- **Voyager**: `reset_placed_if_failed` откатывает установленные блоки; `--difficulty peaceful` до
  прогресса > 15; изоляция исполнения кода — вне нашего периметра.

---

## Что переиспользовать у нас (идея → наш модуль)

| Идея источника | Куда у нас | Как |
|---|---|---|
| Voyager: векторный `retrieve_skills(query)` по описанию + `control_primitives` в промпте | библиотека навыков | при складывании `ScriptCatalog` строить текстовые описания (у нас уже есть `ScriptSpec.summary/preconditions/effects`) и добавлять релевантные в промпт `LlmPlanner.buildPrompt` |
| Voyager: `generate_skill_description` и сохранение скилла **только при success** | библиотека навыков / ран-тайм | верифицированный план → новый скрипт/композиция; нерабочий не сохранять |
| Voyager: `decompose_task` → список подцелей | `LlmPlanner` / авто-куррикулум | декомпозиция цели в `SequenceNode` из `ScriptCall` (у нас это уже дерево) |
| Voyager: `warm_up` + `completed/failed_tasks` + принудительный «deposit» | авто-куррикулум | управление сложностью и защита от заклинивания |
| Voyager: CriticAgent JSON `{success, critique}` | верификация | отдельная проверка postcondition + critique в следующий промпт |
| mc_aiplayer: `PerceptionSnapshot.toJson()` в user-turn | наблюдение | сериализовать `WorldObservation` (уже есть `toJson`/`hash`) в промпт `LlmPlanner` — сейчас `buildPrompt` шлёт только `hash` |
| mc_aiplayer: postcondition ≠ завершение шага, `GoalResult` PARTIAL | верификация | `TaskRunner`/`RunStatus`: отличать «скрипт вернул done» от «цель достигнута» |
| mc_aiplayer: `KnowledgeBase` — правиловая дистилляция смерти/ресурсов/уроков | память | дешёвая долговременная память без LLM, JSON-персист |
| mc_aiplayer: `DecisionSession` lease + latest-wins | LLM-клиент / оркестрация | защита от гонок и устаревших ответов ИИ (важно для REST-оркестратора) |
| mc_aiplayer: `GoalPlanner` (HaveItem→рецепты→gather/craft/smelt) | `ResourceResolver` | у нас уже есть `ResourceResolver`; дополнить скилловыми пред/постусловиями |
| mc-ai-agent-mod: `agent.set_plan` (шаги {skill, description}) + mismatch | формат плана | адаптировать понятие «плана из шагов» под `SequenceNode` и строгий `PlanParser` |
| mc-ai-agent-mod: on-demand документ (`agent.read_document`) | LLM-клиент | вынести большой системный контракт из промпта в читаемый ресурс |
| Mindcraft: `getSkillDocs` из JSDoc, `$CODE_DOCS` | каталог скриптов | генерировать `ScriptSpec`-доки автоматически из аннотаций скриптов |
| Mindcraft: суммаризация памяти с лимитом | память | окно истории + сжатие в текстовую память |

---

## Формат плана / действий и его соотношение с `ScriptCall` / `Sequence` / `Fallback`

У всех четырёх «план» — это либо код (Voyager, Mindcraft `!newAction`), либо плоский список
tool-call (mc_aiplayer, mc-ai-agent-mod). Наше дерево `TaskNode` богаче; полезны конкретные приёмы.

| Проект | Формат действия | Отношение к нашему `TaskNode` |
|---|---|---|
| Voyager | JS-функция `async function f(bot)` + `control_primitives`; декомпозиция — Python-список `["Mine 3 wood log","Craft ..."]` | Аналог `SequenceNode` из `ScriptCall`; декомпозиция = построение `Sequence` снизу вверх |
| Mindcraft | ровно **одна** `!command` на ответ (или `!newAction` → сгенерированный код) | ортогонально: печать одного атомарного действия, у нас — `ScriptCall` |
| mc-ai-agent-mod | `agent.set_plan(steps:[{skill, description}])`, затем строгий порядок; mismatch → replan | прямой аналог линейного `SequenceNode`; добавляет «проверку соответствия шагов плану» |
| mc_aiplayer | множественные `tool_calls`; высокоуровневые goal-tools с **очередью** (`продукт` → `mine_ore` подряд), `stop`/`cancel_all` | концепция очереди целей ≠ наше дерево; ближе к `SequenceNode` + внешний `TaskRunner` |
| **The Mine Spirit** | `TaskNode` = `ScriptCall` \| `SequenceNode` \| `RepeatNode` \| `FallbackNode` \| `ParallelNode` \| `ConditionNode` | `PlanParser` валидирует дерево (MAX_DEPTH=16, MAX_NODES=256) против `ScriptCatalog`; `LlmPlanner` при ошибке LLM/валидации → `FallbackPlanner` |

Практические выводы для парсера/планировщика:
1. **Строгая валидация** (как `PlanParser` + `mc-ai-agent-mod` schema/`plan_step_mismatch`) должна
   быть до исполнения; LLM возвращает только узлы и `script` из `ScriptCatalog`, произвольный код
   запрещён — это уже реализовано у нас, сохранить.
2. **Fallback как первоклассный узел**: у нас `FallbackNode` даёт «первый успешный шаг» — у Voyager
   это retry-петля, у mc_aiplayer — retry того же goal. Стоит явно описать fallback-семантику в промпте LLM.
3. **Проверка postcondition** после каждого шага (mc_aiplayer) полезнее простого «скрипт завершился»;
   `ConditionNode` у нас может быть точкой пост-проверки.
4. **Ограничение размера/глубины** (у нас уже есть) + anti-loop (mc-ai-agent-mod `RunMemory`) — обязательно.
5. **Очередь целей** (mc_aiplayer) — идея для внешнего оркестратора, а не для `TaskNode`.

---

## Конкретные рекомендации (брать / адаптировать / избегать)

**Брать (MIT, разрешено с сохранением copyright):**
- Схему CriticAgent `{reasoning, success, critique}` и примеры из `critic.txt` — как образец
  самопроверки и постанализа причины провала.
- Принципы `decompose_task` (Mine/Craft/Smelt/Kill/Cook/Equip) — маппинг на наши `ScriptCall`.
- `KnowledgeBase`-дистилляцию (danger zones / resource points / lessons) — как дешёвую память.
- `PerceptionSnapshot`-подобный JSON-снимок мира в промпт планировщика.
- JSDoc/аннотации → автоматические описания `ScriptSpec` (Mindcraft `getSkillDocs`).
- `DecisionSession` lease, retry/backoff DeepSeek-клиента, `GoalResult` PARTIAL/FAILED.

**Адаптировать (переписать по-своему):**
- Векторную библиотеку навыков (Voyager/Mindcraft): у нас нет embeddings — завести либо простой
  `wordOverlapScore`-поиск по `ScriptSpec` (как fallback Mindcraft), либо локальные эмбеддинги.
- Авто-куррикулум: вместо LLM-генерации произвольных задач — шаблонный `GoalPlanner`-подход
  (mc_aiplayer) плюс LLM только для декомпозиции в известные скрипты.
- Многоагентную оркестрацию (Mindcraft mindserver) — только если появится несколько духов.
- Safety-уровни (mc-ai-agent-mod) — как вдохновение для guardrails, а не копия (AGPL).

**Избегать:**
- Исполнения произвольного кода от LLM (Voyager `compartment`, Mindcraft
  `allow_insecure_coding`/`eval`) — у нас whitelist-скрипты, это верное решение.
- Единственного «делай один tool call и жди» как единственного режима (Mindcraft) — теряется
  планирование; наше дерево лучше.
- Хардкода правил «как играть» в system prompt в объёме, который не влезает в контекст
  (mc_aiplayer) — вынести в on-demand документ.
- Доверия к «success» от LLM без проверки мира — обязательна postcondition-проверка.

---

## Риски и лицензионные ограничения

**Лицензии (обязательный итог):**
- `mindcraft` — **MIT** (© 2024 Kolby Nottingham). Разрешено использовать/копировать при сохранении
  copyright и текста лицензии. Не copyleft.
- `mc_aiplayer` — **MIT** (© 2026 zoyluo). То же.
- `voyager` — **MIT** (© 2023 MineDojo Team). То же; но `env/mineflayer/mineflayer-collectblock` —
  отдельный пакет, лицензию проверять отдельно перед заимствованием кода.
- `mc-ai-agent-mod` — **AGPL-3.0, строгий copyleft**. **Нельзя** копировать код в наш мод.
  Даже программное использование по сети влечёт требования AGPL. Допустимо изучать идеи и
  реализовывать независимо (clean-room); заметка о Baritone (LGPL) — тоже лишь «informed by».

**Технические/продуктовые риски:**
- **Prompt injection**: любой текст из чата мира может попытаться переопределить правила; у
  mc_aiplayer/mc-ai-agent-mod правила защиты — образец (permission, whitelist, safety interlock).
- **Галлюцинации/невалидный план**: обязательны `PlanParser`-валидация и `FallbackPlanner` (у нас есть).
- **Зацикливание и «флафлинг»**: анти-паттерн описан в mc_aiplayer (retry того же goal, затем стоп);
  anti-loop `RunMemory` (mc-ai-agent-mod) и лимиты replan (mc_aiplayer) — внедрить.
- **Гонки async-ответов LLM**: при внешнем оркестраторе по REST нужен lease/latest-wins
  (mc_aiplayer `DecisionSession`) и идемпотентность запросов (у нас уже `request_id` в `API.md`).
- **Стоимость/токены**: on-demand документ и селективный `warm_up`/retrieval уменьшают промпт.
- **Секреты**: ключи провайдеров держать в окружении bridge, не в git (как в mc_aiplayer `DEEPSEEK_API_KEY`).
- **Юридический риск производного кода**: при заимствовании идей из AGPL-проекта фиксировать
  в документации, что код не копировался; хранить эту карту как доказательство независимости.
