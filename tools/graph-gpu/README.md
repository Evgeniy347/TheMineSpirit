# graph-gpu — модель графа и подбор весов на видеокарте

Python-модель графа потребностей поселения и поиск весов. Источник истины — Java-код мода;
Python нужен только для быстрой пакетной симуляции и подбора коэффициентов.

## Окружение

- Python 3.9;
- PyTorch 2.7.1+cu118 (CUDA), numpy;
- GTX 1060 6 ГБ достаточно: матрицы `(I − A)` имеют размер ~200×200 на мир.

Ничего устанавливать не нужно, если окружение уже собрано. Версии зависимостей выбирает владелец.

## Выгрузка из Java

```bash
./gradlew graphExport
```

Пишет:

- `TestResults/graph-export/graph.json` — копия `board/graph.json`;
- `TestResults/graph-export/conformance.json` — 200 состояний с заданиями Java (`id`, `priority`,
  `feasible`, `chance`).

## Проверка модели

```bash
cd tools/graph-gpu
python test_conformance.py
```

Ожидаемо: `OK: 200 случаев совпали с Java`. Сверяются набор заданий, `feasible`, `chance`;
`priority` — с допуском ±1.

## Симуляция

```bash
python sim_gpu.py
```

Симулирует `GraphMutations.baselines()` (5 миров) пакетом на torch и печатает шаги до конца этапа 3.
Шаги совпадают с Java-прогоном тех же миров.

## Поиск весов

```bash
python search.py --mode sensitivity   # вклад групп весов
python search.py --mode es --generations 10 --population 16 --seeds 1
python search.py --mode es --generations 10 --population 16 --seeds 2 --init best-weights.json
```

- 489 множителей в пределах ±50 %: базы предметов, базы построек, веса путей, `decay`,
  `urgentBoost`, `buildReadyBoost`;
- метрика — сумма шагов до конца этапа 3 по стартам; застрявший мир получает штраф `limit + 500`;
- каждое поколение считается одним пакетом миров, линейная система решается на CUDA.

Результат — `best-weights.json`: множители (`parameters`), они же по видам (`weights`) и готовые
абсолютные значения (`absolute`). Файл `graph.json` скрипт не меняет.

## Файлы

| Файл | Что делает |
|---|---|
| `graph_model.py` | Правила `GraphPlanner`/`GraphFlow`/`SupplyGraph`/`GraphState` на Python |
| `sim_gpu.py` | `GraphSimulation`/`GraphWorld`: пакет миров, выбор заданий, этапы 1–3 |
| `search.py` | Эволюционный поиск весов и отчёт по стартам |
| `test_conformance.py` | Сверка с выгрузкой Java |
| `REPORT.md` | Результаты сверки и поиска |
