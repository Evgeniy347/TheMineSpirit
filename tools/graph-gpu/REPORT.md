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

## Найдено в данных Java (не правил)

- В слиянии графа два разных пути имеют один `id = craft_bread`: ручной делает `food` (weight 0.7),
  сгенерированный — `bread` (weight 1.0). Java `SupplyGraph.way("craft_bread")` возвращает первый
  (ручной), а `producers("bread")` видит второй; в заданиях оба неотличимы по id. Модель повторяет
  поведение Java (первая запись по id), поэтому сверка проходит, но данные стоит развести по id.
- GameTest-сервер падает в `ContainerLid.playChestSound` (`Cannot get property ChestType ... in Block{air}`):
  сундук снят, пока крышка открыта, а звук закрытия читает `ChestBlock.TYPE` уже у воздуха.
  Падение воспроизводится и на весах HEAD, то есть от переноса весов не зависит.

## Что не сделано

- `./gradlew mutationTest` не запускался — по правилам нужно согласие владельца. Для сверки
  симуляции отдельно прогонялся только `simulate_Baselines_WriteTrailReport` временной задачей.
- Полный GameTest не завершён зелёным: на машине одновременно работают чужие рабочие деревья,
  прогон упирается в 300-секундный предел харнесса; в одном завершившемся прогоне упал помеченный
  `[флак]` сценарий `firststage_generatedworldthreespirits_plotbuiltbyplan`, а на весах HEAD тот же
  набор падает в `ContainerLid` (см. выше). Java-код не менялся.
- Веса категорий сгенерированных рецептов (`graph-recipes.json`) в `graph.json` не переносятся:
  файл рецептов генерируется заново. Внесены только ручные предметы, постройки и пути.
- Модель проверена на 200 контрольных состояниях и 5 базовых мирах; другие старты и мутации
  сверяются тем же `test_conformance.py` после `./gradlew graphExport`.

## Запуск

```bash
./gradlew graphExport
cd tools/graph-gpu
PYTHONIOENCODING=utf-8 py -3.9 test_conformance.py
PYTHONIOENCODING=utf-8 py -3.9 sim_gpu.py
PYTHONIOENCODING=utf-8 py -3.9 search.py --mode sensitivity
PYTHONIOENCODING=utf-8 py -3.9 search.py --mode es --generations 10 --population 16 --seeds 1
```
