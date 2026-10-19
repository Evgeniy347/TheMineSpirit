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
  (mc_aiplayer `DecisionSession`) и идемпотентность запросов (у нас уже `request_id` в `docs/api/README.md`).
- **Стоимость/токены**: on-demand документ и селективный `warm_up`/retrieval уменьшают промпт.
- **Секреты**: ключи провайдеров держать в окружении bridge, не в git (как в mc_aiplayer `DEEPSEEK_API_KEY`).
- **Юридический риск производного кода**: при заимствовании идей из AGPL-проекта фиксировать
  в документации, что код не копировался; хранить эту карту как доказательство независимости.
