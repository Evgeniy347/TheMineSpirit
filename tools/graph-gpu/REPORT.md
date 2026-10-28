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
