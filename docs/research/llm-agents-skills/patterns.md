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

