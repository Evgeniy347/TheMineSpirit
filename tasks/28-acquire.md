# 28 — Скрипт `acquire`: добыть предмет любым способом (подплан резолвера, пересчёт, итог)

Источник: `todo.md` → «Скрипт `acquire` есть только в каталоге, исполнения нет (отчёт 17, п. 17): связать с
ResourceResolver/RecipeKnowledge»; «P2 — добыча ресурсов: `acquire` через реальный мир»; карта действий →
«Инвентарь и крафт: `acquire`, `pick_up`, `deposit`, `withdraw`, `sort_chest`, `drop_to_player`». Отчёт GameTest
полного цикла (чеклист 17, п. 17) показал: план `ResourceResolver.resolve("wooden_pickaxe", 1, RecipeKnowledge.of(level))`
исполняется движком, но скрипта `acquire`, который сам бы его строил, нет.

Перед работой прочитай `AGENTS.md`, `docs/CONVENTIONS.md` (Быстрый путь — чистая логика в `core`, адаптер тонкий;
Структура — решение принимается в одном месте; Производительность — тик стоит дорого, тест на масштаб считает
операции; Ошибки — тихий fallback запрещён), `docs/TESTING.md` (JUnit и GameTest, имена тестов, сторожа, время
тестов, `GameTestTimeoutGuardTests`), `docs/GIT.md`, `docs/API.md` (план, каталог, поле `result`).
Код (карта для чтения): `core/planner/{ResourceResolver,ResourceKnowledge,FuelPlanner,FuelTable,Goal,Ingredient}`
(резолвер и знания), `core/plan/{PlanParser,PlanValidator,TaskNode,SequenceNode,ScriptCall,PlanJson}` (план,
лимиты глубины `MAX_DEPTH` 16 и узлов `MAX_NODES` 256 — сейчас приватные), `core/engine/{ScriptInstance,ScriptFactory,
ScriptRegistry,TaskRunner,TaskSnapshot,TaskSnapshotCodec,TaskProgress,NodeExecutor,ScriptExecutor,SequenceExecutor}`
(скрипт-лист, сериализация и восстановление задачи), `core/script/{CoreScripts,CatalogLimits,ParamSpec}`,
`neoforge/crafting/RecipeKnowledge` (реальные рецепты, `GatherPolicy`, `stationFor`, `burnTicks`),
`neoforge/inventory/{RecipeKnowledge,ContainerAccess,ContainerFinder,WithdrawScript,ContainerTask}` (сундуки),
скрипты `gather`/`mine_vein`/`chop_tree`/`craft`/`smelt`/`withdraw`, `neoforge/SpiritScripts`,
`gametest/{FullCycleGameTests,FullCycleScene,CraftTestSupport,SpiritRun,RestFixture,TaskPersistenceGameTests}`.
Публикацию и перезапуск сервера не делать.

**Зависимости.** Чеклиста 27 (общий канал итога `ScriptInstance.result(): Optional<ScriptResult>`, sealed
`Text`/`Data`) **в main пока нет** — он существует только чеклистом. Часть А (добыча, пп. 1–5) делается без него.
Итог задачи (`ScriptResult.Text`, пп. 6 и 18) — только после слияния 27; если 27 не слит, эти пункты не начинать.
Часть Б (адаптер, пп. 7–11) — после А; п. 11 (итог до REST) — после 27. Часть В (GameTest, пп. 12–18) — после Б,
п. 18 — после 27. Чеклисты 13/16/17 (движок задач, инвентарь, крафт и плавка) и 24 (навигация) уже в main и
нужны как есть. Публикацию и перезапуск сервера не делать.

## Что должно получиться

Скрипт `acquire` («добыть предмет любым способом») исполняется и сам строит подплан добычи: чего не хватает,
берёт из инвентаря и ближних сундуков, остальное добывает, крафтит или плавит через `ResourceResolver`; при
провале шага пересчитывает подплан ограниченное число раз; итог отдаёт общим каналом (чеклист 27).

- **`acquire` — не лист, а владелец подплана.** Скрипт разворачивается в подплан через существующий
  `ResourceResolver` (новой логики добычи не пишем) и ведёт его вложенным `TaskRunner` из `core/engine`
  (образец запуска — `SpiritBrain.start`/`TaskRunner.start`). Движок и язык плана не меняются: `acquire`
  остаётся обычным `ScriptCall`, но его `ScriptInstance` — составной.
- **Порядок добычи — «сначала своё, потом мир».** Инвентарь духа учитывается первым; чего нет — берём из
  ближних сундуков (`withdraw` по координатам контейнера, ближний первым); остаток (`count − инвентарь −
  сундуки`) отдаём `ResourceResolver` → `gather`/`craft`/`smelt`. Сбор из мира начинается только тогда, когда
  без него не обойтись.
- **Пересчёт при провале шага.** Если шаг подплана провалился (например, дерево кончилось, контейнер изменился),
  `acquire` перечитывает инвентарь/сундуки/знания и строит подплан заново; попыток не больше
  `CatalogLimits.ACQUIRE_MAX_ATTEMPTS`. Итоговое `FAILED` называет числа: сколько было в инвентаре, в сундуках,
  сколько добыто и сколько попыток сделано — «похоже, работает» не отдаём.
- **Итог — общим каналом (чеклист 27).** Успешный `acquire` отдаёт `ScriptResult.Text` с итогом: предмет,
  сколько нужно, сколько откуда взято, число попыток. После слияния 27 итог доходит до REST `GET /v1/tasks/{id}`
  без отдельного поля — тем же путём, что `ChopTreeScript`.
- **Лимиты глубины и числа шагов.** Построенный подплан проверяется на бюджет: глубина ≤ 16 и узлов ≤ 256
  (те же числа, что `PlanParser.MAX_DEPTH`/`MAX_NODES`; выносим их в общий `PlanLimits`, чтобы число было в
  одном месте). Подплан сверх бюджета — `FAILED` с числами, а не безлимитная рекурсия.
- **Состояние при перезапуске.** Прогресс `acquire` (номер попытки и снимок вложенной задачи) переживает
  перезапуск через `ScriptInstance.saveState()`/`restoreState()`: снимок вложенного `TaskRunner` кладётся
  строкой (`TaskSnapshotCodec.encode`) и восстанавливается (`decode` + `TaskRunner.restore`). Прерванный
  перезапуском `acquire` продолжается с того же шага, а не начинает добычу заново (двойной расход дерева/руды).
- **`acquire` оживает в каталоге.** `SpiritScripts` регистрирует реализацию — скрипт (уже описанный в
  `CoreScripts` и `ScriptCatalogTests`) появляется в `GET /v1/capabilities`; стратег видит его как рабочий.
- **GameTest с нуля.** Сквозные тесты: `acquire` `wooden_pickaxe`, `stone_pickaxe` и `iron_ingot` в пустом
  инвентаре доводят предмет до инвентаря; отдельно — «всё уже в инвентаре» (без похода в мир), «всё в ближнем
  сундуке» (без добычи, только `withdraw`), провал с числами, перезапуск на середине и итог через REST.

Числа (предварительно, на согласование H1): `ACQUIRE_MAX_ATTEMPTS` 3; бюджет подплана — глубина 16, узлов 256
(переносим из `PlanParser`); радиус поиска ближних сундуков для снабжения — 8 (как `CONTAINER_RADIUS_DEFAULT`);
попытка перечитывает мир не чаще одного раза на провал шага.

### Как `acquire` разворачивается в подплан (предлагаемый механизм)

`ScriptInstance` движка — лист (`ScriptExecutor.tick` зовёт `instance.tick()`), составных скриптов нет. Поэтому
`AcquireScript` сам владеет вложенным `TaskRunner`:

1. на первом тике (или по `restoreState`) собирает подплан: снимок инвентаря → шаги `withdraw` по ближним
   сундукам → `ResourceResolver.resolve(остаток)`; проверяет бюджет `PlanBudget`; запускает вложенный
   `TaskRunner.start(подплан, реестр, условия)`;
2. каждый внешний тик — один `tick()` вложенного раннера (не дороже обычного шага); `progress()`,
   `suspend()`, `resume()`, `cancel()`, `policyExemptions()` делегируются вложенному раннеру;
3. `FAILED` вложенного шага → пока попыток меньше `ACQUIRE_MAX_ATTEMPTS`, перечитать мир и пересобрать подплан;
4. `SUCCEEDED` → `result()` (чеклист 27) с итогом.

Альтернатива (движковый составной узел `AcquireNode` + `TaskProgress.AcquireAt` + правки `TaskSnapshotCodec`)
дороже и трогает язык плана, `TaskProgress` и кодек — вынесена в «На согласование» H2.

## Общие точки с другими чеклистами (merge)

- `core/script/CoreScripts` — `acquire` уже описан (`item` обязателен, `count` обязателен, 1..256). Правка
  только текста `effects` при желании; конфликтов нет.
- `core/script/CatalogLimits` — новый блок `ACQUIRE_*` в конце файла (после `HERD_SIGHT_MARGIN`).
- `guards/CatalogDefaultGuardTests`, `core/script/ScriptCatalogTests` — `acquire` уже в списках, строки не
  меняются, если параметры не тронуты.
- `core/plan/PlanParser` — приватные `MAX_DEPTH`/`MAX_NODES` выносятся в общий `core/plan/PlanLimits`
  (значения не меняются; тест на равенство числам в `PlanParser` остаётся). Конфликтов нет.
- `core/engine/ScriptInstance` — зависимость от чеклиста 27 (`result()`); своих правок не вносит.
- `neoforge/SpiritScripts` — новая запись `table.put("acquire", …)`; `catalog()` = `CoreScripts` ∩
  реализованные — `acquire` появляется в capabilities. `LinkedHashMap`, лимита пар нет.
- `neoforge/crafting/RecipeKnowledge` — `isGatherable` расширяется знанием «предмет-дроп ← блок» (п. 9);
  конфликтов нет.
- `neoforge/inventory/{ContainerAccess,ContainerFinder}` — только вызываются (поиск ближних контейнеров);
  `WithdrawScript` не меняется — `acquire` порождает обычные `ScriptCall("withdraw", …)`.
- `docs/API.md` — `acquire` уже в разделе каталога; если поле `result` из 27 уже описано, добавить строку, что
  `acquire` отдаёт итог. Конфликтов нет.
- `gametest/FullCycle*`, `core/inventory/NeedsOfPlan` (там `acquire` уже в `ITEM_SCRIPTS`) — не меняются.

## Часть А — ядро (`core`, без Minecraft)

- [ ] 1. **`AcquireParams`** (`core/script`) — `item()` (обязателен, `requireString`) и `count()`
  (обязателен, `requireInt`, границы `COUNT_MIN`..`COUNT_MAX`) по образцу `GatherParams`/`ChopTreeParams`.
  Тесты: значения читаются; пустой `item` и `count` вне границ — исключение с текстом; умолчаний нет (правило
  `CatalogDefaultGuardTests`). Каталог не меняется — `acquire` там уже есть.
- [ ] 2. **`AcquireSupplies`** (`core/planner`) — порт снабжения, чистый интерфейс: `int inInventory(String item)`
  и `List<ScriptCall> withdraw(String item, int maxCount)` (шаги `withdraw` по ближним сундукам, ближний первым;
  контейнеров нет — пустой список). Координаты и мир остаются в адаптере. Тесты: заглушка отдаёт счётчики и
  шаги; пустой сундуков — пустой список; порядок шагов сохраняется; null-параметры — исключение.
- [ ] 3. **`PlanLimits` + `PlanBudget`** (`core/plan`) — публичные `PlanLimits.MAX_DEPTH = 16`,
  `MAX_NODES = 256` (перенос из `PlanParser`, значения не меняются); `PlanBudget.measure(TaskNode)` возвращает
  `record Budget(int depth, int nodes)` и `exceeds()`-предикат. Тесты: таблица «дерево → глубина/узлы»
  (лист 1/1; sequence из двух — 2/3; вложенность 17 — превышение глубины; 257 листьев — превышение узлов);
  свойство «узлов в дереве ≥ глубины»; тест равенства числам `PlanParser` (перенесённый).
- [ ] 4. **`AcquireScript`** (`core/planner` или новый `core/acquire`) — `ScriptInstance`, владеет вложенным
  `TaskRunner`:
  — конструктор получает `item`, `count`, `ResourceKnowledge`, `AcquireSupplies`, `ScriptCatalog`,
  `Supplier<ScriptRegistry>` (ленивый — иначе рекурсия на себя) и `ConditionRegistry`;
  — на первом тике `buildPlan()`: `have = supplies.inInventory(item)`; если `have ≥ count` — сразу `SUCCEEDED`
  (мир не трогаем); иначе `steps = supplies.withdraw(item, count − have)`, `need = count − have −
  withdrawn(steps)`, при `need > 0` добавляется `ResourceResolver.resolve(item, need, knowledge, SpiritScripts-каталог)`;
  результат — `SequenceNode`; проверка `PlanBudget` → превышение даёт `FAILED` с числами;
  — `tick()` делегирует вложенному раннеру; `FAILED` и `attempts < ACQUIRE_MAX_ATTEMPTS` → пересборка подплана
  (перечитать снабжение), иначе `FAILED` с итоговыми числами; `CANCELLED` — проброс;
  — `progress()`, `suspend()`, `resume()`, `cancel()`, `policyExemptions()` делегируются вложенному раннеру;
  — `saveState()`/`restoreState()` — попытка + строковый снимок вложенного раннера (`TaskSnapshotCodec`);
  — `failureReason()` — с числами. Тесты (без Minecraft, реестр скриптов — поддельный `ScriptFactory`, знания —
  `StubKnowledge`, снабжение — заглушка): инвентарь покрывает цель — `SUCCEEDED` без запуска подплана; снабжение
  даёт шаги `withdraw`; резолвер вызывается на остаток; провал шага → пересборка, число попыток ≤ константы;
  превышение бюджета → `FAILED`; `saveState`/`restoreState` не теряют попытку и продолжают с того же шага
  (сравнение снимков); `cancel`/`suspend` доходят до вложенного раннера.
- [ ] 5. **Предел попыток** (`core/script/CatalogLimits`) — `ACQUIRE_MAX_ATTEMPTS` в блоке `ACQUIRE_*`; тест
  равенства числу из production (`assertEquals(3, CatalogLimits.ACQUIRE_MAX_ATTEMPTS)`), сравнение литерала с
  литералом — не проверка.

## Часть Б — адаптер (после А; п. 11 — после чеклиста 27)

- [ ] 6. **Регистрация `acquire`** (`neoforge/SpiritScripts`) — `table.put("acquire", spirit -> params ->
  new AcquireScript(…))`; в конструктор передаются `RecipeKnowledge.of(spirit.level())`, адаптер снабжения
  (п. 7), ленивый `Supplier<ScriptRegistry>` (`() -> registryFor(spirit)` вызывается на первом тике — к этому
  моменту реестр собран, рекурсии нет) и `conditions()`. Тесты (JUnit адаптера, если возможно; иначе GameTest):
  `catalog()` содержит `acquire`; `capabilities` его отдаёт. Конфликтов нет.
- [ ] 7. **`SpiritSupplies`** (`neoforge/inventory` или `neoforge`) — реализация `AcquireSupplies`: считает
  предмет в инвентаре духа (`SimpleContainer`) и собирает шаги `withdraw` по ближним контейнерам (обход
  `ContainerAccess.allowedBlocks()` в радиусе, `ContainerAccess.open`, порядок — по расстоянию, `container` =
  «x,y,z» в параметрах). Тесты: счёт из инвентаря; один сундук — один шаг; два сундука — ближний первым;
  пустые/защищённые контейнеры пропускаются; радиус соблюдён.
- [ ] 8. **Знание «предмет-дроп ← блок»** (`neoforge/crafting/RecipeKnowledge`) — чтобы `acquire iron_ingot`
  «с нуля» резолвился, `isGatherable`/`gather` должны знать, что `raw_iron` добывается из `iron_ore`. Решение:
  таблица «дроп → блок(и)» для распространённых руд и простых дропов (`raw_iron`←`iron_ore`/`deepslate_iron_ore`,
  `raw_copper`, `raw_gold`, `coal`, `diamond`, `redstone`, `lapis_lazuli`, `emerald`, `cobblestone`←`stone`,
  `*_log` уже сам блок) и учёт её в `isGatherable` (п. H3 — список на согласование). Тесты: `raw_iron` теперь
  `gatherable`; `isGatherable` не ломает уже работающие блоки-предметы; `PlanValidator` не отклонит порождённый
  план.
- [ ] 9. **Лимит попыток в адаптере не дублируется** — `AcquireScript` берёт `ACQUIRE_MAX_ATTEMPTS` из ядра;
  адаптер значения не задаёт. Тест-сторож: в `neoforge` нет своих констант попыток (по образцу существующих
  сторожей литералов).
- [ ] 10. **Запуск и восстановление** (`neoforge/SpiritBrain`) — проверить, что `start`/`restore`/`snapshot`
  работают для `acquire` без правок (вложенный раннер прячется за `saveState`/`restoreState`); если всплывёт
  несовместимость — править минимально. Тесты: запуск плана из одного `acquire`; снимок RUNNING-задачи с
  `acquire` сериализуется и восстанавливается (`TaskSnapshotCodec`), прогресс не теряется.
- [ ] 11. **Итог до REST** (после чеклиста 27) — `AcquireScript.result()` возвращает `ScriptResult.Text`
  с числами; движок и мост из 27 доносят его до `GET /v1/tasks/{id}` без новых полей. Тесты: успешный
  `acquire` кладёт итог в задачу; провал — пустой итог; текст содержит предмет и числа. **Без п. 6 чеклиста 27
  не делается.**

## Часть В — GameTest (`gametest/AcquireGameTests`)

Шаблон `arena` 11×8×11, всё внутри площадки, духи и предметы удаляются (`GameTestFixtures.reset`/`cleanup`),
без `sleep`, время по тикам духа (`SpiritRun`), своя пачка (`batch`) на группу, `timeoutTicks ≤ 400` (иначе —
комментарий `[флак]` с обоснованием, как `GameTestTimeoutGuardTests`). Сцена строит пол, дерево/руду/сундук по
образцу `FullCycleScene`/`CraftTestSupport`.

- [ ] 12. **`acquire wooden_pickaxe` с нуля** (пачка `acquire_wood`): пустой инвентарь, дуб в 6 блоках, верстак
  на площадке; план `{"script":"acquire","params":{"item":"wooden_pickaxe","count":1}}`; `SUCCEEDED`, кирка в
  инвентаре, дерево/доски/палки израсходованы как в плане резолвера, лишнего не собрано. Подлоги: убрать шаг
  добычи (кирки нет); убрать учёт инвентаря (лишняя добыча при уже имеющемся предмете).
- [ ] 13. **`acquire stone_pickaxe` с нуля** (пачка `acquire_stone`): дерево + ряд булыжника; кирка в
  инвентаре, булыжник израсходован на неё, дерево — на палки. Подлог: резолвер не видит булыжник
  (`isGatherable`) — тест краснеет.
- [ ] 14. **`acquire iron_ingot` с нуля** (пачка `acquire_iron`, комментарий `[флак]`, если > 400 тиков):
  ряд `iron_ore`, верстак, печь; пустой инвентарь; ускорение печей `speedUpFurnaces`; `SUCCEEDED`, слиток в
  инвентаре. Подлог: нет знания «`raw_iron` ← `iron_ore`» — `FAILED`; нет топлива/станции — `FAILED` с числами.
- [ ] 15. **Инвентарь первым** (пачка `acquire_have`): предмет уже в инвентаре, вокруг есть источник;
  `SUCCEEDED` за ≤ 2 тика, мир не тронут (дерево/руда целы). Подлог: подплан всегда добывает — лишний расход,
  тест краснеет.
- [ ] 16. **Ближний сундук первым** (пачка `acquire_chest`): предмет только в сундуке, дерева/руды нет;
  `SUCCEEDED`, предмет в инвентаре, сундук отдал предмет (не добыча). Подлог: нет шага `withdraw` — `FAILED`
  «нечего добывать».
- [ ] 17. **Провал с числами и предел попыток** (пачка `acquire_fail`): ни инвентаря, ни сундуков, ни
  источника в радиусе; `FAILED`, причина называет предмет, цель и число попыток ≤ `ACQUIRE_MAX_ATTEMPTS`.
  Подлог: без предела — бесконечная пересборка (тест по тайм-ауту) или неверное число попыток.
- [ ] 18. **Перезапуск на середине и итог через REST** (пачка `acquire_restart`): `acquire wooden_pickaxe`
  прерывается сохранением/восстановлением духа (NBT, как `TaskPersistenceGameTests`) — добыча не начинается
  заново, дерево не расходуется дважды, задача доходит до `SUCCEEDED`; при слиянии 27 — `GET /v1/tasks/{id}`
  отдаёт `result` с итогом (`RestFixture`). Подлоги: `saveState` не хранит снимок подплана (после перезапуска
  добыча с нуля); итог не кладётся в задачу.

## Красными до реализации

Все JUnit-тесты `core/planner/AcquireScriptTests`, `core/script/AcquireParamsTests`, `core/plan/PlanBudgetTests`
(классов и `AcquireParams` нет), `core/plan/PlanLimits` (константы не перенесены) и все GameTest пп. 12–18
(`acquire` не зарегистрирован в `SpiritScripts` — `capabilities` и исполнение отсутствуют; план с `acquire`
отвергается валидатором как нереализованный). Назови красные до прогона. Для `fix:`-коммитов —
`scripts/prove-red.sh`; GameTest скрипт не проверяет — подлоги вручную. Пределы в тестах — литералы с проверкой
равенства production-константе (`assertEquals(3, CatalogLimits.ACQUIRE_MAX_ATTEMPTS)`,
`assertEquals(256, PlanLimits.MAX_NODES)`).

## Стоимость в тике

- Внешний тик `acquire` — ровно один `tick()` вложенного `TaskRunner` (как обычный шаг), плюс O(1) проверки
  статуса/итога.
- Подплан строится один раз на попытку, не каждый тик: снабжение (инвентарь + обход ближних контейнеров в
  радиусе 8) и `ResourceResolver` вызываются только при первом тике и при провале шага (≤ `ACQUIRE_MAX_ATTEMPTS`
  раз). Обход контейнеров — через существующий `ContainerAccess`/`ContainerFinder`, без новых чтений мира.
- Бюджет `PlanBudget.measure` — один проход по дереву на попытку; сериализация снимка — только при
  `saveState` (сохранение задачи), не каждый тик.
- Вложенных циклов «духи × источники» нет; числа — в тестах, не время.

## Завершение

- [ ] 19. `./gradlew test` зелёный; GameTest — через `scripts/gametest.sh` (под `flock`, один сервер на
  машину). Коммиты по пунктам (`test:`/`feat:`); `fix:`-коммиты с JUnit-тестом сопровождаются выводом
  `scripts/prove-red.sh <коммит>`. `todo.md` не править. Отчёт: хэши; сколько тестов прошло и упало; красные
  до реализации; подлоги пп. 12–18; время самого медленного GameTest и причина; что не сделано и почему
  (в том числе: сделан ли п. 11/18 — зависит от слияния чеклиста 27).

## Не входит (уходит в следующие чеклисты)

- `pick_up`, `drop_to_player`, `deliver`, `give`/`take`, `equip`/`swap_tool`/`repair`, `eat`/`drink_potion`.
- Забор топлива и ингредиентов из сундуков (сейчас сундуки отдают только целевой предмет, п. 4); топливо
  берётся из инвентаря духа — расширение отдельным чеклистом.
- Выбор между несколькими рецептами за пределами `RecipeChooser` (резолвер берёт один представительный рецепт).
- Движковый составной узел `acquire` (`AcquireNode`/`TaskProgress.AcquireAt`) — только если принят H2-вариант Б.
- Свой A*/планировщик добычи (резолвер уже есть); MCP-адаптер поверх REST; перезапуск и публикация сервера.

## На согласование

1. **H1 — числа:** `ACQUIRE_MAX_ATTEMPTS` 3; бюджет подплана — глубина 16, узлов 256 (перенос из `PlanParser`);
   радиус ближних сундуков для снабжения — 8. Цена завышения попыток — долгий пересчёт и повторный расход
   ресурсов; цена занижения — из-за одного мигающего контейнера `acquire` сдаётся. Цена радиуса — тяжёлый обход
   сундуков на первом тике.
2. **H2 — механизм подплана:** вложенный `TaskRunner` внутри `AcquireScript` (предлагается: не трогает язык
   плана, `TaskProgress` и кодек; пересчёт и перезапуск — через `saveState`) или новый движковый узел
   `AcquireNode` (подшаги видны в `progress.current_script`, но правки в `NodeExecutor`, `TaskProgress`,
   `TaskSnapshotCodec` и `checkAgainstPlan`; +1 большой коммит). Цена первого — в REST прогресс называет
   `acquire`, а не подскрипт; цена второго — трогает ядро сериализации.
3. **H3 — знание «дроп ← блок»** (без него `acquire iron_ingot` с нуля не резолвится): таблица распространённых
   руд/дропов в `RecipeKnowledge` или в данных (`policy`-подобный json). От пользователя нужен список:
   достаточно ли руд Overworld; чинить ли `deepslate_*`; как быть с модовыми блоками. Цена таблицы — неточность
   при модах; цена данных — новый формат.
4. **H4 — что берут сундуки:** только целевой предмет (предлагается) или ещё ингредиенты/топливо? Цена узкого
   варианта — `acquire` может начать добычу, хотя ингредиент лежал рядом; цена широкого — сложная пересборка
   подплана под содержимое сундуков.
5. **H5 — формат итога `ScriptResult.Text`:** «добыл wooden_pickaxe 1 из 1: инвентарь 0, сундуки 0, добыто 1,
   попыток 1» или короткий JSON-объект в `Text`? Цена строки — стратегу парсить подстроки; цена объекта —
   дублирует `Data` из 27.
