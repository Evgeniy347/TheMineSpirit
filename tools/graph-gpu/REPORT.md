# Веса графа на видеокарте — отчёт

## Что сделано

| Файл | Назначение |
|---|---|
| `graph_model.py` | Перенос Java (`GraphPlanner`, `GraphFlow`, `SupplyGraph`, `GraphCosts`, `GraphState`, `GraphEvent`) один в один |
| `test_conformance.py` | Сверка с `conformance.json` (200 состояний) |
| `sim_gpu.py` | Симуляция во времени: тики, дорога, износ, станции; пакет миров на torch/CUDA |
| `search.py` | Поиск 43 множителей-категорий в пределах ±50 % |
| `best-weights.json` | Результат короткого проверочного запуска (формат категорий; заменить длинным поиском) |

Граф загружается как в Java: `board/graph.json` + `board/graph-recipes.json`.
Добавлены `costs`, `buildStartShare`, `distances`/`stations`, задания `station:*`,
дорога (`travelFactor`), `share` при старте постройки, одна запись пути по id — первая, как `SupplyGraph.way`.

## Сверка с Java

`PYTHONIOENCODING=utf-8 py -3.9 test_conformance.py` — **200/200**: набор заданий, `feasible`
и `chance` совпадают точно, `priority` расходится не больше чем на 1.

Симуляция сверена с Java-прогоном `GraphSimulationTests.simulate_Baselines_WriteTrailReport`
(запускался отдельно, не через `mutationTest`). Совпали шаги, тики и порядок заданий (id + priority)
по всем 5 базовым мирам `GraphMutations.baselines()`:

| Старт | Шагов | Тиков | Этап в конце | Python = Java |
|---|---|---|---|---|
| 0 — пусто | 72 | 54 900 | 1 (нет выполнимых заданий) | да, путь совпал |
| 1 — обжитый | 4000 (предел решений) | 524 840 | 2 | да, путь совпал |
| 2 — тесно | 4398 | 238 900 | 2 | да, путь совпал |
| 3 — этап 2 | 4000 (предел решений) | 528 300 | 2 | да, путь совпал |
| 4 — 10 духов | 7004 | 112 560 | 2 | да, путь совпал |

Число шагов может превышать предел решений: за одно решение задание берут несколько духов.
Незавершённые миры — не расхождение: Java при тех же входных данных останавливается там же.

## Поиск

Параметров — **43** (категории, не каждый путь):

- 3 скаляра: `decay`, `urgentBoost`, `buildReadyBoost`;
- 5 видов предметов: `perSpirit` и этапы 1–4;
- 4 вида построек: этапы 1–4;
- 31 вид путей по `BoardCase` (`GATHER_MATERIALS`, `CRAFT_*`, `FISH`, …).

Множитель категории применяется ко всем её весам. Короткая проверка
`search.py --mode es --generations 1 --population 4` отрабатывает и пишет `best-weights.json`.
В ней все кандидаты получили штраф: стартовые миры поиска на текущем (выросшем) графе
не успевают за 4000 решений; длинный поиск запускает главный агент, при необходимости с бо́льшим `--limit`.

## Найдено в данных Java (не правил)

В слиянии графа два разных пути имеют один `id = craft_bread`: ручной делает `food` (weight 0.7),
сгенерированный — `bread` (weight 1.0). Java `SupplyGraph.way("craft_bread")` возвращает первый
(ручной), а `producers("bread")` видит второй; в заданиях оба неотличимы по id. Модель повторяет
поведение Java (первая запись по id), поэтому сверка проходит, но данные стоит развести по id.

## Что не сделано

- `./gradlew mutationTest` не запускался — по правилам нужно согласие владельца.
- Длинный поиск весов не запускался; `best-weights.json` содержит только проверочный прогон
  (все множители 1.0, сумма 18 000).
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
