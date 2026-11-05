# graph-gpu — модель графа и подбор весов на видеокарте

Python-модель графа потребностей поселения и поиск весов. Источник истины — Java-код мода;
Python нужен только для быстрой пакетной симуляции и подбора коэффициентов.

## Окружение

- Python 3.9 (`py -3.9` на Windows);
- PyTorch 2.7.1+cu118 (CUDA), numpy;
- GTX 1060 6 ГБ достаточно: матрицы `(I − A)` имеют размер ~300×300 на мир.

Ничего устанавливать не нужно, если окружение уже собрано. Версии зависимостей выбирает владелец.

## Выгрузка из Java

```bash
./gradlew graphExport
```

Пишет:

- `TestResults/graph-export/graph.json` — копия ручного `board/graph.json`;
- `TestResults/graph-export/conformance.json` — 200 состояний с заданиями Java (`id`, `priority`,
  `feasible`, `chance`).

Java грузит граф как слияние `board/graph.json` + `board/graph-recipes.json`
(`SupplyGraph.loadDefault`/`merge`). Модель делает то же самое: `Graph.load_merged` читает
`graph.json` из выгрузки и `graph-recipes.json` из `src/main/resources`. Правила слияния — как в Java:
ручные `items`/`buildings`/`groups` главнее, сгенерированные пути добавляются, если их `makes` ещё нет.

## Проверка модели

```bash
cd tools/graph-gpu
PYTHONIOENCODING=utf-8 py -3.9 test_conformance.py
```

Ожидаемо: `OK: 200 случаев совпали с Java`. Сверяются набор заданий, `feasible`, `chance`;
`priority` — с допуском ±1.

## Симуляция во времени

```bash
PYTHONIOENCODING=utf-8 py -3.9 sim_gpu.py
```

`sim_gpu.py` повторяет `GraphSimulation`: тики заданий из `costs` (`caseTicks`, `way.ticks`),
дорога до ресурса (`world` → `distances` → `travel`), износ инструментов (`toolDurability`,
`way.uses`), станции (`stationCapacity`, задания `station:*`), события, этапы 1–3.

Сверка — с Java-прогоном `GraphSimulationTests.simulate_Baselines_WriteTrailReport`
(`TestResults/mutation/graph-trail.md`): шаги, тики и порядок заданий по 5 базовым мирам
`GraphMutations.baselines()` совпадают точно (78/2077/4302/2266/6729 шагов).

## Перебор мутаций на видеокарте

```bash
PYTHONIOENCODING=utf-8 py -3.9 mutation_gpu.py --sample 3 --limit 1200
```

`mutation_gpu.py` берёт `TestResults/mutation/simulation.json` (его пишет `GraphGpuMutationTests` из Java),
считает те же миры и сверяет шаги/этап/финиш (Python = Java), затем гоняет пачку миров с мутациями
`GraphMutations` (постройки, события, предметы, запас) на CUDA. Сид ротации — `MUTATION_SEED`; полный
перебор не нужен, набор и бюджет держит `docs/TESTING.md`.

## Поиск весов

```bash
PYTHONIOENCODING=utf-8 py -3.9 search.py --mode sensitivity
PYTHONIOENCODING=utf-8 py -3.9 search.py --mode es --generations 10 --population 16 --seeds 1
PYTHONIOENCODING=utf-8 py -3.9 search.py --mode es --generations 10 --population 16 --seeds 2 --init best-weights.json
```

- 102 множителя-категории в пределах ±50 %, а не по каждому пути:
  базы предметов (`perSpirit`/этап), базы построек (этап), веса путей по `BoardCase`
  (включая `way:dig`), полосы событий областей
  (`event:life/store/farm/gear/more2/more3/more4/more5/more6/more7`),
  `choice:*` (правила выбора), `decay`, `urgentBoost`, `buildReadyBoost`;
- метрика — сумма шагов до конца этапа 3 по стартам; застрявший мир получает штраф `limit + 500`;
- каждое поколение считается одним пакетом миров, линейная система решается на CUDA.

Результат — `best-weights.json`: множители категорий (`parameters`), они же по конкретным весам
(`weights`) и готовые абсолютные значения (`absolute`). Файл `graph.json` скрипт не меняет.
Длинный поиск запускает главный агент; `--limit` задаёт предел решений на мир (по умолчанию 4000).

Текущий прогон (`--generations 9 --population 8 --seeds 2 --limit 1600 --fixed ...`): доля 0.9783,
сумма шагов 1808 → 1785 до правок ради зелёных правил. В `board/graph.json` и `graph.d` перенесены
абсолютные значения; `decay` внесён 0.55, иначе правила `GraphPlannerTests` упираются в
`maxPriority`. Подробности, тупик этапа 2 и список мёртвых узлов — `REPORT.md`, разделы 03.11, 03.11-2, 03.11-3, 03.11-4 и 05.11-5.

## Файлы

| Файл | Что делает |
|---|---|
| `graph_model.py` | Правила `GraphPlanner`/`GraphFlow`/`SupplyGraph`/`GraphCosts`/`GraphState` на Python |
| `sim_gpu.py` | `GraphSimulation`/`GraphWorld`: время, тики, пакет миров, этапы 1–3 |
| `mutation_gpu.py` | Перебор мутаций на CUDA и сверка выборки с Java |
| `search.py` | Поиск множителей по категориям и отчёт по стартам |
| `test_conformance.py` | Сверка с выгрузкой Java |
| `REPORT.md` | Результаты сверки и поиска |
