# NPC AI, цели и команды — карта переиспользования

Источник: локальный клон `git@github.com:CitizensDev/Citizens2.git` (ветка `master`, коммит `7aecaed`), путь `/opt/minecraft/research/citizens`. Материал собран только чтением репозитория, сборки не запускались.

## Обзор

- **Назначение**: Citizens — фреймворк NPC для **Bukkit/Spigot/Paper** (`main/src/main/resources/plugin.yml`). Позволяет создавать NPC, давать им сущности, черты, цели, поведенческие деревья, команды, патруль, взгляд и движение.
- **Платформа**: Bukkit-плагин, серверный. НЕ NeoForge/Forge. Основной модуль `citizens-main` зависит от `net.citizensnpcs:citizensapi` (`main/pom.xml`) — вся публичная модель (Goal/Behavior/GoalController/Trait/Memory/Navigator) живёт в отдельном артефакте **CitizensAPI**, которого в клоне нет.
- **Версии**: `citizens-parent` 2.0.44-SNAPSHOT. Версионные модули `v26_1_R1`, `v26_2_R1`, `v26_3_R1` рассчитаны на **Paper/Minecraft 26.x** — в этой копии нет модулей под 1.21.1. Это важно: клон не совпадает ни по майнкрафт-версии, ни по платформе с нашим проектом.
- **Лицензия**: **Open Software License 3.0 (OSL 3.0)** — файл `LICENSE`. Copyleft, со специфичной клаузой: *внешнее развёртывание приравнивается к распространению* (раздел 5), производные обязаны лицензироваться под OSL 3.0 (раздел 1c), требуются attribution-заметки (раздел 6). Подробнее — в разделе «Риски и лицензионные ограничения».

## Архитектура (по пакетам/классам)

Все пути — относительно `/opt/minecraft/research/citizens/main/src/main/java/net/citizensnpcs/...`.

### goals / контроллер поведения

- Единый контроллер: `npc.getDefaultBehaviorController().addBehavior(...)` / `removeBehavior(...)` вызывают и «цели», и корень поведенческого дерева (`trait/waypoint/*.java`). В современном Citizens понятия Goal и Behavior схлопнуты в один контракт `Behavior` (API): `shouldExecute()` + `run() -> RUNNING/SUCCESS/FAILURE` + `reset()`. Цель патруля — это обычный Behavior (`LinearWaypointGoal` в `trait/waypoint/LinearWaypointProvider.java:487`), который `shouldExecute()` возвращает `false`, пока уже идёт навигация, и `run()` держит `RUNNING` во время движения.
- API-цели, используемые локально: `api.ai.goals.WanderGoal` (блуждание в боксе/регионе, `trait/waypoint/WanderWaypointProvider.java:273`), builder-паттерн, `pause()/unpause()`, параметры `xrange/yrange/delay/pathfind`, регионы через `PhTreeSolid` (пространственный индекс).
- В API (не в клоне) — собственно `GoalController`, приоритеты и квоты выбора; локально видны только его внешние контракты.

### behavior trees

- `npc/ai/tree/BehaviorTreeParser.java` — парсер YAML (DataKey) в дерево `Behavior`. Узлы: `sequence/seq`, `random/selector`, `parallel` (`ParallelComposite`), `invert`, `loop <условие>`, `timeout <тики>`, `if <условие>/else`, `eval [язык]`; листья:
  - строки с `/` → выполнить команду от NPC (`createCommandBehavior`, поведение `InstantBehavior`);
  - строки с `` ` `` → вычислить выражение с сайд-эффектом;
  - имя + inline-аргументы (с кавычками и `key=value`) → `registry.createBehavior(name, params, ctx)` (функция `parseInlineArgs`).
  - Накопившиеся подряд `InstantBehavior` коалесцируются в `CoalescedBehavior` (экономия тиков).
- `npc/ai/tree/CitizensBehaviorRegistry.java` — поведение, зависящее от реализации: `print`, `say`, `look x y z`, `teleport`. Базовые (`cooldown`, `patrol`, `walkto`, `wait`, `set`, `forget` и т.д.) регистрируются в API (`registerDefaults`), видны в примере `main/src/main/resources/templates/citizens/templates.yml`.
- `trait/BehaviorTrait.java` — черта: превращает YAML-дерево в корневой `Behavior` и кладёт его в контроллер. **Blackboard-память**: `Memory memory` (`getMemory()`), сохраняется/грузится (`load`/`save`), доступна выражениям через `ExpressionScope`. Корень обёрнут в `RootBehavior`: сбрасывается после SUCCESS/FAILURE; интересная деталь — отсечение повторного спама ошибок (`failed` флаг).
- `npc/ai/tree/NPCExpressionScope.java` — биндинги скоупа выражений: `npc.*` (id/name/spawned/x/y/z/yaw/health/velocity...), `target.*` (exists/x/y/z/distance/health — из `npc.getNavigator().getEntityTarget()`), `nav.navigating/paused`, `nearby.count/player/distance`. `Memory` — персистентная хэш-память (blackboard) поверх скоупа.

### traits (черты)

- Регистрация: `npc/CitizensTraitFactory.java` — конструктор регистрирует десятки черт через `TraitInfo.create(Class)` с опциями `.asDefaultTrait()`, `.optInToStats()`, `.withTemplateParser(...)`, `.withSupplier(...)`. Внешний код регистрирует черту тем же способом (`registerTrait`).
- Черты — это не только «свойства», но и активное поведение: у `Trait` есть tick-цикл (`run()`) и жизненный цикл `onSpawn/onDespawn`. Примеры, важные нам:
  - `trait/FollowTrait.java` — следование за сущностью с флоком-расталкиванием (`Flocker`, `RadiusNPCFlock(4,4)`, `SeparationBehavior(1)`), режим `protect` (атаковать обидчика), margin дистанции.
  - `trait/LookClose.java` — «смотрит на игрока»: ближайший в радиусе, per-player сессии вращения, `headOnly`/`linkedBody` (поворот только головы/с телом), случайный взгляд по задержке, `realisticLooking` (через `hasLineOfSight`), отключается во время навигации.
  - `trait/RotationTrait.java` — пакетное вращение головы/тела, сессии на наблюдателя.
  - `trait/Controllable.java` — посадка игрока на NPC; абстракция `MovementController` (`GroundController`, `PlayerInputAirController`, `LookAirController`) — чистая идея «контроллеры движения».
  - `trait/TargetableTrait.java` — можно ли на NPC наводиться мобам; список `targeters` и сброс `Mob.setTarget(null)`.
  - `trait/PausePathfindingTrait.java`, `trait/ChunkTicketTrait.java` — пауза пути и чанк-тикеты.

### commands

- `commands/Citizens.java` (корень пакета — сам плагин `Citizens.java:526`): аннотационно-управляемые классы команд. `registerCommandClass(Class)` → `commands.register(clazz)`; инжектор передаёт NPC и контекст; регистрируются `NPCCommands`, `TraitCommands`, `WaypointCommands`, `AdminCommands`, `EditorCommands`, `TemplateCommands` (`Citizens.java:535`).
- `commands/NPCCommands.java` — десятки методов с `@Command(...)`; модификаторы/флаги, tab-completion (в `Citizens.java:485`).
- `commands/NPCCommandSelector.java` — выбор NPC в команде: по UUID, id, имени + `-range`; при неоднозначности — интерактивная нумерованная беседа (conversation). Хороший шаблон «выбора цели» пользователем.
- `trait/CommandTrait.java` — «команды по клику по NPC», и это самый переиспользуемый командный блок:
  - у команды: сама строка, `Hand` (LEFT/RIGHT/SHIFT_*), `cooldown` (пер-игрок), `globalCooldown`, лимит использований `n`/`gn`, `delay`, стоимость `cost` (деньги/xp/предметы), `perms`, `player`/`op`/`npc` флаги, временные пермишены.
  - **ExecutionMode**: `LINEAR` (все по порядку), `SEQUENTIAL` (по очереди от последней), `CYCLE`, `RANDOM` — готовая модель «очередь действий».
  - персистентное per-player отслеживание (cooldowns, счётчики) со сборкой мусора (`pruneCooldowns`), базовый `getEncodedKey` = base64 команды как ключ.
- Шаблоны-скрипты `main/src/main/resources/templates/citizens/templates.yml` — «шаблон = набор черт+команд» (`on_template_apply`), применяемые к NPC; черта `behavior` поддерживает short-form `tree.yml`.

### memory / navigation

- Навигатор `npc/ai/CitizensNavigator.java` (реализует API `Navigator`):
  - стратегии пути: `StraightLineNavigationStrategy`, `AStarNavigationStrategy` (свой A*), `FlyingAStarNavigationStrategy`, `MCNavigationStrategy` (ванильный пафайндер), `MCTargetStrategy` (движение к сущности).
  - `NavigatorParameters`: `speed`, `range`, `distanceMargin`, `pathDistanceMargin`, `attackRange`, `attackDelayTicks`, `updatePathRate`, `stationaryTicks`, `speedModifier`, `avoidWater`, `destinationTeleportMargin` (телепорт в конце, если цель близко), `stuckAction` (по умолчанию `TeleportStuckAction.INSTANCE` — при застревании телепорт), дефолтный `AttackStrategy` (удар через NMS).
  - **examiners** — плаги к своим предсказаниям проходимости: `MinecraftBlockExaminer` (может стоять), `SwimmingExaminer`, `JumpingExaminer`, `DoorExaminer`, `FallingExaminer`, `FlyingBlockExaminer`, `BoundingBoxExaminer` — декларативный подход «кто говорит, можно ли пройти» (`CitizensNavigator.java:84-89,377-388`).
  - состояние: `stationaryTicks` (застрял → STUCK), single-use callback'и навигации (`addSingleUseCallback` — так `LinearWaypointGoal` узнаёт о прибытии и дёргает триггеры, `LinearWaypointProvider.java:643`), чанк-тикеты во время навигации (`updateTicket`).
  - события навигации: `NavigationBegin/Cancel/Complete/Replace/Stuck` — аналог наших callback'ов.
- `v26_3_R1/.../util/MobAI.java` — как Citizens подключается к ванильному ИИ моба: обёртки `MoveControl`, `PathNavigation`, `JumpControl`, `LookControl`; `EntityNavigation/EntityPathfinder/EntityNodeEvaluator` — переопределение ванильного пафайндера под «свои» барьеры. Прямо не переносится (другие маппинги), но это образец «тонкой прослойки в нативный ИИ».

### Планировщики (beyond goals)

- Официального FSM нет: роль «планировщика» исполняет **BehaviorTreeParser + GoalController** (деревья поверх набора Behaviors).
- Отдельно лежит `MolangEngine.java` — движок выражений (язык Molang), альтернатива дефолтному ExpressionEngine; команда `eval molang:` в деревьях.

## Что переиспользовать у нас (идея → наши цели/тактика/команды)

Идеи Citizens → что взять в The Mine Spirit (ядро `com.theminespirit.core` без импортов Minecraft):

1. **Один контракт «Behavior», а не раздельные Goal/FSM**. `shouldExecute()` + `run() -> RUNNING/SUCCESS/FAILURE` + `reset()`; контроллер тикает все допустимые поведения, а не выбирает одно по приоритету. → наш `SpiritGoal`: «патруль» держит `RUNNING` пока идёт навигация и молчит (`shouldExecute=false`) если уже в пути; приоритет — порядком/квотой, а не числом.
2. **Blackboard-память под Objectives**: `Memory` в `BehaviorTrait` — единая персистентная память NPC для всех целей и дерева. → наши `Memory`/черты духа: помнить цели-точки, счётчики патруля, «агрессия ок/нет».
3. **Патруль как связка «провайдер точек + цель»**: `LinearWaypointProvider` (упорядоченный обход, `cycle` = пинг-понг, `cachePaths`) и `WanderGoal` (случайная точка в боксе/регионе через PhTree). → `SpiritPatrolGoal` + `PatrolPath` (список точек, режим loop/pingpong/random).
4. **Триггеры на достижение точки** (`WaypointTriggerRegistry`: `delay`, `command`, `chat`, `teleport`, `speed`, `animation`, персистятся в waypoint). → события «дух на точке X»: медитация/пауза на точке, запуск команды, телепорт-ветка.
5. **Хук прибытия = single-use callback навигации**, а не опрос дистанции в цели. → наш `onReached` в патруле.
6. **Анти-застревание**: `stationaryTicks` + `stuckAction` (по умолчанию телепорт). → политика «$N тиков без движения → сменить цель/телепорт/пауза».
7. **Combat**: `MCTargetStrategy(aggressive)` — сближение, `attackRange` + distance + Line of Sight + `attackDelay`; **`AttackStrategy` — плагин «как бить»** (дефолт — обычный удар, можно подменить). → `SpiritCombatGoal`: наша замена (у духов может не быть ванильного урона), интерфейс `AttackBehavior`.
8. **Деревья через DSL + выражения**: parse YAML → узлы (sequence/selector/loop/if/timeout) + выражения над скоупом (`npc.*`, `target.*`, `memory.get/set`) + inline-аргументы. → скриптуемые «тактики духа» для ИИ-команд, без правки Java. У нас это можно сделать мини-ДСЛ на объектах (не обязательно YAML): `seq(if(query, walkTo(x), say(...)))`.
9. **Командная модель `CommandTrait`**: команды пользователя как данные: cooldown/globalCooldown, лимиты, `delay`, `ExecutionMode` LINEAR/SEQUENTIAL/CYCLE/RANDOM, per-player состояние. → команды «игрок → дух»: «пойди/GOTO», «атакуй/стой», «патруль/домой» с кулдаунами и очередью исполнения.
10. **Взгляд отдельно от тела**: `LookClose` + `RotationTrait` (headOnly/linkedBody, per-player сессии, случайный взгляд). → дух «осматривается» при патруле/бое, не замирая, и не конфликтуя с навигацией.
11. **Экспрешен-скоуп как единый «сенсорный слой»**: `npc.*`, `target.*`, `nav.*`, `nearby.*` биндинги ленивые (lazy). → наши условия тактик оперируют именованными сенсорами, а не прямыми вызовами Minecraft API.
12. **Выбор сущности в команде**: `NPCCommandSelector` (id/имя/UUID, при неоднозначности — нумерованный промпт). → выбор духа игроком/ИИ, когда имён несколько.

## Портируемость (Bukkit → NeoForge)

- Citizens — Bukkit-плагин, собран под **Paper 26.x**; код этой копии массово использует Bukkit API, NMS-хелперы и версионные модули. В **NeoForge 1.21.1** этого API нет, а ванильные классы имеют другие маппинги/имена. **Напрямую ничего не портируется** — портируются только идеи и интерфейсы.
- CitizensAPI (интерфейсы Goal/Behavior/Memory/Navigator/Trait) — отдельный артефакт той же лицензии; внутри клона его исходников нет, но контракты видны по использованию. Для проекта это скорее «эталон сигнатур», чем источник кода.
- Наши переводы «1:1 по смыслу»: `Behavior` → `SpiritGoal`, `BehaviorStatus` → enum `SpiritGoalStatus { RUNNING, SUCCESS, FAILURE }`, `Memory`(blackboard) → наши `MemoryContext`, `NavigatorParameters.stuckAction/stationaryTicks` → `SpiritNavigationPolicy`, `AttackStrategy` → `SpiritAttack`, `WaypointProvider+WaypointTrigger` → `SpiritPatrol + SpiritPatrolAction`, `ExpressionScope` → `SensorBindings`.
- Vanilla-слой (частный случай): Citizens показывает, что делать прослойку между «своими» целями и нативным ИИ — `MobAI` с Move/Path/Jump/Look-контроллерами. У нас для 1.21.1 NeoForge это аналог: отдельный от ядра серверный адаптер `NeoForgeSpiritBridge` с вызовом ванильного пафайндера (`ServerLevel.getEntities`, `p.setNoAi(false)` и т.п.), но сам AI живёт в ядре.
- Построение «экспертных» предикторов (examiners) у нас в ядре без Minecraft не имеет смысла — это серверный слой проходимости через ванильный `PathNavigation`/`getPathfindBlockPath`. В ядре оставляем интерфейс, в серверном адаптере — реализацию.

## Конкретные рекомендации

**Брать (как идею/сигнатуру, писать с нуля):**
- единый контракт цели-поведения с `RUNNING` и `reset()`;
- blackboard-память, общую для целей/тактик/команд;
- патруль «список точек + режим + триггеры на точке»;
- анти-застревание через stationary-счётчик + stuckAction;
- плагинный `AttackStrategy`/`StuckAction` (интерфейсы, тестируемы без мира);
- single-use callback завершения навигации как источник события «точка достигнута»;
- дерево-ДСЛ (sequence/selector/loop/if/timeout) поверх целей;
- команды-данные с ExecutionMode и cooldown/лимитами (CommandTrait).

**Адаптировать (своя реализация, близкая по назначению):**
- ленивый ExpressionScope (`npc.*/target.*/nav.*/nearby.*`) — у нас сенсоры `SpiritSensor`;
- LookClose/RotationTrait → «дух осматривается»: поворот головы (при 1.21.1 серверно — `Entity#lookAt`/yaw-pitch), head-only vs linked-body;
- NPCCommandSelector → выбор духа по имени/из списка.

**Избегать / не тянуть:**
- сам код Citizens/API (см. лицензию);
- Bukkit-обвязку (Conversation, `Messaging`, `Translator`, `Settings`), PhTreeSolid/phtree и прочие зависимости;
- версионные NMS-модули как эталон маппингов — они под 26.x, не под 1.21.1;
- A* Citizens в ядро — на 1.21.1 выгоднее ванильный пафайндер, нашим духам он доступен через серверный адаптер.

## Риски и лицензионные ограничения

- **OSL 3.0 — copyleft, причём агрессивнее GPL.** Ключевые пункты: (1c) любые распространённые производные обязаны лицензироваться под OSL 3.0 целиком; (5) External Deployment (например, работающий мод-сервер с встроенным кодом Citizens) — это «распространение»; (6) в исходниках производных сохранять все копирайт/лицензионные уведомления Citizens; (2/10) грант на патенты и автоматическое прекращение при патентном иске.
- **Практический вывод**: копировать код Citizens/CitizensAPI в The Mine Spirit нельзя без лицензирования мода под OSL 3.0 (что конфликтует с любым другим выбором лицензии проекта). Разрешена и безопасна **только инспирация**: идеи, имена концептов, сигнатуры — при самостоятельной реализации и без переноса исходников.
- Дополнительно: CitizensAPI как зависимость тоже OSL 3.0; даже «шадим» его, лицензионные обязательства переживают shade/relocation.
- Внутри клона `main/src/main/resources/LICENSE` — та же лицензия; при любом цитировании кода сохраняем заметку `Licensed under the Open Software License version 3.0`.