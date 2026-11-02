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
