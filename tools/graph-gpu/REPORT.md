# Веса графа на видеокарте — отчёт

## Что сделано

| Файл | Назначение |
|---|---|
| `graph_model.py` | Перенос Java (`GraphPlanner`, `GraphFlow`, `SupplyGraph`, `GraphCosts`, `GraphState`, `GraphEvent`) один в один |
| `test_conformance.py` | Сверка с `conformance.json` (200 состояний) |
| `sim_gpu.py` | Симуляция во времени: тики, дорога, износ, станции; пакет миров на torch/CUDA |
| `search.py` | Поиск 43 множителей-категорий в пределах ±50 % |
| `best-weights.json` | Результат поиска `--generations 10 --population 16 --seeds 1` |

Граф загружается как в Java: `board/graph.json` + `board/graph-recipes.json`.
Добавлены `costs`, `buildStartShare`, `distances`/`stations`, задания `station:*`,
дорога (`travelFactor`), `share` при старте постройки, одна запись пути по id — первая, как `SupplyGraph.way`.

Догнано после Java-правок: `HerdCap` (`fixed`/`perSpirit`), заказы игрока (`GraphState.orders`,
`order:` в notices), `GraphBuildOrder.buildingsFirst` (полосы приоритетов построек),
`GraphLack.share/ready` (доля материалов и готовность постройки), ветка питомцев.

## Сверка с Java

`PYTHONIOENCODING=utf-8 py -3.9 test_conformance.py` — **200/200**: набор заданий, `feasible`
и `chance` совпадают точно, `priority` расходится не больше чем на 1.

Симуляция сверена с Java-прогоном `GraphSimulationTests.simulate_Baselines_WriteTrailReport`
(запускался отдельно, не через `mutationTest`). Совпали шаги, тики и порядок заданий (id + priority)
по всем 5 базовым мирам `GraphMutations.baselines()`:

| Старт | Шагов | Тиков | Этап в конце | Python = Java |
|---|---|---|---|---|
| 0 — пусто | 78 | 55 700 | 1 (нет выполнимых заданий) | да |
| 1 — обжитый | 2077 | 366 080 | 3 | да |
| 2 — тесно | 4302 | 270 200 | 2 (предел решений) | да |
| 3 — этап 2 | 2266 | 416 060 | 3 | да |
| 4 — 10 духов | 6729 | 113 780 | 2 (предел решений) | да |

Совпадает весь путь по шагам (этап, id, priority), а не только итог.
Незавершённые миры — не расхождение: Java при тех же входных данных останавливается там же.

## Поиск

Параметров — **43** (категории, не каждый путь):

- 3 скаляра: `decay`, `urgentBoost`, `buildReadyBoost`;
- 5 видов предметов: `perSpirit` и этапы 1–4;
- 4 вида построек: этапы 1–4;
- 31 вид путей по `BoardCase` (`GATHER_MATERIALS`, `CRAFT_*`, `FISH`, …).

Множитель категории применяется ко всем её весам.
`search.py --mode es --generations 10 --population 16 --seeds 1` (GTX 1060, CUDA) дал долю
**0.5735**; все старты доходят до конца этапа 3. Шаги по стартам, до → после:

| Старт | До | После (поиск) | После (внесено) |
|---|---|---|---|
| `stage2-full` | 2266 | 1438 | 1834 |
| `late3-half` | 745 | 382 | 440 |
| `late3` | 1610 | 785 | 1197 |
| `stage2-all` | 1915 | 1147 | 1533 |
| Сумма | 6536 | 3752 | 5004 |

Абсолютные значения из `best-weights.json` перенесены в `board/graph.json`. Одно отступление:
`decay` внесён 0.55 вместо найденных 0.659 — при 0.659 два правила `GraphPlannerTests`
(«нет железной кирки — уголь ниже», «нет деревянных инструментов — дерево важнее») упираются
в `maxPriority` и сравниваются равными. 0.55 — наибольшее значение, при котором оба правила
выполняются строго; цена — сумма 5004 вместо 3752. Тесты не ослаблялись.

## 03.11 — общий пересчёт после наполнения областями

В main влиты области `graph.d`: `life`, `store`, `farm`, `world`, `dig`, `gear`, `more`, `more2`, `more3`.
Симуляция и поиск догнали их: у миров появились `facts` (угрозы, рельеф, повреждения, снаряжение,
сундуки/станции, минимум склада), события областей и метки мира. Миры областей собраны в
`sim_gpu.input_worlds`; у них 16 духов и одна недостроенная постройка, чтобы события успевали выполниться.
Завершение нового задания обновляет вход (яма закрыта, дух освобождён, печь разгружена, руки пусты,
повреждение снято), иначе причина события повторялась бы бесконечно.

Найдена и исправлена ошибка сверки: прямой матричный решатель `(I − A)·p = r` на пустом мире давал
отрицательные потоки (6 шагов вместо 78). Теперь при отрицательном решении строка пересчитывается
итерациями Java (200 итераций, `EPSILON`), и `sim_gpu` снова совпадает с `GraphSimulationTests`.

Поиск: **73 категории** (было 43) — добавлены `event:life/store/farm/gear/more2/more3`, `way:dig` и
категории новых предметов/построек/путей. Полоса жизни масштабируется выше `maxPriority`:
`100 + (base − 100)·m`, поэтому задания жизни не проваливаются на `maxPriority`.
Прогон: `--generations 9 --population 8 --seeds 2 --limit 1600` на GTX 1060, ≈23.5 мин,
доля **0.3776**, сумма шагов 5646 → 4850 (до правок ради зелёных правил).

Метрики стартов (шаги/тики, до → после; миры областей заканчиваются за 2400 тиков):

| Старт | До | После | Этап |
|---|---|---|---|
| `late3` | 977 / 188 200 | 656 / 148 900 | 3, готово |
| `late3-half` | 358 / 90 180 | 193 / 58 940 | 3, готово |
| `stage2-full` | 691 / 186 060 | 730 / 185 220 | 2, застрял |
| `stage2-all` | 422 / 121 700 | 439 / 121 780 | 2, застрял |
| `life` | 11 / 2 400 | 11 / 2 400 | 3, готово |
| `store` | 18 / 2 400 | 18 / 2 400 | 3, готово |
| `world` | 10 / 2 400 | 10 / 2 400 | 3, готово |
| `gear` | 10 / 2 400 | 10 / 2 400 | 3, готово |
| `more` | 17 / 2 400 | 17 / 2 400 | 3, готово |
| `more2` | 16 / 2 400 | 16 / 2 400 | 3, готово |
| `more3` | 18 / 2 400 | 18 / 2 400 | 3, готово |
| `dig` | 11 / 2 400 | 11 / 2 400 | 3, готово |

Базовые миры Java после переноса: 78 / 588 / 1202 / 730 / 3450 шагов — Python совпадает по шагам,
тикам и порядку заданий. Миры областей не простаивают (0 циклов простоя) и заканчивают этап 3;
`stage2-full` и `stage2-all` остаются тупиком этапа 2 и до, и после пересчёта — причина та же
(нет выполнимых заданий у загонов/склада), веса её не лечат.

Отступления от найденного (правила `GraphPlannerTests` не ослаблялись):

- `decay` внесён **0.55** вместо найденных 0.825: при большем значении пять правил упираются в
  `maxPriority`;
- категории топоров (`CRAFT_IRON_AXE`, `CRAFT_STONE_AXE`, `CRAFT_WOODEN_AXE`) возвращены к 1.0 —
  иначе ломается порядок «железный > каменный > деревянный»;
- `cobblestone` и `stick` внесены 36 вместо найденных 18 — иначе «нет каменной кирки» перестаёт
  поднимать булыжник и палки;
- `event:*` и `way:dig` оставлены 1.0: на шагах симуляции они не сказались (события одноразовые),
  поэтому менять их без измерения незачем.

Мёртвые узлы новых областей (ни разу не активировались в 12 стартах):

- пути (15): `assign_chest_purpose`, `clear_trash`, `feed_animals`, `home_night_work`, `milk_animal`,
  `plant_crop`, `raise_bottleneck_weight`, `recover_death_drop`, `repair_world_decay`, `restock_food`,
  `secure_danger`, `sort_chest`, `spread_work`, `till_soil`, `use_idle_station`;
- постройки (3): `more2_field_chest`, `more2_trail`, `storage_space`;
- события (24): `animal_starving`, `crop_dry`, `crop_ready`, `field_empty`, `furnace_needs_fuel`,
  `gear_kit_building`, `gear_kit_farming`, `gear_kit_mining`, `gear_kit_woodcutting`, `life_injury`,
  `orchard_empty`, `pen_full`, `repair_anvil`, `repair_brewing_stand`, `repair_clearing`,
  `repair_crafting_table`, `repair_fence`, `repair_furnace`, `repair_lighting`, `repair_mine`,
  `repair_orchard_oak`, `repair_smithing_table`, `repair_streets`, `storage_mixed`.

Проверка блока: `graphExport` → **200/200**; `sim_gpu` = `GraphSimulationTests`; `unit --fast-analyzers`
(8066 тестов), `pmdMain pmdTest` — зелёные. `graph-recipes.json` после выгрузки возвращён к main.

## 03.11-2 — тупик этапа 2 и мёртвые узлы

**Тупик этапа 2.** Причина не в материале и не в цикле: `warehouse`, `chicken_coop`, `pen_cow`,
`pen_sheep`, `pen_pig` — постройки, которыми владеет сторож (`StageCatalog.watcherOwned`).
`GraphPlanner.requireBuilding` намеренно не ставит их заданием графа (это держит
`WarehouseGraphNoticeTests`), а симуляция не изображала сторожей — поэтому этап 2 не закрывался.
Исправление в симуляции (Java `GraphSimulation` и Python `sim_gpu`): `unbuilt`/`promote` не считают
постройки сторожа препятствием этапу. Производственный граф и его правила не менялись: данные
`graph.json` не могут обойти `watcherOwned`, не сломав тест про лот склада.

Заодно исправлено схлопывание полосы построек в `GraphBuildOrder`: если нестроевое задание имело
приоритет `maxPriority − 1`, все постройки получали один и тот же `maxPriority` и теряли порядок.
Теперь начало полосы ограничено так, чтобы на `order.size()` значений осталось место; это вернуло
правило `plan_PlayerHouse_OutranksFamilyHouses` (и одинаково в Java и Python).

**Мёртвые узлы.** Добавлены входы, которые их вызывают:

- `farm`: события `crop_ready`, `crop_dry`, `animal_starving`, `pen_full`, `orchard_empty`,
  `field_empty`, `storage_mixed`, `furnace_needs_fuel`; `farmland` и `food_reserve` получили
  `xmin = 1`, поэтому заработали `till_soil`, `plant_crop`, `restock_food`; в мире есть `herd_cow`
  и пустой `milk_bucket` — работают `milk_cow` и `milk_animal`;
- `world`: повреждены все постройки, у которых есть `repair_*`, — сработали 13 ремонтов;
- `life`: добавлено событие `life_injury`;
- `gear`: четыре духа с разными занятиями и без инструментов — события `gear_kit_*` подаются
  (они только push, заданий не ставят, поэтому раньше считались мёртвыми по журналу заданий).

Осталось мёртвыми по id (но не по смыслу): 9 путей — `assign_chest_purpose`, `clear_trash`,
`home_night_work`, `raise_bottleneck_weight`, `recover_death_drop`, `repair_world_decay`,
`secure_danger`, `spread_work`, `use_idle_station`: это дубли путей, чьи события ставят задание
с другим id (`label_chest`, `dump_trash`, `night_work_home`, `boost_stage_bottleneck`,
`recover_spirit_drop`, `repair_leaf_litter`, `avoid_danger`, `reschedule_work`/`move_work`,
`load_station`). 2 постройки `more2_field_chest`, `more2_trail` — необязательные заглушки без
входа. 6 событий `crop_dry`, `pen_full`, `gear_kit_*` — только push, заданий не имеют и потому не
попадают в журнал; в мирах они подаются.

**Пересчёт.** Ограниченный прогон `--fixed "decay,way:CRAFT_*_AXE,event:*,way:dig"`:
9 поколений × 8 × 2 сида, доля **0.4682**, сумма 5498 → 4806 до исключений ради правил.
Метрики стартов (до 03.11-2 → после):

| Старт | До | После |
|---|---|---|
| `late3` | 656 | 331, этап 3 |
| `late3-half` | 193 | 113, этап 3 |
| `stage2-full` | 730, этап 2 (тупик) | 1483, этап 3 |
| `stage2-all` | 439, этап 2 (тупик) | 1171, этап 3 |
| `farm` | — | 55, этап 3 |
| `world` | 10 | 21, этап 3 |
| остальные области | 10–18 | 10–19, этап 3 |

Базовые миры Java: 84 / 1323 / 2612 / 1483 / 5868 шагов; Python совпадает. Этап 3 у
`stage2-full`/`stage2-all` пока не закрывается (другой тупик — нехватка материалов на постройки
этапа 3), но этап 2→3 пройден.

Исключения ради зелёных правил: `decay` 0.55, топоры 1.0, `cobblestone`/`stick` 36,
`coal` 28, `event:*` и `way:dig` 1.0. Проверка: `graphExport` → **200/200**; `sim_gpu` = Java;
`unit --fast-analyzers` 8068; `pmdMain pmdTest`; `gametest --full` 15/15.

## 03.11-3 — третий пересчёт: more4–7 и аудит

**Миры.** В main уже были миры `more4`, `more6`, `more7` и факты аудита в `_area_world`; не хватало
`more5`. Добавлен `_more5_world` (12 событий области), число духов в мирах областей поднято до 20,
чтобы все задания событий попадали в первый прогон и ни одно не терялось при завершении мира.
`_area_world` подаёт `missing`/`extra`/`wrong`/`auditPercent`, `Stray.builder` и `blocks`,
`Terrain.cut/fill/floating`; завершение ремонта чистит эти поля, уборки — `cut/fill/floating`.
Python-модель уже читала аудит (`GraphBuildingMaterials`, `GraphEventFacts`); добавлен тест
`test_audit_inputs`: недостача становится материалами ремонта, а вес ремонта растёт от
`auditPercent` и важности этапа. `test_dead_inputs` проверяет, что мир `more5` действительно
вызывает `more5_defend_player`, `more5_pickup_wood_drop`, `more5_light_mine`.

**Пересчёт.** 87 категорий (добавлены `event:more4…more7`). Ограниченный прогон
`--fixed "decay,way:CRAFT_*_AXE,way:dig"`: 9 поколений × 8 × 2 сида, доля **0.8184**,
сумма 4893 → 4800 до исключений. Категории событий на шагах не сказались (события одноразовые),
поэтому оставлены 1.0. Исключения ради зелёных правил: `decay` 0.55, топоры 1.0,
`cobblestone`/`stick` 36, `coal` 28, `orchard_oak` 50 (иначе полоса построек меняет порядок
«сад важнее при нехватке брёвен»), `way:dig` 1.0.

Метрики стартов (03.11-2 → 03.11-3):

| Старт | До | После |
|---|---|---|
| `late3` | 331 | 268, этап 3 |
| `late3-half` | 113 | 83, этап 3 |
| `stage2-full` | 1483 | 1430, этап 3 |
| `stage2-all` | 1171 | 1090, этап 3 |
| `life` / `store` | 12 / 19 | 12 / 19 |
| `farm` / `world` | 60 / 21 | 60 / 21 |
| `more4` / `more5` | — | 18 / 21 |
| `more6` / `more7` | 13 / 11 | 13 / 11 |
| `more` / `more2` / `more3` / `gear` / `dig` | 17 / 16 / 20 / 10 / 11 | без изменений |

Базовые миры Java: 84 / 1239 / 2594 / 1430 / 5275 шагов; Python совпадает по шагам, тикам и
порядку. Простоя в мирах областей нет; `stage2-full`/`stage2-all` доходят до этапа 3, но этап 3
пока не закрывают (нехватка материалов этапа 3 — отдельный тупик).

Мёртвые узлы не изменились: 9 путей-дублей (`assign_chest_purpose`, `clear_trash`,
`home_night_work`, `raise_bottleneck_weight`, `recover_death_drop`, `repair_world_decay`,
`secure_danger`, `spread_work`, `use_idle_station`), 2 необязательные заглушки
(`more2_field_chest`, `more2_trail`) и 6 push-only событий (`crop_dry`, `pen_full`, `gear_kit_*`).
Все новые пути и события more4–7 активируются.

Проверка: `graphExport` → **200/200** до и после; `sim_gpu` = `GraphSimulationTests`;
`unit --fast-analyzers` 8181; `pmdMain pmdTest`; `gametest --full` 15/15 (первый прогон упал
флаком `gatherscene_arenasteps_allpass` на исчезнувшей двери, отдельный и повторный прогон зелёные).

## 03.11-4 — четвёртый пересчёт: без сторожей-постановщиков

**Что изменилось.** Сняты сторожа-постановщики: задачи ставит только граф, `StageCatalog.watcherOwned`
всегда false, `WATCHER_OWNED` пуст. `GraphSimulation` и `sim_gpu` больше не пропускают склад и загоны:
этап 2→3 проходит честно, а `warehouse`/`pen_*` строятся узлами `building:*`. Заодно поправлен
`GraphBuildOrder`: нестроевые поставки не опускаются ниже своего приоритета (иначе «нет кирки —
булыжник важнее» терялось), а при равном `maxPriority` полоса построек не схлопывается.

**Сверка.** В Python найден настоящий источник расхождений: прямой матричный решатель
`(I − A)·p = r` давал другой результат, когда 200 итераций Java ещё не сходились (например,
`explore:tree:acacia` 88 против 78). Теперь `sim_gpu` считает ту же неподвижную точку плотными
итерациями на CUDA: 200 итераций, `EPSILON`, остановка по строкам — как `GraphFlow.solve`.
Симуляция снова совпадает с Java на всех базовых мирах.

**Пересчёт.** 87 категорий. Ограниченный прогон
`--fixed "decay,urgentBoost,way:dig,event:*,way:CRAFT_*,way:GATHER_MATERIALS"`:
9 поколений × 8 × 2 сида, доля **0.7979**, сумма 1776 → 1655 до исключений. Исключения ради
зелёных правил: `decay` 0.55, `urgentBoost` 0, `cobblestone`/`stick` 36, `coal` 28,
`orchard_oak` 50, `craft_*`/`chop_*` веса вдвое ниже найденных, `event:*` и `way:dig` 1.0.
Без этих ограничений `GraphPlannerTests` насыщает `maxPriority` и теряет порядок.

Метрики стартов (03.11-3 → 03.11-4):

| Старт | До | После |
|---|---|---|
| `late3` | 268 | 223, этап 3 |
| `late3-half` | 83 | 84, этап 3 |
| `stage2-full` | 1430, этап 3 (не закрыт) | **652, этап 3 закрыт** |
| `stage2-all` | 1090, этап 3 (не закрыт) | **453, этап 3 закрыт** |
| `farm` | 60 | 135 (включены `weave_wool` и `lure_animal`) |
| `world` / `life` / `store` | 21 / 12 / 19 | без изменений |
| `more4` / `more5` / `more6` / `more7` | 18 / 21 / 13 / 11 | без изменений |
| `more` / `more2` / `more3` / `gear` / `dig` | 17 / 16 / 20 / 10 / 14 | без изменений |

Базовые миры Java: 84 / 608 / 1007 / 652 / 7455 шагов; Python совпадает по шагам, тикам и порядку.
Простоя в мирах областей нет; все старты, кроме пустого и «10 духов» (предел решений), закрывают
этап 3.

Мёртвые узлы: 9 путей-дублей (`assign_chest_purpose`, `clear_trash`, `home_night_work`,
`raise_bottleneck_weight`, `recover_death_drop`, `repair_world_decay`, `secure_danger`,
`spread_work`, `use_idle_station`), 2 необязательные заглушки (`more2_field_chest`, `more2_trail`)
и 6 push-only событий (`crop_dry`, `pen_full`, `gear_kit_*`). `weave_wool` и `lure_animal` ожили
в мире `farm`.

Проверка: `graphExport` → **200/200** до и после; `sim_gpu` = `GraphSimulationTests`;
`unit --fast-analyzers` 8189; `pmdMain pmdTest`; `gametest --full` 15/15 (новых GameTest нет).

## 05.11-5 — пятый пересчёт: обратная связь мира и «грязный» участок

**Новые входы в Python.** Добавлены и проверены: память неудач (`failureBackoff`), время суток и
игрок из `GraphFeedback` (метки `more:player_near`, `more:dusk_soon`, `more:bad_trip_conditions`),
`stuck → rescue_spirit` (одно спасение, без цикла), сканер участка (strays/terrain/damage →
`demolish_stray`/`backfill`/`clear_floating`/`level_lot`/`repair_*`) и грядка `wheat_field` в плане.
`GraphChoice` в Python повторяет все 15 правил выбора; в `search.py` добавлены категории `choice:*`.

**Миры.** В базовые старты добавлены:
- `dirty` — 236 лишних построек, 760 ям, 120 висящих, 50 перепадов, damage у 4 построек;
- `blocked` — провалы путей `mine_cobble`/`chop_oak_log`/`mine_iron`, штраф за дальнюю дорогу,
  игрок рядом, сумерки, застрявший дух;
- `field` — `wheat_field` в плане, семена и земля, посадка.
Грязный мир успевает выполнить 43 шага расчистки/ремонта до конца этапа; `blocked` делает ровно
одно спасение и не зацикливается.

**Пересчёт.** 102 категории (добавлены 15 `choice:*`). Ограниченный прогон
`--fixed "decay,urgentBoost,way:dig,event:*,way:CRAFT_*,way:GATHER_MATERIALS"`:
9 поколений × 8 × 2 сида, доля **0.9783**, сумма 1808 → 1785 до правок. `choice:*` в итоге
оставлены нейтральными: `GraphChoiceTests` требует, чтобы граф по умолчанию не менял поведение.
Исключения: `decay` 0.55, `urgentBoost` 0, `coal` 28, `cobblestone`/`stick` 36,
`orchard_oak` 40, `event:*`/`way:dig` и `choice:*` 1.0.

Метрики стартов (03.11-4 → 05.11-5):

| Старт | До | После |
|---|---|---|
| `late3` | 223 | 224, этап 3 |
| `late3-half` | 84 | 81, этап 3 |
| `stage2-full` | 652 | 632, этап 3 |
| `stage2-all` | 453 | 450, этап 3 |
| `farm` | 135 | 135 |
| `more` / `more2` / `more3` | 17 / 16 / 20 | 12 / 15 / 12 |
| `more4` / `more5` / `more6` / `more7` | 18 / 21 / 13 / 11 | 14 / 19 / 11 / 6 |
| `dirty` / `blocked` / `field` | — | 43 / 10 / 43 |

Базовые миры Java: 84 / 606 / 802 / 632 / 7320 шагов; Python совпадает по шагам, тикам и порядку.

Мёртвые узлы: путей — **0**, построек — 2 необязательные заглушки (`more2_field_chest`,
`more2_trail`), событий — 8 push-only без заданий (`crop_dry`, `pen_full`, `gear_kit_*`,
`more6_spirits_short`, `more7_field_full`); они подаются и меняют веса, но в журнал заданий не
попадают.

**Естественный рельеф и `level_lot`.** Да, похоже на ложные срабатывания: `WorldAreaInputs`
считает `cut` по `found.mounds()` всего участка, а `GraphWorldEvents` поднимает `level_lot` при
любом непустом `cut`. Живые ~3263 «насыпи» — это, скорее всего, естественные холмы. Пока вес
`level_lot` не повышен: base 45, `event:*` заморожены. Предложение: считать `cut/fill` только
внутри контура лота/плана и только относительно схемы (не природного рельефа); природный склон
помечать `natural`/`unknown` и не давать ему событие. Это отдельная правка сканера и тестов
`WorldAreaInputs`; без неё `level_lot` в поиске лучше не усиливать.

**Техдолг.** Удалён мёртвый `neoforge/BuildStands.java` (на него не было production-ссылок).
`choice:strategicBoost`/`bottleneckBoost`/`idleStationBoost`/`spaceFoundBoost` — бонусы с
нулевым значением, множитель поиска на них не действует; нужен отдельный аддитивный параметр,
если их真的要 подбирать. Грязный мир заканчивает этап раньше, чем исчерпывает 236/760 задач
расчистки — для полного прогона нужен отдельный «стресс»-мир без финишной постройки.

Проверка: `graphExport` → **200/200**; `sim_gpu` = `GraphSimulationTests`;
`unit --fast-analyzers` 8361; `pmdMain pmdTest`; `gametest --full` 18/18.

## 05.11-6 — шестой пересчёт: живой этап 1

**Синхрон модели с влитым.** Python догнал правки 05.11:
- закрытая добыча повторяется: симуляция не держит DONE-метку, `dig_dirt` выдаётся снова, пока улицам
  нужна земля (`test_live_stage_one` — 36 выдач); в Java `GraphPost.gather` и `GraphSource` переоткрывают DONE;
- земля берётся с `soil`: `dig_dirt` читает `world: soil`, без него остаётся `dig_dirt_route` с лопатой;
- инструмент — условие: без кирки `mine_cobble`/шахта не выполнимы, граф ставит крафт; без лопаты
  `dig_dirt_route` не выполним;
- жизнь без штрафа: `failure_count` не считает `rescue_spirit` и `event:life_*` (тест на равный приоритет);
- природный рельеф — не работа: `terrain.natural` не поднимает `level_lot`/`backfill`;
- спасение снимает застревание, выход из ловушки — замуровку: `GraphWorld.rescued()/freed()` и
  `GraphSimulation.finish` теперь как Python (иначе Java повторяла `rescue_spirit`/`restore_trace`, 15 вместо 2);
- `search.py --fixed` понимает шаблоны (`event:*`, `way:CRAFT_*`); раньше заморозка молча не работала.

**Живой этап 1.** Новый вход `GraphMutations.liveStageOne()` (выгружается в `TestResults/graph-export/live1.json`,
Python читает его же): улицы 819/1422 с недостачей 400 земли, сад 6/24, шахта без кирки, грядка 5×10
не начата, 2 застрявших духа, 240 лишних построек, 180 висящих, 4 духа без инструментов.
`GraphLiveStageOneTests` и `test_live_stage_one` проверяют, что этап 1 закрывается целиком:
кирка → шахта, карьер → улицы, грядка, сад, спасения, уборка; повторов ≤ 60, заданий ≤ 200.
С весом `craft_stone_pickaxe` = 0.643316101 этап 1 закрывается за **97 заданий / 4380 тиков**.
Держат этап: 36 выдач земли (недостача 400), 34 сноса лишних построек из 240, 6 крафтов факелов,
6 добыч булыжника, 2 спасения; после закрытия этапа уборка продолжается уже на этапе 2.

**Пересчёт.** Категорий — 102, как в 05.11-5. Прогоны ES (`--generations 8 --population 8 --seeds 2
--limit 1600`, заморожены `decay,urgentBoost,choice:*,event:*,way:CRAFT_*,way:dig`):
- без жилого мира: сумма стартов 1868 → **1867**, живой этап 106;
- с живым этапом в каждом поколении: 1868 → 1900 (живой этап 94, остальные хуже) — не перенесён;
- точечная чувствительность по живому этапу (221 вектор): лучший `item:stage1` ×0.7 даёт 1836/91,
  но категория включает сгенерированные рецепты, а `graph.json` их веса не несёт; перенос только ручных
  предметов ломал пустой мир (84 → 103 шага), поэтому не внесён.

Внесена одна проверенная правка: `craft_stone_pickaxe` 0.919023001 → **0.643316101** (×0.7).
Она улучшает живой этап и общий счёт, не меняя остальные старты; `GraphPlannerTests` (63) зелёные.

Метрики (шаги; 5 базовых миров Java + 21 старт поиска):

| Старт | До | После |
|---|---|---|
| `live1` (живой этап 1) | 105 | **97**, этап 2 за 4380 тиков |
| базовые миры Java | 84 / 606 / 802 / 632 / 2814 | 84 / 606 / 802 / 632 / **2746** |
| `late3` / `late3-half` | 224 / 81 | 224 / 81 |
| `stage2-full` / `stage2-all` | 632 / 450 | 632 / 450 |
| области `life`…`dig` | 12 / 19 / 135 / 21 / 10 / 12 / 15 / 12 / 14 / 19 / 43 / 10 / 23 / 6 / 11 / 14 | без изменений |
| **Сумма** | **6806** | **6730** |

**Мёртвые узлы.** В стартах пересчёта не активировались:
- пути: `assign_chest_purpose`, `clear_trash`, `recover_death_drop`, `secure_danger` — дубли, чьи события
  ставят задание с другим id; `transfer_item`, `sort_chest` — склад делает `store:carry:*`/`store:sorted`;
  `dig_dirt_route` — нужна необязательная `route_dirt`, земля идёт с `soil`;
- постройки: 2 необязательные заглушки `more2_field_chest`, `more2_trail`;
- события: 8 push-only без заданий — `crop_dry`, `pen_full`, `gear_kit_*` (4), `more6_spirits_short`,
  `more7_field_full`.
Ожили в новых стартах `home_night_work`, `raise_bottleneck_weight`, `repair_world_decay`, `spread_work`,
`use_idle_station`, `till_soil`, `plant_crop`, `harvest_crop`, `dig_dirt`, `craft_stone_pickaxe`.
Авторецепты (`auto_*`) и узлы неподанных измерений (Nether/End/биомы) в счёт мёртвых не входят.

**Исключения.** `craft_stone_pickaxe` — единственная правка; остальные веса не менялись. `decay` 0.55,
`urgentBoost` 0, `choice:*` нейтральны, `event:*`, `way:dig` и крафты инструментов, кроме кирки, — 1.0,
`coal` 28, `cobblestone`/`stick` 36, `orchard_oak` 40 остались прежними.

Проверка: `graphExport` → **200/200**; `sim_gpu` = `GraphSimulationTests` по шагам
(84 / 606 / 802 / 632 / 6557); `GraphLiveStageOneTests` 2/2; `unit --fast-analyzers` **8468**;
`pmdMain pmdTest`; `gametest --full` **19/19**.

## Найдено в данных Java (не правил)

- В слиянии графа два разных пути имеют один `id = craft_bread`: ручной делает `food` (weight 0.7),
  сгенерированный — `bread` (weight 1.0). Java `SupplyGraph.way("craft_bread")` возвращает первый
  (ручной), а `producers("bread")` видит второй; в заданиях оба неотличимы по id. Модель повторяет
  поведение Java (первая запись по id), поэтому сверка проходит, но данные стоит развести по id.
- GameTest-сервер падает в `ContainerLid.playChestSound` (`Cannot get property ChestType ... in Block{air}`):
  сундук снят, пока крышка открыта, а звук закрытия читает `ChestBlock.TYPE` уже у воздуха.
  Падение воспроизводится и на весах HEAD, то есть от переноса весов не зависит.

## Что не сделано

- Полный `./gradlew mutationTest` запускался по заданию recipesync: процесс Gradle умер через 70 мин
  (exit -1, не assertion); отчётный прогон `simulate_Baselines_WriteTrailReport` зелёный.
- Веса сгенерированных рецептов (`auto_*`) не подбираются: их пересобирает `graphExport`, поэтому
  `search.py.searchable` их замораживает (вес 1.0/0.1 задаёт импорт). Старые ключи `auto_*` в
  `best-weights.json` — исторический артефакт, в перенос не идут.
- Полный GameTest в этом заходе зелёный (19/19, 112 с), включая сценарии первого этапа.
- Модель проверена на 200 контрольных состояниях, 5 базовых мирах и живом этапе 1; другие старты
  и мутации сверяются тем же `test_conformance.py` после `./gradlew graphExport`.

## Запуск

```bash
./gradlew graphExport
cd tools/graph-gpu
PYTHONIOENCODING=utf-8 py -3.9 test_conformance.py
PYTHONIOENCODING=utf-8 py -3.9 sim_gpu.py
PYTHONIOENCODING=utf-8 py -3.9 search.py --mode sensitivity
PYTHONIOENCODING=utf-8 py -3.9 search.py --mode es --generations 10 --population 16 --seeds 1
```
