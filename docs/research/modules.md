# Разбивка на Gradle-модули: исследование 05.11

Задача владельца: отделить механики от доски с графом и перестать гонять все тесты на каждую правку.
Замеры `main@94055b87` инструментом [tools/modules/package_graph.py](../../tools/modules/package_graph.py)
(рёбра — импорты и упоминания классов; см. «Ограничения»). Цифры — файлы/тесты по пакетам.

## 1. Факты

| Что | Число |
|---|---|
| Файлов main / test | 2014 / 1271 |
| @Test-методов / реально исполняемых тестов | 6429 / 8625 |
| Полный `scripts/test.sh unit` | 8625 тестов: холодный 4 м 51 с, тёплый 70 с (CPU 49 с) |
| Файлов с импортом `net.minecraft`/`neoforged`/`com.mojang` | 595: `neoforge.*` 526, `gametest` 68, `TheMineSpirit.java` 1 |
| `core` с импортом игры | 0 (факт подтверждён) |
| Пакетов (depth 2) / рёбер пакет→пакет | 73 / 1529 |

Крупные пакеты (файлы): `core.board` 231 (`board.graph` 62), `core.tactics` 129, `core.script` 113,
`core.intellect` 77, `core.building` 77, `core.rest` 71, `core.mining` 62, `core.town` 52, `core.movement` 44,
`core.navigation` 37, `core.ui` 36, `core.inventory` 34, `core.policy` 33; `neoforge` 215, `neoforge.board` 184,
`neoforge.movement` 35, `neoforge.inventory` 27, `neoforge.husbandry` 23.
Тесты: `core.board` 1618 (граф 639), `core.script` 475, `core.intellect` 416, `core.building` 335,
`core.mining` 317, `core.tactics` 286, `guards` 282, `core.rest` 248, `core.inventory` 222.

## 2. Граф зависимостей: кто кого знает

Срез по задуманным модулям (`summary --depth 2`, `backrefs --into …`); рёбра «X → Y» — X импортирует Y
(полная матрица: `matrix --depth 2 --csv build/tmp/m.csv`).

| Пакет | Зависит от (вес) |
|---|---|
| `core.board` | observation 48, plan 27, reload 21, script 20, building 20, inventory 14, town 12, husbandry 9, crafting 3 |
| `core.board.graph` | board 10, inventory 8, husbandry 2, building 2, plan 1, observation 1, script 1 |
| `core.tactics` | observation 6, perception 4, board 2, reload 2 |
| `core.script` | observation 11, husbandry 7, farming 6, building 5, forestry 5, mining 5, tactics 5 |
| `core.reload` | board 5 (в т.ч. граф 4), tactics 2, inventory 2, building 2 |
| `core.observation` | inventory 1, reload 1 |
| `core.inventory` | observation 14, reload 2, plan 2 |
| `core.mining` | observation 37, inventory 4, reload 3, building 2, tactics 2, board 2 |
| `core.movement` | observation 29, mining 7, building 3, route 2, script 2 |
| `core.policy` | observation 21, mining 9, navigation 2 |
| `core.town` | observation 25, building 12, board 5, policy 2, reload 2 |
| `core.rest` | board 13, observation 6, script 4, tactics 4, ui 4 |
| `neoforge` (склейка) | observation 123, building 92, engine 64, script 55, mining 46, board 35, forestry 19 |

Кто зависит от доски/графа снаружи: `neoforge.board` 223, `neoforge` 35, `gametest` 20, `core.rest` 13
(9 в доску + 4 в граф), `core.town` 5, `core.ui` 3, `core.mining` 2, `core.tactics` 2, `core.reload` 4 (в граф),
`core.husbandry` 1, `core.incident` 1; из графа в доску — 10 упоминаний (`graph/*` → `BoardCase` и др.).

## 3. Циклы

На depth 2 — два SCC: `core` (36 пакетов) и `neoforge` (28); внутри core всё связано со всем. Минимальные циклы — взаимные пары (depth 3, топ):

| Пара | A→B / B→A |
|---|---|
| `neoforge` ↔ `neoforge.board` | 21 / 79 |
| `core.board` ↔ `core.reload` | 21 / 1 |
| `core.board` ↔ `core.town` | 11 / 5 |
| `core.board` ↔ `core.board.graph` | 2 / 10 |
| `core.board` ↔ `core.husbandry` | 7 / 1 |
| `core.board` ↔ `core.mining` | 3 / 2 |
| `core.inventory` ↔ `core.reload` | 2 / 2 |
| `core.inventory` ↔ `core.observation` | 14 / 1 |
| `core.building` ↔ `core.script` | 5 / 5 |
| `core.husbandry` ↔ `core.script` | 2 / 7 |
| `core.mining` ↔ `core.policy` | 1 / 9 |
| `core.building` ↔ `core.town` | 1 / 12 |
| `core.policy` ↔ `core.town` | 1 / 2 |
| `core.movement` ↔ `core.script` | 2 / 1 |
| `core.base` ↔ `core.reload` | 1 / 1 |

## 4. Механика → доска/граф: что именно тянет

| Механика | Файлы и классы доски, которые она знает | Разрыв |
|---|---|---|
| шахта | `mining/ToolChoice.java`, `ToolReserve.java` → `board.ToolGate`, `board.ToolsConfig` | словарь инструментов в API |
| скот | `husbandry/PetRules.java` → `board.BoardCase`, `board.graph.SupplyGraph`, `HerdCap`, `Way` | `HerdCap`/`BoardCase` в API; график не отдаёт `SupplyGraph` наружу |
| склад | обратной связи нет; наоборот, `board/*` 14 файлов → `inventory.JsonResource` | `JsonResource` в API |
| поселение | `town/FloatingWindow`, `SettlementScan`, `SiteLevels`, `StageSites`, `StreetWork` → `SettlementPlot`, `PlotCells`, `PlotLeveling`, `BoardCase`, `TicketPlans`, `StageRules` | геометрия участков — в модуль поселения, `BoardCase` в API |
| перезагрузка данных | `reload/DataTables`, `DataTableDefinitions`, `TableHolder`, `TableRegistry` → `BoardConfig`, `StageRules`, `SupplyGraph`, `DemandRules`, `LevelingRules` | интерфейс `DataTables` в API, схемы таблиц — у владельцев |
| каталог скриптов | `script/CoreScripts`, `MineVeinParams`, `SafeMineParams`, `DigDescentParams` → `OreKind`, `MiningTuning`, `DescentData`, `Species`, `WoodSpecies` | параметры механик — в их модули, словари — в API |
| отдых | `rest/BoardGateway`, `BoardJson`, `BoardListQuery`, `BoardTaskRequest`, `GraphGateway` → `Board*`, `GraphDecisions` | это чтение доски — перенести в `:rest` как адаптер (уже односторонне) |
| тактика | `tactics/adapter/DecisionJournal`, `DecisionRecord` → `board.ConfigStamp` | `ConfigStamp` в API |

## 5. Предлагаемые границы модулей

Направление: механика → api, граф → api + механики; ребро «механика → board/graph» — дефект, его ловит сторож на `package_graph.py`.

| Модуль | Пакеты (main) | Файлов | Тестов | Зависит от |
|---|---|---|---|---|
| `:api` | `core.observation`, `perception`, `task`, `plan`, `engine`, `base` + словари/контракты | 107 | 400 | — (чистая Java, Jackson) |
| `:board` | `core.board`, `core.board.graph` | 231 | 1618 | api |
| `:tactics` | `core.tactics`, `intellect`, `coordinator`, `planner`, `processing` | 247 | 998 | api, board, policy |
| `:policy` | `core.policy` | 33 | 191 | api |
| `:content` | `core.reload` + `core.script` (каталог и таблицы) | 121 | 482 | api, board, tactics, policy, механики |
| `:rest` | `core.rest` | 71 | 248 | api, board |
| `:ui` | `core.ui` | 36 | 116 | api, rest |
| `:mech:forestry` | `core.forestry` + скрипты `neoforge` | 19+ | 96 | api |
| `:mech:mining` | `core.mining` | 62 | 317 | api |
| `:mech:building` | `core.building` | 77 | 335 | api |
| `:mech:farming` | `core.farming` | 17 | 88 | api |
| `:mech:husbandry` | `core.husbandry` | 14 | 28 | api |
| `:mech:inventory` | `core.inventory` | 34 | 222 | api |
| `:mech:movement` | `core.movement`, `navigation`, `route` | 96 | 294 | api |
| `:mech:town` | `core.town` | 52 | 173 | api, building |
| `:mech:crafting` | `core.crafting` | 21 | 116 | api |
| `:mech:small` | `core` мелочи (doors, hunger, sleep, pickup, repair, armor, experience, fishing, social, night, alchemy, rest-факты) | 67 | 319 | api |
| `:mod` | `neoforge.*` (реестры, команды, клиент, адаптеры REST/графа/склада) + `TheMineSpirit` | 637 | 102 | все модули, NeoForge |
| `:gametest` | `gametest` (179 @GameTest в 72 файлах) | 72 | 4 + GameTest | mod |
| `:guards` | `guards` (тесты-сторожа) | — | 282 | все исходники |

`tools/graph-gpu` — Python вне Gradle: читает `board/graph*.json` (путь станет `mod/src/main/resources/...`); формат держит `test_conformance.py`.

## 6. API-модуль: что в нём

Факты мира: `BlockCoord`, `IdFilter`, `ScanRadius`, `ObservationLimits`, `CellReport`, `HazardFacts`, `Memory*`
(`core.observation`, `core.perception`). Работа: `ScriptSpec`, `ParamSpec`, `ParamType`, `ConditionSpec`,
`CatalogLimits`, `ScriptCatalog`/`ConditionCatalog` (сейчас final-классы, станут контрактами), `Plan*`, `TaskRecord`, `TaskState`,
`PolicyExemptions`. Состояния/словари: `BoardCase`, `StuckCause`, `ConfigStamp`, `SafetyView`, `Cell`, `Species`,
`FeedTable`, `OreKind`, `WoodSpecies`, `CropKind`, `TreeSpecies`,
`JsonResource`, `DataTables` (тоже final-класс → контракт), `Point`.

Самые «тяжёлые» для выноса: `BoardCase` (96 файлов вне доски), `TicketTarget` 63, `BoardTicket` 49, `GraphTickets` 35, `SupplyGraph` 28.

## 7. Что мешает сейчас и как рвать

| Цикл | Классы | Способ |
|---|---|---|
| `board` ↔ `board.graph` | `GraphChoiceRules`, `GraphCosts`, `GraphPlan`, `GraphPlanner`, `StageBuilt` → `BoardCase`, `StuckCause`, `SettlementFacts`; `StageCatalog`, `ToolCraftPlan` → `SupplyGraph` | `BoardCase`/`StuckCause` в API; `StageCatalog` получает `SupplyGraph` параметром |
| `board` ↔ `reload` | 21 файл `board` → `reload.DataTables`; `reload` → `BoardConfig`, `StageRules`, `SupplyGraph` | интерфейс `DataTables` в API; схемы таблиц — у владельцев, сборка — в `:content` |
| `board` ↔ `script` | 20 файлов `board` → `script.CatalogLimits`; `CoreScripts` и `*Params` → словари механик | контракты каталога в API, параметры — в модули механик |
| `board` ↔ `town` | `town` → `SettlementPlot`, `PlotCells`, `PlotLeveling`, `TicketPlans`, `StageRules`; `board` → `ColumnMask`, `StageWork`, `TownPlan` | геометрия участков в `:mech:town`, доска спрашивает `SettlementFacts` |
| `board` ↔ `husbandry` | `PetRules` → `SupplyGraph`, `Way`, `HerdCap` | `HerdCap` в API; скот отдаёт факты, график считает сам |
| `board` → `inventory` | 14 файлов `board` → `inventory.JsonResource` (обратной связи нет) | `JsonResource` в API |
| `inventory` ↔ `observation` | `ObservationLimits` → `JsonResource`; `ContainerCell` и др. → `BlockCoord` | оба в API |
| `mining` ↔ `policy` | `BlockProtection`, `BreakVeto`, `DropPolicy`, `ExplosionAvoidance` → `SafetyView`, `Cell` | `SafetyView`/`Cell` в API |
| `movement` → `mining` | `ApproachRoute`, `BodyStep`, `StandChoice`, сеть троп → `MiningSafety` | `SafetyView` в API, `MiningSafety` — реализация в шахте |
| `engine` → `policy` | `NodeExecutor`, `ScriptExecutor` → `PolicyExemptions` | интерфейс в API |
| `plan`/`engine` → `script` | `PlanValidator` → `ScriptCatalog`, `ConditionCatalog`, `ParamSpec` | контракты в API |
| `base` ↔ `reload`, `base` → `tactics` | `RespawnRules` → `DataTables`; `Camp` → `tactics.Point` | `Point` и `DataTables` в API |

## 8. Тесты: сколько будет гоняться

| Типичная правка | Тестов | Доля от 6429 |
|---|---|---|
| механика: лес / скот / стройка | 96 / 28 / 335 | 1,5 / 0,4 / 5 % |
| механика: шахта / движение / склад | 317 / 294 / 222 | 5 / 4,6 / 3,5 % |
| граф и доска (`:board`) | 1618 | 25 % |
| тактика (`:tactics`) | 998 | 16 % |
| склейка (`:mod`) | 102 + GameTest пачки | 2 % |
| данные и каталог (`:content`) | 482 | 7,5 % |
| `:api` (меняется редко) | 400 + перекомпиляция и тесты всех зависимых | до 100 % |
| сторожа при любой правке `.java` | 282 | 4 % |

Полный прогон остаётся один — на слиянии в main (хук) и по запросу; публикация собирает без тестов.
Gradle: конвенции — в `build-logic` (java-library + JUnit + NeoForge-мод), `settings.gradle` — `include` всех
модулей; у каждого модуля свой `test`; `./gradlew :mech:mining:test` гоняет только его, корневой `test` — всё.
Уже включены `org.gradle.caching=true`, `configuration-cache=true`, `parallel=true`; тест-задачи кэшируются,
пока не менялся их classpath (правка `:api` инвалидирует зависимых — это ожидаемая цена).
Хук: `scripts/test.sh unit --changed` → `scripts/changed-modules.sh` сопоставляет пути с модулями и вызывает их
`test`; `:guards:test` — всегда при правке `.java`; `docs/**` и `tools/**` — ничего. `lead-merge` — полный
`unit` и полный `gametest` на main (как сейчас).

## 9. План переезда (каждый шаг — зелёный main)

| Шаг | Что делается | Объём |
|---|---|---|
| 0 | этот отчёт и скрипт; вход в карту `docs/research` | 0,5 дня |
| 1 | `build-logic` + `include` модулей поверх текущего `src/` (sourceSets с include/exclude), корневой `test` собирает всё | 0,5–1 день |
| 2 | индирекция путей в сторожах: `SourceFiles.path("core/board/Board.java")` вместо `Path.of("src/main/java/…")`, перенос `config/filesize-baseline.txt` (87) и `config/comment-baseline.txt` (164) на логические пути | 1–2 дня |
| 3 | `:api`: перенос фактов, словарей и контрактов из §6; рвутся 6 циклов | 2–3 дня |
| 4 | `:board` (board+graph) и `:tactics`; `BoardCase` уже в API, связь тактики с доской односторонняя | 1–2 дня |
| 5 | `:policy` (SafetyView/Cell в API) | 0,5 дня |
| 6 | `:content`: `DataTables`/`ScriptCatalog` — интерфейсы в API, реализация и таблицы в content | 2–3 дня |
| 7 | механики по одной: словари и параметры в свой модуль, скрипты `neoforge` — туда же | 0,5–1 день × 10 |
| 8 | `:mod`, `:gametest`, `:rest`, `:ui`; ресурсы (`data/`, `board/*.json`) — в `:mod` | 1–2 дня |
| 9 | физический переезд каталогов в `<модуль>/src/main/java`, обновление карты README/скриптов | 1–2 дня |
| 10 | `scripts/test.sh unit --changed`, хук, `publish.sh` (jar из `:mod`) | 1 день |

Итого 3–4 недели одного исполнителя; 2–3 параллельных — 1,5–2 недели (шаги 3–8 независимы по механикам).
## 10. Риски и что чинить вместе с переездом

| Риск | Факт | Мера |
|---|---|---|
| Сторожа | ≈40 жёстких путей: `SourceFiles.MAIN/TEST/GAME_TESTS`, `BoardPostGuard`, `ContainerLidGuard`, `DeadCodeExemptions` | шаг 2: единая карта логический путь → модуль; сторожа запускать всегда |
| Baseline | `config/filesize-baseline.txt` 87 записей, `config/comment-baseline.txt` 164 | переписать ключи скриптом в шаге 2, не менять лимиты |
| Анализаторы | `spotless target 'src/*/java/**'`, `checkstyle/pmd/spotbugs` (config/*.xml), `NullAway:AnnotatedPackages=com.theminespirit.core`, `-Werror` | конвенции в `build-logic`; `:api` без MC, иначе `ArchitectureGuardTests` теряет смысл |
| Скрипты | `publish.sh` ищет jar в `build/libs`, `proof-red.sh` — пути `src/test/java` и `gametest`, `test.sh` зовёт `runGameTestServer`, `opencode-task.sh` ловит прямой `gradlew` | `destinationDirectory` jar в корень `build/libs`; пути в тест-скриптах — за одним маппингом, обёртку не трогать |
| GameTest | 179 методов в 72 файлах, регистрация по классам пакета `com.theminespirit.gametest` | отдельный `:gametest` с `moddev`, полный прогон — на слиянии |
| codegraph | индекс 3327 файлов от корня; модули в подкаталогах видны | после переезда перезапустить индексацию; worktree-копии не индексировать |
| Python-инструменты | `tools/graph-gpu` читает `board/graph*.json` | путь к ресурсам — параметром, конформанс-тест уже есть |
| Данные | `src/main/resources/data/theminespirit`, `board/*.json`, `DataTablesGuardTests` читает `src/main/resources` | ресурсы — в `:mod`, сторож — на логический путь |

## 11. Ограничения измерения и техдолг

- Рёбра — по импортам и упоминаниям имён; reflection, строковые имена и данные JSON не видны, неоднозначные
  имена (649 предупреждений) в рёбра не попали: «0 рёбер» не доказывает отсутствие связи.
- `core.observation` уже зависит от `inventory.JsonResource` и `reload.DataTables` — первый кандидат на вынос.
- `core.engine` → `policy.PolicyExemptions`, `core.plan`/`engine` → каталог скриптов: без интерфейсов в API `:api` не собирается.
- `neoforge` (215 файлов) смешивает реестры, команды, клиент и реализации механик — до шага 8 его надо разобрать по механикам, иначе `:mod` останется складом.
